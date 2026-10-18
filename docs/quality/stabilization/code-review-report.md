# Code review report (round 1)

Agent: independent Codex CLI 0.163.0, `codex exec -s read-only`, stdin </dev/null. Commit inspected: `b7ec0e9052a612f7a8aab38f00519107b1dce213`. Self-reported scope: Found three High-severity defects involving deposit accounting, staff MFA enforcement, and approved document integrity. Read-only static review covered the requested areas with deeper inspection of security and transactional paths; it was not exhaustive line-by-line verification. No files changed. Build, integration, and native tests were not run.

Static read-only review; no build/integration tests were run by the reviewer.

## Commands (as reported by agent)
| Command | Exit | Note |
|---|---|---|
| `cat .agents/codex/code-reviewer.md` | 0 | Read reviewer role first. |
| `cat CLAUDE.md docs/delivery/02-execution-protocol.md; rg --files docs/product; git rev-parse HEAD; git status ` | 0 | Confirmed requested HEAD and clean workspace. |
| `cat docs/product/{01-context-and-decisions,03-business-rules,04-state-machines,06-permissions,09-deposit-payme` | 0 | Output truncated. rg reported backend/src/main missing; pipeline exit code was 0. |
| `rg --files backend | head -100; cat .github/workflows/{deploy-preview,preview-images}.yml; cat packages/api-cl` | 0 | Inspected repository layout, deployment workflows, and shared client. |
| `rg --files backend | rg '(OrderService|PaymentService|QuoteService|Security|Idempot|Migration|/V[0-9]|FinanceO` | 0 | Located transaction, security, and migration sources. |
| `cat backend/modules/orders/src/main/java/com/matbakhy/orders/internal/{OrderService,PaymentService}.java` | 0 | Output truncated. |
| `cat backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java; cat backend/modules/financ` | 0 | Inspected session/CSRF chains, cash accounting, and idempotency. |
| `cat backend/modules/finance/src/main/java/com/matbakhy/finance/internal/{LedgerService,FinanceOpsService}.java` | 0 | Inspected ledger, refunds, and settlements. |
| `cat backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java` | 0 | Inspected server pricing and quote revalidation. |
| `cat backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/JdbcMealCatalog.java backend/modules/a` | 0 | Inspected pricing locks, stock transitions, controller permission checks, and deposit migrations. |
| `cat backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderLifecycleService.java; cat apps/cus` | 0 | Output truncated. |
| `cat backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaService.java; cat deploy/we` | 1 | Final cat failed because CaseService.java does not exist; preceding files were read. |
| `rg --files backend | rg '(Storage|Support.*Service|StaffAssurance)'; cat backend/modules/identity/src/main/jav` | 0 | StaffAssurance path was incorrect; subsequent reads succeeded, giving compound command exit code 0. |
| `cat backend/app/src/main/java/com/matbakhy/app/providers/S3ObjectStorage.java backend/app/src/main/java/com/ma` | 0 | Inspected storage signing, MFA validation, and support scope. |
| `cat backend/app/src/main/java/com/matbakhy/app/security/WebAccountFilter.java; cat backend/modules/identity/sr` | 0 | JdbcGrantDirectory.java was missing; remaining reads succeeded. Captured finding line references. |
| `cat backend/modules/identity/src/main/java/com/matbakhy/identity/internal/GrantService.java; rg -n 'otp|authen` | 0 | Confirmed grant mapping and cook cash form behavior. |
| `cat docs/product/03-business-rules.md docs/product/04-state-machines.md; cat .github/workflows/native-build.ym` | 0 | Output truncated. Inspected product rules, native workflow, migration operations, client exposure pa |
| `nl -ba apps/operations-web/src/cook/Orders.tsx | sed -n '178,207p'; nl -ba backend/modules/catalog/src/main/ja` | 0 | Confirmed final HEAD, clean workspace, contract fields, and evidence references. |

## Findings

### CR-001 [High] Cash reporting and verification demand the full total after a deposit
`backend/modules/finance/src/main/java/com/matbakhy/finance/internal/CashService.java:96`
- Scenario: A completed EGP 200 order has an acknowledged EGP 100 deposit and EGP 100 cash remainder. The cook reports the correctly collected EGP 100 cash.
- Expected: The cash report has zero discrepancy and finance can verify EGP 100 against the immutable cash obligation.
- Actual: The report records a negative EGP 100 discrepancy and marks the obligation DISPUTED. Verification rejects EGP 100 because it also requires the full EGP 200 total. The cook form defaults to that full total.
- Evidence: OrderService.placeOnce inserts cash_obligation.amount_minor as q.totalMinor() - q.depositMinor(). CashService.java:96-106 instead calculates discrepancy against customer_order.total_minor; lines 127-150 use that total again for verification. apps/operations-web/src/cook/Orders.tsx:182 initializes the report amount from order.total.
- Suggested fix: Use cash_obligation.amount_minor for reporting and verification, and payment.remainder for the cook form default. Handle zero-remainder orders explicitly.
- Repro test: Add an integration scenario placing a 50% deposit order, acknowledging receipt, completing it, reporting the remainder, and verifying that remainder. Assert zero discrepancy and VERIFIED cash state. Not executed.
- Claude triage: FIXED_PENDING_RETEST — CONFIRMED by reading CashService/OrderService; fixed in this branch

### CR-002 [High] Staff grants become usable without MFA
`backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java:49`
- Scenario: An active account with finance.verify or support.case authenticates using a password-only session or bearer token with no MFA claims, then accesses its staff endpoints.
- Expected: BR-007 and the permission specification require MFA for staff sessions, with recent step-up additionally required for high-risk actions.
- Actual: AccountMapper grants every active database permission without checking assurance. Most staff controllers check only those permissions, so password-only authentication can read support conversations and verify cash reports.
- Evidence: AccountMapper.java:49-50 unconditionally adds active permission authorities; assurance claims are merely copied at lines 56-62. SecurityConfiguration's API chains require ACCOUNT_ACTIVE without a staff MFA guard. StaffCaseController.java:31 requires only PERM_support.case. CashController.java:70 requires only PERM_finance.verify. StaffAssurance is invoked only by selected endpoint annotations.
- Suggested fix: Require a validated MFA assertion before granting or exercising any staff authority. Keep recent authentication age checks as an additional requirement for sensitive actions.
- Repro test: Authenticate an account holding support.case and finance.verify with claims lacking MFA. Assert denial for staff case reads and cash verification; assert access with valid MFA and appropriate grants. Cover both web and bearer chains. Not executed.
- Claude triage: OPEN_OWNER_DECISION — Docs (06-permissions.md:26) place staff MFA at the identity provider; API only enforces step-up on selected endpoints. Defense-in-depth gap is real but enforcement in the API changes behaviour for preview staff accounts and many tests; needs owner decision + IdP verification

### CR-003 [High] Reusable upload URL can replace an approved private document
`backend/app/src/main/java/com/matbakhy/app/providers/S3ObjectStorage.java:49`
- Scenario: A cook uploads a clean document and completes scanning, then reuses the still-valid ten-minute PUT URL to replace it with different bytes of the same length and content type.
- Expected: Approved document reads return exactly the bytes that passed checksum, content inspection, and malware scanning.
- Actual: The PUT signature binds the key, type, and length but no content checksum or create-only condition. Approval retains the upload key, and private reads sign that same mutable key. Completion returns immediately for an APPROVED row, so replacement bytes are served without inspection.
- Evidence: S3ObjectStorage.java:48-50 signs PUT without checksum or overwrite protection. MediaService.java:121 issues the upload URL; lines 132-133 skip verification for approved media; lines 273-274 generate approved document read URLs using row.privateKey().
- Suggested fix: Bind uploads to the declared checksum and ensure approved reads reference immutable verified bytes, using a separate protected object or a verified immutable object version. Prevent upload tickets from overwriting approved content.
- Repro test: Against the configured S3-compatible storage, upload and approve a clean synthetic document, overwrite it through the original ticket before expiry, then read it as a reviewer. Assert overwrite rejection or retrieval of the original verified bytes. Not executed.
- Claude triage: FIXED_PENDING_RETEST — CONFIRMED by code reading; approved documents now sealed to documents/<id> and upload key deleted after commit
