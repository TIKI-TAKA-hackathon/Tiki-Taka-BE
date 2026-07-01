# 000 — Bootstrap BE repo (app + infra + review loop + CI)

## Goal
Stand up the 고찌봄 (Tiki-Taka) backend repository so that, after wiring GitHub Actions
secrets, a push to `main` builds and deploys the API to EC2 over HTTPS. This is the
Phase 0 skeleton: a health endpoint + CORS, no domain logic yet.

## Assumptions
- The flattened app lives at the repo root (`build.gradle.kts`, `src/`, `Dockerfile` at root).
- Stack is Kotlin 2.3.20 + Spring Boot 4.1.0 (Java 21), Gradle 9.0.0 (wrapper committed).
- Deploy target is EC2 `15.164.189.72`, domain `api.stdiodh.xyz`, via docker compose
  (postgres + backend + nginx + certbot) with Let's Encrypt TLS.
- CI builds a Docker image and pushes it to GHCR; the server pulls the prebuilt image.
- detekt uses the `dev.detekt` (2.x) plugin id — the legacy `io.gitlab.arturbosch.detekt`
  id has no release compatible with Gradle 9 / Kotlin 2.3.

## API contract
- `GET /api/health` → `{"status":"UP","service":"gojjibom-api","time":"<ISO-8601>"}`.
- No other endpoints in Phase 0.

## Acceptance criteria
- [ ] Repo layout matches the target (app flattened to root; infra, review loop, CI in place).
- [ ] `docker-compose.yml` backend build context is `.`; `docker-compose.prod.yml` runs the GHCR image.
- [ ] `./gradlew ktlintCheck detekt test build` passes (run locally / in CI where Maven Central is reachable).
- [ ] `.github/workflows/deploy.yml` builds+pushes to GHCR and deploys over SSH on push to `main`.
- [ ] No secret is committed; `.env` and `certbot/` runtime data are gitignored.

## Out of scope
- Domain schema and business logic (added in later specs/phases).
- Frontend (separate Firebase project/repo).
- Creating the EC2 instance, DNS records, and GitHub secrets (documented in DEPLOY.md, done by a human).
