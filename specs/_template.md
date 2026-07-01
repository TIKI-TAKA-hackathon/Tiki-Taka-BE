# <task title>

## Goal
<One or two sentences: what this task delivers and why.>

## Assumptions
- <Explicit assumptions. If an interpretation is uncertain, state the one chosen.>

## API contract
- <Endpoints touched: method + path, request shape, response shape, status codes.>
- <Error responses use the existing consistent shape.>
- <"None" if this task has no API surface.>

## Acceptance criteria
- [ ] <Observable, testable outcome 1.>
- [ ] <Edge/empty/duplicate cases handled.>
- [ ] `./gradlew ktlintCheck detekt test build` passes.

## Out of scope
- <What this task explicitly does NOT change, to keep the diff surgical.>
