# Prerequisites

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — what a developer must install before building or running Matbakhy, with the versions the repository pins.

Versions below come from `package.json`, `.node-version`, `backend/pom.xml`, `backend/.mvn/wrapper/maven-wrapper.properties`, `infra/compose.yaml` and `.github/workflows/ci.yml`. The longer rationale is in [toolchain.md](../../engineering/toolchain.md); where that page and the files disagree, trust the files (they are what CI reads).

## Required tools

| Tool | Version | Where it is pinned | Used for |
|---|---|---|---|
| Git | any recent | n/a | clone, branches, PRs |
| GitHub CLI `gh` | any recent | n/a | listing issues, opening PRs, reading CI runs (`gh run list`) |
| JDK | 21 (LTS) | `backend/pom.xml` (compiler release and enforcer) | backend build and tests |
| Maven | 3.9.11 | `backend/.mvn/wrapper/maven-wrapper.properties` | always run `./mvnw`, never a system `mvn` |
| Node.js | 24.21.0 exactly | `.node-version`; `package.json` `engines.node` is `>=24.21.0 <25` | web apps, mobile, shared packages |
| pnpm | 10.34.6 | `package.json` `packageManager` and `engines.pnpm` | activated with `corepack enable` |
| Docker Engine or Docker Desktop with Compose v2 | recent | `infra/compose.yaml` | local stack, Testcontainers integration tests, secret scan |
| curl, openssl | any | used by `scripts/dev.sh` | smoke test, random local passwords |
| Python 3 | 3.x | `scripts/*.py` | planning validators, DOC-001 check, owner E2E catalog tools |
| Chromium / Google Chrome | any recent | `CHROMIUM_PATH`, `E2E_CHROMIUM` | design checks and Playwright (Playwright 1.56.1 can also download its own browser; NOT VERIFIED offline) |

`scripts/dev.sh doctor` checks Docker (CLI, daemon, compose v2), JDK 21, the exact Node version from `.node-version`, pnpm, curl and openssl, and prints an actionable message for each missing item.

Important consequence: `.npmrc` sets `engine-strict=true` (and `save-exact=true`), so pnpm refuses to install on a Node outside `engines.node`; in addition, `scripts/check_workspace.sh` fails unless `node --version` equals `.node-version` and `pnpm --version` equals the `packageManager` version. A different Node major (for example 22) is not supported for the full workspace, even if some commands appear to work.

## Optional tools (only for the matching task)

| Tool | When | Notes |
|---|---|---|
| Android Studio / Android SDK, JDK 17 for Gradle | native Android build and emulator | CI uses JDK 17 for the Android Gradle Plugin and API level 35 images (`apps/customer-mobile/scripts/launch-android.sh`) |
| macOS with Xcode and CocoaPods | native iOS build | CI uses `macos-26`; Xcode 16.4 fails for Expo SDK 57 per [toolchain.md](../../engineering/toolchain.md) |
| `psql` client | direct database inspection from the host | the database port is published on `127.0.0.1` only |
| Gitleaks | not needed on the host | `scripts/check_secrets.sh` runs a digest-pinned Gitleaks image through Docker |

## Operating systems

| OS | Status |
|---|---|
| Linux (Ubuntu 24.04 is what CI uses) | IMPLEMENTED: all commands in this handbook are written for bash on Linux |
| macOS | NOT VERIFIED for the JavaScript and backend commands; the iOS native job in `.github/workflows/native-build.yml` is the only macOS evidence |
| Windows | NOT VERIFIED. `scripts/dev.sh`, `scripts/check_workspace.sh` and the other `.sh` scripts need bash: use WSL2 with Docker Desktop's WSL integration and keep the clone inside the WSL filesystem. [toolchain.md](../../engineering/toolchain.md) lists PowerShell equivalents for install/typecheck/test/build and `mvnw.cmd`, marked NOT_RUN there |

`docs/status/latest.md` also lists "Windows/macOS developer commands" as NOT_RUN. If you run them, report the result as a documentation defect fix (see [documentation-policy.md](documentation-policy.md)).

## Accounts and access

- A GitHub account with access to the repository (it is public, but pushing needs collaborator rights).
- Preview environment access is by IP allow-list and is granted by the owner; see [preview-environment.md](preview-environment.md). You do not need server SSH access to develop.
- Never ask for, store or commit credentials. Local passwords are generated randomly by `scripts/dev.sh` into a git-ignored file.

## First checks

```bash
git clone <repository-url> matbakhy && cd matbakhy
node --version        # must print v24.21.0
corepack enable && pnpm --version   # must print 10.34.6
java -version         # must report 21
docker compose version
python3 scripts/validate_blueprint.py   # planning integrity only, not product verification
scripts/dev.sh doctor
```

Next: [local-environment.md](local-environment.md).
