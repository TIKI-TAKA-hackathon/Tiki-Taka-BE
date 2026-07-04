# 고찌봄 (Tiki-Taka) 백엔드 구현 계획서

> 보관 문서입니다. 현재 구현 상태는 루트 `README.md`, `docs/ERD.md`,
> `src/main/resources/db/migration/`을 기준으로 확인하세요.

## 1. 제품 개요

**고찌봄**은 약국이 등록한 복약 정보를 바탕으로 어르신의 복약을 가족·사회복지사가 함께 챙기는
**복약 안부** 서비스다. 어르신은 "오늘 먹을 약"을 보고 복용을 확인하고, 보호자·복지사는 복약 상태를
모니터링한다. 약을 추천하지 않으며(비의료), 앱의 복용 확인은 **보조 정보**다.

핵심 가치: (1) 어르신의 단순한 복용 확인, (2) 미복용 시 단계적 알림·에스컬레이션, (3) 보호자·복지사의
원격 모니터링, (4) 약국 처방 기반 정확한 복약 정보.

## 2. 사용자 · 역할

| 역할 | 설명 | 주요 행동 |
| --- | --- | --- |
| 어르신 (SENIOR) | 복약 대상. 기기 페어링으로 시작 | 오늘 약 보기, 버튼/음성으로 복용 확인 |
| 가족 (FAMILY) | 케어그룹 참여자 | 상태 모니터링, 알림 수신, 전화 |
| 사회복지사 (SOCIAL_WORKER) | 승인 후 참여 | 상태 모니터링, 안부 대응 |
| 방장 (OWNER) | 그룹 관리자(보통 가족) | 초대·승인·거절·내보내기 |
| 약사 (PHARMACIST) | 약국 소속 | 처방·복약 스케줄·재처방 등록 |

## 3. 핵심 플로우

1. **온보딩**: 보호자가 케어그룹 생성 → 초대 링크 발급 → 가족/복지사 참여(방장 승인) → 어르신 기기 페어링.
2. **처방 등록**: 약국이 어르신 처방을 등록 → 봉지(시간대)·약·복용규칙(식전/식후)·개수 생성.
3. **일일 복약**: 매일 예정 dose_event 생성 → 예정 시각 알림 → 어르신 확인(버튼/음성) → 상태 기록.
4. **미복용 대응**: 미확인 시 재알림(1·2·3차) → 보호자 알림(에스컬레이션) → 전화.
5. **모니터링**: 보호자 대시보드(오늘 복약, 복용 확인, 7일 요약, 약 개수, 확인 필요).

## 4. 범위 & 단계 (Milestones)

각 단계는 `specs/NNN-*.md` 스펙으로 쪼개어 `./loop.sh`(구현→게이트→Codex 리뷰)로 진행한다.

- **Phase 0 — 배포 골격 (완료)**: health, CI/CD(GHCR→EC2), TLS, 리뷰 루프.
- **Phase 1 — MVP 코어**: 계정/역할, 케어그룹 + 초대 링크(승인/거절/내보내기), 처방·약·복약 스케줄(봉지),
  오늘의 복약 조회, **버튼 복용 확인**, 어드히어런스 로그.
  - 완료 기준: 어르신이 오늘 약을 보고 "완료" 확인 → 보호자가 상태를 조회할 수 있다.
- **Phase 2 — 알림 & 미복용 감지**: 스케줄러가 매일 dose_event 생성, 예정 시각 리마인더 발송,
  미복용 판정, 보호자 알림함(notifications).
- **Phase 3 — 에스컬레이션**: 재알림 래더(1차 +5분/2차 10분/3차 15분) → 보호자 알림 → 전화, 타임라인.
- **Phase 4 — 재고 & 약국**: 약 개수 추적(남은 개수·예상 소진일·재처방 D-day), 약국 처방 등록 플로우,
  약 사진, 음성 확인.
- **Phase 5 — 미디어 (TTS · 이미지)**: 복약 안내 음성(프리렌더 MP3) + 카메라 이미지 저장.
  - **TTS**: 템플릿 문장 → 캐시 미스 시 1회 렌더 → 오브젝트 스토리지 저장 → 클라이언트는 URL 재생.
    엔진은 오픈소스 **Kokoro(Apache-2.0, 한국어 지원)**, 렌더는 요청 경로가 아닌 배치/워커(파이썬)에서.
    다음날 복약 클립은 기기에 프리페치(오프라인 대비). 스크립트는 복약 정보 안내만(의료 조언·추천 금지).
  - **이미지**: 카메라 이미지 → **비공개 버킷**(AWS S3 또는 자체 호스팅 MinIO) + **presigned URL**(업로드/조회,
    짧은 만료). DB엔 object key + 메타데이터만. 케어그룹 멤버십 인가, SSE 암호화, 용량·타입 검증.
  - 데이터: `TTS_CLIPS`(문장 해시 캐시), `IMAGES`(폴리모픽 소유) — ERD 참조.
  - 완료 기준: 어르신 화면에서 안내 음성이 재생되고, "약 사진 보기"가 저장 이미지를 presigned URL로 표시.
- **Phase 6 — 하드닝**: 권한/인가 강화, 관측성(로그·메트릭), 성능(N+1/페이지네이션), 테스트 확대.

## 5. 도메인 모델

[docs/ERD.md](ERD.md) 참조. Phase 1은 `USERS, CARE_GROUPS, CARE_GROUP_MEMBERS, INVITE_LINKS,
PHARMACIES, PRESCRIPTIONS, MEDICATIONS, DOSE_SCHEDULES, DOSE_SCHEDULE_ITEMS, DOSE_EVENTS`까지.
`MEDICATION_INVENTORY, ESCALATION_*, NOTIFICATIONS`는 Phase 2~4, `TTS_CLIPS, IMAGES`는 Phase 5.

## 6. API 초안

원칙(CLAUDE.md/AGENTS.md): 컨트롤러는 얇게, 엔티티 직접 반환 금지(DTO), 성공/에러 응답 형태 일관,
HTTP 동사 의미 준수, 페이지네이션. 문서화는 springdoc-openapi(Swagger UI) 사용.

- **온보딩/인증**
  - `POST /api/v1/care-groups` — 그룹 생성(보호자)
  - `POST /api/v1/seniors/pairing` — 어르신 기기 페어링
- **케어그룹 / 참여자** (함께 보는 사람)
  - `GET /api/v1/care-groups/{id}` — 그룹·참여자 조회
  - `POST /api/v1/care-groups/{id}/invite-links` — 초대 링크 발급(재발급 시 기존 만료)
  - `POST /api/v1/invites/{token}:accept` — 링크로 참여 요청
  - `PATCH /api/v1/care-groups/{id}/members/{memberId}` — 승인/거절/역할 변경
  - `DELETE /api/v1/care-groups/{id}/members/{memberId}` — 내보내기
- **처방 / 스케줄** (약국)
  - `POST /api/v1/seniors/{id}/prescriptions` — 처방 등록(+봉지/약)
  - `GET /api/v1/seniors/{id}/dose-schedules` — 복약 스케줄 조회
- **복약 / 확인** (어르신)
  - `GET /api/v1/seniors/{id}/doses?date=YYYY-MM-DD` — 오늘 복약(예정/완료)
  - `POST /api/v1/dose-events/{id}:confirm` — 복용 확인 `{ method: BUTTON|VOICE }` (멱등)
- **모니터링** (보호자)
  - `GET /api/v1/care-groups/{id}/dashboard` — 대시보드 집계
  - `GET /api/v1/seniors/{id}/adherence?range=7d` — 최근 7일 요약
- **재고 / 알림** (Phase 2~4)
  - `GET /api/v1/prescriptions/{id}/inventory`
  - `GET /api/v1/notifications`, `POST /api/v1/notifications/{id}:read`

응답 예시(공통 envelope): `{ "data": ..., "error": null }`, 에러 `{ "data": null, "error": { "code", "message" } }`.

## 7. 알림 · 스케줄링 설계

- **dose_event 생성**: 일 1회 배치(스케줄러)가 활성 `dose_schedules`로부터 당일 `dose_events`(SCHEDULED)를 생성.
- **리마인더**: 예정 시각에 발송(PUSH → 실패 시 SMS). Phase 1은 기록만, Phase 2에서 실제 발송 연동.
- **미복용 판정**: 예정 시각 + 유예 후 미확인이면 MISSED 후보 → 에스컬레이션 정책 평가.
- **에스컬레이션**: `escalation_policies`(step/delay/action)를 순서대로 실행, `escalation_events`에 로그(=타임라인).
- 구현: Spring `@Scheduled` + DB 잡 테이블(단순) → 필요 시 Quartz/외부 큐로 확장. 멱등 처리 필수(중복 발송 금지).

## 8. 기술 · 인프라 (현재 상태)

- Kotlin 2.3.20 + Spring Boot 4.1.0 (Java 21), Gradle 9.0.0.
- PostgreSQL 16, Flyway 마이그레이션(`db/migration`).
- 게이트: `./gradlew ktlintCheck detekt test build`. 리뷰 루프: `loop.sh`(Claude 구현 → Codex 리뷰).
- 배포: GitHub Actions → GHCR 이미지 → EC2 docker compose(nginx TLS + certbot). 세부는 DEPLOY.md.
- API 문서: springdoc-openapi(Swagger UI) — 엔드포인트 추가 시 자동 노출.
- 오브젝트 스토리지(이미지·TTS MP3): AWS S3 또는 자체 호스팅 MinIO(S3 호환) — 동일 S3 API 코드. (Phase 5)

## 9. 리스크 · 고려사항

- **비의료 경계**: 약 추천/진단 표현 금지. "앱 확인은 보조 정보" 문구를 응답·UX에 유지.
- **개인정보(PII)**: 전화번호·건강정보 최소 수집·마스킹, 응답/로그 비노출, 접근 권한(그룹 멤버십) 검증.
- **어르신 UX**: 단순·큰 버튼·음성 확인. 실패에 관대한 확인 플로우.
- **알림 신뢰성**: 발송 실패/중복 대비 멱등·재시도. 에스컬레이션 오탐 최소화(유예·시간대 고려).
- **권한**: 케어그룹 멤버십 기반 인가. 승인 대기(PENDING) 멤버는 데이터 접근 불가.
- **시간대**: Asia/Seoul 고정, timestamptz 저장.

## 10. 다음 단계 (작업 분해)

1. `specs/001-care-group-and-invites.md` — 그룹/멤버/초대 링크 + 승인 플로우.
2. `specs/002-prescriptions-and-dose-schedules.md` — 처방·약·봉지 스케줄 등록.
3. `specs/003-daily-doses-and-confirmation.md` — 일일 dose_event + 버튼 확인 + 어드히어런스 조회.
4. `specs/004-guardian-dashboard.md` — 대시보드·7일 요약 집계.
5. 이후 Phase 2~4 스펙(알림/에스컬레이션/재고).

각 스펙은 `specs/_template.md` 형식(Goal/Assumptions/API contract/Acceptance/Out of scope)으로 작성하고
`./loop.sh specs/00X-*.md`로 구현·검증한다. 스키마 변경은 반드시 Flyway 마이그레이션으로.

## 11. Out of scope (현재)

- 프론트엔드(별도 Firebase 앱), 실제 푸시/SMS/전화 벤더 연동(Phase 2+에서 결정), 결제, 약국 POS 연동,
  의료 자문/복약 추천.
