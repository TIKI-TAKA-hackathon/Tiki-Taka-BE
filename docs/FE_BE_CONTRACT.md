# FE·BE 계약 정렬

해커톤 중간에 FE와 BE가 서로 다른 API를 보고 있던 것을, BE에 FE 화면 모양 그대로 돌려주는 집계 API를 추가해 맞췄습니다. 복약 서비스의 핵심인 복약 이벤트와 복용 확인도 이때 새로 만들었습니다.

## 이전과 현재

| 항목 | 이전 | 현재 |
| --- | --- | --- |
| 어르신 오늘 복약 화면 | FE가 `GET /senior/today`를 부르지만 BE에 없음 | `GET /api/v1/senior/today` |
| 보호자 대시보드 | FE가 `GET /caregiver/board`를 부르지만 BE에 없음 | `GET /api/v1/care-groups/{id}/board` |
| 일자별 복약 이벤트 | 없음 | `GET /api/v1/seniors/{seniorId}/doses`, `GET /api/v1/dose-events/{id}` |
| 복용 확인 | 없음 | `POST /api/v1/dose-events/{id}:confirm` |
| 복약 상태 값 | BE `TAKEN`·`SCHEDULED`·`MISSED`, FE `done`·`upcoming`·`missed` | 집계 API가 FE 값으로 변환 |
| ID 타입 | BE 숫자, FE 문자열 | 집계 API가 문자열로 반환 |
| 시간·날짜 문구 | BE 원시 값, FE `오후 7:30`·`식후 30분` 같은 완성 문구 기대 | 집계 API가 Asia/Seoul 기준 한국어 문구로 변환 |
| 알림·사진·로그인 | 없음 | 알림 목록·읽음, 복약 사진 업로드·검토, OTP 로그인 API |

## 문제

FE는 화면 두 개를 채우는 집계 API 두 개만 호출했는데, BE는 케어그룹·처방처럼 리소스별 REST만 구현해서 두 경로가 만나지 않았습니다. 경로 prefix(`/api`와 `/api/v1`), 응답 형태(그대로의 JSON과 `{ data, error }`), 상태 값 대소문자, ID 타입, 화면 문구까지 층마다 달랐습니다.

## 바꾼 내용

두 가지 방법을 비교했습니다.

| 방법 | 장점 | 단점 |
| --- | --- | --- |
| BE에 FE 모양의 집계 API 추가 (선택) | FE 변경이 base URL과 `data` 꺼내기 정도로 작음 | 화면 문구 변환이 BE에 생김 |
| FE를 리소스별 REST에 맞춰 수정 | BE가 단순해짐 | 화면마다 여러 API를 조합해야 하고 일정 안에 끝내기 어려움 |

- 화면용 변환은 `bff` 패키지에만 둡니다. 리소스별 API는 ISO 날짜와 enum을 그대로 돌려줍니다.
- 경로는 `/api/v1`로 통일하고, 모든 응답은 `{ data, error }`로 감쌉니다. FE는 `data`를 꺼내 씁니다.
- 상태 변환은 `TAKEN → done`, `SCHEDULED → upcoming`, `MISSED·SKIPPED → missed`입니다.

## 확인한 방법

- 집계 API의 상태 문구, 다음 복약, 주간 집계, 확인 시각 표현을 테스트로 고정했습니다. 전체 목록은 [테스트와 커버리지](./TESTING.md)에 있습니다.
- Testcontainers PostgreSQL에서 Flyway 마이그레이션을 실행한 통합 테스트로 복약 이벤트 생성과 복용 확인을 확인합니다.

## 남은 한계

- 집계 API는 데모용으로 인증 없이 읽을 수 있습니다.
- 동시에 들어온 복용 확인 두 건 중 첫 확인을 지키는 잠금이나 조건부 갱신은 없습니다.
