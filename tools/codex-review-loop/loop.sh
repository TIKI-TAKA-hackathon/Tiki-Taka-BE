#!/usr/bin/env bash
# Cross-model review loop: Claude implements, Codex reviews, repeat until PASS.
#
#   ./loop.sh <spec-file> [package-dir] [max-iters]
#
#   spec-file     path to the task spec (relative to repo root, or absolute)
#   package-dir   dir whose AGENTS.md drives the review:
#                   "."        single-package repo (AGENTS.md at repo root)
#                   "backend"  / "frontend"   monorepo package
#                 default: "."
#   max-iters     hard cap on implement -> review cycles. default: 3
#
# Env overrides:
#   CODEX_SANDBOX   read-only (default) | workspace-write | danger-full-access
#                   read-only is enforced by bubblewrap; inside Docker that needs
#                   user namespaces. If Codex warns it cannot start its sandbox,
#                   re-run with CODEX_SANDBOX=danger-full-access (the container is
#                   then the boundary). Use workspace-write if you want the reviewer
#                   to re-run the gates itself.

set -euo pipefail

SPEC="${1:?usage: ./loop.sh <spec-file> [package-dir] [max-iters]}"
PKG="${2:-.}"
MAX_ITERS="${3:-3}"
CODEX_SANDBOX="${CODEX_SANDBOX:-read-only}"

command -v claude >/dev/null || { echo "claude not on PATH"; exit 127; }
command -v codex  >/dev/null || { echo "codex not on PATH";  exit 127; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(git rev-parse --show-toplevel)"

case "$SPEC" in /*) SPEC_ABS="$SPEC" ;; *) SPEC_ABS="$ROOT/$SPEC" ;; esac
[ -f "$SPEC_ABS" ] || { echo "spec not found: $SPEC_ABS"; exit 1; }
[ -d "$ROOT/$PKG" ] || { echo "package dir not found: $ROOT/$PKG"; exit 1; }
[ -f "$ROOT/$PKG/AGENTS.md" ] || echo "warning: no AGENTS.md in '$PKG' — review will rely on the exec prompt only"

LABEL="$(basename "$PKG")"; [ "$PKG" = "." ] && LABEL="root"
mkdir -p "$ROOT/.review"
FEEDBACK="$ROOT/.review/${LABEL}-feedback.md"
PROMPT_FILE="$ROOT/.review/${LABEL}-prompt.txt"
rm -f "$FEEDBACK"

BASE="$(git -C "$ROOT" rev-parse HEAD)"

for i in $(seq 1 "$MAX_ITERS"); do
  echo "── iteration $i/$MAX_ITERS · implement (Claude) ──"
  IMPL_PROMPT="$(cat "$SCRIPT_DIR/prompts/implementer.md")

--- TARGET ---
Work within: $PKG

--- SPEC ---
$(cat "$SPEC_ABS")"
  if [ -s "$FEEDBACK" ]; then
    IMPL_PROMPT="$IMPL_PROMPT

--- PREVIOUS REVIEW (fix every issue, change nothing else) ---
$(cat "$FEEDBACK")"
  fi
  ( cd "$ROOT" && claude -p "$IMPL_PROMPT" --dangerously-skip-permissions )

  echo "── iteration $i/$MAX_ITERS · review (Codex · sandbox=$CODEX_SANDBOX) ──"
  {
    cat "$SCRIPT_DIR/prompts/reviewer.md"
    printf '\n\n--- SPEC ---\n'
    cat "$SPEC_ABS"
    printf '\n\n--- DIFF (%s) ---\n' "$PKG"
    git -C "$ROOT/$PKG" diff --relative "$BASE" -- .
  } > "$PROMPT_FILE"

  # Codex streams progress to stderr (visible) and the final review to stdout (captured).
  ( cd "$ROOT/$PKG" && codex exec \
      --sandbox "$CODEX_SANDBOX" \
      -a never \
      -c model_reasoning_effort=high \
      -c web_search=disabled \
      - < "$PROMPT_FILE" ) > "$FEEDBACK"

  VERDICT="$(tail -n 5 "$FEEDBACK" | grep -oE '<<<(PASS|FAIL)>>>' | tail -n1 || true)"
  echo "   verdict: ${VERDICT:-<none found>}"
  if [ "$VERDICT" = "<<<PASS>>>" ]; then
    echo "✅ approved on iteration $i — review: $FEEDBACK"
    exit 0
  fi
  echo "↩︎  issues found — see $FEEDBACK — looping"
done

echo "⚠️  reached $MAX_ITERS iterations without PASS. Last review: $FEEDBACK"
exit 1
