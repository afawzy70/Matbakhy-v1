# QA test plan and static analysis (phase 1)

Agent: independent Codex CLI 0.163.0, `codex exec -s read-only`, stdin </dev/null. Commit inspected: `b7ec0e9052a612f7a8aab38f00519107b1dce213`. Self-reported scope: Phase 1 static analysis only; all product tests NOT_RUN because execution belongs in the writable phase 2 worktree. No files written. HEAD matches the requested commit; git status and git diff were empty. Static scan: 54 backend test files, 6 application test files, 5 shared-package test files. Existing backend tests meaningfully exercise PostgreSQL rollback, immutable snapshots, last-portion concurrency, price serialization, authorization and duplicate recovery. No assertion-free Java @Test methods were identified by the static scan; DOM findBy assertions are meaningful presence checks. Two code defects and five specific coverage weaknesses are reported below. Current owner decisions require zero commission and supersede older commission-bearing rule text.

Phase 2 commands, from the isolated worktree root unless otherwise stated:
Prerequisites: JDK 21, Node 24.21.0, pnpm 10.34.6, Docker for Testcontainers/local-stack tests; install dependencies with pnpm install --frozen-lockfile.
Backend full suite: (cd backend && ./mvnw -B -ntp verify).
Backend focused integrity regression: (cd backend && ./mvnw -B -ntp -pl app -am -Dtest=QuoteIntegrationTest,OrderPlacementIntegrationTest,PriceChangeIntegrationTest,OrderLifecycleIntegrationTest,DepositPaymentIntegrationTest,PaymentReconciliationIntegrationTest,CashAndLedgerIntegrationTest,FinanceOperationsIntegrationTest,ZeroCommissionIntegrationTest,StaffAccessIntegrationTest,IdentityIntegrationTest,StrictJsonIntegrationTest,ContractRoutesTest -Dsurefire.failIfNoSpecifiedTests=false test).
Customer web components: pnpm --filter @matbakhy/customer-web test.
Cook/operations web components: pnpm --filter @matbakhy/operations-web test.
API client and contract fixtures: pnpm --filter @matbakhy/api-client test.
Design tokens: pnpm --filter @matbakhy/design-tokens test.
Mobile JavaScript: pnpm --filter @matbakhy/customer-mobile test.
Contract lint/generated drift: REDOCLY_TELEMETRY=off pnpm contract:check.
Static client checks: pnpm typecheck; pnpm lint; pnpm build.
Real API browser suite: scripts/dev.sh e2e. This supplies the throwaway stack and fixture boundary; do not invoke it against shared data.
Local-stack smoke: scripts/dev.sh verify.
Planning and migration checks: python3 scripts/validate_blueprint.py; python3 scripts/check_scope.py; python3 scripts/check_migration_compat.py; python3 -m unittest discover -s scripts -p 'test_*.py' -v.
Native device suites require their platform runners and prepared synthetic stack: bash apps/customer-mobile/scripts/auth-roundtrip-android.sh followed by bash apps/customer-mobile/scripts/order-roundtrip-android.sh; on macOS, bash apps/customer-mobile/scripts/auth-roundtrip-ios.sh followed by bash apps/customer-mobile/scripts/order-roundtrip-ios.sh. JavaScript tests do not establish native PASS.
Optional existing-preview smoke, only with configured synthetic preview accounts: pnpm --filter @matbakhy/operations-web e2e:staging. It creates a demo order and is not a substitute for testing the isolated source revision.
After adding reproductions, rerun the affected focused suites and then the full backend and client suites. None of these phase 2 commands was executed here.

Phase 1 only: all product tests NOT_RUN by the QA agent (read-only). See qa-coverage-matrix.md. Phase 2 execution is recorded in qa-execution-report.md.

## Commands (as reported by agent)
| Command | Exit | Note |
|---|---|---|
| `cat .agents/codex/qa-engineer.md` | 0 | Read requested role first. |
| `git rev-parse HEAD; cat CLAUDE.md docs/delivery/02-execution-protocol.md /home/aiagent/.codex/plugins/cache/op` | 0 | Overall shell exit 0; rg reported nonexistent frontend directory. Actual clients are under apps/. |
| `cat docs/quality/stabilization/qa-coverage-matrix.md; cat planning/rules.json planning/scenarios.json; rg --fi` | 0 | Requirements and test inventory; tool output was truncated. |
| `nl -ba backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java; nl -ba backend/a` | 0 | Static source and suite configuration reading; output truncated. |
| `sed -n '288,560p' backend/modules/orders/src/main/java/com/matbakhy/orders/internal/PaymentService.java; cat d` | 0 | Deposit transitions, documented limits and lifecycle guards. |
| `nl -ba backend/app/src/test/java/com/matbakhy/app/web/ContractRoutesTest.java; cat backend/app/src/test/java/c` | 0 | Integration boundary and quote implementation review; output truncated. |
| `git status --short; rg -n 'void |@Test|assertThat' backend/app/src/test/java/com/matbakhy/app/orders/{OrderPla` | 0 | Clean status; placement/replay implementation and suite entry points. Output truncated. |
| `python3 -B - <<'PY'
from pathlib import Path
import json
for f in ['planning/decisions.json','planning/executi` | 0 | Read-only parsing and targeted contract excerpts. An nl-to-head pipeline reported broken pipe; overa |
| `nl -ba backend/app/src/test/java/com/matbakhy/app/orders/OrderPlacementIntegrationTest.java | sed -n '258,310p` | 0 | Overall exit 0; cat reported scripts/ci.sh does not exist. Subsequently located scripts/check_worksp |
| `rg --files | rg 'Idempot|ci|native.*sh|run.*sh'; rg -n 'hash|fingerprint|expires|execute' backend/modules/iden` | 0 | Overall exit 0; initial idempotency glob was nonexistent. Located implementation under backend/app. |
| `cat scripts/check_workspace.sh; nl -ba backend/app/src/main/java/com/matbakhy/app/idempotency/JdbcIdempotentCo` | 0 | Static heuristic found no Java @Test method lacking assertion/check terminology; not a semantic proo |
| `nl -ba backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java | sed -n '65,190p'` | 0 | Cash/deposit mismatch and contract tests; output slightly truncated. |
| `nl -ba backend/modules/finance/src/main/java/com/matbakhy/finance/internal/LedgerService.java | sed -n '70,150` | 0 | No deposit/remainder matches in finance tests. TestLogins was initially sought in the wrong director |
| `rg -n 'expir|concurr|racing|double|simult|Thread|Executor' backend/app/src/test/java/com/matbakhy/app/orders/O` | 0 | Confirmed deposit race coverage boundary, cash form default, clean diff and unchanged HEAD. |
| `nl -ba backend/app/src/test/java/com/matbakhy/app/identity/StaffAccessIntegrationTest.java | sed -n '150,172p'` | 0 | Final static inventory and evidence excerpts. No test suites executed. |

## Findings

### QA-001 [High] Cash reporting and verification charge the full total after a deposit
`backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java:96`
- Scenario: Complete a 27000-minor-unit order after the kitchen acknowledges its 13500 deposit. Report the correct remaining cash collection of 13500, then verify that collection.
- Expected: Cash discrepancy is zero and finance can verify the 13500 remainder. The cook form defaults to that remainder.
- Actual: Reporting subtracts customer_order.total_minor, producing discrepancy -13500 and setting the obligation DISPUTED. Verification also requires the full 27000. The cook form defaults to 27000.
- Evidence: CashService.java:77-78 selects o.total_minor; :96-106 computes discrepancy and obligation state; :127-150 again uses o.total_minor for verification. OrderService.java:203-208 correctly creates cash_obligation with total minus deposit. apps/operations-web/src/cook/Orders.tsx:182 initializes the form from order.total. docs/product/09-deposit-payment-states.md states cash due at handover is the remainder. Static rg found no deposit/DEPOSIT/remainder references in backend/app/src/test/java/com/matbakhy/app/finance.
- Suggested fix: Use cash_obligation.amount_minor for report and verification comparisons; initialize the form from the payment remainder with a compatible fallback for older orders.
- Repro test: Add a real PostgreSQL deposit-to-completion-to-cash integration test: total 27000, deposit 13500, cash report 13500, discrepancy 0, obligation REPORTED, verification of 13500 succeeds. Add a cook form test expecting 135.00, plus a fully prepaid boundary case. NOT_RUN.
- Claude triage: FIXED_PENDING_RETEST — duplicate of CR-001

### QA-002 [Medium] Permanent request recovery accepts a changed payload when its quote is unchanged
`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:142`
- Scenario: Place an order, delete its cached idempotency record as the existing retention test does, then reuse the original key, clientRequestId and quoteId with a different fulfillmentNote.
- Expected: BR-024 requires a conflict for a different payload using the same actor, command and key.
- Actual: The new cache entry hashes the changed payload, but placeOnce returns the existing order solely because quoteId matches. The changed command succeeds and silently retains the old note.
- Evidence: OrderService.java:139-146 selects only id and quote_id and returns before policy or note checks. JdbcIdempotentCommands.java:54-59 creates a new request hash when no cache record remains. OrderPlacementIntegrationTest.java:268-271 tests retention with an identical body; :284-299 tests changed quote IDs, not changed fields on the same quote. docs/product/03-business-rules.md:68 specifies payload conflict.
- Suggested fix: Persist a durable canonical command fingerprint alongside the permanent request reference and compare it during recovery, preserving identical-request recovery after cache retention.
- Repro test: Extend the retention test: place with note A, remove cached record, resend the same original key/request/quote with note B; assert 409 IDEMPOTENCY_CONFLICT, one order, unchanged note, stock and events. Also exercise changed acceptedPolicyVersions. NOT_RUN.
- Claude triage: OPEN — Medium; durable request fingerprint required (migration). Plan: add column fingerprint to order request reference

### QA-003 [Medium] Deposit expiry coverage does not exercise transfer/receipt races
`backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java:244`
- Scenario: TC-160 and owner direction require race-safe expiry, payment claims and capacity handling.
- Expected: Automated tests exercise transfer-report versus expiry and kitchen-receipt versus expiry with both controlled transaction orderings.
- Actual: The expiry test completes a transfer report, advances time, then calls expireOne sequentially. The concurrent payment test covers duplicate customer reports only. The lifecycle suite races acceptance/cancellation/expiry without covering these deposit transitions.
- Evidence: DepositPaymentIntegrationTest.java:205-223 races two transfer reports; :245-256 reports before advancing the clock and expiring. OrderLifecycleIntegrationTest.java:428 names the acceptance/cancellation/expiry race. docs/delivery/07-owner-decisions-implementation-sequence.md:20 requires expiry and capacity claims to be race-safe.
- Suggested fix: Add transaction-barrier tests using real PostgreSQL and bounded waits; assert final order/payment states, one release, consistent stock, payment events and one open PAYMENT support case.
- Repro test: Force each side to acquire its relevant row lock first for transfer-versus-expiry and receipt-versus-expiry. Assert a reported transfer is reconciled, never silently lost, and repeated expiry cannot release capacity again. This is a coverage finding, not a demonstrated race defect. NOT_RUN.
- Claude triage: OPEN — 

### QA-004 [Medium] Receipt authorization test uses a non-cook instead of a foreign kitchen owner
`backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java:231`
- Scenario: BR-006 requires object scope checks between two legitimate cooks.
- Expected: A second approved cook requesting receipt for the first cook's order receives 404 with no payment mutation.
- Actual: The receipt test supplies a random subject without creating its kitchen and accepts either 403 or 404. It can pass at the 'not a cook' guard without exercising order-to-kitchen scope.
- Evidence: DepositPaymentIntegrationTest.java:231 calls receipt with UUID.randomUUID().toString(). PaymentService.java:81-83 rejects actors lacking a kitchen before :267-270 checks order ownership. PaymentReconciliationIntegrationTest.java:176-179 correctly creates a second approved kitchen for the problem endpoint, demonstrating the stronger fixture pattern.
- Suggested fix: Use a second approved kitchen owner for receipt and refund negative tests; assert exact denial and unchanged payment version/event counts.
- Repro test: Create approved kitchens A and B, place A's deposit order, call its receipt endpoint as B with otherwise valid version/amount; require 404 and no writes. Repeat for refund. NOT_RUN.
- Claude triage: OPEN — 

### QA-005 [Medium] Revocation test reauthenticates instead of reusing the existing authentication
`backend/app/src/test/java/com/matbakhy/app/identity/StaffAccessIntegrationTest.java:167`
- Scenario: BR-007 and the permissions specification require revoked privileges to stop working in an already authenticated session.
- Expected: A request with the same pre-revocation authentication/session is denied after grant removal.
- Actual: The test constructs authentication again through as(staffSubject) after revocation, so it establishes denial after fresh login rather than denial of stale session authorities.
- Evidence: StaffAccessIntegrationTest.java:97-99 defines as() as logins.login(...). The revocation test invokes as(staffSubject) at :162 and again at :167. docs/product/06-permissions.md:26 explicitly requires current grants to be checked for sensitive commands.
- Suggested fix: Retain the initial authentication object and add a real session/token test that reuses its original credentials after revocation.
- Repro test: Authenticate staff once, prove the sensitive endpoint works, revoke the grant from an admin session, then repeat using the original staff session/token and require 403 plus no protected writes. NOT_RUN.
- Claude triage: OPEN — 

### QA-006 [Medium] Price-race tests replace the production price-update command with handwritten SQL
`backend/app/src/test/java/com/matbakhy/app/orders/PriceChangeIntegrationTest.java:209`
- Scenario: TC-155 requires actual cook price updates and placements to serialize.
- Expected: At least one controlled concurrency test invokes the production meal update service/API and placement together.
- Actual: The race tests call changePriceInCurrentTransaction, which reproduces the intended database effects using direct SQL. This tests placement against those SQL locks but cannot prove the production update path retains the same locking behavior.
- Evidence: PriceChangeIntegrationTest.java:118-120 and :150 call the helper; :204-215 explicitly describe and implement a SQL imitation of MealService.update. The existing real browser price-change journey supplies useful sequential integration, but not this concurrent production-command boundary.
- Suggested fix: Keep the low-level locking tests and add production-service/API concurrency coverage with observable transaction barriers.
- Repro test: Run actual cook meal-update and customer placement commands with each committing first. Assert update-first gives PRICE_CHANGED with no order/stock effects; placement-first preserves the validated snapshot and the update applies afterward. NOT_RUN.
- Claude triage: OPEN — 

### QA-007 [Low] Route and mocked component checks do not validate real response payload contracts
`backend/app/src/test/java/com/matbakhy/app/web/ContractRoutesTest.java:52`
- Scenario: Pricing/payment clients depend on response fields and actual server authorization behavior.
- Expected: Real backend quote/order/payment responses are validated against OpenAPI, with browser interactions covering deposit selection and reporting.
- Actual: ContractRoutesTest compares route sets only. Registry tests validate specification metadata and schema compilation. Component tests inject fake fetch handlers. The staging deposit smoke performs quote, placement, transfer and receipt through direct API calls after browser login.
- Evidence: ContractRoutesTest.java:34-52 compares registry and served routes. packages/api-client/test/registry.test.ts:23-55 checks metadata and schema compilation. apps/customer-web/src/App.test.tsx:15-18 and apps/operations-web/src/App.test.tsx:13-28 explicitly use fake APIs. apps/operations-web/e2e-staging/smoke.staging.ts:108-131 and :158-168 issue payment API calls directly.
- Suggested fix: Validate captured real HTTP responses against OpenAPI and add a browser deposit workflow driven through customer/cook controls. Preserve the existing component and API tests with their current, accurate labels.
- Repro test: Schema-validate real quote, placed order and payment-transition responses, including null/legacy payment cases. In Playwright, select deposit, review terms, place, report transfer and acknowledge receipt through UI controls in Arabic and English. NOT_RUN.
- Claude triage: OPEN — 


## Phase 2 execution — 2026-10-18

Tested source: `890bc872468085747c938dd3cb27b77ba036c713`. Scope authorized: execute existing planning, migration, Python, secret, TypeScript, lint, JavaScript, build and contract checks in this isolated worktree. No product changes or new behavior tests. Only synthetic checked-in fixtures. No network or backend/native execution.

Use individual `python3 -m unittest` invocations; modules importing sibling scripts require `discover -s scripts -p <filename> -v`. Execute the five pnpm commands verbatim first, then expand workspace commands with `--config.engine-strict=false` for the owner-authorized Node 22 environment. Capture direct Vitest/Jest JSON reports for exact counts because recursive pnpm logs omit runner summaries. Backend Maven/Testcontainers verification is NOT_RUN: no JDK or Docker. Docker proxy tests and secret scanner must remain BLOCKED when unavailable. Existing Phase 1 findings remain historical and are not revalidated by these checks.

See `qa-execution-report.md` and `phase2-evidence/commands.json` for actual results, commands, exit codes and logs. Passing frontend tests do not prove PostgreSQL concurrency, server authorization, live identity, native behavior or release readiness. Recommendations: repeat the blocked checks on a clean runner with Docker/JDK and supported Node.
