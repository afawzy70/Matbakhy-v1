# Independent QA: completed customer and staff journeys

Application tested: **4eaee3a1f53b929b9e7685fd30da689d470d095c**. Campaign pinned to this pushed implementation/mvp HEAD resolved before execution. It includes the c35ad2d web repair handoff, later operations language repair a817164, native recovery updates9dd6fa/f3ff3f0, and API-001 active policy read. Historical9dd6fa and c35ad2d are ancestors; neither was assumed current. No later product revision was followed or retested.

QA branch: **qa/independent-cross-role-4eaee3a-20261012**; isolated worktree `/workspace/matbakhy-cross-role-qa`. Eligibility and initial assertions were committed at d73638e before runtime. QA controls/evidence are separate from product code. No product change, merge, deployment, release approval, paid tool/service, Kane run, real payment, or production data access occurred.

## Scope and environment

Applicable AGENTS/CLAUDE, product decisions/rules/state machines/screens/permissions, backlog/execution checkpoint, latest status, handoffs06/07, previous reporta7ed1f3, actual frontend/backend/contracts and documented test fixtures were read. Status labels were cross-checked against implementation. Available React UI bug checker was applied in REPORT-ONLY mode with cloud runtime guidance. Its linked checklist/table resources were unavailable through skills.read; direct browser/DOM/table inspection supplied those checks. No third-party skill was installed and no agent was delegated work.

See [eligibility.md](eligibility.md) for the frozen role/surface matrix, delivery evidence, dependencies and execution amendments. Customer web and operations web exist; native app is customer-only. Responsive operations web is not a staff Android/iOS application. MB-031 is delivered; MB-032 is still in progress with implemented pickup/basket/recovery segments. Applicant editing UI and existing catalogue content-edit UI are explicitly partial/future. Customer web logout and public support-thread absence conflict with delivered requirements: these are delivery discrepancies/defects, not excluded future work.

Real local services: API/PostgreSQL16.15 with actual Flyway migrations, Keycloak26.8.0 real password/OTP, S3Mock5.2.3, Mailpit1.31.5; Docker28.4/Compose2.40.3; Chromium154.0.8037.57; Node24.21.0, pnpm10.34.6, full JDK21.0.12, pinned Maven/dependency tree. Dedicated Compose project and volumes `matbakhy-cross-role-4eaee3a`; customer35173, staff35174, API38080, DB35432, IdP38180, S339090, mail38025/31025. Distinct customer-a/b, cook-a/b, reviewer, support, finance-a/b, admin and config accounts/grants. No developer session/DB/volume was used. See [README.md](README.md) for commands, isolation and substitutions.

Baseline grants/approved kitchens/media/published content/verified contact were synthetic SQL prerequisites. New catalogue/photo/slot creation, applicant submit/reviewer approval, orders/lifecycle/cash, grants/revocation and configuration changes were actual authorized UI/API actions. True staff OTP execution mapped to real authentication methods; no forged MFA or bypassed business rules. Phone OTP positive flow is blocked because the fake sender exposes no retrieval channel; fixture-verified contact is not that journey's PASS.

Development storage/scanner/notification/SMS adapters and a synthetic commerce policy replace production providers. Transport injection withheld actual committed order response and lookup responses; no fake order/quote result was substituted. Policy actually configured: max10 per line, tax0%, commission10% HALF_UP on meal subtotal excluding delivery, fee3000 minor for the tested zone. Earlier healthcheck/S3 proxy/JDK/readable-jar problems were QA environment failures and resolved in the isolated override.

Pinned product CI push38272600348 and PR38272607044 finished **cancelled**, not passed. This is inspected CI evidence only. Independent browser/native execution below is the basis of this assessment; unit/build/earlier developer CI results do not make a journey pass.

## Executed web results

**28 reviewed executed case groups:25 PASS,3 FAIL,0 BLOCKED,0 NOT_RUN** in this executed set. Each group contains explicit subchecks; retries and supplemental segments are not counted again. Additional eligible gaps below are separately counted, rather than hidden in this total. [cases.md](cases.md) records requirements, prerequisites, steps, expected/actual subchecks, statuses and evidence. [reviewed-results.json](evidence/reviewed-results.json) is the compact machine-readable result; [results.json](evidence/results.json) preserves raw attempts, including harness failures.

| Actor/surface | Cases and verified scope | Result |
|---|---|---|
| Customer web | Real login/restoration, distinct identities, backend logout401, protected staff denial; customer-facing logout control missing | C-AUTH FAIL / MBQA-003 |
| Customer web | Discovery/search/zone; exact bilingual home/meal/kitchen response content/language; EGP/availability; basket arithmetic/edit/remove/empty/persistence; all4 kitchen/slot replacement paths with exact resulting IDs | C-BROWSE/BASKET +4C-REPLACE PASS |
| Customer web/API | Selected mixed-stock slot exhausted through real second-customer order; disabledAdd/cap protection; pickup quote/terms/client-price rejection; actual cap10 read/enforcement; stale publication/capacity refuse placement atomically | C-STOCK/Q1/QUOTE/STALE/C-CAPACITY-STALE PASS for listed assertions; Q1 client alignment unfinished |
| Customer and cook web/database | Cook publishes offering and opens newly created slot; customer exact order; correct cook queue; lifecycle/handover/customer update; wrong-role/wrong-kitchen/invalid transition; retry without duplicate event | S-CATALOG/S-SLOTS/C-ORDER/S-INVALID/X-FULFILL PASS |
| Customer/cook web/database | Delivery address validation/create, configured fee/tax/total, order, accept/prepare/ready/dispatch/code handover, customerDelivered, immutable prices and commission rounding | C-DELIVERY PASS |
| Customer web/database | Lost committed response, offline pending/locked basket, persisted original keys on reload, restored connectivity and same-key UI retry, exactly one order, clear pending/cart | C-RECOVERY PASS for this injected transport/manual retry flow |
| Customer web/API | Own completed review4, one review only, wrongowner denial; favorite add/remove; pending UI cancellation and exact release | C-TRUST PASS |
| Customer/support web/API | Case creation; staff explicit private/public reply; public-only own API, othercustomer404, cook/customer staff403; customer web cannot read public reply | S-SUPPORT FAIL / MBQA-004; privacy subchecks PASS |
| Cook/staff web/API | Exact granular role navigation, cook session/logout/ownership; reviewer moderation hide/restore/public visibility, order monitor exact number | S-AUTH/S-MODERATE PASS |
| Applicant API + reviewer/cook web | Draft/submit without cook privilege, independent reviewer trueOTP approval, approved owner workspace | S-ONBOARD PASS for this partial applicant-API→reviewer-web segment; no applicant-edit UI PASS |
| Cook/finance web/API/database | CompletionDUE distinct from cash; cook report then separate finance verification; exact commission; full refund request/replay/self-approval denial/separate approval/overrefund rejection/unpaid rejection; settlement draft/self-review denial/separate review/no payment/unpaid reconciliation denial; balanced ledger | S-FINANCE PASS through stated boundaries; no real or recorded bank payment |
| Admin/config web/API | Real OTP-sensitive permission/config mutation; selfescalation403; genuine grant to password-only customer still STEP_UP_REQUIRED; revoke; fee version/audit and restore | S-CONFIG PASS; full new/old quote policy-change matrix NOT_RUN |
| Cook web | DRAFT slot card offers Close, server409, slotunchanged and visible unusable action persists | S-DRAFT-CLOSE FAIL / MBQA-005 |
| Customer/cook/support/admin responsive web | 6screen×3width×2language=36 sampled combinations, no document overflow/critical or serious axe findings; keyboard outline and semantic5column statement table; screenshots inspected | W-A11Y PASS for practical scope; screen-reader BLOCKED |

Pickup order **620ab03d-db91-443f-8e32-d36cbd939e36 / MB-100005** is correlated UI/API/PostgreSQL, kitchenA, staff-created slot **d51ea2ec-9c6b-44da-b112-5f86fd3d5477**, A1×2, subtotal/total24050, fee/tax0. On completion both slot and meal counters are reserved0/allocated0/consumed2; exactly5 lifecycle events and only1ACCEPT after replay. Customer seesDelivered for backendCOMPLETED. Cash separately wentDUE→REPORTED→VERIFIED; commission2405.

Delivery order **9a29b97b-ae92-471e-a448-759997fdd767 / MB-100009** is same-ID-correlated. Subtotal12025 + configured fee3000 + tax0 =15025; 10% HALF_UP commission1203 excludesfee. READY handover refused until dispatch; correct customer code completed. See [cross-role-completed.json](evidence/cross-role-completed.json), [delivery-order.json](evidence/delivery-order.json), [finance.json](evidence/finance.json).

Recovery scope is precise: lookup network was interrupted, loaded SPA remained understandable offline, browser reload retained keys, UI same-key retry reconciled one order and no additional reservation. Offline full-page web reopening/PWA availability was not claimed. Earlier simultaneous reconnect/reload attempts raced navigation and were blocked harness attempts; automatic lookup recovery under every reload timing is not independently established by the final manual-retry PASS. Native app termination recovery is evaluated separately.

Console/network review: normal positive flows had no uncaught page errors in the recorded sample. Expected bounded401/403/404/409/422 appear in negative tests. Injected lookup/response faults generated8 uncaught `Failed to fetch` page errors from the customer recovery lookup; pending UI remained available and retry succeeded. **OBS-001** is a low-priority runtime error-handling observation, reproducible under lookup transport failure (Checkout's lookup promise has no catch), not a claim that recovery duplicated/lost an order. Network failures intentionally injected by QA are disclosed. S3upload500 was resolved by local non-proxy configuration and is not a product defect.

## Confirmed prioritized defects

All findings below test **4eaee3a1f53b929b9e7685fd30da689d470d095c**. They are newly detected in this expanded campaign; the UI absence/action predates this assessment, and no regression claim against an untested earlier version is made. No duplicate native finding is inferred.

### MBQA-003 — Major: delivered customer web has no sign-out action

Role/platform: customer/web. Preconditions: synthetic customer-a, real successful OIDC session200; no staff grants (temporary probe later revoked).

1. Sign in through Keycloak on customer35173.
2. Open My account; inspect header/footer and complete Account content in English/Arabic.
3. Look for a sign-out control and attempt an ordinary user sign-out/account switch.

Expected: CW11 account sign-out; MB-009/027/028 delivered claims and BR-048 private-session cleanup principle. Actual: no sign-out button/link in header, nav or Account. User cannot end a session using the delivered interface; backend `/api/v1/auth/logout` works and was separately verified401 as a QA workaround. Android sign-out/account-switch executed successfully; iOS remains separately blocked unless final native evidence states otherwise.

Impact: shared-browser customer cannot deliberately clear authenticated access or switch accounts normally. Reproducibility: original C-AUTH plus repeated complete Account DOM inspection and implementation inspection agree. Evidence: [C-AUTH-account.png](evidence/C-AUTH-account.png), [C-AUTH-account.json](evidence/C-AUTH-account.json), C-AUTH exact control inventory/session observations and logout network401. Classification: product defect + delivered UI acceptance discrepancy, not a missing future feature.

### MBQA-004 — Major: customer web cannot read a support public reply

Role/platform: customer + support/web (customer thread API works). Fixtures: customer-a, support-a, orderMB-100005, case **CS-A12B4EF8** (exact UUID in [support-case.json](evidence/support-case.json)); othercaseCS-C1352825 is an earlier setup attempt, not another defect.

1. Customer opens Account; selects MB-100005 and creates a quality case with synthetic description.
2. Support staff opens exact case and sends STAFF_ONLY `QA PRIVATE NOTE NEVER FOR CUSTOMER` then PUBLIC `QA PUBLIC REPLY FOR CUSTOMER`.
3. Customer reloads Account; tries to open/read the case/reply.
4. Check the own customer thread API and othercustomer/cook denials.

Expected: CW16 order-linked case public messages/resolution, MB-024/028 delivered support; BR-041 private notes absent. Actual: staff public reply saved201 and own case API includes it; customer Account renders reference/order/category/state only, no case link, message display or reply control. App has no support detail route. Private note correctly absent from customer API; othercustomer404 and unauthorized staff access403.

Impact: customer can request help but cannot read the answer or continue the thread in the delivered web UI. Reproducibility: complete real cross-role creation/reply/reload reproduced; runtime screenshot and Account/App implementation agree. Evidence: [S-SUPPORT-customer-no-reply.png](evidence/S-SUPPORT-customer-no-reply.png), [staff-thread.png](evidence/S-SUPPORT-staff-thread.png), case and per-message API status/body in network/results. Classification: product defect + delivered-thread discrepancy; attachments/real support contacts remain separate future/policy limits.

### MBQA-005 — Moderate: cook DRAFT slot offers an invalid Close command

Role/platform: cook/web. Fixture: cook-a/kitchenA, DRAFT future slot **37dbfa84-5fbb-4766-a000-2c0e74648704**, campaign day7 Cairo13–15, max8; exact slot in S-DRAFT-CLOSE network evidence.

1. Open Slots as cook-a; locate the DRAFT card.
2. Click visible Close.
3. Observe response/error, refresh and read actual state.

Expected: MB-015/022 state-aware operational controls offer valid actions; normative slotDRAFT→OPEN→CLOSED. This expectation does not invent a permission to close a draft. Actual: frontend offers Open and Close forDRAFT; backend rejects DRAFT→CLOSED409 STATE_CONFLICT, error “This action is no longer available. Refreshed.”, draft stays unchanged and Close remains available.

Impact: a normal staff action always fails and misleadingly invites retries; no data corruption observed. Reproducibility: bounded direct command and real card-click both409; row/source inspection matches. Evidence: [S-DRAFT-CLOSE-rejected.png](evidence/S-DRAFT-CLOSE-rejected.png), [DOM](evidence/S-DRAFT-CLOSE-rejected.json), corresponding PATCH409 and rereadDRAFT in network/results. Classification: product interaction defect, server state protection correctly refuses the invalid transition.

## Previous defects and Kane correction

| Prior finding | Independently retested at this SHA | Scope/limits |
|---|---|---|
| MBQA-001 language request uses previous locale | **Repaired on web**: home/meal/kitchen both directions, exact language header and response content verified | Operations navigation/RTL also exercised. Native English↔Arabic navigation is separately assessed; not the same catalogue response-content oracle. |
| MBQA-002 selected unavailable slot canAdd | **Repaired on web**: real exhausted selected slot with another available; Adddisabled/no new draft; cap bounded; legal cancel releases | Native mixed-stock fixture variant was not independently run; developer native tests do not close native gap. |
| FG-001 basket edit/removal/estimate/kitchen missing | **Repaired on web** with exact intermediate integer totals, persistence, all replacement paths, quote invalidation | Native extra result reported separately; never inferred from web or unit tests. |
| Q1 | Configured server cap10 read and enforcement verified; webstill11/20 controls on high-stock slot, quote422 RANGE_1_TO_10 | API-001 segment delivered; client integration explicitly in progress. No claim that this mismatch is repaired; commercial O04 decision remains separate. |

**Correction to my previous Kane comparison:** that report used the older blocked pilot and incorrectly implied no successful Kane execution. Successful report **d0ba6424e165ca50d1136cb4928d508f405118f0**, `verification/tool-pilots/kane-cli/2026-10-12-resume-execution.md`, tested **1ee2eaef8e31e8e821b5a050eba071b32a96feca**. TC-K1 present descriptions/browse, K2 add, K3 different-kitchen keep/replace and same-kitchen different-slot **keep only** did execute and were reportedPASS. Initial agent misread is tool failure; D12 absenceNOT_RUN. Its scope is desktop Chromium/Arabic, no English/mobile, no same-kitchen confirmation or exact cart slot readback inK2. I acknowledge and correct the earlier comparison; no new Kane call/credit was consumed.

Assertion review limits agreement: Kane's present-description check was paragraph presence, not exact localized text. K2's “cart total” claim cannot establish an estimated subtotal/editing journey: that application revision's Checkout renders the price summaries only after quoting, and the pilot did not cover edit/remove; its reported add-to-cart PASS is narrower than FG-001. This campaign's explicit subtotals/result IDs extend coverage; none of these prior PASS labels refutes MBQA-001 or later unavailable-slot handling. D12 absence/schema limitation agrees with our explicit NOT_RUN/requirement question, not a silent requirement change.

## Remaining coverage and repair handoff

Additional eligible web tests outside the executed28 groups: **2 BLOCKED** (positive phoneOTP delivery/entry; required screen-reader) and **4 NOT_RUN** case groups (accepted-cancellation/ops exceptions; operational settings pause/lead-time variants; paid refund/remittance reconciliation; complete configuration new/old-quote snapshot matrix). These are notPASS. Web eligible inventory therefore: **34 groups =25PASS/3FAIL/2BLOCKED/4NOT_RUN**. Within tested groups, history pagination, all validation boundaries, simultaneous concurrency/load, exhaustive staff session expiry/revocation combinations, every moderation/support resolution and notification/provider outage variant remain untested subchecks. Desktop Chrome and narrow viewports are sampled; no Safari/Firefox coverage inferred.

Unfinished/excluded: complete native delivery/full MB-032 acceptance, physical Android/iOS devices/store submissions (MB-034/035), staff native apps (absent), applicant editing UI explicitlylater, existing-content edit UI partial, online payment/real SMS/provider/production readiness. These are not regressions. Paid-refund/remittance recording capabilities exist but were not executed to a final paid/reconciled state; no synthetic reference was represented as a real payment.

Requirement questions remain: D12 optional discovery descriptions versus mandatory published-description constraint/locale fallback; RQ replacement inline versus modal/focus expectations; exhausted offerings' discoverability semantics; Q1 public cap alignment vs commercial O04 choice; O06 mandatory approval documents; O07 staging and O09 support contacts. Onboarding with no documents tests implemented synthetic policy, not owner approval of an identity-check policy. Screen-reader absence keeps MB-029 and dependent MB-030/P06 unclosed.

Claude repair priorities:

1. Add customer web sign-out using real logout/session cleanup, then independently verify reload401/private state/account switch (MBQA-003).
2. Deliver customer public case-detail/read/reply UI using the existing own-case API; keep STAFF_ONLY filtered server-side and retest exact cross-role case (MBQA-004).
3. Align DRAFT slot card actions with allowed server transitions; retain backend refusal and add a UI action-availability regression check (MBQA-005).
4. Complete Q1 client-cap integration against active public configuration and recheck meal/cart controls, changed policies/quotes and native parity. Catch recovery lookup transport rejection (OBS-001) without clearing pending intent or masking uncertainty.

Next useful milestone: separately pin Claude's pushed repair SHA; retest these3 stable defects + basket/stock/language/totals/security adjacency. Native recovery must have independent Android and iOS outcomes, including basket/process/connectivity behavior, plus native unavailable-slot and delivered delivery acceptance when those segments are ready. Obtain an executable screen reader and test critical journeys before closingMB-029. This assessment does not approve a release gate.

## Native checkpoint (campaign still running)

Independent run38273970923: Android actual auth/account switch and replacement/pickup/process-kill/offline uncertain recovery PASS; own DB correlation one order9000 and both counters1. iOS actual release app launch worked, but XCUITest auth launch timed out before login: BLOCKED, dependent journey tests NOT_RUN. Run38279186460 at QA controls6c887d3587ec8fd79a042df7063b0806aefad78c repeats Android auth/order/recoveryPASS and correlationPASS; supplemental basket test was BLOCKED before its first assertion (meal control unavailable on an offline-recovery navigation stack). No product failure is inferred from that agent outcome. iOS is still executing. A bounded third Android-only harness attempt starts a real new foreground process with connectivity restored, preserving private storage/session and all business data. All attempts use the identical productSHA4eaee3a; none is a later repair retest. Native counts/final outcomes must be added before final handoff.
