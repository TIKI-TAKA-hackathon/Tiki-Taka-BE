# CLAUDE.md

Project-level instructions for Claude Code.

These rules exist to reduce unnecessary changes, over-engineering, and coding mistakes. Follow them together with the existing project style.

## 1. Think Before Coding

Do not implement immediately when the request is ambiguous.

Before making changes:

* State the goal in one or two sentences.
* Identify assumptions clearly.
* If there are multiple valid interpretations, explain them briefly.
* If a simpler approach exists, prefer it.
* If the request is unclear and the wrong implementation would be costly, ask a clarifying question before editing.

For trivial tasks such as typo fixes, small renames, or obvious compile errors, proceed directly.

## 2. Simplicity First

Write the minimum code required to solve the current problem.

Do not add speculative features.

Avoid:

* Unrequested abstractions.
* Premature configurability.
* Generic frameworks for one-time logic.
* Large refactors unrelated to the task.
* Defensive error handling for impossible or unsupported scenarios.

If the solution becomes much larger than expected, stop and explain why before continuing.

## 3. Surgical Changes

Touch only the files and lines required by the user request.

When editing existing code:

* Match the current code style.
* Do not reformat unrelated code.
* Do not rename unrelated variables, methods, or files.
* Do not refactor adjacent code unless it is necessary for the requested change.
* If unrelated dead code or problems are found, mention them instead of changing them.

Every changed line should be traceable to the user request.

## 4. Goal-Driven Execution

Convert tasks into verifiable goals.

Examples:

* “Fix the bug” means reproduce or identify the cause, apply the fix, and verify it.
* “Add validation” means define invalid cases, implement validation, and test them.
* “Refactor” means preserve behavior and verify tests before and after if possible.

For non-trivial tasks, use this plan format:

1. Step: [what will be changed] → Verify: [how to check it]
2. Step: [what will be changed] → Verify: [how to check it]
3. Step: [what will be changed] → Verify: [how to check it]

## 5. Backend Development Rules

Follow the existing backend architecture and package structure.

Prefer:

* Small, explicit service methods.
* Clear DTO/request/response boundaries.
* Consistent error response formats.
* Transaction boundaries only where needed.
* Tests for business rules, validation, and bug fixes.

Avoid:

* Putting business logic in controllers.
* Returning entity objects directly from APIs.
* Mixing infrastructure concerns with domain logic.
* Adding dependencies without explaining why.

## 6. Kotlin / Spring Boot Rules

When working in Kotlin or Spring Boot:

* Prefer idiomatic Kotlin over Java-style Kotlin.
* Keep nullable types explicit.
* Avoid unnecessary `!!`.
* Prefer constructor injection.
* Keep controller methods thin.
* Use meaningful method and variable names.
* Keep extension functions limited to cases where they improve readability.

When adding tests:

* Prefer focused tests over broad fragile tests.
* Name tests by behavior, not implementation detail.
* Include edge cases when the logic has branching.

## 7. API Rules

When modifying APIs:

* Preserve backward compatibility unless the user explicitly asks for a breaking change.
* Keep HTTP methods semantically correct:

  * GET for reads.
  * POST for creation or command-style actions.
  * PATCH for partial updates.
  * DELETE for deletion.
* Use consistent success and error response shapes.
* Do not expose sensitive information in logs or API responses.

## 8. Git and PR Rules

Before suggesting a commit or PR:

* Summarize what changed.
* Mention why the change was needed.
* Mention verification performed.
* Mention risks or follow-up work if relevant.

For PR descriptions:

* Use an English PR title.
* Write the PR body in Korean.
* Include:

  * What / Why
  * Related issue
  * Key code Before & After
  * Reason for change
  * Review points

Do not write Korean comments inside code blocks.

## 9. Verification

After making code changes, verify with the most relevant available command.

Prefer project-specific commands if they exist, such as:

* `./gradlew test`
* `./gradlew build`
* `./gradlew ktlintCheck`
* `./gradlew detekt`
* `npm test`
* `npm run lint`

If verification cannot be run, explain why and describe the manual checks performed.

## 10. Communication Style

Be direct and technical.

When reporting results:

* Start with the conclusion.
* Explain the key change.
* Include verification results.
* Mention unresolved risks honestly.
* Do not overstate certainty.

Do not say that something is complete unless it was verified or the limitation is clearly stated.
