# Backend — Kotlin / Spring Boot
(Codex acts as reviewer only here. Rules live in CLAUDE.md. Below is the checklist to enforce on a diff.)
## Review checklist (BE)
Flag only what breaks correctness or a stated requirement.
Correctness & domain
- Logic matches the SPEC; check boundary/empty/duplicate/overflow cases.
- Concurrency: shared mutable state / races; idempotency of command endpoints.
Layering
- No business logic in controllers; controllers stay thin.
- No infrastructure concerns leaking into domain/service.
API surface
- No returning entities directly — DTO/response objects only.
- Consistent success/error response shape.
- HTTP verbs semantically correct (GET read / POST create·command / PATCH partial / DELETE delete).
- Backward compatibility preserved unless the SPEC asks otherwise.
- No secret/PII in responses or logs.
Persistence & transactions
- @Transactional only where needed, read-only where appropriate, no external I/O inside a transaction.
- N+1 (missing fetch join / @EntityGraph); unbounded queries without pagination.
- LazyInitializationException risk outside the transaction boundary.
Kotlin
- No unjustified !!; explicit nullability; Java platform types handled.
- Constructor injection (no field injection); idiomatic Kotlin.
Validation & tests
- Invalid input rejected; error mapping consistent.
- Tests for business rules/validation/bug fixes; edge case per branch; test names describe behavior.
## Verify (implementer must make these green)
./gradlew ktlintCheck detekt test build
