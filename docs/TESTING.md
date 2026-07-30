# 테스트 및 커버리지

## 측정 범위

JaCoCo는 별도 class 제외 없이 기본 `main` source set 전체, 즉 `src/main/kotlin`에서 생성된 모든
클래스를 측정한다. XML과 HTML 보고서를 모두 생성하며, 테스트가 끝나면 보고서 생성이 자동으로
실행된다.

작업 시작 시 저장소 HEAD는 `1e2d8bd`였고, 제시된 기준 커밋 `b89a13c`의 후손이었다.
두 커밋 사이의 정리 변경을 보존한 상태에서 기준선을 다시 측정했으며, 제시된 covered/total과
정확히 일치했다.

## 기준선과 현재 결과

| 구분 | 테스트 | LINE | BRANCH |
| --- | ---: | ---: | ---: |
| 기준선 | 63 passed, 0 failed, 0 errors, 0 skipped | 2,096 / 2,323 | 323 / 571 |
| 현재 | 103 passed, 0 failed, 0 errors, 0 skipped | 2,243 / 2,336 | 388 / 575 |

수치는 Gradle JUnit XML과 `jacocoTestReport.xml`의 bundle counter에서 직접 집계한다. 백분율
표기는 사용하지 않으며 covered/total을 원본 그대로 기록한다.

## 품질 gate

- LINE covered ratio: `0.95` 이상
- BRANCH covered ratio: `0.65` 이상
- `check`는 `jacocoTestCoverageVerification`에 의존한다.
- 초기 gate `LINE 0.90`, `BRANCH 0.56`에서 현재 실측에 맞춰 보수적으로 상향했다.
- 이 gate는 ratchet이다. 이후 변경에서 낮추지 않으며, 커버리지가 부족하면 회귀 위험을 검증하는
  테스트를 보강한다.

## 실행 명령

기준 테스트:

```bash
./gradlew clean test --no-daemon
```

보고서와 gate:

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification --no-daemon
```

로컬 및 CI 최종 검증:

```bash
./gradlew clean ktlintCheck detekt test jacocoTestReport jacocoTestCoverageVerification build --no-daemon
```

보고서 위치:

- XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- HTML: `build/reports/jacoco/test/html/index.html`
- JUnit XML: `build/test-results/test/TEST-*.xml`

## 검증하는 핵심 위험

- 복약 이벤트의 지정일 생성, 반복 조회 멱등성, PostgreSQL unique 제약
- ACTIVE 멤버만 가능한 목록·상세·확인과 첫 확인 정보 보존
- dose event와 care group에 귀속된 사진만 연결하고 PENDING 사진만 리뷰
- 초대 만료 경계, revoked·소진, 중복·재가입, OWNER와 primary 불변식
- 잘못된 JSON, 누락 파라미터, Bean Validation의 공통 오류 envelope
- 미복약 grace·재알림 경계, 최대 재시도, 발송 멱등성과 실패 상태
- 대표 보호자, ACTIVE OWNER, ACTIVE FAMILY 수신 우선순위
- 이미지 형식·크기·object key 경계와 S3 요청 메타데이터
- BFF의 복약 상태 문구, 다음 복약, 주간 집계, 확인 시각의 Asia/Seoul 표현

## 남은 미검증 위험

- 동시 최초 복약 조회에서 unique 충돌 후 같은 외부 트랜잭션이 정상 복구되는지는 검증하지 않았다.
- 동시 복약 확인 두 건의 first-write 보장은 잠금이나 조건부 update가 없어 검증하지 않았다.
- 초대 사용 횟수의 동시 수락과 이미지 object key의 동시 등록은 순차 계약만 검증했다.
- 알림 sender 실패 후 재시도 정책은 없으며, 실패 시 성공 필드를 기록하지 않는 동작까지만 검증했다.
- S3는 AWS SDK mock으로 요청을 검증했다. 실제 bucket, IAM, 네트워크 통합은 검증하지 않았다.
- 현재 BFF aggregate의 데모용 비인증 읽기 계약은 그대로이며 운영 인증·감사 범위는 검증하지 않았다.
