#!/usr/bin/env bash
# Secret scan (MB-008): Gitleaks over the full git history and the working tree, redacted output.
# Uses the digest-pinned official image so local runs and CI use the same scanner.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="zricethezav/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"
docker info >/dev/null 2>&1 || { echo "ERROR: Docker daemon not available; secret scan cannot run" >&2; exit 2; }
run() { docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$ROOT:/repo:ro" "$IMAGE" "$@"; }
echo "== git history"
run git /repo --redact --no-banner --exit-code 1
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
