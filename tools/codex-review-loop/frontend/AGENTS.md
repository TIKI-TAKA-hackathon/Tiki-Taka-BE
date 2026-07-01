# Frontend

Assumes a **TypeScript SPA (React / Vue / Svelte)** — adjust framework-specific items
to your stack. Codex runs in this package as a **reviewer only**.

## Review checklist (FE)
Flag only what breaks correctness or a stated requirement. Everything else is optional.

### Correctness & state
- Behavior matches the SPEC; state transitions are correct.
- Effects declare correct dependencies; no state updates after unmount; no stale closures.
- loading / empty / error states are all handled — never assume the API call succeeds.

### Types & API contract
- No `any`; no non-null `!` that hides real nullability.
- Request / response types match the backend DTOs. Contract drift is the #1 cross-stack bug.

### Rendering & performance
- No avoidable re-renders; large lists virtualized.
- Memoize only where it measurably matters — do not over-memoize.

### Security
- No XSS via raw HTML injection (dangerouslySetInnerHTML / v-html) with untrusted data.
- No secrets in the bundle; no sensitive data in localStorage; no tokens leaked in URLs.

### Accessibility
- Semantic elements, labels, keyboard navigation, focus handling; aria only where needed.

### Consistency & tests
- Matches existing component patterns / design system; no unrelated reformatting.
- No leftover console.log / debugger; no swallowed promise rejections.
- Behavior tests for the logic; edge cases for each branch.

## Verification gates (the implementer must have run these green)
    npm run lint && npx tsc --noEmit && npm test
    # add `npm run build` if the change touches build config
