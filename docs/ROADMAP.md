# 고찌봄 로드맵 (spec 005 이후 후속 통합)

> 작성 2026-07-02. 코어 BE 싱크(`feat/006-be-sync-ux005`, V6~V9 · dose_events/confirm/BFF/prescription-schema/대표자/meal_times/change_log)가 그린으로 완료된 시점의 후속 계획.
> 근거 문서: [SYNC-PLAN.md](./SYNC-PLAN.md), FE `specs/005-ux-changes.md`, 그리고 사진 갤러리·QR 3단계·캘린더·PWA 푸시·TTS 논의.
> 표기: FE / BE / EXT(외부). 우선순위는 데모 임계경로 기준.

## 의존 관계 요약
```
[Phase 0 배선] → [Phase 1 P0 FE 단독] ─┐
                                        ├→ [Phase 2 P1 FE+BE] → [Phase 3 BE/알림/TTS] → [Phase 4 EXT/하드닝]
[feat/006 머지] ────────────────────────┘
```
코어 싱크가 dose_events·대표자·미디어 저장·BFF를 이미 깔았으므로, 후속은 대부분 그 위에 얹힌다.

---

## Phase 0 — 머지 & 계약 배선 (즉시)
| 항목 | 경계 | 내용 |
| --- | --- | --- |
| feat/006 머지 | BE | 코어 싱크 PR 리뷰·머지 |
| 음성 통합 머지 | FE | 정적 음성(Kore 기본) PR 머지 |
| 계약 배선(S0/S4) | FE | base URL `/api/v1`, envelope `.data` 언랩, 스테이징 `DEMO_MODE=false`(폴백 유지), CORS `gojjibom.web.app` |
| 타입 확장 | FE | `NextDose.dispensingType/photoThumbUrl`, `ConfirmLog.photoThumbUrl`, `doseBasis/MealTimes/dispensingType/primary·viewerOnly/PrescriptionHistory/changeLog` |

## Phase 1 — P0 데모 임계경로 (FE 단독, mock)
spec 005 §4 P0. BE 없이 즉시 가능.
| 항목 | 내용 |
| --- | --- |
| A 사진 확인 강화 | dispensingType(봉지/약통) 카메라 분기 · 촬영 후 "이 사진 약 맞나요?" 자가확인 게이트 · done/보호자 "칸 비움 확인" + "사진은 참고용" |
| D 식사시간 파생 표시 | "저녁 식사 7:00 + 식후 30분 → 오후 7:30" 홈/알림/확인 공통 |
| F 안심 톤 + 동의 | 온보딩·등록 카피 안심 톤 + `/senior/consent` 동의 스텝 |
| G 안전 카피 | "약 미확인" · "사진은 참고이며 복용 증명 아님" · 가족없는 분 응급안전안심서비스 안내 |
| 음성 선택 설정 | 기본 재생 완료(Kore). 음성 선택/미리듣기 화면(대표자만 수정) — 카탈로그 `src/lib/voices.ts` 준비됨 |

## Phase 2 — P1 (FE 화면 + BE 연결)
| 항목 | 경계 | BE 상태 |
| --- | --- | --- |
| QR 3단계 | FE+BE | 1 스캔 → 2 confirm-meds(BE `GET /prescriptions:lookup` **구현됨**) → 3 알림 설정(push/calendar 안내) + 동의 |
| 복약 이력 화면 | FE+BE | `/senior/history`·`/caregiver/history`; BE `GET /seniors/{id}/prescriptions` **구현됨**(active/ended 파생) |
| 대표자/역할 UI + 식사시간 설정 | FE+BE | 배지·잠금·"알림 갑니다" 확인; BE 대표자·`meal-times`(대표자만)·`change-log` **구현됨** |
| 사진 갤러리 | FE+BE | `/caregiver/photos`(날짜/시간 그리드) + 대시보드 최근 3 + 타임라인 썸네일. 모델 `DosePhoto[]{id,doseEventId,doseLabel,imageUrl,thumbnailUrl,takenAt,uploadedAt,reviewStatus}`. **BE 필요**: `POST /dose-events/{id}/photo`(=confirm imageId 재사용), `GET /care-groups/{id}/photos`(집계+서명URL). 저장/소유권검증 기반 있음(images DOSE_EVENT) |

## Phase 3 — BE 중심 (알림 · TTS · 재고)
| 항목 | 경계 | 내용 |
| --- | --- | --- |
| 알림 tier1 | BE | PWA 웹푸시 구독 API + 서버 스케줄러(`@Scheduled`) → `reminders`/`escalation`/`notifications` (V11, SYNC-PLAN S6). 문구 "약 미확인"까지만 |
| 알림 tier2 | BE+EXT | 보호자 카카오 알림톡/SMS 에스컬레이션(템플릿 사전승인 리드타임) |
| TTS 라이브 | BE | `GoogleTtsRenderer : TtsRenderer`(기존 인터페이스 재사용) + `VoiceSettings`(V12, 대표자만 수정) + `GET /dose-events/{id}/voice-guide`(dose_events 연결). **비용절감**: 고정문구 전역 캐시 + 쓰기 시점 사전생성 + 정적 배포 + templateVersion 영구 캐시(라이브 합성 최소화) |
| 재고 | BE | `medication_inventory`(V10, S5) → `CaregiverBoard.pills` 실데이터(현재 placeholder) |

## Phase 4 — 외부 통합 · 하드닝
| 항목 | 경계 | 내용 |
| --- | --- | --- |
| 캘린더 | FE+EXT | webcal 구독 피드(RFC5545) 기본 → Google OAuth 동기화(즉시 반영) → `.ics` 폴백. "자동 등록 불가, 사용자 승인" |
| 하드닝(S8) | BE | BFF 무인증(`/senior/today`,`/board`,`viewUrlForImage`) → 토큰/페어링, PII 마스킹, N+1/페이지네이션 |
| detekt 정리 | BE | `CareGroupService`/`ImageService` 함수 임계 초과 baseline 처리됨 → 식사시간/대표자 로직 별도 서비스 분리 후 baseline prune |
| CaregiverBoard.alert | BE | S6 에스컬레이션 붙으면 `null` → 실데이터 |

---

## 진행 전 잠가야 할 결정
| # | 결정 | 권장 |
| --- | --- | --- |
| D1 | TTS 음성 제품 | Gemini-TTS(스타일 O) vs Chirp3-HD vs Neural2(저렴). voicever.txt 톤이 핵심이면 Chirp3-HD/Gemini |
| D2 | 캘린더 채널(데모) | webcal 피드 1개 + "추가하기" 버튼 (OAuth/.ics는 후속) |
| D3 | 푸시 방식 | PWA 웹푸시(iOS 16.4+/설치 PWA 제약) vs 네이티브 래퍼 vs 보호자측 우선 |
| D4 | 사진 카디널리티/reviewStatus | 이벤트당 1장(재촬영 교체), 갤러리는 최신 1장 집계 |
| D5 | 이력 위치(U6) | `/senior/history` + `/caregiver/history` 별 라우트 |
| D6 | 사진 필수(U1)/dispensingType 주체(U3) | 약통=권장 필수, dispensingType은 약국 등록 시 |

> 실행 순서 권장: **Phase 0 → Phase 1(P0, FE 단독으로 데모 즉시 개선) → Phase 2**. Phase 3/4는 D1~D3 결정 후 착수.
