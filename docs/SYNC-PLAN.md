# 고찌봄(Tiki-Taka) FE ↔ BE 싱크 계획서

> 작성 기준: FE `gojjibom-fe`(commit `8d33e0b`), BE `Tiki-Taka-BE`(PR #4 머지 시점).
> 목적: 프론트가 실제로 소비하는 계약과 백엔드가 실제로 구현한 계약의 간극을 메우고, 뒤쳐진 BE를 데모 가능한 상태까지 끌어올리는 실행 계획.

---

## 0. 결론 먼저

- **FE와 BE는 "같은 제품이지만 서로 만나지 않는 두 API"를 보고 있다.** FE는 화면 2개를 채우는 **집계(aggregate) 엔드포인트 2개**만 호출하는데, 그 경로(`/senior/today`, `/caregiver/board`)는 BE에 **존재하지 않는다.** BE는 대신 granular REST(`/api/v1/care-groups`, `/seniors/{id}/prescriptions`, `/seniors/{id}/dose-schedules`)를 구현했고, 이건 FE가 호출하지 않는다.
- **BE가 뒤쳐진 핵심은 "복약 이벤트(dose_events) + 복용 확인(confirm)"이 통째로 없다는 것.** 이게 제품의 심장인데 미구현이다. 대시보드 집계, 재고, 알림/에스컬레이션, 미디어/TTS도 없다.
- **계약 표현이 층위별로 불일치한다:** 경로 prefix(`/api` vs `/api/v1`), 응답 envelope(raw vs `{data,error}`), enum 대소문자(`done` vs `TAKEN`), ID 타입(string vs Long), 표시 문자열(FE가 `'오후 7:30'` 같은 한국어 포맷 완성본을 기대).
- **권장 전략:** BE에 **FE 형태를 그대로 반환하는 BFF 집계 엔드포인트 2개**를 추가(Option A). FE 변경은 base URL·envelope 언랩·쓰기 연결로 최소화. 그와 별개로 dose_events/confirm은 어느 전략이든 신규 구현 필요.
- **현재 FE 배포는 `VITE_DEMO_MODE=true`** 라 BE를 아예 호출하지 않는다. 즉 지금 BE 작업은 데모에 보이지 않는다. 엔드포인트가 살아난 뒤 스테이징에서 플래그를 끄고(폴백은 유지) 검증해야 한다.

한 줄 요약: **BE는 "뒤쳐진" 게 아니라 "다른 모양으로 앞서갔다". dose_events/confirm를 새로 만들고, FE가 기대하는 2개 집계 엔드포인트를 BFF로 덮어씌우는 게 최단 경로다.**

---

## 1. 현재 상태 스냅샷

### 1-1. 프론트엔드 (`gojjibom-fe`)

| 항목 | 실태 |
| --- | --- |
| 스택 | React 19, React Router 7, Vite 7, TypeScript, Tailwind 3 |
| 배포 | Firebase Hosting `gojjibom.web.app`, CI에서 `VITE_API_BASE_URL=https://api.stdiodh.xyz/api`, **`VITE_DEMO_MODE=true`** |
| API 클라이언트 | `src/lib/api.ts` — 함수 **2개뿐** |
| 실제 호출 화면 | `SeniorHomePage`(`/senior/today`), `CaregiverDashboardPage`(`/caregiver`) **2개만** |
| 나머지 14개 라우트 | 네비게이션 전용 또는 **하드코딩 mock**(예: `ManagePage`의 구성원 목록·초대코드 `842 196`) |
| 쓰기(POST/PATCH/DELETE) | **api.ts에 하나도 없음.** 등록·로그인·복용확인·초대·승인·사진 공유는 전부 로컬 상태 또는 `setTimeout` 후 네비게이션 |
| envelope 처리 | `getJson`이 `response.json() as T` — **envelope를 벗기지 않고 raw 객체를 기대** |
| README | **stale.** 예전 "기억카드" 제품을 설명. 실제 코드(복약 도메인)가 진실 |

FE가 실제로 부르는 것 (딱 이게 전부):

```ts
// src/lib/api.ts  (base = env.apiBaseUrl = https://api.stdiodh.xyz/api)
fetchSeniorDay()      -> GET `${base}/senior/today`     -> SeniorDay
fetchCaregiverBoard() -> GET `${base}/caregiver/board`  -> CaregiverBoard
// demoMode=true면 fixture 반환, 실패 시에도 fixture로 폴백
```

라우트 전체(16개): `/onboarding`, `/senior`(=알람), `/senior/today`, `/senior/register`, `/senior/connected`, `/senior/dose`, `/senior/done`, `/senior/photo`, `/senior/alerts`, `/senior/login`, `/senior/alarm`, `/senior/camera`, `/caregiver`, `/caregiver/pills`, `/caregiver/timeline`, `/caregiver/manage`.

### 1-2. 백엔드 (`Tiki-Taka-BE` / `gojjibom-api`)

| 항목 | 실태 |
| --- | --- |
| 스택 | Kotlin 2.3.20, Spring Boot 4.1.0(Java 21), PostgreSQL 16, Flyway, springdoc |
| 서버 | context-path 없음. 라우트는 `/api/v1/...`, health는 `/api/health`, Swagger `/swagger-ui/index.html` |
| envelope | `ApiResponse<T> = { data, error }` (모든 응답 래핑) |
| 인증 | **없음.** `ownerUserId` / `actorUserId` / `pharmacistUserId`를 파라미터로 받아 멤버십만 검증 |
| 머지 이력 | PR #2(spec 001 케어그룹+초대), PR #4(spec 002 처방+복약스케줄) |
| 마이그레이션 | `V1__baseline`, `V3__care_groups_and_invites`, `V4__prescriptions_and_dose_schedules` (**V2 결번** — 미디어 PR용으로 예약/보류 추정) |
| 테스트 | `CareGroupIntegrationTest`, `PrescriptionIntegrationTest` |

BE가 실제로 구현한 엔드포인트:

| Method | Path | 반환(엔벨로프 내부) |
| --- | --- | --- |
| POST | `/api/v1/care-groups` | `CareGroupResponse` |
| GET | `/api/v1/care-groups/{id}` | `CareGroupResponse` |
| POST | `/api/v1/care-groups/{id}/invite-links` | `InviteLinkResponse` |
| POST | `/api/v1/invites/{token}:accept` | `CareGroupMemberResponse` |
| PATCH | `/api/v1/care-groups/{id}/members/{memberId}` | `CareGroupMemberResponse` |
| DELETE | `/api/v1/care-groups/{id}/members/{memberId}?actorUserId=` | `CareGroupMemberResponse` |
| POST | `/api/v1/seniors/{seniorId}/prescriptions` | `PrescriptionResponse` |
| GET | `/api/v1/seniors/{seniorId}/dose-schedules?actorUserId=` | `DoseScheduleListResponse` |
| GET | `/api/health` | health |

**미구현(=간극):** dose_events(일자별 복약 인스턴스), 복용 확인, `senior/today`·`caregiver/board` 집계, 대시보드/타임라인, 재고(inventory), 알림/에스컬레이션, 미디어/TTS, 어르신 기기 페어링.

---

## 2. 갭 분석

### 2-1. 화면 ↔ 데이터 ↔ BE 엔드포인트 매핑

| FE 라우트 | 필요한 데이터 | FE가 지금 부르는 것 | BE 현재 | 상태 |
| --- | --- | --- | --- | --- |
| `/onboarding` | 정적 안내 + 진입점 | 없음(네비게이션) | 없어도 됨 | ✅ |
| `/senior/register` | 어르신·보호자 등록 | 없음(로컬, setTimeout) | `POST /care-groups` **존재** | ⚠️ 미연결 |
| `/senior/connected` | 연결 확인 | 없음 | `GET /care-groups/{id}` 존재 | ⚠️ 미연결 |
| `/senior/login` | 기기 페어링 | 없음(네비게이션) | **미구현** | ❌ |
| `/senior`,`/senior/alarm` | 다음 약 알람 | 없음(정적/`SeniorDay` 재사용) | 집계에서 파생 | ❌(집계) |
| `/senior/today` | 오늘 복약 전체 | `GET /senior/today`→`SeniorDay` | **미구현** | ❌ |
| `/senior/dose` | 봉지 상세 | 없음(로컬) | dose-event 상세 **미구현** | ❌ |
| `/senior/done` | 확인 결과 | 없음(로컬) | **confirm 미구현** | ❌ |
| `/senior/camera` | 복용 사진 공유 | 인메모리 `shareStore` | 미디어 **미구현** | ❌(후순위) |
| `/senior/photo` | 약 사진 보기 | 없음(정적) | 미디어 **미구현** | ❌(후순위) |
| `/senior/alerts` | 어르신 알림 | 없음(정적) | notifications **미구현** | ❌(후순위) |
| `/caregiver` | 대시보드 집계 | `GET /caregiver/board`→`CaregiverBoard` | **미구현** | ❌ |
| `/caregiver/manage` | 구성원·초대 관리 | **하드코딩** | invite/member API **존재** | ⚠️ 미연결 |
| `/caregiver/pills` | 약 개수 | 없음(정적) | inventory **미구현** | ❌(후순위) |
| `/caregiver/timeline` | 이벤트 타임라인 | 없음(정적) | timeline **미구현** | ❌(후순위) |

핵심: **❌(집계) 2개 + confirm/ dose-event = MVP 데모의 필수 경로.** ⚠️ 2개는 BE가 이미 있으니 FE 연결만 하면 됨.

### 2-2. 계약 표현 불일치 (전 구간 공통)

| 축 | FE 기대 | BE 현재 | 조치 |
| --- | --- | --- | --- |
| 경로 prefix | base `.../api` + `/senior/today` | 실제 라우트 `/api/v1/...` | **`/api/v1`로 표준화** → FE base를 `https://api.stdiodh.xyz/api/v1`로 |
| 응답 envelope | raw JSON(`as T`) | `{ data, error }` | **FE에서 `body.data` 언랩** (BE 일관성 유지) |
| enum(복약상태) | `done`/`upcoming`/`missed` | `SCHEDULED`/`TAKEN`/`MISSED`/`SKIPPED` | **BFF DTO에서 소문자 매핑** (`TAKEN→done`, `SCHEDULED→upcoming`, `MISSED→missed`; `SKIPPED`는 표현 미정) |
| enum(확인수단) | `voice`/`button` | `VOICE`/`BUTTON` | BFF DTO에서 소문자 매핑 |
| ID 타입 | `string` | `Long`(JSON number) | **BFF DTO에서 문자열 직렬화** 또는 FE에서 `String()` 수용 |
| 표시 문자열 | `'2026년 7월 2일 목요일'`, `'오후 7:30'`, `'식후 30분'`, `'D-13'`, `'7월 14일'` | 원시 date/time/enum | **BFF 매퍼가 Asia/Seoul·한국어로 포맷** (granular API는 ISO 유지) |
| `Pill.shape` | `round`/`oval`/`capsule` | **컬럼 없음** | medications에 `shape` 추가 or 기본값 매핑(결정 필요) |

---

## 3. 싱크 전략 결정

### Option A — BE에 FE-형태 BFF 집계 엔드포인트 추가 ✅ 권장
- BE가 `GET /api/v1/senior/today`, `GET /api/v1/caregiver/board`를 추가하고, **`SeniorDay`/`CaregiverBoard`와 1:1로 동일한 DTO**(소문자 enum, string id, 한국어 포맷 포함)를 반환.
- 이 BFF는 내부적으로 기존 granular 서비스(care-group, prescription, dose-event)를 조합.
- FE 변경: base URL, envelope 언랩, 쓰기 연결만. **읽기 화면 2개는 타입·컴포넌트 무변경.**
- 장점: 데모까지 최단. FE 리스크 최소. 단점: BE가 표현 포맷을 진다 → **전용 매퍼 계층에 격리**하면 수용 가능(해커톤 맥락).

### Option B — FE를 granular REST + envelope로 리팩터링
- FE 데이터 계층 재작성, 화면당 여러 호출 조합, 타입 재정의.
- 장점: REST 순수성. 단점: FE 작업량·리스크 큼, 데모 전 부적합.

> **결정: Option A.** 단, dose_events/confirm 신규 구현은 공통 필수. granular API는 도메인-네이티브 표현(대문자 enum, Long, ISO) 유지 / BFF 2개만 FE 표현으로 맞춘다. 이 이중 표현이 계약 경계를 깔끔하게 만든다.

---

## 4. 표준 계약 규칙 (양 팀 합의 대상)

1. **공통 prefix:** `/api/v1`. FE `.env`의 `VITE_API_BASE_URL=https://api.stdiodh.xyz/api/v1`, 경로 문자열은 `/senior/today`·`/caregiver/board` 유지.
2. **Envelope:** 모든 응답 `{ data, error }`. FE `getJson`은 `return (await res.json()).data as T`로 언랩. 에러 시 `error.code/message` 노출.
3. **BFF 표현 규칙(집계 2종 한정):** enum 소문자, id 문자열, 날짜/시간/기간은 한국어 표시 문자열. 포맷은 BE `presentation` 매퍼에서만.
4. **granular 표현 규칙:** enum 대문자, id number, 날짜 ISO-8601, 시간 `HH:mm`. FE가 직접 쓸 땐 각 화면에서 매핑.
5. **CORS:** BE `APP_CORS_ALLOWED_ORIGINS`에 `https://gojjibom.web.app` 포함.
6. **시간대:** Asia/Seoul 고정. dose_event 일자 롤오버·`scheduled_at`은 timestamptz.
7. **식별자 전달(무인증 임시):** 집계는 `?seniorId=`(senior/today)·`?careGroupId=`(caregiver/board)·`?date=YYYY-MM-DD`. 데모 폴백으로 단일 케어그룹이면 파라미터 생략 시 최신 그룹 사용 허용.
8. **비의료 경계:** 응답 문구에 진단성 표현 금지("앱 확인은 보조 정보" 유지).

---

## 5. 단계별 실행 계획

> 각 단계는 레포 관례(`specs/NNN-*.md` + `./loop.sh`)를 따른다. Step → Verify 형식.

### S0. 계약 동결 & 배선 (0.5d) — **선행**
1. `docs/CONTRACT.md`에 `SeniorDay`/`CaregiverBoard` 스키마를 진실원본으로 고정(FE `types.ts` 복사 + 필드별 소스 표=§6). → Verify: FE/BE 양 팀 리뷰 승인.
2. FE: `.env`·CI의 `VITE_API_BASE_URL`을 `/api/v1`로, `getJson` envelope 언랩, mock 폴백 유지. → Verify: `npm run lint && npx tsc --noEmit && npm test && npm run build`.
3. BE: CORS에 `https://gojjibom.web.app` 추가. → Verify: preflight `OPTIONS` 200 + `Access-Control-Allow-Origin` 확인.

### S1. dose_events + 복용 확인 (spec 003) — **BE 핵심 결손**
1. `V5__dose_events.sql`: `dose_events`(ERD대로) 생성. → Verify: `./gradlew flywayMigrate` 후 `ddl-auto: validate` 통과.
2. 당일 dose_event 생성기: 활성 `dose_schedules` → 오늘(Asia/Seoul) `SCHEDULED` 이벤트. 우선 **조회 시 지연 생성(lazy)** 으로 단순화, 배치는 Phase 2에서. → Verify: 통합테스트(스케줄 2개→이벤트 2개, 멱등).
3. 엔드포인트:
   - `GET /api/v1/seniors/{id}/doses?date=&actorUserId=` → 당일 이벤트 목록(granular).
   - `GET /api/v1/dose-events/{id}?actorUserId=` → 이벤트 상세.
   - `POST /api/v1/dose-events/{id}:confirm` `{ method: BUTTON|VOICE }` — **멱등**(이미 TAKEN이면 그대로 200). → Verify: confirm 2회 호출 시 상태·confirmed_at 불변, 권한 없는 actor 403.
4. → **Acceptance:** 어르신이 오늘 약을 보고 확인→ status `TAKEN`, 보호자 조회 반영.

### S2. `senior/today` BFF (SeniorDay 매핑)
1. `GET /api/v1/senior/today?seniorId=&date=` → `SeniorDay`(§6-1 규칙대로 포맷). `nextDose`=가장 이른 `upcoming`. → Verify: 픽스처 값과 필드별 계약 스냅샷 테스트.
2. 포맷 로직은 `presentation/SeniorDayAssembler`에 격리(한국어·Asia/Seoul). → Verify: 경계값(자정 전후, 미복용) 테스트.
3. → **Acceptance:** FE `DEMO_MODE=false`에서 `/senior/today` 화면이 실데이터로 동일 렌더.

### S3. `caregiver/board` BFF (CaregiverBoard 매핑)
1. `GET /api/v1/care-groups/{id}/board?date=` (또는 `?seniorId=`) → `CaregiverBoard`. **단계적 채움**: 1차로 `patientName·circle·doses·confirmations·week` 실데이터, `pills=placeholder`·`alert=null`. → Verify: 필드별 스냅샷 + `week` 7일 집계 테스트.
2. `circle` = ACTIVE 멤버 role 집계(`family`=OWNER+FAMILY, `social`=SOCIAL_WORKER). `week`=최근 7일 dose_events로 `done/warn/none`. `confirmations`=당일 이벤트의 `doseLabel/status/detail('08:32 · 음성 확인')`. → Verify: 위 규칙 단위테스트.
3. → **Acceptance:** `/caregiver` 화면 실데이터 렌더(pills/alert는 후속 단계 전까지 안전한 기본값).

### S4. FE 쓰기 흐름을 실 BE에 연결
1. `RegisterPage` → `POST /api/v1/care-groups`(senior+owner), 반환 group/senior id 로컬 저장. → Verify: 등록 후 `/senior/connected`가 `GET /care-groups/{id}`로 확인.
2. `ManagePage` → `GET /care-groups/{id}` + `invite-links` 발급 + `:accept` + member `PATCH/DELETE`. **하드코딩 구성원/코드 제거.** → Verify: 초대 링크 발급→수락→승인 플로우 E2E.
3. `DosePage/DonePage` → `POST .../:confirm`, 성공 후 `done`. → Verify: 확인이 대시보드에 반영.
4. (선택) 어르신 기기 페어링 `POST /api/v1/senior-devices:pair` — 데모는 group/senior id 재사용으로 생략 가능. → Verify: 페어링 토큰으로 senior 식별.

### S5. 재고 (spec 005) → `CaregiverBoard.pills` + `/caregiver/pills`
1. `V6__medication_inventory.sql`. 남은개수·예상소진일·재처방 D-day 계산. 표시 `'D-13'`·`'7월 14일'` 포맷. → Verify: 소진일 계산 테스트.
2. `GET /api/v1/care-groups/{id}/inventory`, board의 `pills` 실데이터로 교체. → Verify: 대시보드 pills 카드 실렌더.

### S6. 알림·에스컬레이션 (spec 007) → `CaregiverBoard.alert`·`/caregiver/timeline`·`/senior/alerts`
1. `V7`: `reminders`, `escalation_policies`, `escalation_events`, `notifications`. 배치(@Scheduled)로 리마인더/미복용 판정/에스컬레이션 로그. → Verify: 미복용→래더(+5/10/15분) 로그 테스트(멱등).
2. `GET /api/v1/care-groups/{id}/timeline?date=`, board의 `alert` 조립, `GET /api/v1/seniors/{id}/notifications`. → Verify: 타임라인·알림·alert 카드 렌더.

### S7. 미디어·TTS (spec 006, 보류 PR #3 리베이스) → `/senior/photo`·`Pill.shape`·음성
1. `V8`: 이미지(서명 URL)·`medications.shape`(또는 매핑) 추가. **보류 미디어 PR의 V2 마이그레이션을 다음 빈 버전으로 리넘버링**(V2 결번 주의). → Verify: `flywayValidate` 순서 무결성.
2. `photo_url` 배선, `nextDose.pills[].shape` 실데이터, (선택) `spokenText` 음성 `audioUrl`. → Verify: 사진 화면·shape 렌더.

### S8. 하드닝
- 무인증 `actorUserId`를 최소 토큰/페어링으로 대체, PII 마스킹, N+1/페이지네이션, 테스트 확대. → Verify: `./gradlew ktlintCheck detekt test build`.

---

## 6. BFF 계약 상세 (필드별 데이터 소스)

### 6-1. `SeniorDay` (`GET /api/v1/senior/today`)

| 필드 | 타입/예시 | 소스 · 파생 규칙 |
| --- | --- | --- |
| `dateLabel` | `'2026년 7월 2일 목요일'` | 요청 `date`(기본 오늘, Asia/Seoul) 한국어 포맷 |
| `doses[]` | `Dose` | 당일 dose_events. `label`=slot 한국어, `time`=`HH:mm`, `mealTag`=meal_relation+offset(`'식후 30분'`), `status`=소문자 매핑, `note`=상태별 문구 |
| `nextDose.doseId` | `string` | 가장 이른 `upcoming` 이벤트 id(문자열) |
| `nextDose.label` | `'저녁약 · 1번 봉지'` | slot 한국어 + `packet_no` |
| `nextDose.alarmLabel` | `'오후 7:30'` | `scheduled_time` 오전/오후 포맷 |
| `nextDose.mealTag` | `'식후 30분'` | meal_relation + offset |
| `nextDose.includesNote` | `'혈압약이 포함돼 있어요'` | dose_schedule_items의 category 파생(대표 1개) |
| `nextDose.baselineNote` | `'저녁 식사 오후 7시 기준'` | meal_relation + (scheduled_time − offset) |
| `nextDose.spokenText` | 문장 | 템플릿 조립(식전/후 N분 + 시각 + 봉지 + 알약수) |
| `nextDose.doneTimeLabel` | `'오후 7:32'` | confirmed_at 포맷(미확인 시 규칙 정의 필요) |
| `nextDose.pills[]` | `Pill` | items → `name`(설명형), `shape`(**컬럼 필요/기본값**), `note` |

### 6-2. `CaregiverBoard` (`GET /api/v1/care-groups/{id}/board`)

| 필드 | 타입/예시 | 소스 · 파생 규칙 · 단계 |
| --- | --- | --- |
| `patientName` | `'어머니'` | senior.name 또는 care_group.name(**결정 필요**) — S3 |
| `circle` | `{family:2, social:1}` | ACTIVE 멤버 role 집계(family=OWNER+FAMILY) — S3 |
| `doses[]` | `Dose` | 당일 dose_events(6-1과 동일 매핑) — S3 |
| `confirmations[]` | `{doseLabel,status,detail?}` | 이벤트별 확인시각+수단(`'08:32 · 음성 확인'`) — S3 |
| `week[]` | `{label,status}` | 최근 7일 집계 `done/warn/none` — S3 |
| `pills` | `{remaining,runOutDate,refillDDay}` | medication_inventory — **S5**(그전엔 placeholder) |
| `alert` | `EscalationAlert \| null` | 에스컬레이션 상태 — **S6**(그전엔 `null`) |

---

## 7. Flyway 마이그레이션 계획

| 버전 | 내용 | 단계 | 비고 |
| --- | --- | --- | --- |
| V1,V3,V4 | (완료) baseline·care-group·prescription | — | V2 결번 |
| `V5` | `dose_events` | S1 | 리마인더는 S6로 분리 |
| `V6` | `medication_inventory` | S5 | |
| `V7` | `reminders`,`escalation_policies`,`escalation_events`,`notifications` | S6 | 분할 가능 |
| `V8` | 이미지 + `medications.shape` | S7 | **보류 미디어 PR의 V2를 여기로 리넘버링** |

원칙: forward-only, 스키마 변경은 반드시 마이그레이션으로, `ddl-auto: validate` 유지.

---

## 8. 리스크 · 결정 필요 사항

| # | 항목 | 리스크 | 결정/완화 |
| --- | --- | --- | --- |
| R1 | 포맷 소유권 | BE가 표현 포맷을 지면 결합↑ | BFF `presentation` 매퍼로 격리, granular은 ISO 유지 |
| R2 | `VITE_DEMO_MODE=true` | 지금 BE 작업이 데모에 안 보임 | S2/S3 후 스테이징에서 `false` + 폴백 유지 |
| R3 | 무인증 `actorUserId` | 누구나 남의 그룹 조회 가능 | 데모 허용, S8에서 최소 토큰화. 로그/응답 PII 마스킹 |
| R4 | ID 타입 | Long→string 불일치로 잠재 버그 | BFF에서 문자열 직렬화 통일 |
| R5 | `SKIPPED` 상태 | FE에 표현 없음 | `missed`로 접거나 FE에 상태 추가(결정) |
| R6 | `Pill.shape` | DB 소스 없음 | `medications.shape` 추가 or 기본값 매핑(결정) |
| R7 | `patientName` | senior.name vs 관계호칭 | 소스 확정(권장: senior.name) |
| R8 | V2 결번 | 미디어 PR 리베이스 시 순서 충돌 | S7에서 다음 빈 버전으로 리넘버 |
| R9 | dose_event 롤오버 | 자정 경계·시간대 오류 | Asia/Seoul 고정, 경계 테스트 |
| R10 | FE README stale | 신규 인원 혼란 | README를 복약 도메인으로 갱신(별도 작업) |

---

## 9. 즉시 착수 체크리스트 (데모 임계경로)

1. [ ] **S0** 계약 동결 `docs/CONTRACT.md` + FE base/envelope 배선 + CORS.
2. [ ] **S1** `V5 dose_events` + `doses`/`dose-event 상세`/`:confirm`(멱등).
3. [ ] **S2** `GET /senior/today` BFF = `SeniorDay`.
4. [ ] **S3** `GET /caregiver/board` BFF = `CaregiverBoard`(pills/alert 후순위).
5. [ ] **S4** FE 쓰기 연결(register→care-group, manage→invite, dose→confirm).
6. [ ] 스테이징 `DEMO_MODE=false`로 두 화면 실데이터 검증(폴백 유지).

> 위 1~6만 끝나면 "어르신이 오늘 약을 보고 확인 → 보호자가 상태를 본다"는 제품 핵심 루프가 실제 BE로 돈다. 재고·타임라인·알림·미디어(S5~S7)는 그 다음.
