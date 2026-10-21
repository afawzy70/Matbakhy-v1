# Fresh-developer simulation (first-week readiness)

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — evidence of a documentation-only walk-through; it proves the docs lead to the right places, not that the build works here
> **Language / اللغة:** English · [العربية](fresh-developer-simulation-ar.md)

**Method.** A subagent with no project context received a clean clone of the branch at commit `6c94a7c` and was told to use only the repository documentation. The authoring machine has python3 and Node 22 but no pnpm, Java, Maven or a Docker daemon, so everything needing them is **BLOCKED**, never PASS. A PASS below means "completed from the documentation, with the file/line evidence checked", not "ran in production". This is an AI simulation, not a human onboarding; Mohamed's real first week (see [final assessment](../06-onboarding-labs/final-assessment.md)) is the authoritative test.

| # | Task | Result | Evidence / note |
|---|---|---|---|
| 1 | Explain the product goals | PASS | `00-getting-started/project-overview.md`, `01-business/product-vision.md` |
| 2 | Explain roles and how permissions work | PASS | No role objects; 17 `Permission` values granted via `user_grant`, re-read per request; object-level scoping returns 404 |
| 3 | Locate Customer Web | PASS | `apps/customer-web/src` |
| 4 | Locate Mobile | PASS | `apps/customer-mobile` |
| 5 | Locate Kitchen and Back-office | PASS | `apps/operations-web/src/cook`, `src/ops` |
| 6 | Locate order placement | PASS | `OrderController.place` → `OrderService.place` → `placeOnce` → `QuoteService.requireValid` (line numbers checked) |
| 7 | Explain pricing, snapshots, stale quote | PASS | `QuoteService.create/requireValid`: `QUOTE_EXPIRED`, `PRICE_CHANGED`; client must accept the change explicitly |
| 8 | Explain Keycloak integration | PASS | Clients, MFA categories CUSTOMER/KITCHEN/STAFF with DISABLED/OPTIONAL/REQUIRED, V31 seeds OPTIONAL, missing row fails safe to REQUIRED |
| 9 | Identify required tools | PASS | JDK 21, Maven wrapper, Node 24.21.0, pnpm 10.34.6, Docker Compose v2, Python 3 |
| 10 | Follow local setup | BLOCKED | `scripts/dev.sh doctor` correctly reports the missing JDK, Node 24, pnpm and Docker daemon |
| 11 | Run a build and selected tests | PARTIAL / BLOCKED | Python gate tests, catalog and fixture checks ran and passed (21 gate tests; 200 cases); backend `verify`, pnpm lint/test/build, `dev.sh e2e` BLOCKED (no toolchain) |
| 12 | Find the workflow for a new issue | PASS (thin) | Only Day-5 lab and the PR-workflow page existed → the "Starting on an issue" section was added afterwards |
| 13 | Prepare a DOC-001 assessment | PASS with matrix gap | Hypothetical "quote lifetime 5 → 4 minutes" found 11 pages stating 5 minutes; the matrix did not force the pricing pages → area `quotes-pricing` added |
| 14 | Explain how a PR reaches Preview | PASS | A PR never does; only an owner merge to `develop` triggers `deploy-preview.yml` (CI wait, images by SHA, SSH deploy, smoke, admin-console enable) |
| 15 | Pick owner E2E cases for price-change reconfirmation | PASS | E2E-BASKET-014, E2E-BASKET-015, E2E-BASKET-022, E2E-ORDER-112, E2E-OPS-106 verified in `docs/quality/owner-acceptance/cases/` |

## Documentation defects found and what was done

| Defect | Resolution |
|---|---|
| Handbook README linked this (then missing) page, which broke `doc001_check`, `validate_blueprint` and `check_scope` on a clean checkout | page written; scripts re-run |
| Day-3 lab cited a build-output directory as a repository path | reworded as something the build creates |
| Truncated/nonexistent paths in the gap page | corrected |
| Git workflow page claimed the PR template lacked the DOC-001 block | corrected |
| Owner-acceptance README said 199 cases / 53 | corrected to 200 / 54 |
| Root README still described a planning pack | status text updated; historical counts labelled |
| "5 minutes" presented as fixed rule although it is `commerce_policy.quote_ttl_seconds` | "Changing the quote lifetime" section added to [pricing-implementation](../04-codebase/pricing-implementation.md) |
| Preview realm has no `matbakhy-mobile` client; hosting provider naming inconsistent; stale status strings in `backlog.json`/`endpoints.json`/`docs/status/latest.md` | recorded in [inventory-and-gaps](inventory-and-gaps.md); the source documents remain to be corrected in their own PRs |

## Hidden prerequisites discovered
Node exactly 24.21.0 and pnpm 10.34.6 (network); a running Docker daemon for backend tests (Testcontainers); JDK 21; an authenticated `gh`; an owner-allow-listed IP and credentials for the Preview; macOS for iOS.

## Not covered by this simulation
No dependency install, build, backend test, Playwright or native run (no toolchain), no Windows/macOS setup, no physical device, no access to the Preview. Mark these **NOT VERIFIED** until a human completes the Day-4 lab and records the evidence.
