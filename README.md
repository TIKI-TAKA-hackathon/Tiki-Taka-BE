# 고찌봄 (Tiki-Taka) — Backend (Phase 0)

Kotlin + Spring Boot API. Phase 0 목표: 프론트(Firebase, HTTPS) → EC2 백엔드(Nginx TLS) →
Postgres 왕복 검증. 아직 도메인 로직은 없고 `GET /api/health` + CORS만 있어, 배포 파이프라인·
TLS·CORS·DB 연결을 검증하는 단계입니다.

## Stack
- Kotlin 2.3.20 + Spring Boot 4.1.0 (Java 21), Gradle 9.0.0 (wrapper committed)
- PostgreSQL 16
- docker compose: postgres + backend + nginx + certbot (Let's Encrypt TLS)
- CI/CD: GitHub Actions → GHCR image → EC2 (`api.stdiodh.xyz`, `15.164.189.72`)

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
