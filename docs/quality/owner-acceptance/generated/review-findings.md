# Review of the original PR #12 catalog

Original: 108 case headings (62 functional + 46 configuration/security), not the 121 the PR text claims; none cited a TC or BR; every functional case repeated the same generic steps. Findings and per-case dispositions below come from the domain authors' source review (file:line evidence inside each finding).

## config-security

### Findings

- owner-e2e-configuration-security-cases.md:17 hard-codes id.167-86-90-174.sslip.io; the domain is the variable PREVIEW_DOMAIN (deploy.sh load, Caddyfile {$PREVIEW_DOMAIN}) and may differ per environment
- owner-e2e-configuration-security-cases.md:5 and owner-e2e-execution-guide.md environment table say customer/operations URLs 'may be public'; deploy/web-preview/Caddyfile:1-7,43-50 is deny-by-default (CUSTOMER_ALLOWED_IPS / STAFF_ALLOWED_IPS), so a tester outside the lists gets 403 'Not available' and CFG-034 'HTML accessible' (line 523) is false for ops.
- owner-e2e-configuration-security-cases.md:538 (CFG-035) expects 'Blocked'; Caddyfile id. site and smoke.sh:62-65 require exactly 404 for /admin and /realms/master; the case is also a duplicate of CFG-001
- Every case carries identical boilerplate preconditions and steps 1,3,4,5 (e.g. lines 11-21, 296-309); only step 2 differs and is one sentence, so the cases are not executable by a human without another document
- CFG-007..013 and CFG-017 (lines 101-264) assume the MFA realm migration and D18 API are deployed; origin/develop contains no V31 migration, MfaController or MfaEnforcementFilter (git ls-tree origin/develop), and docs/operations/13-keycloak-mfa-migration.md says the live run has NOT happened
- The catalog assumes Admin A/Admin B with TOTP, Support, Finance fixtures; the preview seeds only preview-ops-1 (bootstrap: staff.grant and audit.read only, GrantService.bootstrapAdministratorIfNone) and preview-ops-2 (no grants). Nobody on the preview holds security.mfa_policy or security.mfa_reset until preview-ops-1 grants them to another account (self-grant is 403)
- CFG-012/010/009 imply STAFF or KITCHEN DISABLED disables MFA for that role; MfaPolicyService.resolve gives every account the CUSTOMER category, so a staff or cook account is DISABLED only when CUSTOMER is DISABLED too (matrix rows R7, R9)
- CFG-015 requires two admins racing; the rule is deterministic with one admin sending a stale expectedVersion (MfaPolicyService.update)
- CFG-039 (line 598) 'Throttling ... against login and OTP endpoints' presumes application rate limiting; the only RATE_LIMITED users are PhoneVerificationService, ReviewService and OrderLifecycleService (grep). Login throttling is Keycloak bruteForceProtected=true with default thresholds (matbakhy-realm.preview.json:10). TC-054/BR-054 coverage for login/OTP/quote/upload/search is not demonstrable
- CFG-044 (line 673) expects 'interrupted agent tasks resumed only from evidence' which concerns development agents, not the product; dropped from expectations
- CFG-041 says merge to develop and watch Actions: that is a production-affecting trigger on the shared preview; also deploy-preview.yml failure path only does `cp "$old" .env; exit 1` (lines ~133-140): running containers are not reverted, so .env and the running stack can disagree after a failed deploy (new CFG-042 step 3)
- Directory inconsistency: deploy-preview.yml uses /srv/matbakhy-staging/deploy/web-preview while docs/operations/06-web-preview.md:176 uses /srv/matbakhy-preview/src; workflow name says Contabo while docs/operations/11 describes Hetzner. Operators must confirm the real path
- CFG-038 expected 'Report-Only acknowledged'; the doc-backed pass criterion is that NO enforced CSP header exists and an injected inline script still runs (csp-rollout.md); an enforced-CSP claim would be false
- CFG-046 and CFG-024..026 refer to a TestFlight build and physical device, but docs/mobile/ios-beta-device-test-plan.md exists only on branch beta/ios-testflight-prep (claude-beta worktree), not on PR #9; section C (C1-C6) is the only MFA device plan and mfa-policy.md states no device/simulator MFA run exists yet
- Catalog header claims 121 cases while the whole catalog has 108 IDs (task statement); this file alone had 46 IDs CFG-001..046 and no traceability fields to TC-/BR- ids
- Execution guide says 'run configuration mutations in isolated environment first' but no case states how to build FX-ISOLATED; prerequisites here define it (fresh stack, realm imported, optional migration from origin/develop's realm file, migrate-mfa.sh apply)
- docs/security/mfa-policy.md says preview-ops-* must stay password-only for the E2E suite, yet enabling and testing admin policy/reset requires an enrolled administrator; use a separate FX-STAFF-ADMIN-B rather than enrolling preview-ops-1
- Original catalog has no case for: staff grant self-escalation and last-administrator protection (TC-043), high-risk step-up independent of MFA mode (BR-007), admin client least privilege, fixture-clean, rollback-plan, e2e-reset guard, security headers per host, Android device; all added
- Unsafe suggestions corrected: any test that changes CUSTOMER or KITCHEN to REQUIRED, resets a live user's authenticator, or deletes mfa_policy rows is now isolated-only (mfa-policy.md step 5: do not set CUSTOMER/KITCHEN REQUIRED until mobile is device-verified); no case publishes the Keycloak admin console (CFG-001 asserts 404; administration only through SSH tunnel/kcadm-in-container)
- Review round 2: CFG-034 vantage points corrected (a staff-only IP gets proxy 403 not backend 401), customer order table is customer_order (not orders) and idempotency_record (not idempotency), account route is #/account, CFG-016/104 split so shared-preview checks are refusal-only, and refs.scenarios now lists only end-to-end TCs (CFG-043 etc. moved to refs.partial)

### Dispositions of the original cases

| Original | Action | Reason |
|---|---|---|
| CFG-001 | rewritten | Hard-coded 167-86-90-174.sslip.io replaced by <PREVIEW_DOMAIN>; expected 'blocked' made exact (404); absorbs CFG-035; actuator and http->https checks added |
| CFG-002 | rewritten | Concrete authorize-request parameters (PKCE S256, redirect_uri), no-OTP assertion and logout/cookie checks |
| CFG-003 | rewritten | Concrete hostile inputs; added returnTo open-redirect probe (WebReturnTo); removed vague 'separate isolated browser sessions' |
| CFG-004 | rewritten | Original was not executable ('expire/revoke session'); now WEB_SESSION_TIMEOUT idle test and spring_session deletion on isolated stack; refresh-token expiry moved to mobile CFG-026 |
| CFG-005 | rewritten | Concrete endpoints and forged-header probes; adds proxy staff-class caveat and the no-grant staff account |
| CFG-006 | rewritten | Concrete web account-switch steps; mobile switch lives in CFG-025 |
| CFG-007 | merged_into:CFG-101 | Row R1/R3 of the table-driven decision matrix |
| CFG-008 | rewritten | Now covers preference lifecycle with real error codes; absorbs CFG-017 |
| CFG-009 | merged_into:CFG-101 | Rows R7/R8 (cook/KITCHEN) |
| CFG-010 | merged_into:CFG-101 | Rows R9/R10 (staff); staff high-risk behaviour moved to CFG-104 |
| CFG-011 | rewritten | Enforcement and cannot-disable under REQUIRED with exact open/protected endpoint list |
| CFG-012 | rewritten | DISABLED behaviour with preference retained and staff high-risk control; matrix rows live in CFG-101 |
| CFG-013 | merged_into:CFG-101 | Rows R7-R12 cover cross-role precedence |
| CFG-014 | rewritten | Isolated-only; exact SQL and expected REQUIRED for kitchen accounts only |
| CFG-015 | rewritten | Single-admin stale-version proof instead of requiring two admins; adds reason validation |
| CFG-017 | merged_into:CFG-008 | Disable safety is steps 4-6 of CFG-008 |
| CFG-018 | rewritten | Guard ladder with exact statuses and audit rows; 'Support' fixture marked PROVISION |
| CFG-019 | rewritten | Authorized reset with exact result fields, audit sequence and live-credential caution |
| CFG-020 | rewritten | Concrete outage drill with exact audit rows |
| CFG-021 | rewritten | Token-level proof limited to harness/device; automated tests named as primary evidence |
| CFG-022 | rewritten | Replay acceptance marked unverified (OTP policy not configured); brute-force on isolated user only |
| CFG-023 | rewritten | Exact 503 + field ADMIN_ACCESS_NOT_CONFIGURED and no-audit-row expectation |
| CFG-024 | rewritten | Device steps from the iOS plan C1-C3 with no-auto-resend and idempotency expectations |
| CFG-025 | rewritten | Account-switch cleanup and cancel path (plan C4, B5) |
| CFG-026 | rewritten | Background/kill/refresh/airplane rows (plan B2, B3, B6, B7) |
| CFG-027 | rewritten | Exact status keys and pre-migration expected values |
| CFG-028 | rewritten | Exact order of apply phases, RESULT/exit codes, owner gate and isolated rehearsal first |
| CFG-029 | rewritten | Idempotency plus rollback-baseline preservation (ff64f32) |
| CFG-030 | rewritten | Now describes verify-mfa-flow.py PASS/FAIL/INCOMPLETE semantics instead of a generic synthetic-staff check |
| CFG-031 | rewritten | State-file-as-data and PURGE paths with injection probe |
| CFG-032 | rewritten | Interruption before the binding with exact recovery expectations |
| CFG-033 | rewritten | Default state checked for customer, cook and staff plus policy rows |
| CFG-034 | rewritten | Original 'HTML accessible' was false for non-staff IPs; now network-class table |
| CFG-035 | merged_into:CFG-001 | Duplicate of the admin boundary half of CFG-001 |
| CFG-036 | rewritten | Exact CORS header list from SecurityConfiguration; bearer-chain CSRF exemption noted |
| CFG-037 | rewritten | Exact cookie names and attributes from code, with unverified parts flagged |
| CFG-038 | rewritten | Report-Only exact policy, injection probe proving non-enforcement, headers per host |
| CFG-039 | rewritten | Reduced to what exists: Keycloak lockout and phone-verification limiter; generic limiter reported as a finding |
| CFG-040 | rewritten | Uses scripts/check_secrets.sh and concrete log greps |
| CFG-041 | rewritten | Observation of a real workflow run with exact commands; not a test trigger on the shared preview |
| CFG-042 | rewritten | Drill built around the real CD failure handler behaviour |
| CFG-043 | rewritten | deploy.sh backup/restore-db with corrupt-dump negative |
| CFG-044 | rewritten | Product-side reboot drill; development-agent text dropped; BLOCKED on change window |
| CFG-045 | rewritten | Concrete UI/log evidence; egress capture flagged as best-effort |
| CFG-046 | rewritten | Mapped to the iOS device plan rows; Android split to CFG-112 |
| CFG-016 | split_into:[CFG-016,CFG-122] | Refusal-only checks stay shareable on the preview; the authorized mutation moved to an isolated, destructive case with explicit restore |

## cook-ops

### Findings

- Boilerplate: every one of the 15 cases in docs/quality/owner-e2e-functional-cases.md:545-712 repeats the same steps 1, 3 and 4 ('Record the current build SHA...', 'Check both visible UI state and the relevant protected API result...', 'Inspect authoritative DB/audit/capacity/finance evidence...'), has no TC-/BR- reference and no fixture, e.g. lines 549-552 for COOK-048.
- Count: the file has 62 '### E2E-' headings (grep -c) while the task statement says the catalog claims 121 cases and only 108 IDs exist; this domain (048-062) holds 15 of them.
- Unsupported UI: E2E-COOK-048 'Open apply-as-cook' - no screen creates a cook application (apps/operations-web/src/cook/Onboarding.tsx:4 'Editing the application is a later customer-web/mobile flow'; grep of customer-web/customer-mobile finds no /api/v1/cook/application call).
- Unsupported: E2E-COOK-053 'approve' a private document - there is no document approval by staff; approval is automatic in MediaService.complete (type/size/checksum/scanner) and staff only read documents attached to a submitted revision (MediaService.status).
- Wrong expectation: E2E-COOK-054 expects 'Denied'; the code answers 404 NOT_FOUND for another kitchen's meal, slot, order and upload (MealService.lockOwn, SlotService.slot lookup, ScopedAccessPolicy.requireOwnerOrPermitted) and 403 FORBIDDEN only for an account with no kitchen (MealService.requireSeller).
- Unsupported: E2E-OPS-056 'reported kitchen' and 're-open permitted review' - the only report feature is for reviews (ReviewController /reviews/{id}/reports); meals are hidden/restored directly (MealService.moderate).
- Unsupported: E2E-OPS-057 'inspect private fields' and 'respond, assign' - the staff case view has no customer phone/address; assignment is only 'assign to me' (SupportService.update StaffUpdate.assignToMe).
- Spec/implementation gap: docs/product/06-permissions.md says Support reads 'assigned/authorized case' only, but SupportService.queue (SupportService.java:167) has no assignment or authorization filter: every holder of support.case sees all cases.
- Unsupported UI: E2E-OPS-061 'edit policy using expectedVersion' - apps/operations-web/src/ops/Configuration.tsx has only FlagRow (ordering-enabled) and ZoneRow (fee/active); the commerce policy is changed only by POST /api/v1/ops/configuration/commerce-policy/versions (ConfigurationService.newPolicy).
- Staff list cannot give a first grant: GrantService.staff() (GrantService.java:124) joins user_grant, so a user with no grant (FX-STAFF-NOGRANT) never appears on the 'Staff permissions' screen; the first grant must be sent by API or fixture SQL.
- Deployed baseline cannot do staff MFA: origin/develop docs/operations/06-web-preview.md line 173 says 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; its realm JSON has no authenticationFlows or acr mapping. Cook approval/suspension, config changes, grants and deposit-recipient approval all use @staffAssurance.recentMfa (CookReviewController:65,76; ConfigurationController:43; StaffController:68,75; PaymentController:81,107) and therefore need PR #9 plus the realm migration.
- Seed approves kitchens by SQL because the staff step-up cannot run on the deployed baseline (seed-preview.sql header comment); the first real approval path (E2E-COOK-049) is therefore untested on any deployed system.
- Finance tab visibility: apps/operations-web/src/App.tsx:69 shows the Finance tab only when GET /ops/finance/statements answers 404, which needs finance.read, finance.settlement or finance.refund_approve (FinanceOpsController:72). The deposit-recipient approval panel lives inside that tab (Finance.tsx:31), so a staff member with only finance.verify (the permission the API requires, PaymentController:81,98) cannot reach the screen.
- Documents and step-up inconsistency: refund approval, settlements and cash verification need only their permission, no recentMfa (FinanceOpsController:58-88, CashController:70), while docs/product/06-permissions.md says high-risk changes always require recent step-up; unclear whether finance approvals count as high-risk.
- PR #9-only security fix: sealing of approved onboarding documents under 'documents/<id>' and cleanup of the upload key exists only in PR #9 (MediaService.java diff); on origin/develop a replayed presigned PUT can still overwrite the object the reviewer later reads (expected FAIL of E2E-COOK-053 step 5 there).
- Untranslated error: KITCHEN_UNAVAILABLE (suspended or paused kitchen, cook tries to accept a pending order) has no entry in apps/operations-web/src/i18n.ts errors (lines 185 ar, 524 en), so the cook sees the generic 'Something went wrong.' (errorText fallback).
- UI cannot declare allergens: apps/operations-web/src/cook/Meals.tsx:249-252 offers only 'None declared' and 'Unknown'; declaring CONTAINS with allergen codes (required to list real allergens) is API-only, so a cook cannot publish a dish with allergens through the web workspace.
- No audit viewing UI: apps/operations-web has no screen for /api/v1/ops/audit; audit review is API-only. API-066 GET /ops/dashboard and API-095 GET /ops/audit/export are in contracts/openapi.yaml but not implemented (contracts/endpoints.json:76/598/859 'implemented': false) - captured as BLOCKED case E2E-OPS-112.
- Cook account deletion dead end: a cook's deletion request ends ON_HOLD with legal_hold_reason COOK_ACCOUNT_REVIEW (CookPersonalData.blocker, AccountErasureService:79-82) and there is no staff screen or endpoint to review or release it (docs/operations/04-privacy-and-recovery.md says only 'cook accounts need staff review').
- Separation of duties not recorded: docs/product/06-permissions.md requires recording when separation of duties could not be achieved; PaymentService.approveRecipient (line 172) refuses own-kitchen approval without an audit row, unlike KitchenService.changeOperatingState and CookReviewService.refuseSelfReview which audit the denial.
- Fixture gaps in seed-preview.sql: it creates preview-cook-1 with kitchen 'مطبخ المعاينة' and a second kitchen whose owner has no login; there is no second cook login (FX-COOK-B), no pending/suspended cook, no staff with grants other than the bootstrap administrator (staff.grant, audit.read only), so most backoffice cases need provisioning.

### Dispositions of the original cases

| Original | Action | Reason |
|---|---|---|
| E2E-COOK-048 | rewritten | Kept as the applicant case. The step 'Open apply-as-cook' has no UI in any client (Onboarding.tsx:4 says editing the application is a later flow; no call to /api/v1/cook/application in customer-web or customer-mobile), so it is driven through the API and the ops-web status line. Documents moved to E2E-COOK-053. |
| E2E-COOK-049 | split_into:[E2E-COOK-049,E2E-COOK-111,E2E-OPS-109,E2E-OPS-108] | Approval now has the real sequence START_REVIEW then APPROVE with reason and fresh MFA, and the listing/dashboard effect. Request-changes/reject history, decision guards and the self-review rule became separate cases. Needs PR #9 (no OTP on the deployed realm). |
| E2E-COOK-050 | split_into:[E2E-COOK-050,E2E-COOK-107] | Suspension rewritten with concrete order counts and the real outcomes (pending cannot be accepted, accepted can be fulfilled, quote refused). Cook pause and the 'staff pause cannot be resumed by the cook' rule split out. |
| E2E-COOK-051 | split_into:[E2E-COOK-051,E2E-COOK-101,E2E-COOK-102,E2E-COOK-103,E2E-COOK-104] | One catch-all 'meal publishing' became: happy path with a moderated photo (UI), publication gates incl. allergen declaration (API, because the UI cannot express CONTAINS), photo upload validation, price history, pause/archive. |
| E2E-COOK-052 | split_into:[E2E-COOK-052,E2E-COOK-105,E2E-COOK-106] | Slot creation/opening (UI) separated from validation errors (cutoff, overlap, ranges, negative capacity) and the capacity-below-obligations rule that needs a real order. 'update capacity' has no UI and is API-only. |
| E2E-COOK-053 | rewritten | There is no staff 'approve document' step: media approval is automatic (type, size, checksum, scanner). Rewritten to the real security properties: private document, scoped reads (404 for others, audited staff reads), expiring read link, replay of the old upload ticket cannot alter the sealed copy (PR #9 only). |
| E2E-COOK-054 | rewritten | Expected result corrected: another kitchen's meals/slots/orders/uploads answer 404 NOT_FOUND (existence hidden), not a generic 'Denied'; a customer without kitchen gets 403. |
| E2E-OPS-055 | rewritten | Became a concrete account x section x probe matrix with exact permission names, expected tabs and the audit rows of denials; the admin-is-not-superuser rule is asserted. |
| E2E-OPS-056 | split_into:[E2E-OPS-056,E2E-OPS-104] | Meal hide/restore kept. 'Reported kitchen' and 're-open permitted review' do not exist in the code and were dropped; the real reported-review moderation became E2E-OPS-104. |
| E2E-OPS-057 | split_into:[E2E-OPS-057,E2E-OPS-105] | Case handling (internal note vs public reply, audited view, assignment) separated from the closing rules. 'Inspect private fields' has no counterpart in the case view and was dropped. |
| E2E-OPS-058 | rewritten | 'Edit client role claim' replaced by what the server can be asked: unknown role field in PATCH /me, self-grant, staff endpoints, forged unsigned bearer token. |
| E2E-OPS-059 | rewritten | Now a concrete grant with step-up, read back through GET /api/v1/ops/audit (no audit screen exists) with exact fields, redaction check and cleanup. |
| E2E-OPS-060 | split_into:[E2E-OPS-060,E2E-OPS-101,E2E-OPS-102] | Last-administrator protection kept alone; self-escalation (and invalid/duplicate grant) and revoke/expiry effects are separate cases. |
| E2E-OPS-061 | split_into:[E2E-OPS-061,E2E-OPS-106,E2E-OPS-107] | The UI edits zone fees and the ordering flag, not the commerce policy, so the version conflict is tested on a zone; policy versions (zero commission, approval guard, no scheduling) and 'new quotes only' are separate cases. |
| E2E-OPS-062 | rewritten | Concrete anonymous checks with curl and the exact sign-in page text; adds the 'no audit row for anonymous' and logout checks. |

## discovery-basket

### Findings

- All 20 original cases in this domain share one boilerplate step list and one evidence line, e.g. docs/quality/owner-e2e-functional-cases.md:18-24 (DISC-001) repeated unchanged through :230-236 (BASKET-020); only the second step and the one-line Expected differ. No fixture IDs, labels, endpoints or TC/BR references are given.
- E2E-DISC-002 (sign-up and verification) is unsupported in every environment: registrationAllowed=false in deploy/web-preview/identity/matbakhy-realm.preview.json:6 and infra/identity/matbakhy-realm.json:6; verifyEmail=true (line 9) with no SMTP server in the preview realm; phone codes are kept only in memory by backend/app/src/main/java/com/matbakhy/app/providers/FakePhoneVerificationSender.java:14-18 and are never logged or returned, so no manual tester can ever complete OTP verification. Even changing a verified customer's number on the shared preview would permanently lock the fixture out of ordering (PhoneVerificationService.start clears phone_verified_at).
- E2E-DISC-006 expects category, date, price and kitchen filters plus pagination. The web home (apps/customer-web/src/Browse.tsx:34-46) sends only q, zoneId, categoryId, maxPriceMinor, sort and limit=50; it has no date, slot, minPrice or kitchen filter, no 'load more' and no cursor handling (paging exists only in the API). Native Discover (apps/customer-mobile/src/screens/Discover.tsx:23) offers search only.
- The category prerequisite ('Mixed categories') is not met by the seed: deploy/web-preview/seed-preview.sql inserts meals without the category column (nullable, db/migration/V9__discovery.sql:3), so every category chip returns the empty state on the preview; the cook UI also has no category control (apps/operations-web/src/cook/Meals.tsx has no category field; only the API accepts it).
- E2E-DISC-007 expects 'explainable serviceability feedback'. The checkout maps only COD_NOT_ELIGIBLE and DEPOSIT_NOT_OFFERED (apps/customer-web/src/Checkout.tsx:384); a NOT_SERVICEABLE quote refusal shows the generic errors.VALIDATION_FAILED text 'تحقق من البيانات.' / 'Check the details.' (apps/customer-web/src/i18n.ts). Expected text in the rewritten case reflects the real behaviour; owner should decide whether this is acceptable.
- STOCK_CHANGED, SLOT_CLOSED, RATE_LIMITED and IDEMPOTENCY_CONFLICT have no entry in the customer web error map (apps/customer-web/src/i18n.ts errors object), so BASKET-012/016/018 and the cutoff case show the generic 'حدث خطأ.' instead of the 'actionable validation' the original expects. The native app was not checked for the same gap.
- After sign-out, navigating to #/orders leaves the list on 'جارٍ التحميل…' indefinitely because useServer returns on a 401 without setting state (apps/customer-web/src/shared.tsx:40-43) and the route renders regardless of the session (apps/customer-web/src/App.tsx route === '/orders'). No private data is shown, but BR-045 requires a denied state with an actionable recovery. Needs confirmation by running E2E-DISC-003.
- E2E-BASKET-013 assumes advancing a test clock; the preview has none (quote lifetime is commerce_policy.quote_ttl_seconds=300 from backend/app/src/main/resources/db/devdata/R__dev_commerce_policy.sql), so the case waits 5 real minutes. The web UI shows no quote countdown.
- E2E-BASKET-014/015 'Versioned price' prerequisite cannot use the seeded meals: they have no photo, and the cook edit path validates published meals as complete (backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/meal/MealService.java:170-172 and :512-514 APPROVED_PHOTO_REQUIRED), so the price change is refused with 422. A cook-published test meal with an uploaded photo is required. Seeded meals must also never be paused (cook pause) because they could not be re-published without a photo.
- E2E-BASKET-014 describes 'refresh and explicitly accept'. The web does not ask for a refresh: on 409 PRICE_CHANGED it re-quotes automatically and shows the notice with an explicit 'أوافق على الإجمالي الجديد' button (apps/customer-web/src/Checkout.tsx requote/place); the rewritten steps follow that flow.
- E2E-BASKET-011 'Meal max quantity configured' is wrong: the limit is one global commerce-policy value (max_item_quantity, 10 in the preview policy), exposed by GET /api/v1/configuration/public; there is no per-meal maximum. Slot stock (10 per meal on the preview) coincides with the limit, so a 15-portion slot is needed to test the limit alone.
- The seed's slots are created relative to the seed date (13:00 Cairo on the next 3 days; deploy/web-preview/seed-preview.sql) and are never refreshed, so after 3 days no basket case can run without FX-COOK-A creating slots. The execution guide does not say this.
- The execution guide promises 'approved read-only operator method' DB evidence but documents none for the preview: deploy.sh offers init-secrets/render/up/seed/smoke/backup/restore-db/status/rollback-plan/prune-qa/e2e-reset only (docs/operations/06-web-preview.md file table). The SQL in the rewritten cases assumes a read-only psql session on the host and is marked as such.
- API-008 (GET /api/v1/policies/{key}) is x-implemented:false (contracts/openapi.yaml:331) and no controller exists although the route is whitelisted (SecurityConfiguration.java:63). The checkout shows only the checkbox 'أوافق على الشروط وسياسة الإلغاء' with the policy version strings sent in the order, never the policy text, so BR-020 'exposes complete cost/policy before confirmation' can only be tested for costs. No policy-reading case was written.
- Organic-activity 'consent' does not exist: the web records MEAL_IMPRESSION/MEAL_VIEW/KITCHEN_VIEW/ADD_TO_CART for every visitor with no banner or opt-out (apps/customer-web/src/activity.ts); docs/product/10-organic-activity-and-cook-insights.md lists the customer privacy notice as an open point (O06). E2E-DISC-112 tests the designed privacy properties (once per visitor-day, no identity) and records the missing notice as an observation.
- The original catalog contains no case for: phone verification gate and OTP limits (BR-003/004), addresses (BR-005), favorites (BR-040), notifications inbox (BR-037/038), profile/language preference, reviews (BR-039), activity measurement (D17), account deletion intake, demo-environment notice, server-side basket rules (BR-018), quote immutability/stock-free quotes, fee/policy changes (TC-152/154), native offline/discovery/basket/account-switch (TC-049, TC-148). New cases DISC-101..117 and BASKET-021..030 add them.
- Customer-visible Arabic currency/time formatting is produced by Intl (apps/customer-web/src/api.ts formatMoney/formatTime); exact glyph sequences depend on the browser ICU and are not asserted verbatim in the cases (listed under unverified).
- Staff-only parts (zone fee, commerce policy, kitchen suspension) require recent MFA step-up and, for commerce-policy, a profile that allows synthetic policies (ConfigurationService.newPolicy: synthetic only in LOCAL/CI); on the PREVIEW profile a policy change would need an approvalReference, so TC-152 and the fee-change case are isolated-stack only.
- E2E-DISC-005/E2E-DISC-104 need FX-COOK-B, FX-COOK-PENDING and FX-STAFF-OPS which the seed does not contain (only preview-cook-1 and an unreachable 'preview-cook-2 (no login)' row exist).
- Traceability corrected after QA review: refs.scenarios now lists only TCs a case exercises end to end; all other related TCs moved to refs.partial. TC-044 was removed from BASKET-013, BASKET-030 and DISC-004 (stable placement key is not exercised there); TC-018 server rejection belongs to BASKET-021 (BASKET-010 only partial).

### Dispositions of the original cases

| Original | Action | Reason |
|---|---|---|
| E2E-DISC-001 | rewritten | Boilerplate replaced by exact seed titles/prices, labels and the public API payload; language switch kept here, detailed language checks moved to E2E-DISC-008. |
| E2E-DISC-002 | rewritten | Kept as a BLOCKED case: self-registration and verification cannot be run in any current environment (see findings 3). The verification gate is exercised by new E2E-DISC-105. |
| E2E-DISC-003 | rewritten | Concrete sign-in/out path, session and 401 checks; documents the loading-forever state of #/orders after sign-out. |
| E2E-DISC-004 | rewritten | Uses a real order of A, cross-account API probe (404) and Local Storage inspection; includes cleanup. |
| E2E-DISC-005 | split_into:[E2E-DISC-005,E2E-DISC-104] | Preview-feasible part (cook draft + reversible kitchen pause) stays in 005; suspended/pending cooks need FX-COOK-B/-PENDING/-STAFF-OPS provisioning and moved to 104. |
| E2E-DISC-006 | split_into:[E2E-DISC-006,E2E-DISC-101,E2E-DISC-102,E2E-DISC-103] | Search/price/sort/clear (006), zone + kitchens view (101), category chips (102, isolated: seed has no categories), cursor paging (103, API-only: web has no pager). Date/kitchen filters in the original do not exist in the web UI. |
| E2E-DISC-007 | rewritten | Grounded in Test zone B (active, not served by kitchen 1) and the inactive zone; expected result is the real generic 'Check the details.' message. |
| E2E-DISC-008 | split_into:[E2E-DISC-008,E2E-DISC-113] | Language/RTL/numerals/Accept-Language kept in 008; keyboard and focus moved to 113. 200% dynamic text and screen reader remain observations (browser zoom / device cases). |
| E2E-BASKET-009 | rewritten | Exact meal, slot, quantities and integer estimates; asserts that the basket is client-side (no quote or stock). |
| E2E-BASKET-010 | split_into:[E2E-BASKET-010,E2E-BASKET-021] | Web confirmation dialog stays in 010; the server-side rejection of mixed kitchens/slots (BR-018) is a separate API case 021. |
| E2E-BASKET-011 | rewritten | Limit is a global commerce-policy value (10 on the preview), not per meal; includes API RANGE_1_TO_10 and a 15-portion slot to separate limit from stock. Lowered-limit behaviour added as E2E-BASKET-024. |
| E2E-BASKET-012 | split_into:[E2E-BASKET-012,E2E-BASKET-023,E2E-BASKET-029] | Sold-out (012, via a real order of 10 portions), cutoff (023, cook-created short slot) and quote-holds-no-stock (029) are different behaviours with different fixtures. |
| E2E-BASKET-013 | rewritten | No test clock exists on the preview: real 5-minute wait; exact UI/API results. |
| E2E-BASKET-014 | split_into:[E2E-BASKET-014,E2E-BASKET-022] | Kitchen meal-price change (014, needs a cook-published meal with photo) vs delivery-fee change (022, isolated, staff configuration). |
| E2E-BASKET-015 | rewritten | Immutable order snapshot checked in UI, API and order_item/meal_revision rows. |
| E2E-BASKET-016 | rewritten | Isolated stack, one-portion slot, timed concurrent placement from two consoles, three rounds. |
| E2E-BASKET-017 | rewritten | Isolated stack; adds the deterministic quote-time aggregate check and the concurrent two-meal race. |
| E2E-BASKET-018 | rewritten | Concrete LIM slot (koshari 5, mahshi 1) and B taking the last mahshi; DB proves no partial reservation. |
| E2E-BASKET-019 | rewritten | Pickup vs delivery vs deposit quotes with exact piastre values and the quote rows. |
| E2E-BASKET-020 | rewritten | Concrete forged quote/placement bodies, missing key, wrong policy version and foreign quote; ties order total to the quote. |

## orders-finance

### Findings

- Boilerplate: all 27 cases in this domain (owner-e2e-functional-cases.md:242-540) repeat the same steps 1, 3 and 4 ('Record the current build SHA…', 'Check both visible UI state and the relevant protected API result…', 'Inspect authoritative DB/audit/capacity/finance evidence…', e.g. lines 246, 248, 249 for E2E-ORDER-021) and have a one-line step 2, so no case is executable by a tester without other documents.
- No traceability: lines 242-540 contain no TC-xxx or BR-xxx reference (0 matches), although planning/scenarios.json already has TC-022..TC-038, TC-119..TC-121, TC-156..TC-163 for this domain.
- No core journey: the catalog has no case that chains placement -> cook accept/prepare/ready -> handover -> customer completion -> finance/ops inspection for pickup and delivery with COD and deposit (the 'primary happy path' of owner-e2e-execution-guide.md pass 2). Added E2E-ORDER-101/102 and E2E-FIN-039/101.
- Unsupported premise, E2E-FIN-041 (line 470): 'Attempt deposit > payable through interface/API'. Clients never send a deposit amount (QuoteController.Body has no amount fields; the server derives it, PaymentService.terms) and quote_payment_ck (V24) forbids deposit > total. Rewritten as 'forged/wrong amounts are refused'.
- Unsupported step, E2E-FIN-044 (line 503): 'Customer requests refund'. No customer refund endpoint exists (contracts/openapi.yaml: refunds only under /api/v1/ops/finance/refunds with finance.refund_approve); customers use a cancellation request or support case.
- Unsupported step, E2E-FIN-045 (line 514): 'View ledger export'. No ledger export endpoint exists (getOpsAuditExport is x-implemented: false); the real verification is infra/db/reconcile.sql (10 checks) plus SQL.
- Unsupported step, E2E-ORDER-029 (line 335): 'Advance controlled clock'. The preview has no clock control; expiry runs on server time with a 30 s job (OrderExpiryJob). Only the isolated stack could shorten the window.
- Unsupported precondition, E2E-ORDER-024 (line 277): 'Injected response loss after DB commit'. No fault-injection hook is documented; rewritten as offline-submit plus API post-commit replay.
- Wrong mechanism, E2E-FIN-042 (line 480): 'same event key'. Cash reports use Idempotency-Key plus UNIQUE(order_id); the customer transfer report takes no key and is idempotent by state (PaymentService.reportTransfer).
- Wrong role, E2E-ORDER-029 (line 332): Role 'Clock/system' is not an actor a tester can sign in as.
- Impossible expectation on the deployed baseline, E2E-FIN-039 (line 451): 'no dispute false positive'. On origin/develop CashService compares the cook's report with order.total_minor and the cook UI pre-fills the order total, so a correct remainder report is flagged DISPUTED (discrepancy = -deposit). Fixed only in PR #9 (CashService.java diff, apps/operations-web/src/cook/Orders.tsx diff). Case marked needs_updated_deploy.
- Fixture gap: FX-STAFF-ADMIN (preview-ops-1) holds only staff.grant and audit.read and cannot self-grant; it cannot read orders or finance. Order, support and finance cases rely on FX-STAFF-OPS/FINANCE/SUPPORT from seed-owner-e2e.sql (grants listed in conventions.staff_fixture_note). FX-STAFF-ADMIN-B has no finance permission, so a second refund approver or FX-MULTIROLE finance.verify needs a temporary grant (requires recent MFA).
- MFA gate: payment recipient approval, payment decision and handover override require a recent MFA proof (@staffAssurance.recentMfa); origin/develop's Keycloak realm has no OTP/acr flow, so these steps are not runnable there (PR #9 realm migration needed). The original catalog does not mention this prerequisite.
- Cash verification has no UI: apps/operations-web/src/ops/Finance.tsx exposes statements, refunds, settlements and payment decisions but no screen for POST /api/v1/ops/finance/cash-reports/{reportId}/verification, so E2E-FIN-038/043 finance steps are API-only through a browser console.
- Finance tab visibility: the ops UI probes GET /api/v1/ops/finance/statements and shows 'Finance' only when it answers 404 (finance.read, refund_approve or settlement); an account with finance.verify alone cannot reach the payment recipient/decision panels through the UI (App.tsx probe).
- Dormant capability: settlements cannot be created under zero commission (FinanceOpsService.createSettlement rejects ZERO_AMOUNT) and the legacy refund path reverses a zero commission; original catalog has no case acknowledging D10 for settlements (added E2E-FIN-110).
- Implementation observation (needs owner decision): a COD order that ends DELIVERY_FAILED keeps cash_obligation.state = DUE (OrderLifecycleService delivery_failure branch has no cash() call) and no support case is opened, while docs/product/04-state-machines.md says 'support task'.
- Implementation observation: the cook UI order list has only 'Active' and 'Completed' filters (cook/Orders.tsx), so DELIVERY_FAILED, REJECTED, EXPIRED and CANCELLED orders cannot be reviewed by the cook in the UI.
- Implementation observation: the English/Arabic label for state COMPLETED is 'Delivered'/'تم التسليم' even for pickup orders (customer-web and mobile i18n state.COMPLETED).
- Implementation observation: order.exception_cancel is listed as a high-risk permission in docs but OpsOrderController applies only the permission, no recent-MFA check (only the handover override has it).
- Scope mismatch: E2E-ORDER-035 mixed two behaviours (customer request vs ops exception cancel) with a customer that 'cannot force state' - split into 035 and 109.
- Count claim: the supplied catalog headings in this domain are 27 (ORDER-021..037 = 17, FIN-038..047 = 10); the catalog-wide '121 cases' claim could not be found in owner-e2e-functional-cases.md or owner-e2e-execution-guide.md (grep 121/108 returned nothing), and the whole functional file has 62 case headings.

### Dispositions of the original cases

| Original | Action | Reason |
|---|---|---|
| E2E-ORDER-021 | rewritten | Narrowed to the placement transaction (snapshot, locked price, reservation); the end-to-end chain is the new P0 journey E2E-ORDER-101/102. |
| E2E-ORDER-022 | rewritten | Made executable with Idempotency-Key replay and exact assertions; the parallel variant is E2E-ORDER-123 (isolated stack). |
| E2E-ORDER-023 | split_into:[E2E-ORDER-023,E2E-ORDER-105,E2E-ORDER-106] | Same key/different body (023), reused clientRequestId with another quote (105) and with a changed field using the V29 fingerprint (106, PR #9 only). |
| E2E-ORDER-024 | split_into:[E2E-ORDER-024,E2E-ORDER-107] | Web offline-submit recovery and API post-commit recovery (024); native kill-and-relaunch (107, BLOCKED: no device build). The original 'injected response loss after DB commit' has no documented injection hook. |
| E2E-ORDER-025 | rewritten | Exact accept assertions: stock RESERVED->ALLOCATED, contact reveal, replay and conflict codes, notification. |
| E2E-ORDER-026 | rewritten | Reject with reason codes; release exactly once; ORDER_REJECTED notification. |
| E2E-ORDER-027 | rewritten | Customer cancel of a pending order (UI hard-codes CHANGED_MIND); release once. |
| E2E-ORDER-028 | rewritten | Race case, flagged needs_isolated_stack with iteration count and reconcile check. |
| E2E-ORDER-029 | rewritten | No controllable clock exists on the preview; rewritten as a real-time wait (15 min + job interval 30 s); the original 'advance controlled clock' step is unsupported outside the isolated stack. |
| E2E-ORDER-030 | rewritten | Prepare/ready with stock consumption and handover-code visibility rules (owner only). |
| E2E-ORDER-031 | rewritten | Pickup handover with single-use code; ledger/cash independence; original 'auditable receipt' replaced by order_event + handover_attempt evidence (no receipt object exists). |
| E2E-ORDER-032 | rewritten | Delivery dispatch/handover and wrong-mode refusals. |
| E2E-ORDER-033 | rewritten | Five wrong codes lock the challenge; recovery moved to new E2E-ORDER-108 (ops override). |
| E2E-ORDER-034 | rewritten | Delivery failure with exact ledger/refund/cash assertions and ops exception flag. |
| E2E-ORDER-035 | split_into:[E2E-ORDER-035,E2E-ORDER-109] | Customer cancellation request after acceptance (035) and ops exception cancel while PREPARING (109) are two different behaviours with different actors and permissions. |
| E2E-ORDER-036 | rewritten | Concrete command/state matrix with codes. |
| E2E-ORDER-037 | rewritten | Uses kitchen B's seeded order (no FX-COOK-B login needed); customer-vs-customer isolation moved to E2E-ORDER-120. |
| E2E-FIN-038 | rewritten | COD cash report and finance verification with exact amounts; cash verification is API-only (no ops UI). |
| E2E-FIN-039 | rewritten | Became the P0 pickup + deposit journey; needs PR #9 (cash report compared with the obligation, remainder prefilled). |
| E2E-FIN-040 | rewritten | Kitchen 'Not received' -> DISPUTED + PAYMENT case; amount mismatch (109) and recovery (108) split out. |
| E2E-FIN-041 | rewritten | 'Deposit greater than total' cannot be attempted (clients send no deposit amount; deposit is server-computed); rewritten as forged/wrong amounts being refused. |
| E2E-FIN-042 | split_into:[E2E-FIN-042,E2E-FIN-102] | Customer transfer report idempotency (042) and duplicate/invalid cook cash reports (102). The original 'same event key' does not exist; the real mechanisms are Idempotency-Key, UNIQUE(order_id) and state checks. |
| E2E-FIN-043 | rewritten | Shortfall: exact discrepancy, reason rules, DISPUTE decision; separation of duties moved to E2E-FIN-114. |
| E2E-FIN-044 | rewritten | Original step 'customer requests refund' is unsupported (no customer refund endpoint); rewritten as the four-eyes staff refund workflow with permissions. |
| E2E-FIN-045 | rewritten | 'Ledger export' does not exist; replaced by infra/db/reconcile.sql and explicit SQL invariants. |
| E2E-FIN-046 | rewritten | Zero commission/fee across pickup/delivery x COD/deposit with policy and ledger evidence. |
| E2E-FIN-047 | rewritten | Network-host, demo-notice and fake-recipient evidence for 'no external money movement'. |
