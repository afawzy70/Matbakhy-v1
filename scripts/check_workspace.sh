#!/usr/bin/env bash
# MB-005 / P01 workspace check: from a clean clone of the committed HEAD, verify pinned
# toolchain versions, install from lockfiles and compile/test the backend, web apps and the
# Expo customer-mobile project (typecheck, unit test, Android and iOS Metro/Hermes bundles).
# Native Gradle/Xcode compilation is a separate check (MB-047). Exits non-zero on any failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
step() { echo; echo "== $*"; }

step "Clean clone of committed HEAD $(git -C "$ROOT" rev-parse HEAD)"
git clone --quiet --no-local "$ROOT" "$WORK/repo"
cd "$WORK/repo"

step "Toolchain versions"
want_node="$(cat .node-version)"
have_node="$(node --version | sed 's/^v//')"
[ "$have_node" = "$want_node" ] || fail "Node $want_node required (found $have_node); see docs/engineering/toolchain.md"
want_pnpm="$(python3 -c 'import json;print(json.load(open("package.json"))["packageManager"].split("@")[1])')"
have_pnpm="$(pnpm --version)"
[ "$have_pnpm" = "$want_pnpm" ] || fail "pnpm $want_pnpm required (found $have_pnpm); run: corepack enable"
java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
[ "$java_major" = "21" ] || fail "JDK 21 required (found $java_major)"
echo "node $have_node, pnpm $have_pnpm, java $java_major"

step "Backend: ./mvnw verify"
(cd backend && ./mvnw -B -ntp verify)

step "Workspace install from lockfile"
pnpm install --frozen-lockfile

step "API contract: OpenAPI lint and generated client drift"
REDOCLY_TELEMETRY=off pnpm contract:check

step "Typecheck, lint, unit tests and web builds"
pnpm typecheck
pnpm lint
pnpm test
pnpm build

step "customer-mobile: Android and iOS JavaScript bundles"
(cd apps/customer-mobile && CI=1 EXPO_NO_TELEMETRY=1 pnpm bundle:native)
for platform in android ios; do
  ls apps/customer-mobile/dist/_expo/static/js/"$platform"/*.hbc >/dev/null 2>&1 \
    || fail "no $platform Hermes bundle produced"
done

echo
echo "PASS: clean checkout installed and compiled backend, web and Expo projects with pinned versions"
