# 고찌봄 (Tiki-Taka) — Backend

**고찌봄**은 약국이 등록한 복약 정보를 바탕으로 어르신의 복약을 가족·사회복지사가 함께 챙기는
**복약 안부** 서비스입니다. 약을 추천하지 않고, 약국이 등록한 복약 정보만 안내합니다.

현재 코드는 Phase 0 골격(`GET /api/health` + CORS)으로 배포 파이프라인·TLS·CORS·DB 연결을
검증하는 단계입니다. 도메인 모델과 구현 로드맵은 [docs/ERD.md](docs/ERD.md) ·
[docs/PLAN.md](docs/PLAN.md)를 참고하세요.

## Stack
- Kotlin 2.3.20 + Spring Boot 4.1.0 (Java 21), Gradle 9.0.0 (wrapper committed)
- PostgreSQL 16
- docker compose: postgres + backend + nginx + certbot (Let's Encrypt TLS)
- CI/CD: GitHub Actions → GHCR image → EC2 (docker compose, Nginx TLS)

## Layout

```
build.gradle.kts settings.gradle.kts gradlew gradle/   Gradle (root)
src/main/kotlin/xyz/stdiodh/gojjibom/                   app: health + CORS
src/main/resources/                                     application.yml, Flyway db/migration
Dockerfile                                              multi-stage build -> app.jar
docker-compose.yml                                      base: builds the backend image
docker-compose.prod.yml                                 prod override: runs the GHCR image
nginx/conf.d/api.conf  init-letsencrypt.sh              TLS termination + cert bootstrap
.github/workflows/deploy.yml                            verify -> build/push -> deploy
CLAUDE.md AGENTS.md prompts/reviewer.md specs/ loop.sh  implement + review loop
docs/ERD.md docs/PLAN.md                                domain model + build plan
```

## Local dev

```bash
cp .env.example .env            # set POSTGRES_* and APP_CORS_ALLOWED_ORIGINS
docker compose up -d postgres   # or use a local Postgres
./gradlew bootRun
curl http://localhost:8080/api/health
```

## Verify

```bash
./gradlew ktlintCheck detekt test build
```

## Deploy & operate

See [DEPLOY.md](DEPLOY.md) — EC2 first-time setup, GitHub secrets, TLS (`STAGING=0`), and how
to run the review loop (`./loop.sh`).
