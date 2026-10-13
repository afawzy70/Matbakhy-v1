#!/usr/bin/env bash
# Secret scan (MB-008): Gitleaks over the full git history and the working tree, redacted output.
# Uses the digest-pinned official image so local runs and CI use the same scanner.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="zricethezav/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"
docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon not available; secret scan cannot run" >&2; exit 2; }
# In a git worktree, .git is a pointer to the main repository's git directory: mount that directory at the same path,
# or the history scan finds no repository, scans nothing and still passes (seen 2026-10-13 in four evidence runs).
COMMON="$(cd "$ROOT" && cd "$(git rev-parse --git-common-dir)" && pwd)"
EXTRA=()
[ "$COMMON" = "$ROOT/.git" ] || EXTRA=(-v "$COMMON:$COMMON:ro")
run() { docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$ROOT:/repo:ro" "${EXTRA[@]}" "$IMAGE" "$@"; }
echo "== git history (all fetched refs; reviewed false positives in .gitleaksignore)"
set +e
HISTORY="$(run git /repo --redact --no-banner --exit-code 1 2>&1)"
STATUS=$?
set -e
echo "$HISTORY"
[ "$STATUS" -eq 0 ] || exit "$STATUS"
SCANNED="$(echo "$HISTORY" | sed -n 's/.*[^0-9]\([0-9][0-9]*\) commits scanned.*/\1/p' | head -1)"
if [ -z "$SCANNED" ] || [ "$SCANNED" -lt 1 ] || echo "$HISTORY" | grep -q "ERR"; then
  echo "ERROR: the history scan did not scan the repository's commits (${SCANNED:-unknown} scanned); not a PASS" >&2
  exit 3
fi
echo "== working tree: every file Git could commit (tracked and untracked, not ignored)"
# Ignored local files (infra/.env, infra/.generated, build output, dependencies) hold generated
# local-only values and are never committed; the history scan above covers everything published.
SNAPSHOT="$(mktemp -d)"
trap 'rm -rf "$SNAPSHOT"' EXIT
(cd "$ROOT" && git ls-files -z --cached --others --exclude-standard | while IFS= read -r -d '' f; do
  [ -f "$f" ] && mkdir -p "$SNAPSHOT/$(dirname "$f")" && cp "$f" "$SNAPSHOT/$f"; done)
chmod -R a+rX "$SNAPSHOT"
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$SNAPSHOT:/repo:ro" "$IMAGE" \
  dir /repo --redact --no-banner --exit-code 1 --max-target-megabytes 5
echo "PASS: no secrets detected"
