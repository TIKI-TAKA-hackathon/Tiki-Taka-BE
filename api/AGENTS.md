# Backend API - Kotlin / Spring Boot

Use this file as the shared implementation and review checklist for the API package.
Keep changes small, requirement-driven, and verified.

## Scope

- Work only inside `api` unless the task explicitly requires repository-level changes.
- Follow the existing Kotlin, Spring Boot, JPA, and Flyway style in this package.
- Do not add unrelated refactors, speculative abstractions, or unrequested configuration.

## Correctness

- Match the task SPEC exactly, including boundaries, empty inputs, duplicates, and error cases.
- Keep controllers thin; put business rules in service/domain code.
- Do not return JPA entities from endpoints. Use DTOs or response objects.
- Keep API responses and errors consistent with existing endpoints.
- Do not expose secrets, credentials, or unnecessary personal data in responses or logs.

## Persistence and Transactions

- Use `@Transactional` only where it is needed; use read-only transactions for read paths where appropriate.
- Avoid N+1 queries and unbounded queries. Add pagination where the API can return many rows.
- Do not rely on lazy-loaded state outside the transaction boundary.
- Keep schema changes in Flyway migrations when persistence shape changes.

## Kotlin

- Avoid unjustified `!!`.
- Make nullability explicit, especially around Java platform types.
- Use constructor injection.
- Prefer idiomatic Kotlin over Java-style Kotlin.

## Tests

- Add or update tests for business rules, validation, bug fixes, and edge cases touched by the task.
- Name tests by behavior rather than implementation details.
- Keep test scope proportional to the change.

## Verification Gates

Run these from the `api` directory before finishing implementation work:

```bash
./gradlew test build
```
