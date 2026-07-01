# Backend — Kotlin / Spring Boot

Codex runs in this package as a **reviewer only**. Project conventions live in
CLAUDE.md; the checklist below is what to enforce when reviewing a diff.

## Review checklist (BE)
Flag only what breaks correctness or a stated requirement. Everything else is optional.

### Correctness & domain
- Logic matches the SPEC. Check boundaries, empty / duplicate / overflow cases.
- Concurrency: shared mutable state, races. Command endpoints are idempotent where needed.

### Layering
- No business logic in controllers; controllers stay thin.
- Domain / service layer free of infrastructure concerns.

### API surface
- No entity returned from an endpoint — DTO / response objects only.
- Consistent success and error response shapes.
- HTTP verb matches semantics: GET read, POST create/command, PATCH partial update, DELETE delete.
- Backward compatible unless the SPEC explicitly asks for a break.
- No secrets or PII in responses or logs.

### Persistence & transactions
- @Transactional only where needed; read-only where appropriate; no external I/O inside a transaction.
- N+1 queries (missing fetch join / @EntityGraph). No unbounded queries — pagination present.
- No LazyInitializationException across the transaction boundary.

### Kotlin
- No unjustified `!!`; nullability explicit; Java platform types handled.
- Constructor injection (no field injection).
- Idiomatic Kotlin, not Java-in-Kotlin.

### Validation & tests
- Invalid inputs rejected; consistent error mapping.
- Business rules, validation, and bug fixes covered by tests; edge cases for each branch.
- Tests named by behavior, not implementation.

## Verification gates (the implementer must have run these green)
    ./gradlew test ktlintCheck detekt build
