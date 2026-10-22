# Final quality gate — stabilization phase (PR #9)

**Source commit under test: `ff64f323b6266583b1c98599a6aa8803f5722190`** (branch `stabilize/integration-2026-10-18`, `develop` = `0eaf755` + PR #7 + PR #8 + stabilization fixes + D18). The successor commit that adds this report changes only `docs/quality/` and `docs/operations/13-*.md`; its own CI result is the PR's check list.
PR #9 is **Draft**. `main` is untouched. Nothing was merged, deployed or deleted.

## 1. Required checks on the exact source commit
| Check | GitHub Actions run | Result |
|---|---|---|
| Product CI (push) — workspace (Maven `verify` with Testcontainers: **339 backend tests, 0 failures, 0 errors, 0 skipped**, plus a 4-test module; web, mobile, contract), local stack smoke, design, secret scan | 39135520478 | success |
| Product CI (pull request) — same jobs | 39135516734 | success |
| Blueprint integrity (push / PR) | 39135520523 / 39135516791 | success |
| Native builds — **Android debug APK (Gradle)** and **iOS simulator build (Xcode, unsigned)** incl. the sign-in and ordering round trips | 39135520462 | success |
Earlier green baselines: `1df6b0d` (pre-D18) and `240cf0c` (D18 reviewed) — all seven check types passed on each. One transient failure (Design tokens screenshot, `Page.captureScreenshot`) on `9ff290f` passed on re-run; it is unrelated to the change. Native checks are separate evidence for Android and iOS; neither counts as device evidence.

## 2. Local execution (VPS; Java 21 and Keycloak 26.8.0 distribution in user space, no Docker)
Jest/vitest: customer-web 53, operations-web 31, api-client 34, design-tokens 47, customer-mobile 60 (all pass). Python guards/unit tests: 86 pass, 1 skipped (Docker). Offline JVM unit tests (precedence, staff assurance, PDF inspection, fingerprint, module boundaries): pass. Real Keycloak 26.8.0 run locally: `MfaIdpTest` 8/8; the MFA migration scripts applied, re-applied (idempotent), verified and rolled back on a realm imported from the pre-D18 preview file, also through the in-container wrapper with a test double for `docker compose exec`.

## 3. Independent reviews (Codex CLI 0.163.0, read-only sandbox, stdin `/dev/null`, no `danger-full-access`)
| Scope | Rounds | Outcome |
|---|---|---|
| Stabilization code review + QA + security | 6 rounds on `b7ec0e9` … `4650f5c` | All High findings fixed (cash after deposit, sealed approved documents, replay, idempotency fingerprint, durable cleanup race, in-flight approval); final pass: 0 findings |
| D18 configurable MFA | 4 rounds (`c139bdf`, `9ff290f`, `7e61db7`, `240cf0c`) | Round 1: 4 High (profile writes bypass, reset left app sessions, DISABLED, unaudited partial reset); round 2: 2 High + 1 Medium; round 3: 1 High; final `240cf0c`: reviewer, security and QA all **0 findings** |
| Mobile step-up + Keycloak migration (`fef0010`) | 1 round | 1 High (step-up as another person) and 1 Medium (rollback baseline) — fixed in `ff64f32`; re-reviewed in section 7 |
| iOS beta preparation (PR #11) | 1 round | 2 Medium — fixed (submit bound to one build id, ASC app id variable) |
Reports: `code-review-report.md`, `qa-*`, `round3/`, `mfa-round1..3/`, `mfa-final/`, `gates/`, raw outputs in `logs/*-final.json`. Reviews are independent agent reviews, not a human audit; Docker/Testcontainers tests were never run by the agents (CI is that evidence).

## 4. The six gates requested by the owner
| # | Gate | Status |
|---|---|---|
| 1 | Android and iOS native checks on the exact commit | **PASS** (run 39135520462 on `ff64f32`) |
| 2 | Version-controlled idempotent Keycloak migration for the existing preview realm (flows, levels, service account, backup, verification, rollback) | **Prepared and rehearsed locally; live run NOT RUN.** `deploy/web-preview/migrate-mfa.sh`, `identity/mfa-migration.kcadm.sh`, `identity/verify-mfa-flow.py`, `docs/operations/13-keycloak-mfa-migration.md`; static guards run in CI. Needs a person with Docker on Contabo. |
| 3 | OPTIONAL MFA does not break mobile journeys | **Remediated in code, NOT verified on a device.** Step-up banner (`acr_values=mfa 2`), account-switch safe; jest + simulator CI. Device plan: `docs/mobile/ios-beta-device-test-plan.md` §C (in PR #11). Keep CUSTOMER/KITCHEN OPTIONAL; no in-app Security screen. |
| 4 | Reset invalidates earlier elevated evidence; remaining token lifetime cannot bypass sensitive controls | **PASS (CI + review).** Reset records a time; any MFA assertion older than it fails the policy check and every `recentMfa` high-risk action (all sensitive controls route through it): `after_a_reset_a_still_valid_pre_reset_token_cannot_perform_a_sensitive_operation`, `StaffAssuranceTest`, `an_administrative_reset_invalidates_assertions_made_before_it`; application web sessions are deleted and Keycloak sessions ended (refresh token rejected, tested against real Keycloak). A pre-reset access token (≤ 5 min) can still call non-MFA-protected functions. |
| 5 | CSP stays Report-Only with a documented follow-up | **PASS** — `docs/security/csp-rollout.md` (follow-up before any public exposure) |
| 6 | Final quality gate report | this document |

## 5. Outstanding risks and open items
* **Live Keycloak (Contabo preview) is not migrated**; until `migrate-mfa.sh apply` runs, step-up asks for nothing extra and a user cannot complete enabling MFA. The API is safe in that state (it never trusts a missing assertion).
* **Mobile MFA on a phone is unverified**; TestFlight/EAS/Apple steps need owner credentials (PR #11, nothing built or uploaded).
* Preview API reachability from phones depends on the customer allow-list (owner decision).
* SEC-004 CSP not enforced; DEP-NODE (mobile build tooling advisories) not updated; QA-007 (payload contract coverage) and an interleaving test of the production meal update against placement (QA-006 residual) remain as recorded Medium/Low items.
* Under DISABLED a user can still add an authenticator in Keycloak's account console (no effect in the app); anonymous step-up requests are passed to the provider as asked.
* Forward-only migrations V29–V31 are expand-only (V31 relaxes a check); the application rollback needs the previous image plus the identity-store dump named in the migration state file; no data restore was exercised.
* Gitleaks: reviewed false positives (hash manifests of evidence files, one older documentation line) are recorded by exact fingerprint in `.gitleaksignore` with the reason; no genuine secret was found.

## 6. Release recommendation
**Recommend: approve PR #9 for controlled integration into `develop`** (not `main`), with these conditions, because everything the repository can prove is green on the exact commit and all independent High findings are closed:
1. Merge only on the owner's explicit go; merging triggers the Contabo CD workflow (build, deploy, smoke). The deploy runs `deploy.sh up` (backup first, adds the admin client secret to the server `.env`).
2. Treat CD success as proof of the *application* deploy only. After it, a person with host access runs `./migrate-mfa.sh status|apply|verify` and records the live evidence in `docs/operations/13-keycloak-mfa-migration.md`.
3. Keep every MFA policy OPTIONAL; do not set CUSTOMER or KITCHEN to REQUIRED before the device test plan passes.
4. Do not start the TestFlight build until the realm migration, the mobile client registration and the allow-list decision are done.
Not recommended yet: promotion to `main`, production deployment, enforcing CSP, or any statement that MFA works on a phone.

## 7. Remediation after owner approval of 2026-10-18 (mobile step-up and migration)
**Final source commit: `a746e6d`** (supersedes `ff64f32` as the tested source; the report commit that adds this section changes only `docs/quality/`).
The independent Codex review of `ff64f32` (read-only, Codex CLI 0.163.0) found four issues: High cross-account state after an account switch during step-up; Medium step-up recovery; Medium unsafe sourcing of the rollback state; Medium verification that did not prove assurance. Five further review rounds (on `5c7d912`, `89ec820`, `10cfe77`, `a746e6d`) found deeper variants (cleanup failures, restart, late answers, guest-basket migration, fixture cleanup reliability, `apply` exit status); all were fixed. **Last mobile review on `a746e6d`: 0 findings.** The shell-script fixes (fixture cleanup, exit 3) were last reviewed on `89ec820` ("correct by inspection") and are unchanged since; they are not covered by a Codex pass on `a746e6d`.
| Check on `a746e6d` | Result |
|---|---|
| Product CI (push run 39155515728 / PR run 39155512069): 339 backend tests + 4-test module, 0 failures, 0 skipped; customer-mobile 71, customer-web 53, operations-web 31, api-client 34, design-tokens 47; local stack smoke; secret scan | success |
| Blueprint integrity (push / PR) | success |
| Native builds, run 39155515697: Android debug APK (Gradle), iOS simulator build (Xcode, unsigned) | success |
| Local: `scripts/test_keycloak_mfa_migration.py` (13 tests incl. hostile flow names, tampered state, fixture cleanup with a stub kcadm), jest 71/71, `tsc` | pass |
| Assurance verification rehearsed on throwaway Keycloak 26.8.0 (stand-in for the API's MFA contract) | PASS; INCOMPLETE (exit 3) without fixture; FAIL with the `amr` mapper removed |
| Assurance verification against the **real API and the live realm** | **NOT_RUN** |
| Mobile step-up on a physical device | **NOT_RUN** (D18-RESIDUAL-2) |
Accepted limitation: a guest basket can be lost (never shared) if storage fails while it moves to the signing-in person. Before PR #9 is merged nothing has been deployed and the live Keycloak realm is unchanged.
