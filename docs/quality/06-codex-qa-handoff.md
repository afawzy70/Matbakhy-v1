# Independent QA handoff (Codex) — 2026-10-12

For an independent reviewer. Everything listed as passing was run by the implementation agent and is **self-review**. Nothing is deployed. Data is synthetic only.

## Candidate

- **Repository:** `afawzy70/Matbakhy-v1`, branch `implementation/mvp` (draft PR #1; do not merge).
- **Fixed candidate SHA:** given in the closing entry of `docs/status/daily/2026-10-12.md` and in `docs/status/latest.md`, together with its CI results. Check out that exact SHA, not the branch head:

```bash
git fetch origin implementation/mvp
git checkout <full candidate SHA>
```

## Isolated startup (local machine, synthetic data)

Prerequisites: Docker with Compose v2, JDK 21, and Node.js at the version in `.node-version` with corepack/pnpm. `scripts/dev.sh doctor` checks them.

| Purpose | Command | Isolation and reset |
|---|---|---|
| Whole backend suite | `cd backend && ./mvnw -B -ntp verify` | Testcontainers start their own PostgreSQL and S3 emulator; they are removed afterwards |
| Web and mobile unit/UI tests | `corepack enable && pnpm install --frozen-lockfile && pnpm -r test` | No services |
| Contract drift | `pnpm contract:check` | No services |
| Throwaway stack plus smoke test | `scripts/dev.sh verify` | Separate compose project on alternate ports (15432, 18180, 18080…) with random passwords; deleted on exit |
| Browser journeys (Playwright, Chromium) | `scripts/dev.sh e2e` | Same throwaway project; deleted on exit. `E2E_SCREENSHOT_DIR` keeps screenshots |
| Interactive local stack | `scripts/dev.sh up`, then `scripts/dev.sh smoke` | Writes `infra/.env` with random local passwords. **Reset:** `scripts/dev.sh reset` (refuses env files not marked local) |
| Native checks (Android emulator, iOS simulator) | GitHub workflow `.github/workflows/native-build.yml` (`workflow_dispatch` or a push to mobile paths) | Fresh runners. Android uses the compose stack; iOS runs PostgreSQL, Keycloak and the API natively |

## Synthetic fixtures

- **Identity:** realm `infra/identity/matbakhy-realm.json` with customer-a, customer-b, cook-a and ops-a. The password is generated per run (`DEV_USER_PASSWORD` in the throwaway env file); never use real accounts.
- **Commerce:**
  - Synthetic commerce policy and service zones: `backend/app/src/main/resources/db/devdata/` (dev profile only; refused outside LOCAL/CI).
  - Browser journeys: kitchen approval and phone verification are synthetic SQL in `apps/operations-web/e2e/` (local MFA and SMS are not available).
  - Native ordering: `apps/customer-mobile/e2e-native/seed.sql` (two kitchens, one meal and one open pickup slot each).
- **Backend integration tests:** fixtures in `backend/app/src/test/java/com/matbakhy/app/support/`.

## Where the evidence is

| Evidence | Location |
|---|---|
| Per-story evidence (six checks plus native checks, source commit, logs) | `verification/runs/<date>-<story>/evidence.json` |
| Story and scenario status | `planning/backlog.json`, `planning/scenarios.json` |
| Retrospective test-hardening pass (requirement mapping, results) | `docs/quality/05-retrospective-hardening.md` |
| Earlier external QA scope and bug template | `docs/quality/04-external-qa-handoff.md` |
| Status and daily log | `docs/status/latest.md`, `docs/status/daily/2026-10-12.md` |
| CI | GitHub Actions: Product CI (`ci.yml`), Native builds (`native-build.yml`), Blueprint integrity |

## Suggested focus (highest business risk)

1. **One kitchen and one slot per basket.** Keep and replace on web (browser) and native (emulator/simulator); no replacement while an order is pending.
2. **Totals and limits.**
   - Server-calculated totals and forged amounts.
   - The quantity cap per line: client 20, synthetic policy 10. This is open question Q1 (`docs/product/q1-quantity-limit-decision.md`); expect a quote-time validation error for 11–20.
3. **No duplicate orders.** Lost response or reload on web; app killed during submit, offline reopen and recovery on native (MB-032, verification in progress).
4. **Authorization.**
   - Customer A cannot read, cancel, review or look up (by request ID) customer B's orders.
   - Cooks see only their own kitchen.
   - The customer cannot run cook commands.
5. **Account switch on native.** The next person always sees a login form; nothing of the previous person remains.

## Known limits (not defects unless the reviewer disagrees)

- **Screen-reader testing:** NOT_RUN (BLK-003); MB-029 stays BLOCKED.
- **Native coverage:**
  - Delivery checkout is not exercised on native; pickup only. Delivery is covered on the server and in the browser.
  - No physical devices, signed builds or store builds (MB-034/MB-035).
- **Environment:**
  - Staging does not exist (O07).
  - Retention period is a proposed default (O06).
  - Visual approval for the whole product is pending (O05).

Report bugs with the template in `docs/quality/04-external-qa-handoff.md`, quoting the candidate SHA.
