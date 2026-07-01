#!/usr/bin/env bash
# Implement (Claude) -> gates -> review (Codex). Iterate until PASS or max iters.
# Usage: ./loop.sh specs/<task>.md [max_iters]
set -uo pipefail
SPEC="${1:?usage: ./loop.sh specs/<task>.md [max_iters]}"; MAX="${2:-5}"
mkdir -p .review; BASE="$(git rev-parse HEAD)"; FB=".review/feedback.md"; : > "$FB"
gates() { ./gradlew ktlintCheck detekt test build; }
for i in $(seq 1 "$MAX"); do
  echo "=== iter $i: implement ==="
  claude -p "Implement the task in $SPEC following ./CLAUDE.md and the Verify gates in ./AGENTS.md.
Only change what the task requires (surgical). If a prior review/gate log is present below, fix every Critical/Major item first:
$(cat "$FB")"
  echo "=== iter $i: gates ==="
  if ! gates > .review/gates.log 2>&1; then
    { echo "GATES FAILED — fix these first:"; tail -n 80 .review/gates.log; } > "$FB"; continue
  fi
  echo "=== iter $i: review (Codex, read-only) ==="
  codex exec --sandbox read-only -a never -c model_reasoning_effort=high -c web_search=disabled \
    "$(cat prompts/reviewer.md)
--- SPEC ---
$(cat "$SPEC")
--- DIFF ---
$(git diff "$BASE" -- .)" > "$FB"
  if tail -n 3 "$FB" | grep -q '<<<PASS>>>'; then echo "=== PASS iter $i ==="; exit 0; fi
  echo "review FAILED; feeding back"
done
echo "=== no PASS after $MAX iters ==="; exit 1
