# 테스트 및 커버리지

해커톤 직후 63개였던 테스트를 위험한 규칙 중심으로 159개까지 늘리고, CI가 커버리지 하락을 막도록 기준을 올렸습니다. 측정은 `src/main/kotlin` 전체 클래스를 제외 없이 대상으로 합니다.

## 이전과 현재

| 항목 | 이전 (기준선) | 현재 (2026-11-27) |
| --- | --- | --- |
| 테스트 | 63 passed | 159 passed |
| LINE | 2,096 / 2,323 (90.23%) | 2,330 / 2,363 (98.60%) |
| BRANCH | 323 / 571 (56.57%) | 504 / 587 (85.86%) |
| CI 커버리지 기준 | 처음 도입 때 LINE 0.90, BRANCH 0.56 | LINE 0.95, BRANCH 0.65 |

수치는 Gradle JUnit XML과 `jacocoTestReport.xml`의 covered/total을 그대로 옮겼습니다. 커버리지 기준은 낮추지 않는 하한이고, 부족하면 테스트를 보강합니다. `check`가 `jacocoTestCoverageVerification`을 실행합니다.

## 확인한 방법

로컬과 CI에서 같은 명령으로 lint, 정적 분석, 테스트, 커버리지 기준, build를 확인합니다.

```bash
./gradlew clean ktlintCheck detekt test jacocoTestReport jacocoTestCoverageVerification build --no-daemon
```

보고서는 `build/reports/jacoco/test/html/index.html`(HTML), `build/reports/jacoco/test/jacocoTestReport.xml`(XML), `build/test-results/test/`(JUnit XML)에 생깁니다.

## 테스트가 지키는 규칙

- 복약 이벤트의 지정일 생성, 반복 조회 멱등성, PostgreSQL unique 제약
- ACTIVE 멤버만 가능한 목록·상세·확인과 첫 확인 정보 보존
- dose event와 care group에 귀속된 사진만 연결하고 PENDING 사진만 리뷰
- 초대 만료 경계, revoked·소진, 중복·재가입, OWNER와 primary 불변식
- 잘못된 JSON, 누락 파라미터, Bean Validation의 공통 오류 envelope
- 미복약 grace·재알림 경계, 최대 재시도, 발송 멱등성과 실패 상태
- 대표 보호자, ACTIVE OWNER, ACTIVE FAMILY 수신 우선순위
- 이미지 형식·크기·object key 경계와 S3 요청 메타데이터
- BFF의 복약 상태 문구, 다음 복약, 주간 집계, 확인 시각의 Asia/Seoul 표현

## 남은 한계

- 동시 최초 복약 조회에서 unique 충돌 후 같은 외부 트랜잭션이 정상 복구되는지는 검증하지 않았습니다.
- 동시 복약 확인 두 건의 first-write 보장은 잠금이나 조건부 update가 없어 검증하지 않았습니다.
- 초대 사용 횟수의 동시 수락과 이미지 object key의 동시 등록은 순차 계약만 검증했습니다.
- 알림 sender 실패 후 재시도 정책은 없고, 실패 시 성공 필드를 기록하지 않는 동작까지만 검증했습니다.
- S3는 AWS SDK mock으로 요청을 검증했습니다. 실제 bucket, IAM, 네트워크 통합은 검증하지 않았습니다.
- 현재 BFF aggregate의 데모용 비인증 읽기 계약은 그대로이며 운영 인증·감사 범위는 검증하지 않았습니다.
