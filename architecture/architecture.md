# Gojjibom System Architecture

![시스템 아키텍처](./architecture.png)

[SVG 원본](./architecture.svg)

## 구조 요약

- 약국이 등록한 복약 정보를 어르신·보호자에게 잇는 Spring Boot(Kotlin) API 하나입니다.
- 운영 배포 설정은 EC2 한 대의 Docker Compose로 Nginx, Certbot, API, PostgreSQL 컨테이너를 띄웁니다. 스키마는 Flyway로 관리합니다.
- 복약 사진은 Amazon S3에 비공개로 두고, API가 발급한 Presigned URL로 클라이언트가 직접 올리고 봅니다.
- `main`에 반영되면 GitHub Actions가 ktlint·detekt·테스트·JaCoCo를 통과시킨 뒤 GHCR에 이미지를 올리고, SSH로 EC2에서 갱신합니다.

## 조사 기준

| 항목 | 값 |
| --- | --- |
| 분석일 | 2026-11-26 |
| 저장소 | [TIKI-TAKA-hackathon/Tiki-Taka-BE](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE) |
| 확인한 커밋 | `main` [`bb2823a`](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/commit/bb2823a2c73ab72b15bda16382410ddba119ff56) |
| 대상 환경 | `deploy.yml`이 배포하는 운영 구성 (저장소 설정 기준) |
| 운영 서버 | 직접 확인하지 않음 |

## 근거표

`B` = `https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56`

| 도면의 구성요소·연결·경계 | 의미 | 근거 | 환경 | 확인 상태 |
| --- | --- | --- | --- | --- |
| AWS EC2 · Docker Compose | 배포 대상 호스트와 컨테이너 묶음 | [deploy.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/.github/workflows/deploy.yml) (`EC2_HOST`로 SSH), [docker-compose.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/docker-compose.yml), [docker-compose.prod.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/docker-compose.prod.yml) | 운영 | 코드·설정 확인 |
| Nginx | TLS 종료, `/api/`·Swagger 경로를 API로 프록시 | [nginx/conf.d/api.conf](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/nginx/conf.d/api.conf), [docker-compose.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/docker-compose.yml) | 운영 | 코드·설정 확인 |
| Certbot | 인증서 12시간 주기 갱신 | [docker-compose.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/docker-compose.yml) | 운영 | 코드·설정 확인 |
| Gojjibom API | Spring Boot · Kotlin, BFF 엔드포인트와 미복약 판정 스케줄러 포함 | [build.gradle.kts](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/build.gradle.kts), [BffController.kt](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/kotlin/xyz/stdiodh/gojjibom/presentation/BffController.kt), [MissedDoseScheduler.kt](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/kotlin/xyz/stdiodh/gojjibom/notification/MissedDoseScheduler.kt) | 운영 | 코드·설정 확인 |
| API → PostgreSQL | JPA 저장소, Flyway 마이그레이션 | [application.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/resources/application.yml), [db/migration](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/resources/db/migration), [docker-compose.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/docker-compose.yml) | 운영 | 코드·설정 확인 |
| API → Amazon S3 | 서버 측 `putObject` 업로드 | [S3ObjectStorage.kt](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/kotlin/xyz/stdiodh/gojjibom/media/S3ObjectStorage.kt), [MediaConfig.kt](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/kotlin/xyz/stdiodh/gojjibom/media/MediaConfig.kt) | 운영 | 코드·설정 확인 |
| Client → Amazon S3 (Presigned) | API가 발급한 URL로 사진 업로드·조회 | [ImageService.kt](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/src/main/kotlin/xyz/stdiodh/gojjibom/media/ImageService.kt) (`createUploadUrl`, `createViewUrl`) | 운영 | 코드·설정 확인 (FE 저장소는 열람하지 않음) |
| Frontend (Firebase Hosting) | `gojjibom.web.app`에 배포된 화면 | [README 링크 표](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/README.md) | 운영 | 사용자 문서 확인 |
| GitHub Actions → GHCR | 검증 후 이미지 build·push | [deploy.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/.github/workflows/deploy.yml) (`verify`, `build-and-push`) | CI/CD | 코드·설정 확인 |
| Actions → EC2 (SSH), GHCR → EC2 (Pull) | `git pull`, `compose pull`·`up -d`, Nginx reload | [deploy.yml](https://github.com/TIKI-TAKA-hackathon/Tiki-Taka-BE/blob/bb2823a2c73ab72b15bda16382410ddba119ff56/.github/workflows/deploy.yml) (`deploy`) | CI/CD | 코드·설정 확인 |

## 로고 출처

| 기술·제품 | 사용 파일 | 공식 출처 페이지 | 실제 다운로드 URL·패키지 내 경로 | 확인일 | 원본 SHA-256 |
| --- | --- | --- | --- | --- | --- |
| Amazon EC2 | `assets/logos/aws-ec2.svg` | https://aws.amazon.com/architecture/icons/ | https://d1.awsstatic.com/onedam/marketing-channels/website/public/shared/architecture-icon-release/Icon-package_07312026.5846e92413caa21490223536cc97f1269e44fa92.zip: Architecture-Service-Icons_07312026/Arch_Compute/64/Arch_Amazon-EC2_64.svg | 2026-11-26 | `5a7a0cef7a9f44ccdd544564ce9076abec3024090be0d6454504c49d149ce9ec` |
| Firebase | `assets/logos/firebase.svg` | https://firebase.google.com/brand-guidelines | https://firebase.google.com/static/downloads/brand-guidelines/firebase-brand-assets.zip: firebase-brand-assets/Firebase Logo & Product Icons  2/Firebase Logo/Primary Logomark/SVG/Logomark_Full Color.svg | 2026-11-26 | `120799f51cff880e87bdc5c3954f1938ecc4cd401bec3fe188e1e22db641c556` |
| NGINX (open source) | `assets/logos/nginx.svg` | https://nginx.org/ | https://nginx.org/img/nginx_logo.svg | 2026-11-26 | `9bd543826050ee506dd234f03fbbe972444bb20d9d4854ba7b0e1cf71ef28f07` |
| Certbot (EFF) | `assets/logos/certbot.svg` | https://certbot.eff.org/ | https://certbot.eff.org/assets/Certbot-solid-c4e500f9953fc8ee1d38cb0b22778163602a82cb2b39a5bc89211315c5c877c9.svg | 2026-11-26 | `e636d2d8e663f880ee5a86a1f43a5f54734e649c65e95430e2e3c57614cb07d2` |
| PostgreSQL | `assets/logos/postgresql.svg` | https://wiki.postgresql.org/wiki/Logo | https://wiki.postgresql.org/images/a/a4/PostgreSQL_logo.3colors.svg | 2026-11-26 | `51f93e19516081fc7d6fe6ab9bbab07abe5f7819e28016eefacea6dea691bc54` |
| Amazon Simple Storage Service (S3) | `assets/logos/aws-s3.svg` | https://aws.amazon.com/architecture/icons/ | https://d1.awsstatic.com/onedam/marketing-channels/website/public/shared/architecture-icon-release/Icon-package_07312026.5846e92413caa21490223536cc97f1269e44fa92.zip: Architecture-Service-Icons_07312026/Arch_Storage/64/Arch_Amazon-Simple-Storage-Service_64.svg | 2026-11-26 | `b92c5101cc6565dd12df37655802279f8fba0c719279f62f93106d26750b36c6` |
| GitHub (also for GHCR) | `assets/logos/github.svg` | https://brand.github.com/foundations/logo | https://brand.github.com/GitHub_Logos.zip: GitHub Logos/SVG/GitHub_Invertocat_Black.svg | 2026-11-26 | `693d7abe6f899646cc2e96856723b45e95f71885a54910b2749f6decdf7e1ee1` |

## 확인 필요 사항

- 알림 발송은 `stub` 구현만 있어 외부 알림 서비스는 도면에서 뺐습니다.
- Firebase Hosting은 README의 Live Demo 주소 기준이며 FE 저장소의 배포 설정은 열람하지 않았습니다.
