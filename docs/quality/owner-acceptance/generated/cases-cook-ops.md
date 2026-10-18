# Owner E2E cases — cook-ops

Generated from `cases/*.json` by `scripts/owner_e2e_catalog.py`; do not edit. Every case is NOT_RUN or BLOCKED until a run records evidence.

### E2E-COOK-048 — Applicant saves a draft, is told what is missing, submits revision 1 and stays a non-cook until staff decide

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** api, operations-web · **Actors:** FX-CUST-NEW
- **Fixtures to provision:** FX-CUST-NEW
- **Traces to:** scenarios: TC-008, TC-112; rules: BR-008; operations: GET /api/v1/cook/application, PUT /api/v1/cook/application, POST /api/v1/cook/application/submit, GET /api/v1/cook/dashboard
- **Prerequisites:**
  - FX-CUST-NEW (PROVISION): a Keycloak user that has signed in at least once (so an app_user row exists) and has never created a cook application. Verify: SELECT count(*) FROM cook_application WHERE user_id = (SELECT id FROM app_user WHERE subject = '<subject>') returns 0.
  - The API is reachable at https://api.<PREVIEW_DOMAIN> and https://ops.<PREVIEW_DOMAIN> opens (IP allow-list permitting). No customer-web or mobile screen can create a cook application: apps/customer-web/src and apps/customer-mobile/src contain no call to /api/v1/cook/application, so this case drives the API from the browser console.
- **Steps:**
  1. (FX-CUST-NEW) Open https://ops.<PREVIEW_DOMAIN> and press 'Sign in' (ar 'تسجيل الدخول'); sign in as FX-CUST-NEW (password from the private fixture sheet). — data: Page title 'Cook workspace - ...' (ar 'مساحة الطاهي'). Use the language button (shows 'العربية' / 'English') to check both languages.
  2. (FX-CUST-NEW) On the screen read the heading and the status line; note that no tab bar is shown.
  3. (FX-CUST-NEW) Open DevTools console on this tab and define the helper, then run: await x('GET','/api/v1/cook/dashboard') and await x('GET','/api/v1/cook/application'). — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  4. (FX-CUST-NEW) Run: await x('PUT','/api/v1/cook/application',{publicName:'مطبخ القبول',bio:'أكل بيتي تجريبي',contactPhone:'12345',privateLocation:'عنوان تجريبي رقم 5',pickupEnabled:true,zoneIds:[],documentIds:[]}) — data: contactPhone '12345' is deliberately not an Egyptian mobile number.
  5. (FX-CUST-NEW) Run the same PUT with contactPhone:'01000000999' and WITHOUT the bio property (omit bio), no expectedVersion (the application does not exist yet). — data: publicName 'مطبخ القبول', privateLocation 'عنوان تجريبي رقم 5', pickupEnabled true, zoneIds [], documentIds []
  6. (FX-CUST-NEW) Run: await x('POST','/api/v1/cook/application/submit',{expectedVersion:0}) — data: Expected to fail: the draft has no bio.
  7. (FX-CUST-NEW) Run the PUT again with all fields including bio:'أكل بيتي تجريبي', zoneIds:['00000000-0000-4000-8000-00000000a001'], expectedVersion:0. — data: Phone is stored normalized as +201000000999.
  8. (FX-CUST-NEW) Run: await x('POST','/api/v1/cook/application/submit',{expectedVersion:1})
  9. (FX-CUST-NEW) Reload the page https://ops.<PREVIEW_DOMAIN> (F5).
  10. (FX-CUST-NEW) Run: await x('POST','/api/v1/cook/meals',{titleAr:'محاولة'}) and await x('PUT','/api/v1/cook/application',{publicName:'اسم جديد',expectedVersion:2})
- **Expected:**
  - UI: Step 2: heading 'This account has no approved kitchen yet.' (ar 'لا يوجد مطبخ معتمد لهذا الحساب بعد.') and the line 'You have not started an application.' (ar 'لم تبدأ طلب الانضمام بعد.'); no tab navigation.
  - UI: Step 9: the line 'Application status: SUBMITTED' (ar 'حالة الطلب: SUBMITTED'; the state code is shown untranslated); still no tab bar; still the heading 'This account has no approved kitchen yet.'.
  - API: Step 3: GET /api/v1/cook/dashboard -> 403 code FORBIDDEN; GET /api/v1/cook/application -> 404 code NOT_FOUND.
  - API: Step 4: 422 code VALIDATION_FAILED with fieldErrors containing {field:'contactPhone', code:'INVALID_EGYPTIAN_MOBILE'}; nothing is stored.
  - API: Step 5: 200, body.state 'DRAFT', body.version 0, body.draft.contactPhone '+201000000999', draft.bio null.
  - API: Step 6: 422 VALIDATION_FAILED with fieldErrors containing {field:'bio', code:'REQUIRED'}; application stays DRAFT, currentRevision 0.
  - API: Step 7: 200, state 'DRAFT', version 1 (the second save requires expectedVersion equal to the stored version 0 and increments it).
  - API: Step 8: 200, state 'SUBMITTED', currentRevision 1, history has one entry {revision:1, decision:null}, version 2.
  - API: Step 10: POST /api/v1/cook/meals -> 403 FORBIDDEN (no approved kitchen); PUT draft in SUBMITTED -> 409 STATE_CONFLICT.
  - DB: SELECT state, current_revision, version FROM cook_application WHERE user_id = (SELECT id FROM app_user WHERE subject = '<FX-CUST-NEW subject>') -> SUBMITTED, 1, 2.
  - DB: SELECT revision, decision, reviewer_id FROM cook_application_revision WHERE application_id = <id> -> exactly one row (1, NULL, NULL).
  - DB: SELECT count(*) FROM kitchen WHERE owner_id = <user id> -> 0 (no kitchen without an approval).
  - DB: SELECT action, outcome, object_type FROM audit_event WHERE actor_id = <user id> AND action = 'cook.application.submit' -> one row, ALLOWED, cook_application, details {"revision":1}.
- **Also covered by automated tests:** backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest (applicant_saves_a_private_draft_with_normalized_phone_and_no_cook_status, stale_version_and_invalid_fields_are_rejected, incomplete_application_cannot_be_submitted_and_creates_no_revision)
- **Unverified against code (confirm while executing):** The exact JSON field name of the stored phone in body.draft (contactPhone) is taken from CookApplicationService.Draft; the OpenAPI schema CookApplication was not diffed field by field.; Whether the customer-web will get an 'apply as cook' screen later is not in the code; a UI-based application cannot be tested today.

### E2E-COOK-049 — Staff approval creates the kitchen; the applicant becomes a cook only after START_REVIEW and APPROVE with fresh MFA

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-OPS, FX-COOK-PENDING · **Needs:** updated deploy, isolated stack
- **Fixtures to provision:** FX-STAFF-OPS, FX-COOK-PENDING
- **Traces to:** rules: BR-008, BR-007; decisions: D18; operations: POST /api/v1/ops/cooks/{applicationId}/decision, GET /api/v1/ops/cooks, GET /api/v1/ops/cooks/{applicationId}; partial: TC-112, TC-008, TC-123, TC-007
- **Prerequisites:**
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - FX-COOK-PENDING (seed-owner-e2e.sql, CONSUMED by this case): login owner-e2e-cook-pending (subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000b2), application id 00000000-0000-4000-8000-0000000ba1b2 in state SUBMITTED, public_name 'مطبخ تحت المراجعة', phone +201000000203, private location 'Synthetic location 3', pickup only, NO delivery zones, current_revision 1, version 0 (column default). Run on a fresh/disposable stack (fixtures.md: consumed fixtures; re-run the seed to reset). Read the live version from GET /api/v1/ops/cooks/<id>; do not assume it.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - FX-STAFF-OPS has an authenticator app (TOTP) available for the Keycloak step-up (first step-up enrols it). The two accounts are different people (a reviewer can never decide their own application).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-OPS) Open https://ops.<PREVIEW_DOMAIN>, sign in as FX-STAFF-OPS (password from the private fixture sheet). — data: Expect the tab bar to show 'Cook applications', 'Catalogue moderation', 'Order monitoring' (ar 'طلبات الطهاة', 'مراجعة الأطباق', 'متابعة الطلبات').
  2. (FX-STAFF-OPS) Open tab 'Cook applications' (ar 'طلبات الطهاة'). The state filter 'Submitted' (ar 'مُقدَّم') is selected by default. Click the entry 'مطبخ تحت المراجعة'.
  3. (FX-STAFF-OPS) Read the detail card (do not press anything).
  4. (FX-STAFF-OPS) FX-STAFF-OPS: leave 'Decision reason' empty and click 'Start review' (ar 'بدء المراجعة') ONLY to obtain the second factor: if the session has no MFA proof younger than 10 minutes the notice 'This action needs additional verification (MFA).' appears (403 STEP_UP_REQUIRED, nothing changes); press 'Verify again' (ar 'إعادة التحقق'), enter the TOTP code (first time: scan the QR code to enrol) and return to the ops page. The decision is NOT executed in this step.
  5. (FX-STAFF-OPS) After the step-up returns to https://ops.<PREVIEW_DOMAIN>, open 'Cook applications' > 'Submitted' > 'مطبخ تحت المراجعة' again and click 'Start review' - this is the single execution of START_REVIEW.
  6. (FX-STAFF-OPS) Type in 'Decision reason' (ar 'سبب القرار'): 'اكتملت البيانات وتم التحقق التشغيلي' and click 'Approve' (ar 'اعتماد').
  7. (FX-COOK-PENDING) Sign in to https://ops.<PREVIEW_DOMAIN> as FX-COOK-PENDING in a second browser profile (or sign out first) and reload.
  8. (FX-COOK-PENDING) Anonymously open https://api.<PREVIEW_DOMAIN>/api/v1/kitchens/<slug> where <slug> comes from SELECT public_slug FROM kitchen WHERE application_id = <application id>. — data: slug has the form kitchen-<10 hex characters>
- **Expected:**
  - UI: Step 3: heading '<name> - Submitted' (ar 'مُقدَّم'), fields Bio, Contact phone (+201000000203, left-to-right), Private location 'Synthetic location 3', Pickup tick, '0 delivery zone(s)'; the only decision button is 'Start review'.
  - UI: Step 4: notice 'This action needs additional verification (MFA).' + button 'Verify again'; the heading still says 'Submitted'.
  - UI: Step 5: heading changes to 'Under review' (ar 'قيد المراجعة'); decision buttons are now 'Approve', 'Request changes', 'Reject'.
  - UI: Step 6: heading shows 'Approved' (ar 'معتمد'); a card 'Kitchen: مطبخ تحت المراجعة - Active' (ar 'نشط') appears with 'Active orders affected: 0'.
  - UI: Step 7: FX-COOK-PENDING now sees the tabs Home, Orders, Meals, Slots, Statement, Insights, Settings (ar 'الرئيسية','الطلبات','الأطباق','المواعيد','الكشف','الأداء','الإعدادات') instead of the application status; Home shows the badge 'Active'.
  - API: Step 4: POST /api/v1/ops/cooks/{applicationId}/decision {decision:'START_REVIEW',revision:1,expectedVersion:<version read from GET /api/v1/ops/cooks/<id>, seeded 0>} -> 403 STEP_UP_REQUIRED (no state change).
  - API: Step 5: same call after step-up -> 200, body.state 'UNDER_REVIEW', version 1.
  - API: Step 6: {decision:'APPROVE',revision:1,expectedVersion:1,reason:'...'} with an Idempotency-Key header -> 200, state 'APPROVED', version 2, kitchenId non-null, kitchen.operatingState 'ACTIVE'.
  - API: Step 8: GET /api/v1/kitchens/{slug} -> 200 with verificationLabel 'OPERATIONALLY_VERIFIED', available true, pickupEnabled true; the body contains no private location and no contact phone.
  - API: FX-COOK-PENDING: GET /api/v1/cook/dashboard -> 200 (was 403).
  - DB: SELECT state FROM cook_application WHERE id = <application id> -> APPROVED.
  - DB: SELECT decision, decision_reason, reviewer_id, decided_at FROM cook_application_revision WHERE application_id = <id> AND revision = 1 -> APPROVED, the typed reason, reviewer_id = FX-STAFF-OPS id, decided_at not null.
  - DB: SELECT operating_state, approved_revision, application_id FROM kitchen WHERE owner_id = <FX-COOK-PENDING id> -> ACTIVE, 1, <application id> (exactly one kitchen).
  - DB: SELECT action, permission, outcome, reason, details FROM audit_event WHERE object_type = 'cook_application' AND object_id = '<id>' ORDER BY occurred_at -> cook.application.view (cook.review, ALLOWED), cook.application.decision START_REVIEW ALLOWED, cook.application.decision APPROVE ALLOWED with the typed reason; plus one DENIED row action 'denied POST /api/v1/ops/cooks/<id>/decision', reason STEP_UP_REQUIRED from step 4.
- **Also covered by automated tests:** CookWorkflowIntegrationTest (only_a_scoped_reviewer_with_recent_mfa_can_decide, changes_requested_then_resubmitted_then_approved_preserves_every_revision, concurrent_approvals_create_exactly_one_kitchen); apps/operations-web/src/App.test.tsx (UI only, mocked API)
- **Unverified against code (confirm while executing):** I did not run Keycloak: that the origin/develop realm cannot raise acr=mfa is inferred from the absence of flows/mappers in its realm JSON and from line 173 of its web-preview doc.; Exact display format of the contact phone and of 'Application status' depends on the browser locale; only the labels are asserted.; Consumes FX-COOK-PENDING (seed-owner-e2e.sql); re-run the seed to reset; do not run on a shared preview that other cases depend on.

### E2E-COOK-050 — Suspending a kitchen stops new sales and acceptance of pending orders but lets already accepted orders be fulfilled

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-OPS, FX-CUST-B · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-OPS, FX-ISOLATED
- **Traces to:** scenarios: TC-009; rules: BR-009; operations: POST /api/v1/ops/kitchens/{kitchenId}/operating-state, POST /api/v1/cook/orders/{orderId}/accept, POST /api/v1/quotes; partial: TC-123, TC-008
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - The seeded kitchen has an OPEN slot tomorrow 13:00-14:00 Africa/Cairo with 10 portions of 'كشري تجريبي' (Preview koshari, 75.00 EGP, meal id 00000000-0000-4000-8000-0000000bb001); FX-CUST-A has a verified phone (seeded).
  - The active commerce_policy has acceptance_window_minutes = 15 (devdata R__dev_commerce_policy.sql); perform steps 1-3 within 10 minutes so order O1 is still PENDING_ACCEPTANCE when the kitchen is suspended.
  - Nobody else may use kitchen A during this case; the last step restores it to ACTIVE. Orders created here remain as synthetic data.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
  - Run on a disposable stack (FX-ISOLATED): this case changes shared seeded state (kitchen A operating state / payment recipient / administrator grants); on any shared preview it would disturb other cases. Restoration steps are listed, but the stack is discarded afterwards.
- **Steps:**
  1. (FX-CUST-A) FX-CUST-A: open https://app.<PREVIEW_DOMAIN>, sign in, open the dish 'كشري تجريبي' (Preview koshari), choose tomorrow's 13:00 slot, quantity 1, 'Add to cart' (ar 'أضف إلى السلة'), go to the cart, mode 'Pickup from the kitchen', payment 'Cash on receipt' (chosen BEFORE calculating), 'Calculate total' (ar 'احسب الإجمالي'), tick 'I accept the terms and the cancellation policy' (ar 'أوافق على الشروط وسياسة الإلغاء'; Place order stays disabled until ticked) and click 'Place order'. Repeat once more so there are two orders O1 and O2. — data: Two PICKUP cash orders of 75.00 EGP each. Note both public order numbers.
  2. (FX-COOK-A) FX-COOK-A: open https://ops.<PREVIEW_DOMAIN> > Orders (ar 'الطلبات') > 'Active' > order O2 > 'Accept' then 'Start preparing' (ar 'قبول' / 'بدء التحضير'). — data: O2 shows 'Preparing'; O1 stays 'Waiting for acceptance'.
  3. (FX-STAFF-OPS) FX-STAFF-OPS: open https://ops.<PREVIEW_DOMAIN> > 'Cook applications' > filter 'Approved' (ar 'معتمد') > click 'مطبخ المعاينة' > card 'Kitchen'. Read 'Active orders affected'. Type reason 'مخالفة تشغيلية - اختبار الإيقاف' in the field 'Reason' (ar 'السبب') and click 'Suspend' (ar 'إيقاف'). If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  4. (FX-COOK-A) FX-COOK-A: reload https://ops.<PREVIEW_DOMAIN>. Look at Home, then open Settings (ar 'الإعدادات').
  5. (FX-COOK-A) FX-COOK-A: Orders > 'Active' > O1 > 'Accept'.
  6. (FX-COOK-A) FX-COOK-A: Orders > 'Active' > O2 > 'Ready' (ar 'جاهز').
  7. (FX-CUST-B) FX-CUST-B: open https://app.<PREVIEW_DOMAIN> home list and search 'كشري'; then open https://app.<PREVIEW_DOMAIN> kitchen page of 'مطبخ المعاينة' (link from its dish or /kitchen/preview-kitchen path of the app).
  8. (FX-CUST-B) FX-CUST-B: in the console of https://app.<PREVIEW_DOMAIN> run POST /api/v1/quotes with body {slotId:<tomorrow's slot id of kitchen A from GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots>, fulfillmentMode:'PICKUP', items:[{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}]} using the same x(...) helper (API host https://api.<PREVIEW_DOMAIN>). — data: Helper definition: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  9. (FX-STAFF-OPS) FX-STAFF-OPS: same card 'Kitchen' > reason 'انتهاء الاختبار' > 'Restore' (ar 'استعادة'). — data: Required to leave kitchen A usable.
- **Expected:**
  - UI: Step 3: before pressing, the card shows 'Kitchen: مطبخ المعاينة - Active' and 'Active orders affected: Waiting for acceptance 1 - Preparing 1' (counts could also include older seed-test orders); after pressing 'Suspend' it shows 'Suspended by operations' (ar 'موقوف من الإدارة') and the only action offered is 'Restore'. The note 'Pausing or suspending does not cancel active orders...' stays visible.
  - UI: Step 4: Home badge 'Suspended by operations'; Settings heading 'Suspended by operations' with neither 'Pause new sales' nor 'Resume sales' button.
  - UI: Step 5: the screen shows the generic error 'Something went wrong.' (the code KITCHEN_UNAVAILABLE has no translation in apps/operations-web/src/i18n.ts); O1 stays 'Waiting for acceptance'.
  - UI: Step 6: O2 changes to 'Ready' - fulfilment of an already accepted order is still possible.
  - UI: Step 7: 'Preview koshari' and 'Preview mahshi' no longer appear in the dish list; the kitchen page shows 'This kitchen is not taking orders right now.' (ar 'المطبخ مش بياخد طلبات دلوقتي.') and no order buttons.
  - UI: Step 9: the card shows 'Active' again and 'Pause' / 'Suspend' offered.
  - API: Step 3: POST /api/v1/ops/kitchens/{kitchenId}/operating-state {action:'SUSPEND',reason:'...',expectedVersion:<kitchen version>} -> 200 operatingState 'SUSPENDED' (first attempt without MFA: 403 STEP_UP_REQUIRED).
  - API: Step 5: POST /api/v1/cook/orders/{O1}/accept -> 409 KITCHEN_UNAVAILABLE.
  - API: Step 6: POST /api/v1/cook/orders/{O2}/ready -> 200 state READY.
  - API: Step 7: GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 -> 404 NOT_FOUND; GET /api/v1/kitchens/preview-kitchen -> 200 with available:false.
  - API: Step 8: POST /api/v1/quotes -> 409 KITCHEN_UNAVAILABLE.
  - API: Step 9: 200 operatingState 'ACTIVE'.
  - DB: SELECT operating_state, state_reason FROM kitchen WHERE id = '00000000-0000-4000-8000-0000000bd001' -> SUSPENDED / 'مخالفة تشغيلية - اختبار الإيقاف' after step 3, ACTIVE / 'انتهاء الاختبار' after step 9.
  - DB: SELECT state FROM customer_order WHERE public_number IN (<O1>,<O2>) -> PENDING_ACCEPTANCE (O1), READY (O2) while suspended.
  - DB: SELECT action, outcome, reason, details FROM audit_event WHERE object_type = 'kitchen' AND object_id = '00000000-0000-4000-8000-0000000bd001' ORDER BY occurred_at -> kitchen.operating_state ALLOWED {action:SUSPEND, from:ACTIVE, to:SUSPENDED} then {action:RESTORE, from:SUSPENDED, to:ACTIVE}.
- **Also covered by automated tests:** CookWorkflowIntegrationTest.pause_stops_new_sales_suspension_stops_acceptance_and_accepted_orders_stay_fulfillable; ModerationIntegrationTest.suspension_and_restore_are_reflected_in_discovery_and_checkout_with_the_active_order_impact
- **Unverified against code (confirm while executing):** The kitchen-page path and the exact way the customer-web reaches a kitchen page were not traced; step 7 may be done by opening the dish link instead.; What O1 does when the 15 minute acceptance window passes while the kitchen is suspended (expiry job) is not asserted here.

### E2E-COOK-051 — Cook creates a meal draft with an uploaded photo and publishes it; customers see it only after publication

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-GUEST
- **Traces to:** rules: BR-011, BR-010, BR-008; operations: POST /api/v1/cook/meals, POST /api/v1/cook/meals/{mealId}/publication, POST /api/v1/uploads, POST /api/v1/uploads/{uploadId}/complete, GET /api/v1/meals/{mealId}; partial: TC-011, TC-114, TC-010
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - A synthetic JPEG file acceptance-dish.jpg, 640x480 pixels, under 1 MB, containing no real person or brand (the server accepts 320-8000 px sides, max 8 MiB, JPEG or PNG; policy in MediaPolicy.java).
  - The PREVIEW stack uses a fake malware scanner that returns CLEAN and a real SeaweedFS upload target (docs/operations/06-web-preview.md).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: open https://ops.<PREVIEW_DOMAIN>, sign in (password from the private fixture sheet), open tab 'Meals' (ar 'الأطباق').
  2. (FX-COOK-A) In the form 'New meal (draft)' (ar 'طبق جديد (مسودة)') fill: Arabic name 'طبق قبول تجريبي'; English name 'Acceptance test dish'; Public description 'طبق تجريبي لاختبار النشر والقبول'; Portion 'علبة وسط'; Ingredients 'أرز، خضار، بهارات'; Price (EGP) '85.50'; Allergens 'None declared' (ar 'لا يوجد ما يُعلن عنه'). Do not attach a photo yet. Click 'Create' (ar 'إنشاء').
  3. (FX-COOK-A) On the new card 'طبق قبول تجريبي' (status Draft) click 'Publish' (ar 'نشر'). — data: Expected to be refused: there is no approved photo.
  4. (FX-COOK-A) Fill the form again with the same values (name 'طبق قبول تجريبي 2') and choose the file acceptance-dish.jpg in 'Meal photo' (ar 'صورة الطبق'). Click 'Create'.
  5. (FX-COOK-A) On the new card click 'Publish'.
  6. (FX-GUEST) FX-GUEST: open https://app.<PREVIEW_DOMAIN> (not signed in), search 'طبق قبول تجريبي 2' and open the dish.
- **Expected:**
  - UI: Step 3: the card shows the error 'Check the fields.' (ar 'تحقق من الحقول.') and the status stays 'Draft' (ar 'مسودة').
  - UI: Step 4: a second card appears with status 'Draft' and price 85.50 EGP.
  - UI: Step 5: status badge becomes 'Published' (ar 'منشور'); buttons change to 'Pause' and 'Archive'.
  - UI: Step 6: the dish page lists 'Acceptance test dish' / 'طبق قبول تجريبي 2', price 85.50 EGP, the allergy text 'The kitchen declared no known allergens in this dish.' and the photo; the first draft (step 2) is NOT findable.
  - API: Step 3: POST /api/v1/cook/meals/{mealId}/publication {action:'PUBLISH',expectedVersion:0} -> 422 VALIDATION_FAILED with fieldErrors containing {field:'photos', code:'APPROVED_PHOTO_REQUIRED'}.
  - API: Step 4 (browser network tab): POST /api/v1/uploads {purpose:'MEAL_PHOTO',contentType:'image/jpeg',bytes,sha256} -> 201 with uploadUrl (host media.<PREVIEW_DOMAIN>), method PUT; the browser PUT to uploadUrl -> 200; POST /api/v1/uploads/{id}/complete -> 200 state 'APPROVED'; POST /api/v1/cook/meals -> 201 with content.photos[0].mediaId = that upload id, status 'DRAFT', priceMinor 8550.
  - API: Step 5: 200 status 'PUBLISHED', publishedAt set.
  - API: Step 6: GET /api/v1/meals/{mealId} (anonymous) -> 200 for the published meal and 404 NOT_FOUND for the draft; the photo URL is on the public media host and is a re-encoded JPEG (no original filename or EXIF).
  - DB: SELECT status, price_minor, allergen_declaration, moderation_state FROM meal WHERE title_ar = 'طبق قبول تجريبي 2' -> PUBLISHED, 8550, NONE_DECLARED, VISIBLE; the draft from step 2 -> DRAFT, 8550.
  - DB: SELECT state, purpose, scan_verdict, width, height FROM media_asset WHERE owner_id = <FX-COOK-A id> ORDER BY created_at DESC LIMIT 1 -> APPROVED, MEAL_PHOTO, CLEAN, 640, 480; public_key not null.
  - DB: SELECT revision, price_minor FROM meal_revision WHERE meal_id = <published meal id> -> at least revision 1 with price 8550.
- **Also covered by automated tests:** MealIntegrationTest (draft_without_allergen_declaration_cannot_be_published_and_stays_private, published_meal_has_a_public_dto_without_private_or_internal_fields); MediaIntegrationTest.approved_meal_photo_gets_a_metadata_free_public_variant_and_nothing_else_is_public; apps/operations-web/e2e/cook-workspace.e2e.ts (uses seeded meals)
- **Unverified against code (confirm while executing):** Arabic text of errors.VALIDATION_FAILED in the ops web was not read; the English string is 'Check the fields.'.; The format of the price string on the card (digits and currency symbol) comes from formatMoney and is not asserted.

### E2E-COOK-052 — Cook creates a slot in the ops web, opens it, and it becomes orderable for customers

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-GUEST
- **Traces to:** rules: BR-013, BR-014, BR-015; operations: POST /api/v1/cook/slots, PATCH /api/v1/cook/slots/{slotId}, GET /api/v1/kitchens/{kitchenId}/slots; partial: TC-115, TC-122, TC-014
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Choose a free interval: tomorrow +10 days (so it does not overlap the seeded 13:00-14:00 slots of the next 3 days), 16:00-18:00. Africa/Cairo times are entered; the browser timezone does not matter (server interprets them as Cairo).
  - Kitchen A has PUBLISHED meals 'كشري تجريبي' and 'محشي تجريبي' (seed).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: https://ops.<PREVIEW_DOMAIN> > tab 'Slots' (ar 'المواعيد'). Read the form 'New slot' (ar 'موعد جديد').
  2. (FX-COOK-A) Fill Date = today+10 days, Start 16:00, End 18:00, Maximum portions 12, Order lead time (minutes) 120, and under 'Portions per meal' (ar 'حصص لكل طبق') enter 6 for 'كشري تجريبي' and 6 for 'محشي تجريبي'. Click 'Create' (ar 'إنشاء').
  3. (FX-COOK-A) On the new slot card click 'Open' (ar 'فتح').
  4. (FX-GUEST) FX-GUEST: https://app.<PREVIEW_DOMAIN> open the dish 'كشري تجريبي' and look at 'Cooking slots' (ar 'مواعيد الطهي').
  5. (FX-COOK-A) FX-COOK-A: click 'Close' (ar 'إغلاق') on the slot card, then reload the guest page.
- **Expected:**
  - UI: Step 2: a card titled with the time range appears with badge 'Draft' (ar 'مسودة') and '0 of 12 portions'.
  - UI: Step 3: badge 'Open' (ar 'مفتوح').
  - UI: Step 4: the new slot is listed for the date with '6 left' for the dish (ar 'متبقٍ ٦').
  - UI: Step 5: badge 'Closed' (ar 'مغلق'); the guest page no longer lists the slot after reload.
  - API: Step 2: POST /api/v1/cook/slots {localDate,localStart:'16:00',localEnd:'18:00',maxPortions:12,leadMinutes:120,mealCapacities:[{mealId,capacity:6},...]} -> 201 state 'DRAFT', timezone 'Africa/Cairo', cutoffAt = startsAt minus 120 minutes, version 0.
  - API: Step 3: PATCH /api/v1/cook/slots/{id} {state:'OPEN',expectedVersion:0} -> 200 state 'OPEN', orderable true.
  - API: Step 4: GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/slots -> contains the slot.
  - API: Step 5: PATCH {state:'CLOSED'} -> 200; the public listing no longer returns it.
  - DB: SELECT starts_at, ends_at, cutoff_at, max_portions, state, lead_minutes FROM cook_slot WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001' AND local_date = <date> -> starts_at = 16:00 Cairo as UTC (14:00Z or 13:00Z depending on DST), cutoff_at = starts_at - 2h, 12, CLOSED at the end, 120.
  - DB: SELECT meal_id, capacity FROM meal_slot WHERE cook_slot_id = <id> -> two rows capacity 6.
- **Also covered by automated tests:** SlotIntegrationTest (ordinary_slots_store_utc_instants_for_cairo_wall_clock_times, pause_hides_a_slot_and_opening_requires_offered_meals, public_listing_shows_only_open_future_slots_of_selling_kitchens_with_their_fulfillment_modes); apps/operations-web/e2e/cook-workspace.e2e.ts
- **Unverified against code (confirm while executing):** Whether Egypt is on DST on the test date (Africa/Cairo offset +02:00 or +03:00) decides the UTC value in the DB check; compare with the Cairo wall clock, not a fixed offset.; Where exactly the customer-web prints the per-slot 'N left' label (dish page slot list) was not traced to a component.

### E2E-COOK-053 — A cook's onboarding document is private: only owner and the reviewing staff can read it, and replaying the upload ticket cannot change the approved copy

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-CUST-NEW, FX-STAFF-OPS, FX-CUST-A, FX-COOK-B · **Needs:** updated deploy
- **Fixtures to provision:** FX-CUST-NEW, FX-STAFF-OPS, FX-COOK-B
- **Traces to:** rules: BR-010, BR-006, BR-008; operations: POST /api/v1/uploads, POST /api/v1/uploads/{uploadId}/complete, GET /api/v1/uploads/{uploadId}; partial: TC-010, TC-113, TC-123, TC-006
- **Prerequisites:**
  - FX-CUST-NEW (PROVISION): a Keycloak user that has signed in at least once (so an app_user row exists) and has never created a cook application. Verify: SELECT count(*) FROM cook_application WHERE user_id = (SELECT id FROM app_user WHERE subject = '<subject>') returns 0. Use an account that has NOT been used for E2E-COOK-048 (fixture sheet must supply a second FX-CUST-NEW).
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - FX-COOK-B (PROVISION by deploy/web-preview/seed-owner-e2e.sql): login owner-e2e-cook-b, re-pointed to the seeded second kitchen 'مطبخ تجريبي تاني' (id 00000000-0000-4000-8000-0000000bd002, 1 meal, one OPEN slot from seed-preview.sql). Verify: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1' returns that kitchen, ACTIVE. Never create FX-COOK-B by approving FX-COOK-PENDING: that fixture is consumed only by E2E-COOK-049.
  - Replay (step 5) must happen within 10 minutes of the ticket (UPLOAD_TTL = 10 min). Nothing in this case needs a staff second factor (reading the application and its documents needs only cook.review). The sealing of approved documents (step 5/DB check) exists only in PR #9 (MediaService.java diff).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-CUST-NEW) As FX-CUST-NEW in the console of https://ops.<PREVIEW_DOMAIN> define the helper, then create a synthetic 400x400 PNG and its checksum: const c=document.createElement('canvas');c.width=c.height=400;const g=c.getContext('2d');g.fillStyle='#cfe';g.fillRect(0,0,400,400);g.fillStyle='#000';g.font='24px sans-serif';g.fillText('SYNTHETIC TEST DOCUMENT',20,200);const blob=await new Promise(r=>c.toBlob(r,'image/png'));const buf=await blob.arrayBuffer();const sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',buf))].map(b=>b.toString(16).padStart(2,'0')).join(''); — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-CUST-NEW) Create the ticket: const t=(await x('POST','/api/v1/uploads',{purpose:'COOK_DOCUMENT',filename:'licence.png',contentType:'image/png',bytes:buf.byteLength,sha256:sha})).body; then upload: const put=await fetch(t.uploadUrl,{method:'PUT',headers:t.requiredHeaders,body:buf}); then complete: await x('POST','/api/v1/uploads/'+t.uploadId+'/complete',{}) — data: Keep t.uploadId and t.uploadUrl.
  3. (FX-CUST-NEW) Attach and submit: PUT /api/v1/cook/application with the full draft of E2E-COOK-048 step 7 plus documentIds:[t.uploadId] and the current expectedVersion, then POST /api/v1/cook/application/submit.
  4. (FX-CUST-NEW) Read as the owner: await x('GET','/api/v1/uploads/'+t.uploadId)
  5. (FX-CUST-NEW) Replay the old ticket: build a DIFFERENT PNG (change the text to 'TAMPERED'), compute nothing, and run await fetch(t.uploadUrl,{method:'PUT',headers:t.requiredHeaders,body:otherBuf}); then await x('POST','/api/v1/uploads/'+t.uploadId+'/complete',{}) and await x('GET','/api/v1/uploads/'+t.uploadId). — data: Note the status code of the PUT.
  6. (FX-CUST-A) Other accounts read the same id: FX-CUST-A, FX-COOK-B and FX-STAFF-OPS each run await x('GET','/api/v1/uploads/<uploadId>') in their own signed-in ops tab (FX-STAFF-OPS has not opened the application yet).
  7. (FX-STAFF-OPS) FX-STAFF-OPS: https://ops.<PREVIEW_DOMAIN> > 'Cook applications' > 'Submitted' > the application (no decision needed) > section 'Documents' (ar 'المستندات') > 'View document #1' (ar 'عرض المستند'), then click the link 'Temporary link (5 minutes) #1'.
  8. (FX-STAFF-OPS) Wait 6 minutes and reload the temporary link in the browser.
- **Expected:**
  - UI: Step 7: the link opens the synthetic image 'SYNTHETIC TEST DOCUMENT' (not 'TAMPERED'). Step 8: the browser shows an expired/denied storage error instead of the image.
  - API: Step 2: ticket 201 {uploadId, uploadUrl (host media.<PREVIEW_DOMAIN>), method 'PUT', requiredHeaders {Content-Type:'image/png'}, expiresAt = +10 min}; PUT 200; complete 200 state 'APPROVED'.
  - API: Step 3: application PUT 200 and submit 200 state SUBMITTED; documentIds must be APPROVED COOK_DOCUMENT uploads owned by the applicant (otherwise 422 documentIds DOCUMENT_NOT_APPROVED).
  - API: Step 4: GET -> 200 state 'APPROVED' with a privateReadUrl for the owner.
  - API: Step 5: the replay PUT is either refused by storage (403) or accepted at the old upload key; in both cases complete -> 200 state 'APPROVED' and the document served afterwards is still the original (sha256 of the bytes behind privateReadUrl equals the first sha).
  - API: Step 6: FX-CUST-A and FX-COOK-B -> 404 NOT_FOUND; FX-STAFF-OPS -> 200 (the document is attached to a submitted revision) and an audit row is written.
  - DB: SELECT state, purpose, private_key, public_key, scan_verdict FROM media_asset WHERE id = <uploadId> -> APPROVED, COOK_DOCUMENT, 'documents/<uploadId>' (the sealed key, not 'uploads/<uploadId>'), NULL public_key, CLEAN.
  - DB: SELECT action, permission, outcome FROM audit_event WHERE object_type = 'media' AND object_id = '<uploadId>' -> access.media, cook.review, ALLOWED for FX-STAFF-OPS (one row per staff read).
- **Also covered by automated tests:** MediaIntegrationTest (replaying_the_upload_ticket_after_approval_does_not_change_the_approved_document, private_documents_have_no_public_url_and_only_owner_or_scoped_reviewer_of_a_submitted_revision_can_read); MediaCleanupIntegrationTest (PR #9 only)
- **Unverified against code (confirm while executing):** PR #9 ONLY: the sealed copy 'documents/<id>' and the cleanup of the upload key exist only in PR #9 (git diff origin/develop...HEAD on MediaService.java: develop leaves private_key = the ticket key 'uploads/<id>'). On origin/develop step 5 is expected to FAIL (the replayed PUT can overwrite the approved document) - run it there only to document that known defect.; Whether SeaweedFS accepts a replayed presigned PUT after approval (200) or refuses it is storage behaviour I did not trace; the case asserts only that the sealed copy is unchanged.; The expiry response of the 5-minute read link (likely 403 from the S3 endpoint) was not read from code.; That the temporary link renders inline in the browser depends on the stored content type.

### E2E-COOK-054 — A cook cannot read or change another cook's meals, slots, orders or uploads; the other kitchen's data stays unchanged

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** api, operations-web · **Actors:** FX-COOK-B, FX-COOK-A, FX-CUST-A
- **Fixtures to provision:** FX-COOK-B
- **Traces to:** scenarios: TC-006; rules: BR-006, BR-008; operations: PATCH /api/v1/cook/meals/{mealId}, POST /api/v1/cook/meals/{mealId}/publication, PATCH /api/v1/cook/slots/{slotId}, GET /api/v1/cook/orders/{orderId}, GET /api/v1/uploads/{uploadId}; partial: TC-111, TC-122
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-COOK-B (PROVISION by deploy/web-preview/seed-owner-e2e.sql): login owner-e2e-cook-b, re-pointed to the seeded second kitchen 'مطبخ تجريبي تاني' (id 00000000-0000-4000-8000-0000000bd002, 1 meal, one OPEN slot from seed-preview.sql). Verify: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1' returns that kitchen, ACTIVE. Never create FX-COOK-B by approving FX-COOK-PENDING: that fixture is consumed only by E2E-COOK-049.
  - Kitchen A resources (record ids first as FX-COOK-A: GET /api/v1/cook/meals, GET /api/v1/cook/slots, GET /api/v1/cook/orders): meal M_A = 00000000-0000-4000-8000-0000000bb002, slot S_A = id of any seeded OPEN slot, order O_A = any order of kitchen A (create one with FX-CUST-A if none exists), upload U_A = the meal-photo upload id of E2E-COOK-051.
  - Baseline before the attacks: SELECT version, price_minor FROM meal WHERE id = '00000000-0000-4000-8000-0000000bb002'; SELECT version, max_portions, state FROM cook_slot WHERE id = <S_A>; SELECT recipient_identifier, version FROM kitchen_payment_profile WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001'.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-COOK-B) As FX-COOK-B in the console of https://ops.<PREVIEW_DOMAIN> (helper as in E2E-COOK-048) run: await x('PATCH','/api/v1/cook/meals/00000000-0000-4000-8000-0000000bb002',{priceMinor:1,expectedVersion:<M_A version>}) — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-COOK-B) await x('POST','/api/v1/cook/meals/00000000-0000-4000-8000-0000000bb002/publication',{action:'ARCHIVE',expectedVersion:<M_A version>}) and await x('GET','/api/v1/cook/meals/00000000-0000-4000-8000-0000000bb002/price-history')
  3. (FX-COOK-B) await x('PATCH','/api/v1/cook/slots/<S_A>',{state:'CLOSED',expectedVersion:<S_A version>})
  4. (FX-COOK-B) await x('POST','/api/v1/cook/slots',{localDate:<free date of kitchen B>,localStart:'16:00',localEnd:'17:00',maxPortions:5,mealCapacities:[{mealId:'00000000-0000-4000-8000-0000000bb002',capacity:1}]})
  5. (FX-COOK-B) await x('GET','/api/v1/cook/orders/<O_A>') and await x('POST','/api/v1/cook/orders/<O_A>/accept',{expectedVersion:0})
  6. (FX-COOK-B) await x('GET','/api/v1/cook/meals?limit=100') and await x('GET','/api/v1/cook/slots?limit=100') — data: Look for any id belonging to kitchen A.
  7. (FX-COOK-B) await x('GET','/api/v1/uploads/<U_A>') and await x('GET','/api/v1/cook/payment-profile')
  8. (FX-CUST-A) As FX-CUST-A (no kitchen) in the ops tab console: await x('PATCH','/api/v1/cook/meals/00000000-0000-4000-8000-0000000bb002',{priceMinor:1,expectedVersion:<M_A version>}) and open https://ops.<PREVIEW_DOMAIN>
- **Expected:**
  - UI: Step 8: the ops web of FX-CUST-A shows 'This account has no approved kitchen yet.' and no tabs.
  - API: Step 1-2: 404 NOT_FOUND for the meal PATCH, the publication change and price-history (existence of another kitchen's meal is not revealed; not 403).
  - API: Step 3: 404 NOT_FOUND ('slot not found').
  - API: Step 4: 422 VALIDATION_FAILED with fieldErrors [{field:'mealCapacities', code:'MEAL_NOT_OFFERABLE'}]; no slot is created.
  - API: Step 5: GET -> 404 NOT_FOUND; accept -> 404 NOT_FOUND.
  - API: Step 6: neither list contains an id of kitchen A.
  - API: Step 7: uploads -> 404 NOT_FOUND; payment-profile -> 200 with kitchen B's own profile (not kitchen A's recipient).
  - API: Step 8: 403 FORBIDDEN ('caller has no approved kitchen').
  - DB: Re-run the baseline queries: meal version/price_minor, cook_slot version/max_portions/state and kitchen_payment_profile recipient/version are all identical to the baseline.
  - DB: SELECT count(*) FROM cook_slot WHERE kitchen_id = '<kitchen B id>' AND local_date = <date> -> 0.
- **Also covered by automated tests:** MealIntegrationTest.cooks_only_see_and_change_their_own_meals; SlotIntegrationTest.meals_must_belong_to_the_kitchen_and_cooks_only_touch_their_own_slots; StaffAccessIntegrationTest.kitchen_scope_matrix
- **Unverified against code (confirm while executing):** That GET /api/v1/cook/orders/{id} and the accept action answer 404 (not 403) for another kitchen's order is inferred from OrderService.cookView/lock('o.kitchen_id = ?'); not run.

### E2E-OPS-055 — Each staff account sees and can call exactly its granted sections; the administrator is not a superuser; denials are audited

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-STAFF-SUPPORT, FX-STAFF-OPS, FX-STAFF-FINANCE, FX-STAFF-ADMIN, FX-STAFF-NOGRANT
- **Fixtures to provision:** FX-STAFF-SUPPORT, FX-STAFF-OPS, FX-STAFF-FINANCE
- **Traces to:** rules: BR-006, BR-007; operations: GET /api/v1/ops/cooks, GET /api/v1/ops/catalogue, GET /api/v1/ops/cases, GET /api/v1/ops/orders, GET /api/v1/ops/finance/statements, GET /api/v1/ops/payments, GET /api/v1/ops/configuration, GET /api/v1/ops/staff, GET /api/v1/ops/audit, POST /api/v1/ops/kitchens/{kitchenId}/operating-state; partial: TC-007, TC-006, TC-111, TC-126
- **Prerequisites:**
  - FX-STAFF-SUPPORT (PROVISION): staff account with exactly one active grant, support.case. Provision as FX-STAFF-OPS (one INSERT into user_grant with permission 'support.case' after the first sign-in, or POST /api/v1/ops/staff/{userId}/grants by FX-STAFF-ADMIN with MFA).
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - FX-STAFF-FINANCE (PROVISION): finance staff with the grants of fixtures.json / seed-owner-e2e.sql: finance.read, finance.verify, finance.refund_approve, finance.settlement, config.read. Not in seed-preview.sql. Verify only the grants the case needs (for example finance.read, finance.verify).
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - FX-STAFF-NOGRANT (EXISTS) = preview-ops-2, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f2: staff-style login with no grants. Its app_user row is created at its first sign-in (not in seed-preview.sql); its user id for API calls: SELECT id FROM app_user WHERE subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f2'.
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Record t0 = current time before the first sign-in. Use one browser profile per account (or sign out between accounts).
  - Kitchen A on the shared preview is never mutated by this case: P10 is skipped for OPS (it holds cook.review, so with recent MFA it could pause the kitchen).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-SUPPORT) For each account in turn (SUPPORT, OPS, FINANCE, ADMIN, NOGRANT) open https://ops.<PREVIEW_DOMAIN>, sign in (password from the private fixture sheet) and write down the tab names in the navigation bar (language English). — data: Opening the page already makes the client send 8 probe GETs (9 in PR #9) plus GET /api/v1/me (App.tsx checkSession).
  2. (FX-STAFF-SUPPORT) In the same tab define the helper and run probes P1-P11 for that account. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me') Probes (console helper, run each and record status + body.code): P1 GET /api/v1/ops/cooks?limit=1 [cook.review]; P2 GET /api/v1/ops/catalogue?limit=1 [content.moderate]; P3 GET /api/v1/ops/cases?limit=1 [support.case]; P4 GET /api/v1/ops/orders?limit=1 [order.ops]; P5 GET /api/v1/ops/finance/statements?kitchenId=00000000-0000-4000-8000-0000000bd001 [finance.read or finance.settlement or finance.refund_approve]; P6 GET /api/v1/ops/payments?state=DISPUTED [finance.verify]; P7 GET /api/v1/ops/configuration [config.read or config.write]; P8 GET /api/v1/ops/staff [staff.grant]; P9 GET /api/v1/ops/audit?limit=1 [audit.read]; P10 POST /api/v1/ops/kitchens/00000000-0000-4000-8000-0000000bd001/operating-state body {action:'PAUSE',reason:'probe only',expectedVersion:0} [cook.review + MFA]: run P10 ONLY for accounts WITHOUT cook.review (SUPPORT, FINANCE, ADMIN, NOGRANT); for OPS run it only on a disposable stack (FX-ISOLATED) in a session confirmed password-only (no acr/amr MFA claim; never after a step-up), because with a recent MFA proof it really pauses the kitchen; P11 GET /api/v1/ops/security/mfa-policy [security.mfa_policy, PR #9 only].
  3. (FX-STAFF-SUPPORT) After all five accounts are done, read the denial audit rows (SQL under database).
- **Expected:**
  - UI: Tabs per account (en / ar): SUPPORT -> 'Support' (الدعم) only. OPS -> 'Cook applications' (طلبات الطهاة), 'Catalogue moderation' (مراجعة الأطباق), 'Order monitoring' (متابعة الطلبات) - no 'Support' because OPS has no support.case. FINANCE -> 'Finance' (المالية) and 'Operating configuration' (الإعدادات التشغيلية, from config.read). ADMIN -> 'Staff permissions' (صلاحيات الموظفين) only. NOGRANT -> no tab bar; heading 'This account has no approved kitchen yet.'.
  - UI: PR #9 additionally appends a tab 'Security' (ar from sec.tab) to every account that has at least one section; NOGRANT still shows no tabs.
  - UI: No account sees the cook tabs (Home, Orders, Meals, ...) because none owns a kitchen.
  - API: Expected status per probe (200 allowed, 403 FORBIDDEN denied). SUPPORT: P3=200, all others 403. OPS: P1, P2, P4 = 200, P3 = 403 (no support.case), P5-P9 = 403, P10 is NOT run on the shared preview for OPS (on FX-ISOLATED with a password-only session: 403 STEP_UP_REQUIRED, because cook.review passes and recentMfa fails), P11 403. FINANCE: P5 = 200, P6 = 200, P7 = 200 (config.read), all others 403. ADMIN: P8=200, P9=200, all others 403 including P1-P7 (an administrator holds only what was granted). NOGRANT: every probe 403.
  - API: Every 403 body is {code:'FORBIDDEN', fieldErrors:[], requestId:<non-empty>, retryable:false}.
  - API: P10 by SUPPORT/FINANCE/ADMIN/NOGRANT -> 403 FORBIDDEN (no cook.review); kitchen A is still ACTIVE.
  - DB: SELECT a.actor_id, count(*), array_agg(DISTINCT a.action) FROM audit_event a WHERE a.outcome = 'DENIED' AND a.object_type = 'endpoint' AND a.occurred_at > '<t0>' GROUP BY 1 -> one group per account; actions look like 'denied GET /api/v1/ops/orders' with reason 'FORBIDDEN' and request_id set; the count for each account equals its number of 403 probes plus the automatic 403s of its login (the page probes).
  - DB: SELECT operating_state, version FROM kitchen WHERE id = '00000000-0000-4000-8000-0000000bd001' -> ACTIVE, unchanged.
  - DB: SELECT user_id, permission FROM user_grant WHERE revoked_at IS NULL -> unchanged by the case (no probe grants anything).
- **Also covered by automated tests:** StaffAccessIntegrationTest (kitchen_scope_matrix, customer_cannot_grant_privileges_and_the_attempt_is_audited); AuditAndCorrelationReadinessIntegrationTest.a_denied_request_on_a_long_path_is_refused_and_audited; apps/operations-web/src/App.test.tsx (tab visibility, mocked API)
- **Unverified against code (confirm while executing):** Anonymous or non-ApiException 403s (for example a service-level ApiException FORBIDDEN such as 'caller has no approved kitchen' on /cook/dashboard) are NOT audited; only denials raised by Spring Security (@PreAuthorize, step-up) write 'denied ...' rows (ApiSecurityErrors.handle).; The 11th probe (security.mfa_policy) does not exist on origin/develop (404/403 there); the PR #9 expectation is 403 FORBIDDEN.; OPS holds cook.review: CookReviewController:76 evaluates the permission and then recentMfa, so OPS gets STEP_UP_REQUIRED (not FORBIDDEN) without MFA and a real pause with MFA; hence the isolated-stack-only variant.

### E2E-OPS-056 — Operations hide a meal with a reason; it leaves customer sale at once but the cook's draft/publication state is untouched; restore brings it back

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, customer-web, api · **Actors:** FX-STAFF-OPS, FX-COOK-A, FX-GUEST
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** rules: BR-009, BR-011; operations: GET /api/v1/ops/catalogue, POST /api/v1/ops/meals/{mealId}/moderation; partial: TC-123
- **Prerequisites:**
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Use the meal 'محشي تجريبي' (00000000-0000-4000-8000-0000000bb002, PUBLISHED, 180.00 EGP); no staff second factor is needed for meal moderation (only content.moderate).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-OPS) FX-STAFF-OPS: https://ops.<PREVIEW_DOMAIN> > 'Catalogue moderation' (ar 'مراجعة الأطباق'). Type 'محشي' in 'Search' (ar 'بحث') and press 'Show' (ar 'عرض'). Read the card.
  2. (FX-STAFF-OPS) In the card, leave 'Reason' (ar 'السبب') empty and look at the red button; then type 'صورة مخالفة للمواصفات - اختبار' and click 'Hide' (ar 'إخفاء').
  3. (FX-GUEST) FX-GUEST: https://app.<PREVIEW_DOMAIN> search 'محشي' and open https://api.<PREVIEW_DOMAIN>/api/v1/meals/00000000-0000-4000-8000-0000000bb002 directly.
  4. (FX-COOK-A) FX-COOK-A: https://ops.<PREVIEW_DOMAIN> > Meals: look at the card 'محشي تجريبي'. Try 'Publish'/'Pause' only by looking at the offered buttons.
  5. (FX-STAFF-OPS) FX-STAFF-OPS: filter 'Hidden' (ar 'مخفي'); in the card type reason 'تمت المراجعة' and click 'Show' (ar 'إظهار').
  6. (FX-GUEST) FX-GUEST: search 'محشي' again.
  7. (FX-STAFF-OPS) FX-STAFF-OPS: hide the same meal twice quickly in two tabs (second click on a stale card). — data: Tab 1 hides; tab 2 (loaded before) clicks 'Hide'.
- **Expected:**
  - UI: Step 1: card shows title, status badge 'Published' (ar 'منشور'), badge 'Visible' (ar 'ظاهر'), 'مطبخ المعاينة - 180.00'.
  - UI: Step 2: with an empty reason the button is disabled; after clicking, the card shows badge 'Hidden' (ar 'مخفي') and 'Reason: صورة مخالفة للمواصفات - اختبار'.
  - UI: Step 3: the guest no longer finds the dish; the direct API call returns 404.
  - UI: Step 4: the cook still sees status 'Published' and the same price - moderation is separate from the cook's status; the cook gets no 'Publish' offer because the status did not change.
  - UI: Step 5-6: the dish is visible again and found by search.
  - UI: Step 7: tab 2 shows 'The data changed. Refreshed; please try again.' (ar from errors.VERSION_CONFLICT).
  - API: Step 2: POST /api/v1/ops/meals/00000000-0000-4000-8000-0000000bb002/moderation {action:'HIDE',reason,expectedVersion} -> 200 moderationState 'HIDDEN', status 'PUBLISHED'; reason shorter than 3 characters -> 422 reason LENGTH_3_1000; repeating HIDE -> 409 STATE_CONFLICT ('meal is already HIDDEN').
  - API: Step 3: GET /api/v1/meals/00000000-0000-4000-8000-0000000bb002 -> 404 NOT_FOUND; GET /api/v1/meals does not list it.
  - API: Step 5: {action:'RESTORE'} -> 200 moderationState 'VISIBLE'.
  - API: Step 7: 409 VERSION_CONFLICT.
  - DB: SELECT status, moderation_state, moderation_reason, moderated_by FROM meal WHERE id = '00000000-0000-4000-8000-0000000bb002' -> PUBLISHED, HIDDEN, the typed reason, FX-STAFF-OPS id; after restore VISIBLE with NULL reason (DB constraint meal_hidden_reason_ck).
  - DB: SELECT action, permission, outcome, reason, details FROM audit_event WHERE object_type = 'meal' AND object_id = '00000000-0000-4000-8000-0000000bb002' -> two meal.moderation rows (content.moderate, ALLOWED) with details action HIDE / RESTORE and kitchenId.
- **Also covered by automated tests:** ModerationIntegrationTest.hiding_a_meal_removes_it_from_discovery_detail_and_checkout_until_restored
- **Unverified against code (confirm while executing):** The original catalog mentions 're-open permitted review' and a 'reported kitchen'; the code has neither a report-a-kitchen feature nor a re-open workflow: only hide/restore of a meal and of a review exist.

### E2E-OPS-057 — Support staff work a customer's case: internal notes stay hidden from the customer, public replies reach them, and every staff view is audited

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-STAFF-SUPPORT, FX-STAFF-FINANCE
- **Fixtures to provision:** FX-STAFF-SUPPORT, FX-STAFF-FINANCE
- **Traces to:** rules: BR-041, BR-006; operations: GET /api/v1/ops/cases, GET /api/v1/ops/cases/{caseId}, POST /api/v1/ops/cases/{caseId}/messages, PATCH /api/v1/ops/cases/{caseId}, POST /api/v1/support/cases; partial: TC-041, TC-124, TC-006
- **Prerequisites:**
  - FX-STAFF-SUPPORT (PROVISION): staff account with exactly one active grant, support.case. Provision as FX-STAFF-OPS (one INSERT into user_grant with permission 'support.case' after the first sign-in, or POST /api/v1/ops/staff/{userId}/grants by FX-STAFF-ADMIN with MFA).
  - FX-STAFF-FINANCE (PROVISION): finance staff with the grants of fixtures.json / seed-owner-e2e.sql: finance.read, finance.verify, finance.refund_approve, finance.settlement, config.read. Not in seed-preview.sql. Verify only the grants the case needs (for example finance.read, finance.verify).
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-CUST-A owns at least one order (for example an order placed in E2E-COOK-050); note its public order number <N>. Cases may be opened on any own order.
  - The customer-web screen is 'My account' > 'Support' > 'Get help with an order'. No staff second factor is needed for support.case.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-CUST-A) FX-CUST-A: https://app.<PREVIEW_DOMAIN> > 'My account' (ar 'حسابي') > 'Support' > 'Get help with an order' (ar 'طلب مساعدة بخصوص طلب'): Order = <N>, Problem type = 'Quality' (ar 'الجودة'), Describe the problem = 'الأكل وصل بارد جدًا - اختبار الدعم' and send. Note the reference 'CS-XXXXXXXX'.
  2. (FX-STAFF-SUPPORT) FX-STAFF-SUPPORT: https://ops.<PREVIEW_DOMAIN> > 'Support' (ar 'الدعم') > filter 'Active' (ar 'النشطة'). Click the reference.
  3. (FX-STAFF-SUPPORT) In the reply box labelled 'Internal note' (ar 'ملاحظة داخلية', the default) type 'تم التواصل مع المطبخ وجارٍ التحقق' and click 'Send' (ar 'إرسال').
  4. (FX-CUST-A) FX-CUST-A: reload the case on the customer web.
  5. (FX-STAFF-SUPPORT) FX-STAFF-SUPPORT: switch the toggle to 'Message to customer' (ar 'رسالة للعميل'), type 'نعتذر عن التأخير وسنتابع مع المطبخ' and click 'Send'.
  6. (FX-CUST-A) FX-CUST-A: reload the case.
  7. (FX-STAFF-SUPPORT) FX-STAFF-SUPPORT: click 'Assign to me' (ar 'إسناد إليّ'); then filter 'Assigned to me' (ar 'المسندة إليّ').
  8. (FX-STAFF-FINANCE) FX-STAFF-FINANCE (no support.case): console GET /api/v1/ops/cases and GET /api/v1/ops/cases/<caseId>. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
- **Expected:**
  - UI: Step 2: queue row: badge 'Normal' (ar 'عادي'), 'CS-XXXXXXXX', 'Quality' (ar 'الجودة'), the order number, 'Open' (ar 'مفتوح'), 'Respond by <time 24 h after creation>'.
  - UI: Step 2: detail card heading 'CS-XXXXXXXX - Quality - Open'; the description 'الأكل وصل بارد جدًا - اختبار الدعم' is shown.
  - UI: Step 3: the case state in the heading changes to 'In progress' (ar 'قيد المعالجة'); the note is highlighted as an internal note.
  - UI: Step 4: the customer sees NO trace of the internal note and the case is not marked answered.
  - UI: Step 6: the customer sees exactly one staff message, 'نعتذر عن التأخير وسنتابع مع المطبخ'.
  - UI: Step 7: the case appears under 'Assigned to me'.
  - API: Step 2: GET /api/v1/ops/cases/{caseId} -> 200 StaffSupportCase with severity 'NORMAL', slaDueAt = createdAt + 24 h, firstResponseAt null.
  - API: Step 3: POST /api/v1/ops/cases/{id}/messages {body, visibility:'STAFF_ONLY'} with Idempotency-Key -> 201; firstResponseAt stays null (an internal note is not a response).
  - API: Step 4: GET /api/v1/support/cases/{id} (customer) -> messages do not contain the internal note.
  - API: Step 5: visibility 'PUBLIC' -> 201; the case's firstResponseAt is now set and overdue false.
  - API: Step 8: both calls -> 403 FORBIDDEN.
  - DB: SELECT visibility, author_role FROM case_message WHERE case_id = <id> ORDER BY created_at -> customer PUBLIC (opening description is on support_case), one STAFF_ONLY and one PUBLIC staff message.
  - DB: SELECT state, assignee_id, first_response_at, sla_due_at - created_at FROM support_case WHERE reference = 'CS-XXXXXXXX' -> IN_PROGRESS, FX-STAFF-SUPPORT id, set, 24:00:00.
  - DB: SELECT action, permission, outcome FROM audit_event WHERE object_type = 'support_case' AND object_id = '<id>' -> access.support_case (one per staff view of the detail), case.message x2 (details.visibility STAFF_ONLY / PUBLIC), case.update (assigned); no message text in the audit row.
- **Also covered by automated tests:** backend/app/src/test/java/com/matbakhy/app/support/cases/* (support case tests); apps/operations-web/src/App.test.tsx
- **Unverified against code (confirm while executing):** The original case 'inspect private fields' has no counterpart: the staff case view shows customerId and the description but no customer phone or address; contact is shown only in the order monitoring view while the order is active.; docs/product/06-permissions.md says support staff see 'assigned/authorized' cases, but the queue returns all cases to every support.case holder (SupportService.queue has no assignment filter).

### E2E-OPS-058 — A customer cannot make themselves staff: role fields, staff endpoints, forged tokens and grant requests are all refused server-side

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api, operations-web, customer-web · **Actors:** FX-CUST-A, FX-GUEST
- **Traces to:** scenarios: TC-007; rules: BR-007, BR-001; operations: PATCH /api/v1/me, POST /api/v1/ops/staff/{userId}/grants, GET /api/v1/ops/staff; partial: TC-109, TC-111
- **Prerequisites:**
  - FX-CUST-A (EXISTS, preview-customer-1, subject 7d3f0a52-1c0e-4b8e-9a3e-000000000c01) holds no staff grant: SELECT count(*) FROM user_grant WHERE user_id = '00000000-0000-4000-8000-0000000b0001' -> 0.
  - Record t0 = current time. All calls are made from the DevTools console of the signed-in customer tab (https://app.<PREVIEW_DOMAIN>) with the helper (cookie session, XSRF header).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-CUST-A) FX-CUST-A: define the helper (change the page origin: run it in the console of https://app.<PREVIEW_DOMAIN>), then await x('GET','/api/v1/me') and keep body.version. — data: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-CUST-A) Try to set a role through the profile: await x('PATCH','/api/v1/me',{expectedVersion:<version>,role:'ADMIN'})
  3. (FX-CUST-A) Ask for a staff grant for yourself: await x('POST','/api/v1/ops/staff/00000000-0000-4000-8000-0000000b0001/grants',{permission:'staff.grant',reason:'please make me admin'})
  4. (FX-CUST-A) Call staff endpoints: GET /api/v1/ops/staff, GET /api/v1/ops/audit, GET /api/v1/ops/cooks, GET /api/v1/ops/orders, POST /api/v1/ops/cooks/00000000-0000-4000-8000-000000000001/decision {decision:'START_REVIEW',revision:1,expectedVersion:0}.
  5. (FX-CUST-A) Send a forged unsigned bearer token with a role claim and no cookies, built in the browser console: const b=o=>btoa(JSON.stringify(o)).replace(/=/g,''); fetch('https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff',{headers:{Authorization:'Bearer '+b({alg:'none'})+'.'+b({sub:'x',roles:['ADMIN']})+'.'}}).then(r=>r.status) — data: Header = {"alg":"none"}, payload = {"sub":"x","roles":["ADMIN"]}, empty signature.
  6. (FX-CUST-A) Open https://ops.<PREVIEW_DOMAIN> in the same browser.
- **Expected:**
  - UI: Step 6: the page shows 'This account has no approved kitchen yet.' (ar 'لا يوجد مطبخ معتمد لهذا الحساب بعد.') and no navigation tabs.
  - API: Step 2: 400 MALFORMED_REQUEST (the strict request schema rejects the unknown field 'role').
  - API: Step 3: 403 FORBIDDEN.
  - API: Step 4: every call -> 403 FORBIDDEN, no data.
  - API: Step 5: 401 UNAUTHENTICATED.
  - DB: SELECT count(*) FROM user_grant WHERE user_id = '00000000-0000-4000-8000-0000000b0001' -> 0.
  - DB: SELECT action, outcome, reason FROM audit_event WHERE actor_id = '00000000-0000-4000-8000-0000000b0001' AND occurred_at > '<t0>' AND outcome = 'DENIED' -> rows 'denied POST /api/v1/ops/staff/<id>/grants', 'denied GET /api/v1/ops/staff', 'denied GET /api/v1/ops/audit', ... all reason FORBIDDEN (step 5 writes nothing: no authenticated actor).
- **Also covered by automated tests:** StaffAccessIntegrationTest (customer_cannot_grant_privileges_and_the_attempt_is_audited, role_fields_in_customer_requests_are_rejected); IdentityIntegrationTest
- **Unverified against code (confirm while executing):** The unsigned-token request is sent cross-origin with an Authorization header; if the browser blocks it before sending (CORS preflight), repeat it with curl from the tester's machine.

### E2E-OPS-059 — Granting a permission needs fresh MFA, a reason and is audited with actor, time, target and request id and no secrets

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-ADMIN-B, FX-STAFF-OPS · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-OPS, FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-007, BR-043; decisions: D18; operations: POST /api/v1/ops/staff/{userId}/grants, POST /api/v1/ops/staff/{userId}/grant-revocations, GET /api/v1/ops/audit; partial: TC-007, TC-111, TC-126
- **Prerequisites:**
  - FX-STAFF-ADMIN-B (seed-owner-e2e.sql): staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset; used as grantor AND revoker in its own browser profile with a recent MFA proof, because preview-ops-* accounts stay password-only (docs/security/mfa-policy.md:35) and FX-STAFF-OPS has no staff.grant.
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - FX-STAFF-OPS does not hold finance.read yet (check: SELECT count(*) FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<FX-STAFF-OPS subject>' AND g.permission = 'finance.read' AND g.revoked_at IS NULL -> 0).
  - The audit trail has no screen in the ops web; it is read through the API in the same browser (GET request, cookie session of the administrator).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B: open https://ops.<PREVIEW_DOMAIN> and sign in. Tab 'Staff permissions' (ar 'صلاحيات الموظفين'). Find the card of FX-STAFF-OPS (its display name).
  2. (FX-STAFF-ADMIN-B) In that card choose Permission 'finance.read' (ar label 'الصلاحية'), type Reason 'دعم المراجعة المالية - اختبار' and click 'Grant' (ar 'منح'). If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  3. (FX-STAFF-ADMIN-B) In the same browser open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?action=staff.grant&objectType=user&objectId=<FX-STAFF-OPS user id>&limit=5 — data: Read the JSON.
  4. (FX-STAFF-ADMIN-B) Open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?actorId=<FX-STAFF-ADMIN-B user id>&action=audit.read&limit=3 — data: The administrator's own reads.
  5. (FX-STAFF-ADMIN-B) Search both JSON bodies for the strings 'Authorization', 'Bearer', 'eyJ', 'password', 'secret', '+20'.
  6. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B (not FX-STAFF-OPS, which lacks staff.grant): Cleanup - in the card of FX-STAFF-OPS click 'Revoke' (ar 'سحب') next to finance.read with reason 'انتهى الاختبار' (fresh MFA if asked).
- **Expected:**
  - UI: Step 2: before MFA the notice 'This action needs additional verification (MFA).' + 'Verify again'; after step-up and a second click on 'Grant' the card lists 'finance.read' with a 'Revoke' button.
  - UI: Step 6: the grant disappears from the card.
  - API: Step 2: POST /api/v1/ops/staff/{userId}/grants {permission:'finance.read',reason} -> first 403 STEP_UP_REQUIRED, then 201 {id, userId, permission:'finance.read', grantedAt, expiresAt:null, revokedAt:null}.
  - API: Step 3: 200 {items:[{id, occurredAt (ISO-8601 UTC), actorId:<admin id>, action:'staff.grant', permission:'finance.read', objectType:'user', objectId:<target id>, outcome:'ALLOWED', reason:'دعم المراجعة المالية - اختبار', requestId:<non-empty>, details:'{"grantId":"...","expiresAt":"never"}'}], nextCursor, hasMore}.
  - API: Step 4: contains the audit.read events for the admin's own reads with objectType 'audit_event'.
  - API: Step 5: none of the strings appears (details are redacted; phone-like values are masked).
  - DB: SELECT occurred_at, actor_id, action, permission, object_type, object_id, outcome, reason, request_id, details FROM audit_event WHERE action IN ('staff.grant','staff.revoke') AND object_id = '<target id>' ORDER BY occurred_at -> staff.grant ALLOWED then staff.revoke ALLOWED, plus one DENIED 'denied POST /api/v1/ops/staff/<id>/grants' (reason STEP_UP_REQUIRED) from the first click.
  - DB: SELECT granted_by, grant_reason, granted_at, revoked_at, revoke_reason FROM user_grant WHERE user_id = '<target id>' AND permission = 'finance.read' -> granted_by = admin id, reason as typed, revoked_at set after step 6; occurred_at of the audit row is within a second of granted_at.
- **Also covered by automated tests:** StaffAccessIntegrationTest (grant_changes_require_recent_mfa, audit_is_append_only_and_redacted, redaction_masks_sensitive_detail_keys_and_values)
- **Unverified against code (confirm while executing):** The user id of FX-STAFF-OPS is read from GET /api/v1/ops/staff (field userId) in the browser: open https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff as the administrator.; Redaction patterns are implemented in operations/api/Redaction.java; which keys are masked was not enumerated.

### E2E-OPS-060 — The last administrator's staff.grant cannot be revoked, even by themself

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-ADMIN-B, FX-STAFF-ADMIN · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-ISOLATED
- **Traces to:** scenarios: TC-043; rules: BR-043, BR-007; operations: POST /api/v1/ops/staff/{userId}/grant-revocations; partial: TC-126, TC-007
- **Prerequisites:**
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Run on a disposable stack (FX-ISOLATED): this case changes shared seeded state (kitchen A operating state / payment recipient / administrator grants); on any shared preview it would disturb other cases. Restoration steps are listed, but the stack is discarded afterwards.
  - Setup on FX-ISOLATED: make FX-STAFF-ADMIN-B the only holder of staff.grant by revoking FX-STAFF-ADMIN's staff.grant (SQL UPDATE user_grant SET revoked_at = now(), revoked_by = user_id, revoke_reason = 'test setup' on the isolated database). Verify: SELECT count(DISTINCT g.user_id) FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE g.permission = 'staff.grant' AND g.revoked_at IS NULL AND u.status = 'ACTIVE' -> 1 (FX-STAFF-ADMIN-B). The actor is FX-STAFF-ADMIN-B (it can pass MFA; preview-ops-* stay password-only).
- **Steps:**
  1. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B: https://ops.<PREVIEW_DOMAIN> > 'Staff permissions'. Find your own card; it lists 'audit.read' and 'staff.grant', each with a red 'Revoke' (ar 'سحب') button.
  2. (FX-STAFF-ADMIN-B) Type Reason 'اختبار حماية آخر مسؤول' and click 'Revoke' next to staff.grant. If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  3. (FX-STAFF-ADMIN-B) Reload the page.
  4. (FX-STAFF-ADMIN-B) Restoration: in the card of FX-STAFF-ADMIN grant 'staff.grant' (reason 'استعادة المسؤول') - the card exists because FX-STAFF-ADMIN still holds audit.read - or discard the isolated stack. — data: Grant needs a fresh MFA proof.
- **Expected:**
  - UI: Step 2: after the step-up the error 'This action is no longer available. Refreshed.' (ar errors.STATE_CONFLICT) appears; the card of FX-STAFF-ADMIN-B still lists staff.grant.
  - UI: Step 3: the Staff permissions tab is still available.
  - API: Step 2: POST /api/v1/ops/staff/{ownUserId}/grant-revocations {grantId,reason} -> 403 STEP_UP_REQUIRED first, then 409 STATE_CONFLICT ('last administrator').
  - DB: SELECT revoked_at FROM user_grant WHERE id = '<staff.grant grant id>' -> NULL.
  - DB: SELECT action, permission, outcome, reason FROM audit_event WHERE action = 'staff.revoke' AND object_id = '<FX-STAFF-ADMIN-B id>' -> DENIED, 'staff.grant', reason 'last administrator protected'.
- **Also covered by automated tests:** StaffAccessIntegrationTest.self_escalation_unknown_permissions_and_last_admin_are_refused
- **Unverified against code (confirm while executing):** The protection counts ACTIVE holders of staff.grant only; it does not check that the remaining administrator can actually complete MFA (the 'recoverable' part of BR-043 is not enforced beyond holder count).

### E2E-OPS-061 — Two sessions edit the same zone fee from the same version: the first saves, the second gets a version conflict and a refreshed form

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-ADMIN-B · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-042; operations: POST /api/v1/ops/configuration/{key}/versions, GET /api/v1/ops/configuration; partial: TC-126, TC-042
- **Prerequisites:**
  - FX-STAFF-ADMIN-B (PROVISION): second administrator with the grants of fixtures.json / seed-owner-e2e.sql: staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset. Not in seed-preview.sql. For E2E-OPS-102 first grant it content.moderate.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Zone 'Test zone B' (ar 'منطقة تجريبية ب', id 00000000-0000-4000-8000-00000000a002) has delivery fee 45.00 EGP and is active (devdata). Note its version: GET /api/v1/ops/configuration -> zones[].version.
  - Two separate browser profiles (A and B), both signed in as FX-STAFF-ADMIN-B, with a fresh MFA proof (younger than 10 minutes) in each so no step-up interrupts the sequence. Policy edits are API-only (the screen offers zones and the ordering switch).
- **Steps:**
  1. (FX-STAFF-ADMIN-B) Profile A and B: open https://ops.<PREVIEW_DOMAIN> > 'Operating configuration' (ar 'الإعدادات التشغيلية'). Both show the card 'Test zone B - 45.00' with 'Delivery fee (EGP)' = 45.00.
  2. (FX-STAFF-ADMIN-B) Profile A: set 'Delivery fee (EGP)' to 46.00, 'Reason for change' (ar 'سبب التغيير') = 'اختبار تعارض الإصدار 1', click 'Save' (ar 'حفظ').
  3. (FX-STAFF-ADMIN-B) Profile B (still showing 45.00, stale version): set 47.00, reason 'اختبار تعارض الإصدار 2', click 'Save'.
  4. (FX-STAFF-ADMIN-B) Profile B: read the form after the refresh and the 'Change log' (ar 'سجل التغييرات').
  5. (FX-STAFF-ADMIN-B) Restore: in profile B set 45.00 with reason 'استعادة الرسوم' and Save.
- **Expected:**
  - UI: Step 2: the zone heading becomes 'Test zone B - 46.00' and the change log shows '<time> - zone:<id> v<N+1> - اختبار تعارض الإصدار 1'.
  - UI: Step 3: the notice 'The data changed. Refreshed; please try again.' (ar errors.VERSION_CONFLICT) appears, the form reloads with 46.00 (NOT 47.00).
  - UI: Step 4: the change log contains exactly one new entry for the pair of attempts.
  - API: Step 2: POST /api/v1/ops/configuration/zone%3A00000000-0000-4000-8000-00000000a002/versions {value:{active:true,deliveryFeeMinor:4600},reason,expectedVersion:N} -> 200 {key, version:N+1, value, reason, actorId}.
  - API: Step 3: same call with expectedVersion N -> 409 VERSION_CONFLICT; nothing changes.
  - DB: SELECT delivery_fee_minor, version FROM service_zone WHERE id = '00000000-0000-4000-8000-00000000a002' -> 4600 / N+1 after step 3; 4500 / N+2 after step 5.
  - DB: SELECT config_key, resulting_version, value, reason, actor_id FROM config_change WHERE config_key = 'zone:00000000-0000-4000-8000-00000000a002' ORDER BY created_at -> one row per successful save only; the conflicting attempt left no row (and config_change is append-only).
  - DB: SELECT outcome FROM audit_event WHERE action = 'config.change' AND object_id = 'zone:00000000-0000-4000-8000-00000000a002' -> ALLOWED rows only for the two successful saves.
- **Also covered by automated tests:** ConfigurationIntegrationTest.a_fee_change_keeps_placed_orders_invalidates_stale_quotes_and_applies_to_new_quotes
- **Unverified against code (confirm while executing):** The original catalog text 'both edit policy using expectedVersion=v' cannot be done in the UI; the commerce policy is changed only through the API (see E2E-OPS-107). Zone fee was used because the screen supports it.; service_zone has columns delivery_fee_minor and version (names taken from ZoneAdministration usage and devdata, not from the migration file V3).

### E2E-OPS-062 — The public operations URL serves only the sign-in page; every protected call without a session is refused and writes no data

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-GUEST, FX-CUST-A
- **Traces to:** rules: BR-001, BR-002, BR-007; operations: GET /api/v1/ops/staff, GET /api/v1/me, POST /api/v1/ops/cooks/{applicationId}/decision; partial: TC-001, TC-007, TC-136
- **Prerequisites:**
  - The ops and api hosts are reachable from the tester's network (the preview proxy allows only STAFF_ALLOWED_IPS for these hosts: check from an allowed address first). A shell with curl. Record t0.
  - No cookies in the browser profile / curl (private window).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-GUEST) FX-GUEST: open https://ops.<PREVIEW_DOMAIN> in a private window.
  2. (FX-GUEST) Run: for p in /api/v1/ops/staff /api/v1/ops/audit '/api/v1/ops/orders?limit=1' /api/v1/ops/cases /api/v1/ops/configuration /api/v1/cook/dashboard /api/v1/me; do curl -s -o /dev/null -w "$p %{http_code}\n" https://api.<PREVIEW_DOMAIN>$p; done
  3. (FX-GUEST) Run: curl -s -i -X POST https://api.<PREVIEW_DOMAIN>/api/v1/ops/cooks/00000000-0000-4000-8000-000000000001/decision -H 'Content-Type: application/json' -d '{"decision":"APPROVE","revision":1,"expectedVersion":0,"reason":"no session"}' and the same for /api/v1/ops/staff/00000000-0000-4000-8000-000000000001/grants.
  4. (FX-GUEST) Run: curl -s -o /dev/null -w '%{http_code}\n' https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public and https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=1 and https://api.<PREVIEW_DOMAIN>/actuator/prometheus.
  5. (FX-GUEST) In the page of step 1 press 'Sign in' (ar 'تسجيل الدخول').
  6. (FX-CUST-A) Sign in as FX-CUST-A, then press 'Sign out' (ar 'تسجيل الخروج'), then in a console of https://ops.<PREVIEW_DOMAIN> run fetch('https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff',{credentials:'include'}).then(r=>r.status).
- **Expected:**
  - UI: Step 1: the title 'Cook workspace - Matbakhy' style (ar 'مساحة الطاهي'), the slogan, the text 'Sign in to manage your kitchen.' (ar 'سجّل الدخول لإدارة مطبخك.') and a 'Sign in' button; NO tabs, NO data, no error card; footer 'API: https://api.<PREVIEW_DOMAIN>'. The demo notice 'Demo environment' (ar 'بيئة تجريبية') is shown on the preview.
  - UI: Step 5: the browser goes to https://id.<PREVIEW_DOMAIN>/realms/matbakhy/... (login form).
  - UI: Step 6: after sign-out the page is back to the sign-in view.
  - API: Step 2: every protected path prints 401 (body {code:'UNAUTHENTICATED'}); /api/v1/me also 401.
  - API: Step 3: 401 or 403 (a request without session and CSRF token is refused by the CSRF/authentication layer); never 2xx; no state change.
  - API: Step 4: configuration/public 200 and meals 200 (public reads); /actuator/prometheus is NOT served on the public host (404/403).
  - API: Step 6: 401 UNAUTHENTICATED after logout.
  - DB: SELECT count(*) FROM audit_event WHERE occurred_at > '<t0>' AND actor_id IS NULL AND action LIKE 'denied%' -> 0 (anonymous refusals are not audited); SELECT count(*) FROM cook_application WHERE state <> 'SUBMITTED' AND updated_at > '<t0>' -> 0; user_grant unchanged.
- **Also covered by automated tests:** IdentityIntegrationTest; scripts/test_proxy_access.py (real Caddy); deploy/web-preview/smoke.sh (boundary of privileged operations, read-only)
- **Unverified against code (confirm while executing):** Status of anonymous POST (step 3) - 401 vs 403 - depends on whether the CSRF filter or the authentication entry point fires first; the case accepts either.; /actuator/prometheus exposure: docs say only /actuator/health is exposed and the proxy does not route actuator; the precise status is not asserted.

### E2E-COOK-101 — A meal cannot be published without a complete allergen declaration, price, and approved photo; each missing field is named

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-COOK-A
- **Traces to:** scenarios: TC-011; rules: BR-011, BR-012; operations: POST /api/v1/cook/meals, PATCH /api/v1/cook/meals/{mealId}, POST /api/v1/cook/meals/{mealId}/publication; partial: TC-008, TC-114
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - The ops web 'New meal' form cannot express every case below (it only offers 'None declared' / 'Unknown' allergens and always sends a declaration), so this case uses the API from the signed-in browser.
  - An APPROVED meal photo owned by FX-COOK-A: use the upload id created in E2E-COOK-051 step 4 (SELECT id FROM media_asset WHERE owner_id = <cook id> AND purpose = 'MEAL_PHOTO' AND state = 'APPROVED').
- **Steps:**
  1. (FX-COOK-A) Sign in to https://ops.<PREVIEW_DOMAIN> as FX-COOK-A and define the helper in the console. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-COOK-A) Create a draft with NO allergen declaration and no photo: await x('POST','/api/v1/cook/meals',{titleAr:'طبق بلا بيان',descriptionAr:'وصف كافٍ للاختبار',portionAr:'علبة',ingredientsAr:'مكونات الاختبار',priceMinor:5000}) — data: Keep body.id and body.version.
  3. (FX-COOK-A) Publish it: await x('POST','/api/v1/cook/meals/<id>/publication',{action:'PUBLISH',expectedVersion:<version>})
  4. (FX-COOK-A) Patch the draft to CONTAINS without codes: await x('PATCH','/api/v1/cook/meals/<id>',{allergenDeclaration:'CONTAINS',expectedVersion:<version>})
  5. (FX-COOK-A) Patch NONE_DECLARED together with codes: x('PATCH',...,{allergenDeclaration:'NONE_DECLARED',allergenCodes:['MILK'],expectedVersion:<version>})
  6. (FX-COOK-A) Patch a valid declaration and the approved photo: x('PATCH',...,{allergenDeclaration:'CONTAINS',allergenCodes:['GLUTEN','MILK'],photos:[{mediaId:'<approved upload id>'}],expectedVersion:<version>}) then publish again.
  7. (FX-COOK-A) Anonymously open https://app.<PREVIEW_DOMAIN> and the dish page of the new meal (title 'طبق بلا بيان').
  8. (FX-COOK-A) Send a PATCH with a photo id that belongs to another account or to a PDF document upload (use a random UUID if none is available): photos:[{mediaId:'00000000-0000-4000-8000-00000000dead'}].
- **Expected:**
  - UI: Step 7: the customer dish page lists the allergens 'Contains: Gluten, Milk' (ar from allergenNames); before step 6 the dish is not findable at all.
  - API: Step 2: 201, status 'DRAFT' (incomplete drafts are allowed).
  - API: Step 3: 422 VALIDATION_FAILED, fieldErrors include {field:'allergenDeclaration', code:'REQUIRED'} and {field:'photos', code:'APPROVED_PHOTO_REQUIRED'} (priceMinor is present so it is not listed).
  - API: Step 4: 422 with {field:'allergenCodes', code:'REQUIRED_FOR_CONTAINS'}.
  - API: Step 5: 422 with {field:'allergenCodes', code:'ONLY_WITH_CONTAINS'}.
  - API: Step 6: PATCH 200 (revision +1); publish 200 status 'PUBLISHED'.
  - API: Step 7: GET /api/v1/meals/{id} -> 200 allergenCodes ['GLUTEN','MILK'], allergenStatement ''.
  - API: Step 8: 422 with {field:'photos', code:'PHOTO_NOT_APPROVED'}.
  - DB: SELECT status, allergen_declaration, allergen_codes, revision FROM meal WHERE title_ar = 'طبق بلا بيان' -> DRAFT with allergen_declaration NULL until step 6, then PUBLISHED, CONTAINS, {GLUTEN,MILK}.
  - DB: SELECT revision, snapshot->>'allergenDeclaration' FROM meal_revision WHERE meal_id = <id> ORDER BY revision -> one new immutable row per successful content change.
- **Also covered by automated tests:** MealIntegrationTest (every_publication_requirement_is_named_and_invalid_declarations_are_refused, photos_must_be_the_cooks_own_approved_uploads)
- **Unverified against code (confirm while executing):** The exact set of fieldErrors for step 3 (e.g. whether titleEn is reported) follows MealService.validate; titleEn is not required, descriptions in English are not required.

### E2E-COOK-102 — Meal photo upload tickets refuse wrong types, oversize files and spoofed content

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-COOK-A
- **Traces to:** rules: BR-010; operations: POST /api/v1/uploads, POST /api/v1/uploads/{uploadId}/complete; partial: TC-010, TC-113
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Three synthetic files: (a) a real PNG 400x400 px named ok.png; (b) a text file renamed spoof.png whose bytes are not an image; (c) the claim of 9 MiB size (no file needed for the ticket step).
  - Browser console helper from E2E-COOK-048 on the signed-in ops tab; compute sha256 with: Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer())),b=>b.toString(16).padStart(2,'0')).join('')
- **Steps:**
  1. (FX-COOK-A) Request a ticket with a PDF as meal photo: await x('POST','/api/v1/uploads',{purpose:'MEAL_PHOTO',filename:'a.pdf',contentType:'application/pdf',bytes:1000,sha256:'<64 hex>'}) — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-COOK-A) Request a ticket claiming 9437184 bytes (9 MiB) with contentType 'image/png'.
  3. (FX-COOK-A) Request a ticket with sha256:'xyz'.
  4. (FX-COOK-A) Request a valid ticket for spoof.png (contentType 'image/png', its real byte size and sha256), PUT the file bytes to the returned uploadUrl with the header from requiredHeaders (Content-Type: image/png), then POST /api/v1/uploads/<id>/complete with body {}. — data: The file content is not a PNG.
  5. (FX-COOK-A) Request a valid ticket for ok.png, PUT it, complete it. Then PATCH or create a meal with photos:[{mediaId:<spoof id>}].
  6. (FX-COOK-A) Request 11 valid tickets in a row without uploading any of them. — data: Pending tickets are limited to 10.
- **Expected:**
  - API: Step 1: 422 VALIDATION_FAILED fieldErrors [{field:'contentType', code:'UNSUPPORTED_TYPE'}].
  - API: Step 2: 422 with {field:'bytes', code:'SIZE_LIMIT'}.
  - API: Step 3: 422 with {field:'sha256', code:'INVALID_SHA256'}.
  - API: Step 4: complete -> 200 with state 'REJECTED' and rejectionReason 'UNSUPPORTED_TYPE'; the object is never served publicly.
  - API: Step 5: the ok.png upload -> state 'APPROVED'; using the spoof id as a photo -> 422 {field:'photos', code:'PHOTO_NOT_APPROVED'}.
  - API: Step 6: the 11th ticket -> 429 RATE_LIMITED.
  - DB: SELECT state, rejection_reason FROM media_asset WHERE original_filename = 'spoof.png' -> REJECTED, UNSUPPORTED_TYPE; public_key IS NULL.
  - DB: SELECT count(*) FROM media_asset WHERE owner_id = <cook id> AND state = 'PENDING_UPLOAD' AND ticket_expires_at > now() -> 10 after step 6.
- **Also covered by automated tests:** MediaIntegrationTest (spoofed_types_are_rejected_and_never_published, oversize_size_mismatch_checksum_and_dimension_violations_are_rejected, pending_ticket_count_is_limited)
- **Unverified against code (confirm while executing):** The JSON name of the rejection field in the MediaStatus response (rejectionReason) was read from the DB column, not from the OpenAPI schema; check it.; Oversize of an actual 9 MiB upload (SIZE_MISMATCH / storage-side refusal) is not exercised here.

### E2E-COOK-103 — Changing a meal price records price history; description-only edits add no entry; another cook gets 404

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-COOK-B
- **Fixtures to provision:** FX-COOK-B
- **Traces to:** scenarios: TC-153; rules: BR-012, BR-006; decisions: D14; operations: GET /api/v1/cook/meals/{mealId}/price-history, PATCH /api/v1/cook/meals/{mealId}; partial: TC-012, TC-006
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Meal 'محشي تجريبي' (Preview mahshi, 00000000-0000-4000-8000-0000000bb002, 180.00 EGP) is PUBLISHED in the seed. Restore its price to 180.00 at the end.
  - FX-COOK-B (PROVISION by deploy/web-preview/seed-owner-e2e.sql): login owner-e2e-cook-b, re-pointed to the seeded second kitchen 'مطبخ تجريبي تاني' (id 00000000-0000-4000-8000-0000000bd002, 1 meal, one OPEN slot from seed-preview.sql). Verify: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1' returns that kitchen, ACTIVE. Never create FX-COOK-B by approving FX-COOK-PENDING: that fixture is consumed only by E2E-COOK-049.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: https://ops.<PREVIEW_DOMAIN> > Meals > card 'محشي تجريبي' > button 'Price history' (ar 'سجل الأسعار').
  2. (FX-COOK-A) In the opened section type New price (EGP) '190.00' and click 'Change price' (ar 'تغيير السعر'). Repeat with '195.50'.
  3. (FX-COOK-A) Console: await x('PATCH','/api/v1/cook/meals/00000000-0000-4000-8000-0000000bb002',{descriptionAr:'محشي كرنب وورق عنب بالطريقة البيتية',expectedVersion:<meal version from GET /api/v1/cook/meals>}) — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  4. (FX-COOK-A) Reopen 'Price history' (close and open).
  5. (FX-COOK-B) FX-COOK-B console: await x('GET','/api/v1/cook/meals/00000000-0000-4000-8000-0000000bb002/price-history')
  6. (FX-COOK-A) FX-COOK-A: change the price back to '180.00' with 'Change price'.
- **Expected:**
  - UI: Step 1: section shows 'Active price: 180.00 EGP - since <time>' and 'No earlier price changes.' (ar 'لا توجد تغييرات سابقة في السعر.') plus the note 'A new price applies to orders not yet placed; existing orders keep their agreed prices.'.
  - UI: Step 4: 'Active price' 195.50; list 'Previous prices' newest first: 190.00, 180.00, each with an Africa/Cairo time; no extra row for the description edit.
  - API: Step 3: PATCH 200 (new revision, price unchanged).
  - API: Step 4: GET /api/v1/cook/meals/{id}/price-history -> 200 activePrice {amountMinor:19550,currency:'EGP'}, changes (newest first) with prices 19550, 19000, 18000.
  - API: Step 5: 404 NOT_FOUND.
  - DB: SELECT revision, price_minor FROM meal_revision WHERE meal_id = '00000000-0000-4000-8000-0000000bb002' ORDER BY revision -> checked before step 6: price sequence 18000, 19000, 19550 and an extra revision with unchanged 19550 for the description edit (revisions are immutable); step 6 adds a revision with 18000.
  - DB: SELECT price_minor, version FROM meal WHERE id = '00000000-0000-4000-8000-0000000bb002' -> 18000 after step 6.
- **Also covered by automated tests:** PriceHistoryIntegrationTest; MealIntegrationTest.edits_create_new_revisions_while_earlier_snapshots_stay_unchanged
- **Unverified against code (confirm while executing):** The ops-web price form validates input with egpToMinor (comma/decimal handling not read).

### E2E-COOK-104 — Pausing or archiving a meal removes it from sale without touching history

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, customer-web · **Actors:** FX-COOK-A, FX-GUEST
- **Traces to:** rules: BR-012; operations: POST /api/v1/cook/meals/{mealId}/publication; partial: TC-012, TC-114
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - A disposable published meal: the one created in E2E-COOK-051 ('طبق قبول تجريبي 2').
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: Meals > card 'طبق قبول تجريبي 2' > 'Pause' (ar 'إيقاف مؤقت').
  2. (FX-GUEST) FX-GUEST: search the title in https://app.<PREVIEW_DOMAIN>.
  3. (FX-COOK-A) FX-COOK-A: the card now offers 'Publish' and 'Archive' (ar 'أرشفة'); click 'Publish', then 'Archive'.
  4. (FX-COOK-A) FX-COOK-A: try to publish again; FX-GUEST: search again.
- **Expected:**
  - UI: Step 1: badge 'Paused' (ar 'متوقف'). Step 3: after Archive the badge is 'Archived' (ar 'مؤرشف') and the card offers no buttons. Step 2 and 4: the dish is not shown to the guest.
  - API: Step 2: GET /api/v1/meals/{id} -> 404 NOT_FOUND while paused/archived. Archived meal PATCH -> 409 STATE_CONFLICT ('archived meals are read-only').
  - DB: SELECT status, archived_at, version FROM meal WHERE id = <id> -> ARCHIVED, archived_at set; meal_revision rows unchanged (count identical before and after).
- **Also covered by automated tests:** MealIntegrationTest (pause_and_republish_and_photo_deletion_is_refused_while_in_use, archive_removes_the_meal_from_sale_without_touching_history)

### E2E-COOK-105 — Slot creation refuses a passed cutoff, overlap, out-of-range numbers and negative or oversized meal capacity

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-COOK-A
- **Traces to:** rules: BR-013, BR-014, BR-015; operations: POST /api/v1/cook/slots; partial: TC-015, TC-115, TC-014
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Seeded OPEN slot tomorrow 13:00-14:00 exists (use its date as <D1>). Helper in the console of the signed-in ops tab.
  - Capacity cannot be reduced in the ops web at all (the Slots screen only opens, pauses and closes a slot), so all payloads are sent with the console helper.
- **Steps:**
  1. (FX-COOK-A) Cutoff already passed: POST /api/v1/cook/slots with localDate = today (Cairo), localStart = the current Cairo time + 30 minutes, localEnd = + 90 minutes, maxPortions 5, leadMinutes 120 (cutoff = start - 120 min is in the past). — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-COOK-A) Overlap: localDate <D1>, localStart '13:30', localEnd '15:00', maxPortions 5.
  3. (FX-COOK-A) Ranges: maxPortions 501; then maxPortions 0; then leadMinutes 20; then leadMinutes 1441 (use a free future interval each time).
  4. (FX-COOK-A) End not after start: localStart '17:00', localEnd '17:00'.
  5. (FX-COOK-A) Wrong timezone: add timezone:'UTC'.
  6. (FX-COOK-A) Negative meal capacity: mealCapacities:[{mealId:'00000000-0000-4000-8000-0000000bb001',capacity:-1}] on a free interval, maxPortions 5.
  7. (FX-COOK-A) Capacity above total: mealCapacities:[{mealId:'00000000-0000-4000-8000-0000000bb001',capacity:6}] with maxPortions 5.
  8. (FX-COOK-A) Date beyond 60 days: localDate = today + 90 days.
- **Expected:**
  - API: Step 1: 409 SLOT_CLOSED with fieldErrors [{field:'localStart', code:'CUTOFF_PASSED'}].
  - API: Step 2: 409 STATE_CONFLICT with {field:'localStart', code:'OVERLAPS_EXISTING_SLOT'}.
  - API: Step 3: 422 VALIDATION_FAILED - the contract bounds (501, 0, 20, 1441) are rejected either by request validation or with codes RANGE_1_500 / RANGE_30_1440.
  - API: Step 4: 422 {field:'localEnd', code:'END_NOT_AFTER_START'}.
  - API: Step 5: 422 {field:'timezone', code:'ONLY_AFRICA_CAIRO'} (or enum rejection).
  - API: Step 6: 422 VALIDATION_FAILED (bean validation @Min(0)).
  - API: Step 7: 422 {field:'mealCapacities', code:'CAPACITY_0_TO_MAX_PORTIONS'}.
  - API: Step 8: 422 {field:'localDate', code:'MAX_60_DAYS_AHEAD'}.
  - DB: SELECT count(*) FROM cook_slot WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001' -> unchanged from before the case (no partial slot rows). SELECT count(*) FROM meal_slot for the kitchen's slots unchanged.
- **Also covered by automated tests:** SlotIntegrationTest (server_time_reaching_cutoff_closes_ordering_and_capacity_edits, a_local_time_skipped_by_the_spring_change_is_rejected, a_repeated_local_time_needs_an_explicit_offset_and_maps_to_one_instant)
- **Unverified against code (confirm while executing):** Whether range violations (501, 0, 20, 1441) are caught by the OpenAPI/bean validation layer or the service is not determined; either way the status is 422 and VALIDATION_FAILED, the sub-code may differ.

### E2E-COOK-106 — A slot's capacity cannot be reduced below portions already held by orders

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api, operations-web · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** scenarios: TC-013; rules: BR-013; operations: PATCH /api/v1/cook/slots/{slotId}, POST /api/v1/orders; partial: TC-115, TC-006
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - A fresh slot created by FX-COOK-A as in E2E-COOK-052 (maxPortions 12, 6 per meal, state OPEN) so existing seed slots are untouched.
  - FX-CUST-A (seeded, verified phone) places an order for 5 portions of 'كشري تجريبي' in that slot (quantity 5 in the cart; the per-line limit of the synthetic policy is 10), mode Pickup, Cash on receipt.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-CUST-A) FX-CUST-A: https://app.<PREVIEW_DOMAIN> > dish 'كشري تجريبي' > pick the new slot > quantity 5 > Add to cart > cart > 'Pickup from the kitchen' > choose 'Cash on receipt' under 'How you pay' > 'Calculate total' > tick the checkbox 'I accept the terms and the cancellation policy' (ar 'أوافق على الشروط وسياسة الإلغاء'; Place order stays disabled until ticked), click 'Place order'. — data: Total 375.00 EGP (5 x 75.00, zero tax and fee in the synthetic policy).
  2. (FX-COOK-A) FX-COOK-A console (helper from E2E-COOK-048): await x('GET','/api/v1/cook/slots?limit=50') and note slot id, version and the meal capacity. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  3. (FX-COOK-A) Reduce the meal capacity below obligations: await x('PATCH','/api/v1/cook/slots/<id>',{mealCapacities:[{mealId:'00000000-0000-4000-8000-0000000bb001',capacity:4},{mealId:'00000000-0000-4000-8000-0000000bb002',capacity:6}],expectedVersion:<version>})
  4. (FX-COOK-A) Reduce the total below obligations: x('PATCH',...,{maxPortions:4,expectedVersion:<version>})
  5. (FX-COOK-A) Reduce to exactly the obligation: x('PATCH',...,{mealCapacities:[{mealId:'00000000-0000-4000-8000-0000000bb001',capacity:5},{mealId:'00000000-0000-4000-8000-0000000bb002',capacity:6}],maxPortions:11,expectedVersion:<version>})
  6. (FX-COOK-A) Cancel the slot with obligations: x('PATCH',...,{state:'CANCELLED',expectedVersion:<version>})
  7. (FX-COOK-A) FX-COOK-A: ops web > Slots > the slot card.
- **Expected:**
  - UI: Step 7: the card shows '5 of 11 portions' (reserved/allocated + consumed over the new maximum) and state 'Open'.
  - API: Step 3: 409 STATE_CONFLICT fieldErrors [{field:'mealCapacities', code:'BELOW_OBLIGATIONS'}]; slot unchanged.
  - API: Step 4: 409 STATE_CONFLICT [{field:'maxPortions', code:'BELOW_OBLIGATIONS'}].
  - API: Step 5: 200, maxPortions 11, meals capacity 5/6.
  - API: Step 6: 409 STATE_CONFLICT ('slot has order obligations').
  - DB: SELECT max_portions, version FROM cook_slot WHERE id = <id> -> 11 after step 5 (12 before; unchanged by the failed calls).
  - DB: SELECT capacity FROM meal_slot WHERE cook_slot_id = <id> AND meal_id = '00000000-0000-4000-8000-0000000bb001' -> 5; reserved/allocated counters for the meal slot still equal the 5 ordered portions (reconcile query infra/db/reconcile.sql prints 0 problems).
- **Also covered by automated tests:** SlotIntegrationTest.capacity_cannot_be_reduced_below_what_orders_already_hold
- **Unverified against code (confirm while executing):** Whether the cook slot card counts reserved (not yet accepted) portions in '5 of 11 portions': the Slots screen sums reserved + allocated + consumed (Slots.tsx line 58).

### E2E-COOK-107 — Cook pause and resume work; a pause set by staff cannot be undone by the cook

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-COOK-A, FX-STAFF-OPS · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-OPS, FX-ISOLATED
- **Traces to:** rules: BR-009; operations: PATCH /api/v1/cook/settings, POST /api/v1/ops/kitchens/{kitchenId}/operating-state; partial: TC-009, TC-122
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm. (steps 4-6 only; steps 1-3 need no staff MFA)
  - Kitchen A is ACTIVE at the start and is restored at the end.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
  - Run on a disposable stack (FX-ISOLATED): this case changes shared seeded state (kitchen A operating state / payment recipient / administrator grants); on any shared preview it would disturb other cases. Restoration steps are listed, but the stack is discarded afterwards.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: open https://ops.<PREVIEW_DOMAIN> > Settings (ar 'الإعدادات'). Click 'Pause new sales' (ar 'إيقاف البيع مؤقتًا').
  2. (FX-COOK-A) Click 'Resume sales' (ar 'استئناف البيع').
  3. (FX-COOK-A) Click 'Pause new sales' again and leave the kitchen paused by the cook.
  4. (FX-STAFF-OPS) FX-STAFF-OPS: 'Cook applications' > 'Approved' > 'مطبخ المعاينة' > reason 'مراجعة جودة' > 'Suspend' (allowed from PAUSED). If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  5. (FX-STAFF-OPS) FX-STAFF-OPS: press 'Restore' with reason 'انتهت المراجعة', then 'Pause' with reason 'إيقاف إداري مؤقت'. — data: Now the kitchen is paused BY STAFF (restore first so it is ACTIVE, then pause).
  6. (FX-COOK-A) FX-COOK-A: reload Settings. Then in the console (helper as in E2E-COOK-048) run: await x('PATCH','/api/v1/cook/settings',{paused:false,expectedVersion:<kitchen.version from GET /api/v1/cook/dashboard>}) — data: Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  7. (FX-STAFF-OPS) FX-STAFF-OPS: 'Restore' with reason 'انتهى الإيقاف'.
- **Expected:**
  - UI: Step 1: Settings heading 'Paused' (ar 'متوقف مؤقتًا') and the button 'Resume sales' appears.
  - UI: Step 2: heading 'Active' (ar 'نشط') and 'Pause new sales' returns.
  - UI: Step 5-6: heading 'Paused', the text 'Paused by operations; you cannot resume it.' (ar 'أوقفته الإدارة مؤقتًا؛ لا يمكنك استئنافه.') and NO 'Resume sales' button.
  - API: Step 1-3: PATCH /api/v1/cook/settings {paused:true|false,expectedVersion} -> 200 operatingState 'PAUSED' / 'ACTIVE', pausedBy 'COOK' while paused.
  - API: Step 6: PATCH {paused:false} -> 409 STATE_CONFLICT with fieldErrors [{field:'operatingState', code:'PAUSED'}]; state remains PAUSED.
  - API: Customer side while paused: POST /api/v1/quotes for kitchen A -> 409 KITCHEN_UNAVAILABLE; GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 -> 404.
  - DB: SELECT operating_state, paused_by FROM kitchen WHERE id = '00000000-0000-4000-8000-0000000bd001' -> PAUSED/COOK (step 3), PAUSED/STAFF (step 5), ACTIVE/NULL (end).
  - DB: audit_event: kitchen.settings rows by the cook (details.operatingState) and kitchen.operating_state rows by staff (ALLOWED, with from/to).
- **Also covered by automated tests:** CookWorkspaceIntegrationTest.cook_pause_and_resume_stop_and_restore_new_sales_but_not_a_staff_pause; CookWorkflowIntegrationTest.only_scoped_staff_change_operating_state_and_never_for_their_own_kitchen
- **Unverified against code (confirm while executing):** Whether state SUSPENDED -> PAUSED staff transition exists: the code has no such action (PAUSE only from ACTIVE), so step 4 ends in SUSPENDED, not PAUSED; the case therefore restores before pausing.

### E2E-COOK-108 — A changed deposit recipient is hidden from customers until finance staff approve it (zero platform fee, direct kitchen account)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-A, FX-CUST-A, FX-STAFF-FINANCE, FX-STAFF-OPS · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-STAFF-FINANCE, FX-STAFF-OPS, FX-ISOLATED
- **Traces to:** rules: BR-019, BR-033; decisions: D16, D10; operations: PUT /api/v1/cook/payment-profile, GET /api/v1/ops/payment-recipients, POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval, POST /api/v1/quotes; partial: TC-163, TC-158
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-STAFF-FINANCE (PROVISION): finance staff with the grants of fixtures.json / seed-owner-e2e.sql: finance.read, finance.verify, finance.refund_approve, finance.settlement, config.read. Not in seed-preview.sql. Verify only the grants the case needs (for example finance.read, finance.verify).
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - Seed state: SELECT deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_status FROM kitchen_payment_profile WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001' -> 5000, NULL, INSTAPAY, demo-kitchen@instapay.invalid, APPROVED (seed-preview.sql, 'approved' by the seeded reviewer row without MFA).
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm. (step 7 only)
  - Only fake recipient values with the .invalid domain are used; no money moves in the preview.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
  - Run on a disposable stack (FX-ISOLATED): this case changes shared seeded state (kitchen A operating state / payment recipient / administrator grants); on any shared preview it would disturb other cases. Restoration steps are listed, but the stack is discarded afterwards.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: https://ops.<PREVIEW_DOMAIN> > Settings > scroll to section 'Payment' (ar 'الدفع'). Read the Deposit (%) field and the status line under 'Account for deposits'.
  2. (FX-CUST-A) FX-CUST-A: https://app.<PREVIEW_DOMAIN> > dish 'كشري تجريبي' > tomorrow's slot > quantity 1 > Add to cart > cart > 'Pickup from the kitchen' > under 'How you pay' choose 'Deposit sent straight to the kitchen' > 'Calculate total'.
  3. (FX-COOK-A) FX-COOK-A: change 'Account address or number' to 'demo-kitchen-2@instapay.invalid' and 'Account holder name' to 'Demo kitchen 2 (not a real account)'; leave method InstaPay and Deposit 50; click 'Save' (ar 'حفظ').
  4. (FX-CUST-A) FX-CUST-A: in the same cart press 'Calculate total' again with the deposit option selected, then switch to 'Cash on receipt' and press it again.
  5. (FX-STAFF-OPS) FX-STAFF-OPS (no finance.verify): console GET /api/v1/ops/payment-recipients. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  6. (FX-STAFF-FINANCE) FX-STAFF-FINANCE: https://ops.<PREVIEW_DOMAIN> > tab 'Finance' (ar 'المالية'): panel 'Accounts waiting for approval' (ar 'حسابات تنتظر الموافقة') shows the row. Type reason 'قصير' (5 characters) - the button 'Approve account' (ar 'موافقة على الحساب') stays disabled; then type 'تم التحقق من بيانات الحساب التجريبي' and click it. If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  7. (FX-COOK-A) FX-COOK-A: reload Settings. FX-CUST-A: repeat the deposit quote.
- **Expected:**
  - UI: Step 1: Deposit (%) 50; status line 'Staff approved this account.' (ar 'تمت موافقة الإدارة على الحساب.'; data-testid recipient-status).
  - UI: Step 2: the cart shows 'Deposit now: 37.50' and 'Rest on receipt: 37.50' (75.00 EGP x 50%) and 'Send the deposit before <time>.'.
  - UI: Step 3 right after saving: status line 'Waiting for staff approval; deposits are not offered to customers until then.' (ar 'في انتظار موافقة الإدارة؛ لن يُعرض العربون على العملاء قبلها.').
  - UI: Step 4: deposit option -> message 'This kitchen does not take deposits at the moment. Choose cash on receipt.' (ar from pay.depositNotOffered); cash on receipt still calculates a total of 75.00 EGP with no platform fee line.
  - UI: Step 6: 'Approve account' is disabled until the reason has at least 10 characters; after approval the row disappears and the panel says 'No accounts are waiting for approval.' (ar 'لا توجد حسابات تنتظر الموافقة.'); the tab 'Finance' is visible only because FX-STAFF-FINANCE also holds finance.read/refund_approve/settlement.
  - UI: Step 7: cook status 'Staff approved this account.'; the customer quote shows the deposit again, paid to 'demo-kitchen-2@instapay.invalid'.
  - API: Step 3: PUT /api/v1/cook/payment-profile {expectedVersion,depositBps:5000,codMaxTotalMinor:null,recipient:{method:'INSTAPAY',identifier,name}} -> 200 with recipientStatus 'NONE'.
  - API: Step 4: POST /api/v1/quotes with paymentMethod 'DEPOSIT' -> 422 VALIDATION_FAILED fieldErrors [{field:'paymentMethod', code:'DEPOSIT_NOT_OFFERED'}]; with 'COD' -> 201.
  - API: Step 5: GET /api/v1/ops/payment-recipients -> 403 FORBIDDEN (needs finance.verify).
  - API: Step 6: POST /api/v1/ops/kitchens/00000000-0000-4000-8000-0000000bd001/payment-recipient/approval {expectedVersion,reason} -> 403 STEP_UP_REQUIRED first, then 200 recipientStatus 'APPROVED'; a reason shorter than 10 characters -> 422 LENGTH_10_500.
  - DB: SELECT recipient_identifier, recipient_status, approved_by, approved_at, version FROM kitchen_payment_profile WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001' -> NONE with approved_by NULL after step 3; APPROVED, approved_by = FX-STAFF-FINANCE id, approved_at set after step 6.
  - DB: SELECT action, permission, outcome, reason FROM audit_event WHERE object_type = 'kitchen' AND object_id = '00000000-0000-4000-8000-0000000bd001' AND action LIKE 'payment.%' ORDER BY occurred_at -> payment.profile_update (cook, details recipientChanged true) then payment.recipient_approve (finance.verify, ALLOWED, the typed reason).
- **Also covered by automated tests:** DepositPaymentIntegrationTest; apps/operations-web/src/App.test.tsx (mocked)
- **Unverified against code (confirm while executing):** The exact wording of the customer-web options was taken from apps/customer-web/src/i18n.ts; element roles (radio vs select) were not read.; A staff member holding ONLY finance.verify cannot use this screen: the Finance tab is shown only when GET /api/v1/ops/finance/statements answers 404 (App.tsx), which needs finance.read, finance.settlement or finance.refund_approve. See findings.

### E2E-COOK-109 — The cook's free insights count one visit per visitor and day, ignore crawlers, show only the own kitchen and never claim profit

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api, customer-web · **Actors:** FX-COOK-A, FX-GUEST, FX-COOK-B, FX-CUST-A
- **Traces to:** rules: BR-019; decisions: D17; operations: GET /api/v1/cook/insights, POST /api/v1/activity; partial: TC-164
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-COOK-B (PROVISION by deploy/web-preview/seed-owner-e2e.sql): login owner-e2e-cook-b, re-pointed to the seeded second kitchen 'مطبخ تجريبي تاني' (id 00000000-0000-4000-8000-0000000bd002, 1 meal, one OPEN slot from seed-preview.sql). Verify: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1' returns that kitchen, ACTIVE. Never create FX-COOK-B by approving FX-COOK-PENDING: that fixture is consumed only by E2E-COOK-049.
  - A shell with curl on the tester's machine (the activity endpoint needs no cookie or CSRF token), the meal id 00000000-0000-4000-8000-0000000bb001, and the visitor ids below (8-64 characters).
  - Cairo day boundary: do not run across 00:00 Africa/Cairo.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: https://ops.<PREVIEW_DOMAIN> > tab 'Insights' (ar 'الأداء'). Set From and To (ar 'من' / 'إلى') both to today's date and click 'Show' (ar 'عرض'). Note 'Dish page views' (ar 'مشاهدات صفحات الأطباق') = V0.
  2. (FX-COOK-A) Send the same event three times: curl -s -o /dev/null -w '%{http_code}\n' -X POST https://api.<PREVIEW_DOMAIN>/api/v1/activity -H 'Content-Type: application/json' -H 'User-Agent: Mozilla/5.0 e2e' -d '{"visitorId":"e2e-visitor-0001","events":[{"kind":"MEAL_VIEW","subjectId":"00000000-0000-4000-8000-0000000bb001"}]}' — data: Three identical requests.
  3. (FX-COOK-A) Send one event with another visitor: same command with "visitorId":"e2e-visitor-0002".
  4. (FX-COOK-A) Send one event as a declared crawler: same command with -H 'User-Agent: ExampleBot/1.0' and "visitorId":"e2e-visitor-0003".
  5. (FX-COOK-A) FX-COOK-A: press 'Show' again.
  6. (FX-COOK-B) FX-COOK-B: https://ops.<PREVIEW_DOMAIN> > 'Insights' with the same dates.
  7. (FX-CUST-A) FX-CUST-A console: await x('GET','/api/v1/cook/insights') (helper as in E2E-COOK-048). — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
- **Expected:**
  - UI: Step 5: 'Dish page views' = V0 + 2 (visitors 0001 and 0002 count once each; the crawler counts 0).
  - UI: Step 5: the notice 'Free figures for your kitchen only... The net is not profit: the platform does not know your costs.' and under 'Money' the line 'This is not profit; no costs are included.' (ar 'هذا ليس ربحًا؛ لا تُحتسب أي تكاليف.') are visible; there is no button to buy, boost or sponsor anything.
  - UI: Step 6: kitchen B's 'Dish page views' does not include the events for kitchen A's meal.
  - API: Steps 2-4: every request answers 202 with an empty body (the response never reveals whether an event counted).
  - API: Step 5: GET /api/v1/cook/insights?from=<today>&to=<today> -> activity.mealViews = V0 + 2; orders/portions/money figures come from order records and are unaffected.
  - API: Step 7: 403 FORBIDDEN (not a cook).
  - DB: SELECT kind, count(*) FROM activity_daily WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001' AND day = (now() AT TIME ZONE 'Africa/Cairo')::date GROUP BY kind -> MEAL_VIEW rows increased by exactly 2; visitor_hash is a 32-byte value, no readable id (SELECT length(visitor_hash) -> 32).
- **Also covered by automated tests:** ActivityInsightsIntegrationTest (activity_is_deduplicated_per_visitor_and_day_bots_are_ignored_and_no_visitor_identity_is_stored, the_cook_report_keeps_gross_refunds_and_known_net_apart_from_server_records_and_claims_no_profit, only_a_cook_sees_insights_and_only_for_the_own_kitchen)
- **Unverified against code (confirm while executing):** The window in which the first request of the day is counted uses the server's Cairo date; V0 may already include other testers' traffic, therefore deltas are asserted, not absolute counts.

### E2E-COOK-110 — A cook account that asks for deletion is disabled at once and then held for staff review instead of being erased

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Isolated disposable stack only
- **Surface:** customer-web, api · **Actors:** FX-COOK-B · **Needs:** isolated stack · **Destructive**
- **Fixtures to provision:** FX-COOK-B, FX-ISOLATED
- **Traces to:** rules: BR-055; operations: POST /api/v1/me/deletion-requests; partial: TC-055
- **Prerequisites:**
  - FX-ISOLATED: a disposable Docker stack (deploy/web-preview or scripts/dev.sh) started with PRIVACY_DELETION_GRACE=PT1M so the grace period is one minute; the erasure job runs every 15 minutes (matbakhy.privacy.erasure-job.interval default 15m, @Scheduled fixedDelay), so allow up to 16 minutes. Destructive: the account becomes unusable.
  - FX-COOK-B (PROVISION by deploy/web-preview/seed-owner-e2e.sql): login owner-e2e-cook-b, re-pointed to the seeded second kitchen 'مطبخ تجريبي تاني' (id 00000000-0000-4000-8000-0000000bd002, 1 meal, one OPEN slot from seed-preview.sql). Verify: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000b1' returns that kitchen, ACTIVE. Never create FX-COOK-B by approving FX-COOK-PENDING: that fixture is consumed only by E2E-COOK-049.
- **Steps:**
  1. (FX-COOK-B) FX-COOK-B: https://app.<PREVIEW_DOMAIN> > 'My account' (ar 'حسابي') > 'Request account deletion' (ar 'طلب حذف الحساب'): tick 'I confirm I want my account deleted' and confirm.
  2. (FX-COOK-B) Reload https://ops.<PREVIEW_DOMAIN> and, in its console, run await x('GET','/api/v1/cook/dashboard'). — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  3. (FX-COOK-B) Wait at least 16 minutes. As an operator run the read-only SQL listed under database.
- **Expected:**
  - UI: Step 1: message 'Deletion request received; the account is disabled.'.
  - UI: Step 2: the ops web falls back to the signed-out or error state; the API call answers 403 ACCOUNT_DISABLED.
  - API: Step 1: POST /api/v1/me/deletion-requests {confirm:true,policyVersion:'retention-policy-draft'} -> 200. Step 2: 403 ACCOUNT_DISABLED. The account is not erased.
  - DB: SELECT status FROM app_user WHERE id = <cook id> -> DELETION_REQUESTED (not DELETED).
  - DB: SELECT state, legal_hold_reason FROM deletion_request WHERE user_id = <cook id> -> ON_HOLD, COOK_ACCOUNT_REVIEW.
  - DB: SELECT count(*) FROM kitchen WHERE owner_id = <cook id> -> 1 and display_name, contact_phone still populated (nothing erased).
  - DB: SELECT outcome, reason FROM audit_event WHERE action = 'account.erasure' AND object_id = '<cook id>' -> DENIED, COOK_ACCOUNT_REVIEW.
- **Also covered by automated tests:** AccountErasureIntegrationTest.a_cook_account_is_put_on_hold_for_staff_review_and_keeps_its_data
- **Unverified against code (confirm while executing):** There is no ops-web screen or API to review or release an ON_HOLD deletion request; what 'staff review' means operationally is undefined in the code (see findings).; The exact request body the customer-web sends (policyVersion string) comes from AccountErasureIntegrationTest.

### E2E-COOK-111 — Request changes, resubmit as revision 2, then reject: history is preserved and no kitchen is ever created

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-OPS, FX-CUST-NEW · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-OPS, FX-CUST-NEW
- **Traces to:** rules: BR-008; operations: POST /api/v1/ops/cooks/{applicationId}/decision, PUT /api/v1/cook/application; partial: TC-112, TC-008
- **Prerequisites:**
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - A second SUBMITTED application: use FX-CUST-NEW after E2E-COOK-048 (state SUBMITTED, revision 1). Do not use the account approved in E2E-COOK-049.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-OPS) As FX-STAFF-OPS open https://ops.<PREVIEW_DOMAIN> > 'Cook applications' > 'Submitted' > 'مطبخ القبول' (the FX-CUST-NEW application) and click 'Start review'. If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  2. (FX-STAFF-OPS) Type reason 'الصورة الشخصية والعنوان غير واضحين' and click 'Request changes' (ar 'طلب تعديلات').
  3. (FX-CUST-NEW) As FX-CUST-NEW run in the console (helper from E2E-COOK-048): await x('GET','/api/v1/cook/application') — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  4. (FX-CUST-NEW) Run PUT /api/v1/cook/application with publicName:'مطبخ القبول المعدل' and the version from step 3 as expectedVersion, then POST /api/v1/cook/application/submit with the new version.
  5. (FX-STAFF-OPS) As FX-STAFF-OPS open 'Cook applications' > 'Submitted' > the application (now 'Revision 2') > 'Start review', then reason 'مرفوض لعدم استيفاء الشروط' > 'Reject' (ar 'رفض').
  6. (FX-CUST-NEW) As FX-CUST-NEW run: await x('PUT','/api/v1/cook/application',{publicName:'محاولة بعد الرفض',expectedVersion:<current version>}) and reload https://ops.<PREVIEW_DOMAIN>
- **Expected:**
  - UI: Step 1-2: the heading changes Submitted > Under review > 'Changes requested' (ar 'مطلوب تعديلات'); no 'Kitchen' card appears.
  - UI: Step 5: heading 'Rejected' (ar 'مرفوض'); no decision buttons remain.
  - UI: Step 6: FX-CUST-NEW still sees 'This account has no approved kitchen yet.' and 'Application status: REJECTED'.
  - API: Step 3: state 'CHANGES_REQUESTED', history[0] = {revision:1, decision:'CHANGES_REQUESTED', decisionReason:'الصورة الشخصية والعنوان غير واضحين'} - the applicant sees the reason but not the reviewer identity.
  - API: Step 4: PUT 200 (editable in CHANGES_REQUESTED); submit 200 state SUBMITTED currentRevision 2.
  - API: Step 6: PUT -> 409 STATE_CONFLICT (REJECTED is final).
  - DB: SELECT revision, decision, decision_reason, reviewer_id FROM cook_application_revision WHERE application_id = <id> ORDER BY revision -> (1, CHANGES_REQUESTED, ..., staff id), (2, REJECTED, 'مرفوض لعدم استيفاء الشروط', staff id).
  - DB: SELECT count(*) FROM kitchen WHERE application_id = <id> -> 0.
  - DB: audit_event: two cook.application.decision START_REVIEW rows and one each for REQUEST_CHANGES and REJECT, all ALLOWED, details.revision 1 / 2.
- **Also covered by automated tests:** CookWorkflowIntegrationTest (changes_requested_then_resubmitted_then_approved_preserves_every_revision, rejection_preserves_history_and_never_creates_a_kitchen)
- **Unverified against code (confirm while executing):** Revision immutability (DB trigger cook_revision_guard) is not exercised here because the tester has no write access; it is covered by CookWorkflowIntegrationTest.submitted_revisions_are_immutable_in_the_database.

### E2E-COOK-112 — Kitchen settings keep the kitchen serviceable and refuse unapproved zones and stale edits

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** operations-web, api · **Actors:** FX-COOK-A
- **Traces to:** rules: BR-009; operations: PATCH /api/v1/cook/settings, GET /api/v1/cook/dashboard; partial: TC-122
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Kitchen A is approved for pickup and delivery to 'Test zone A' (ar 'منطقة تجريبية أ', id 00000000-0000-4000-8000-00000000a001) only (seed-preview.sql kitchen_zone). Record the original description to restore it.
- **Steps:**
  1. (FX-COOK-A) FX-COOK-A: https://ops.<PREVIEW_DOMAIN> > Settings. Untick 'Pickup from the kitchen' (ar 'الاستلام من المطبخ') leaving the zone ticked, click 'Save'.
  2. (FX-COOK-A) Untick the zone 'Test zone A' too (pickup still unticked) and click 'Save'.
  3. (FX-COOK-A) Tick 'Pickup from the kitchen' again, replace the description by 'أكل بيتي تجريبي - تعديل' and Save.
  4. (FX-COOK-A) Open Settings in a second browser tab (tab 2), change the description in tab 1 and Save, then change it in tab 2 and Save.
  5. (FX-COOK-A) Console: await x('PATCH','/api/v1/cook/settings',{deliveryZones:[{zoneId:'00000000-0000-4000-8000-00000000a002',deliveryEnabled:true}],expectedVersion:<current>}) — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  6. (FX-COOK-A) Restore the original description and Save.
- **Expected:**
  - UI: Step 1: 'Saved' (ar 'تم الحفظ') appears or the form reloads without error. Step 2: error 'Check the fields.' (ar 'تحقق من الحقول.'). Step 4 tab 2: error 'The data changed. Refreshed; please try again.' (ar from errors.VERSION_CONFLICT) and the form shows tab 1's description.
  - API: Step 2: 422 VALIDATION_FAILED fieldErrors [{field:'pickupEnabled', code:'PICKUP_OR_ZONE_REQUIRED'}].
  - API: Step 4 (tab 2): 409 VERSION_CONFLICT.
  - API: Step 5: 422 [{field:'deliveryZones', code:'ZONE_NOT_APPROVED'}] (kitchen A was not approved for Test zone B).
  - DB: SELECT description, pickup_enabled, version FROM kitchen WHERE id = '00000000-0000-4000-8000-0000000bd001'; SELECT delivery_enabled FROM kitchen_zone WHERE kitchen_id = '00000000-0000-4000-8000-0000000bd001' -> pickup true and zone enabled after step 3; audit_event action 'kitchen.settings' one ALLOWED row per successful save.
- **Also covered by automated tests:** CookWorkspaceIntegrationTest.settings_validate_serviceability_zones_and_versions_and_update_the_public_profile

### E2E-OPS-101 — An administrator cannot grant themselves a permission; unknown permissions, past expiry and duplicate grants are refused

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-ADMIN-B, FX-STAFF-OPS · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-ADMIN-B, FX-STAFF-OPS
- **Traces to:** rules: BR-043, BR-007; operations: POST /api/v1/ops/staff/{userId}/grants; partial: TC-043, TC-126, TC-007
- **Prerequisites:**
  - MFA-gated grants/revokes are done by FX-STAFF-ADMIN-B in its own browser profile with a recent MFA proof (preview-ops-* stay password-only, docs/security/mfa-policy.md:35); FX-STAFF-ADMIN is used only for steps without MFA.
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Keep the user ids from GET https://api.<PREVIEW_DOMAIN>/api/v1/ops/staff (own id = FX-STAFF-ADMIN's userId).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B: 'Staff permissions' > own card > Permission 'finance.verify', Reason 'منح ذاتي - اختبار' > 'Grant'. If the session has no MFA proof younger than 10 minutes the action answers 403 STEP_UP_REQUIRED and the screen shows 'This action needs additional verification (MFA).' (ar: 'هذا الإجراء يتطلب تحققًا إضافيًا (MFA).') with a button 'Verify again' (ar: 'إعادة التحقق'); press it, enter the TOTP code from the authenticator app (first time: scan the QR code to enrol), then repeat the action.
  2. (FX-STAFF-ADMIN-B) Console (helper): POST /api/v1/ops/staff/<FX-STAFF-OPS id>/grants with permission 'not.a.permission', reason 'اختبار'. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  3. (FX-STAFF-ADMIN-B) Same with permission 'support.case' and expiresAt '2020-01-01T00:00:00Z'.
  4. (FX-STAFF-ADMIN-B) Same with permission 'cook.review' (FX-STAFF-OPS already holds it), reason 'تكرار'.
  5. (FX-STAFF-ADMIN-B) Same with a random unknown user id 00000000-0000-4000-8000-00000000dead and permission 'support.case'.
- **Expected:**
  - UI: Step 1: the error 'Not allowed.' (ar errors.FORBIDDEN) appears and the card does not list finance.verify.
  - API: Step 1: 403 FORBIDDEN ('self escalation') after the MFA check.
  - API: Step 2: 422 VALIDATION_FAILED fieldErrors [{field:'permission', code:'UNKNOWN_PERMISSION'}].
  - API: Step 3: 422 [{field:'expiresAt', code:'FUTURE'}].
  - API: Step 4: 409 STATE_CONFLICT ('grant already active').
  - API: Step 5: 404 NOT_FOUND.
  - DB: SELECT permission FROM user_grant WHERE user_id = <admin id> AND revoked_at IS NULL -> unchanged (no finance.verify added).
  - DB: SELECT permission, outcome, reason FROM audit_event WHERE action = 'staff.grant' AND actor_id = <admin id> AND outcome = 'DENIED' -> finance.verify, DENIED, 'self-escalation refused'.
- **Also covered by automated tests:** StaffAccessIntegrationTest.self_escalation_unknown_permissions_and_last_admin_are_refused

### E2E-OPS-102 — A revoked or expired grant stops working on the very next request; revoking staff.grant is allowed while another administrator remains

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-STAFF-ADMIN-B, FX-STAFF-ADMIN, FX-STAFF-OPS, FX-STAFF-NOGRANT · **Needs:** updated deploy, isolated stack · **Destructive**
- **Fixtures to provision:** FX-ISOLATED, FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-007, BR-043; operations: POST /api/v1/ops/staff/{userId}/grant-revocations, POST /api/v1/ops/staff/{userId}/grants; partial: TC-007, TC-043, TC-111
- **Prerequisites:**
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - FX-STAFF-ADMIN-B (PROVISION): second administrator with the grants of fixtures.json / seed-owner-e2e.sql: staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset. Not in seed-preview.sql. For E2E-OPS-102 first grant it content.moderate.
  - FX-STAFF-NOGRANT (EXISTS) = preview-ops-2, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f2: staff-style login with no grants. Its app_user row is created at its first sign-in (not in seed-preview.sql); its user id for API calls: SELECT id FROM app_user WHERE subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f2'.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
  - Run on a disposable stack (FX-ISOLATED): this case changes shared seeded state (kitchen A operating state / payment recipient / administrator grants); on any shared preview it would disturb other cases. Restoration steps are listed, but the stack is discarded afterwards.
  - Revoker/grantor is FX-STAFF-ADMIN-B (own browser profile, recent MFA); preview-ops-* stay password-only (docs/security/mfa-policy.md:35). Two administrators hold staff.grant: FX-STAFF-ADMIN-B and FX-STAFF-ADMIN. FX-STAFF-OPS holds content.moderate (seed).
- **Steps:**
  1. (FX-STAFF-OPS) FX-STAFF-OPS (profile 2): open https://ops.<PREVIEW_DOMAIN>, note the tabs ('Catalogue moderation' present) and in the console GET /api/v1/ops/catalogue?limit=1. — data: Helper as in E2E-COOK-048.
  2. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B (profile 1): 'Staff permissions' > card of FX-STAFF-OPS > click 'Revoke' next to content.moderate with reason 'سحب لاختبار الأثر الفوري' (step-up if asked, then repeat).
  3. (FX-STAFF-OPS) FX-STAFF-OPS (no new login): repeat GET /api/v1/ops/catalogue?limit=1, then reload the page.
  4. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B: grant support.case to FX-STAFF-NOGRANT through the console with an expiry three minutes ahead: x('POST','/api/v1/ops/staff/<NOGRANT id>/grants',{permission:'support.case',reason:'اختبار انتهاء الصلاحية',expiresAt:new Date(Date.now()+180000).toISOString()})
  5. (FX-STAFF-NOGRANT) FX-STAFF-NOGRANT: GET /api/v1/ops/cases?limit=1 now, and again after 4 minutes.
  6. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B: card of FX-STAFF-ADMIN > 'Revoke' next to staff.grant, reason 'نهاية الاختبار' (allowed: a second administrator, FX-STAFF-ADMIN-B itself, remains).
  7. (FX-STAFF-ADMIN) FX-STAFF-ADMIN (password-only, no MFA needed for reading): reload the page and GET /api/v1/ops/staff.
  8. (FX-STAFF-ADMIN-B) Restoration: grant staff.grant back to FX-STAFF-ADMIN (reason 'استعادة') and content.moderate back to FX-STAFF-OPS, or discard the isolated stack. — data: Needs a fresh MFA proof.
- **Expected:**
  - UI: Step 3: after the reload the tab 'Catalogue moderation' is gone from FX-STAFF-OPS's navigation.
  - UI: Step 7: after the reload FX-STAFF-ADMIN no longer has the tab 'Staff permissions'.
  - API: Step 1: 200. Step 3: 403 FORBIDDEN on the next request without re-login (permissions are read from the database per request).
  - API: Step 4: 201 with expiresAt set. Step 5: first call 200, call after expiry 403 FORBIDDEN.
  - API: Step 6: revocation -> 200 with revokedAt set; Step 7: FX-STAFF-ADMIN's GET /api/v1/ops/staff -> 403.
  - DB: SELECT permission, revoked_at, revoked_by, revoke_reason, expires_at FROM user_grant WHERE user_id IN (<OPS id>, <ADMIN id>, <NOGRANT id>) ORDER BY granted_at -> revoked rows carry revoked_by = FX-STAFF-ADMIN-B, the expiry row has expires_at in the past and revoked_at NULL; audit_event staff.revoke ALLOWED rows with the reasons.
- **Also covered by automated tests:** StaffAccessIntegrationTest (revoked_grant_stops_working_on_the_next_request, expired_grants_confer_nothing)
- **Unverified against code (confirm while executing):** Access tokens/sessions are not invalidated on revoke; the check relies on grants being read per request (GrantService.activePermissions). Whether the web session cache of authorities is refreshed per request was inferred from StaffAccessIntegrationTest.revoked_grant_stops_working_on_the_next_request, not run.

### E2E-OPS-103 — Journey end: after a customer's order is completed by the kitchen, operations and finance inspect it - totals, timeline, cash state, zero platform commission and masked contact

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A, FX-STAFF-OPS, FX-STAFF-FINANCE, FX-STAFF-ADMIN
- **Fixtures to provision:** FX-STAFF-OPS, FX-STAFF-FINANCE
- **Traces to:** rules: BR-032, BR-033, BR-026, BR-006; decisions: D10; operations: GET /api/v1/ops/orders, GET /api/v1/ops/orders/{orderId}, GET /api/v1/ops/finance/statements, GET /api/v1/ops/audit; partial: TC-157, TC-120, TC-124, TC-122, TC-127
- **Prerequisites:**
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - FX-STAFF-FINANCE (PROVISION): finance staff with the grants of fixtures.json / seed-owner-e2e.sql: finance.read, finance.verify, finance.refund_approve, finance.settlement, config.read. Not in seed-preview.sql. Verify only the grants the case needs (for example finance.read, finance.verify).
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - Seed: kitchen A has an OPEN slot tomorrow 13:00-14:00 with 10 portions of 'كشري تجريبي' (75.00 EGP, 00000000-0000-4000-8000-0000000bb001); the synthetic commerce policy has tax 0, commission 0, acceptance window 15 minutes. FX-CUST-A has a verified phone.
  - No staff second factor is needed anywhere in this case (order.ops, finance.read and audit.read are read-only). Steps 1-2 must finish within 15 minutes of placing the order.
  - Record the order's public number <N> from the customer web.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
  - Only these grants are needed: FX-STAFF-OPS order.ops, FX-STAFF-FINANCE finance.read, FX-STAFF-ADMIN audit.read; extra grants from the seed are irrelevant.
- **Steps:**
  1. (FX-CUST-A) FX-CUST-A: https://app.<PREVIEW_DOMAIN> > dish 'كشري تجريبي' (Preview koshari) > tomorrow's 13:00 slot > quantity 2 > 'Add to cart' > cart > 'Pickup from the kitchen' > choose 'Cash on receipt' under 'How you pay' > 'Calculate total' > tick the checkbox 'I accept the terms and the cancellation policy' (ar 'أوافق على الشروط وسياسة الإلغاء'; Place order stays disabled until ticked), click 'Place order'. — data: Expected total 150.00 EGP (2 x 75.00). The handover code does NOT exist yet: it is created when the cook presses Ready.
  2. (FX-COOK-A) FX-COOK-A (own browser profile): ops web > Orders > 'Active' > the order <N>: click 'Accept', 'Start preparing', then 'Ready'. FX-CUST-A: reload the order page and read the six-digit 'Handover code' (ar 'رمز التسليم'). FX-COOK-A: type it into 'Customer handover code' and click 'Confirm handover' (ar 'تأكيد التسليم'); filter 'Completed' (ar 'المكتملة'). — data: Pickup orders go Accepted > Preparing > Ready > Completed. Steps 1-2 within 15 minutes of placing.
  3. (FX-STAFF-OPS) FX-STAFF-OPS: https://ops.<PREVIEW_DOMAIN> > 'Order monitoring' (ar 'متابعة الطلبات') > filter 'All' (ar 'الكل'). Click the order number <N>.
  4. (FX-STAFF-OPS) FX-STAFF-OPS: in the console GET /api/v1/ops/orders/<order id> (id from the list: open DevTools Network tab or GET /api/v1/ops/orders?limit=5). — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  5. (FX-STAFF-FINANCE) FX-STAFF-FINANCE: https://ops.<PREVIEW_DOMAIN> > 'Finance' (ar 'المالية') > field 'Kitchen id' (ar 'معرّف المطبخ'): paste 00000000-0000-4000-8000-0000000bd001 > 'Show' (ar 'عرض').
  6. (FX-STAFF-ADMIN) FX-STAFF-ADMIN: in the same browser open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?objectType=order&objectId=<order id>&limit=20 and https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?action=access.order_contact&limit=20
- **Expected:**
  - UI: Step 3 list row: '<N> - Completed (ar 'مكتمل') - Pickup from the kitchen - 150.00 EGP - <creation time>' with no exception badge. Detail heading '<N> - Completed'; line 'Total: 150.00 EGP - Cash due' (ar 'النقد مستحق'); NO customer name/phone: the text 'Contact details are not shown in this state.' (ar 'لا تظهر بيانات التواصل في هذه الحالة.'); a 'timeline' list reading, oldest first: 'Waiting for acceptance', 'Waiting for acceptance -> Accepted', '... -> Preparing', '... -> Ready', '... -> Completed' with Africa/Cairo times; no 'Exception cancellation' or 'Handover override' form (they appear only for Preparing/Ready orders).
  - UI: Step 5: the statement lists a line '<N> - 150.00 EGP - Cash due'; the notices 'The cash you collect is yours. Includes refunds and settlements.' and 'No platform commission: the whole order amount is yours.' are shown and no commission rows appear.
  - API: Step 4: 200 OpsOrderView: order.state 'COMPLETED', order.total {amountMinor:15000,currency:'EGP'}, subtotal 15000, deliveryFee 0, tax 0, cashState 'DUE', order.handoverCode null, order.allowedActions [], customerContact null, exceptions [], events in sequence order with the five states above.
  - API: Step 5: GET /api/v1/ops/finance/statements?kitchenId=... -> 200; summary commissionPosted/commissionOutstanding all 0; items[] contains the order with cashState 'DUE' and commission 0 or null.
  - API: Step 6: the first audit query returns NO row with action 'access.order_contact' for this order (a completed order discloses no contact, so nothing is logged); the second query returns only reads made while orders were active. The audit read itself appears as action 'audit.read' for the administrator.
  - DB: SELECT state, total_minor, subtotal_minor, delivery_fee_minor, tax_minor, commission_bps_snapshot, commission_minor, fulfillment_mode FROM customer_order WHERE public_number = '<N>' -> COMPLETED, 15000, 15000, 0, 0, 0, 0, PICKUP.
  - DB: SELECT sequence, from_state, to_state, actor_type FROM order_event WHERE order_id = <id> ORDER BY sequence -> (1,NULL,PENDING_ACCEPTANCE,CUSTOMER),(2,PENDING_ACCEPTANCE,ACCEPTED,COOK),(3,ACCEPTED,PREPARING,COOK),(4,PREPARING,READY,COOK),(5,READY,COMPLETED,COOK).
  - DB: SELECT count(*) FROM cash_report WHERE order_id = <id> -> 0 (completion does not imply payment); SELECT count(*) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id WHERE g.order_id = <id> AND e.amount_minor > 0 -> 0 (no commission posted).
  - DB: SELECT count(*) FROM audit_event WHERE action = 'access.order_contact' AND object_id = '<id>' -> 0.
- **Also covered by automated tests:** apps/operations-web/e2e/cook-workspace.e2e.ts and customer-journey.e2e.ts (customer and cook halves, mocked staff not covered); ZeroCommissionIntegrationTest; FinanceOperationsIntegrationTest
- **Unverified against code (confirm while executing):** The order timeline's first row formatting (an event with no from-state) and the exact initial order state sequence (PENDING_ACCEPTANCE first) were read from OpsOrders.tsx and the order_event schema, not observed.; Whether a pickup order skips any intermediate event or adds a system event (for example a slot-consumed event) is not verified; compare the actual order_event rows before judging the exact sequence.; The statement's default period is the current Cairo calendar month through today (StatementService.java:91); the order must fall in it.

### E2E-OPS-104 — A reported customer review is hidden with a reason, leaves the public kitchen page, and can be restored

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, customer-web, api · **Actors:** FX-STAFF-OPS, FX-CUST-A, FX-CUST-B, FX-GUEST
- **Fixtures to provision:** FX-STAFF-OPS
- **Traces to:** rules: BR-039; operations: GET /api/v1/ops/reviews, POST /api/v1/ops/reviews/{reviewId}/moderation, POST /api/v1/reviews/{reviewId}/reports; partial: TC-123, TC-128
- **Prerequisites:**
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - Setup S1: a COMPLETED order of FX-CUST-A at kitchen A (produce it with steps 1-2 of E2E-OPS-103). S2: FX-CUST-A rates it on the customer web (order page 'Rate your order', rating 2, comment 'تقييم تجريبي للاختبار', 'Submit review'). S3: FX-CUST-B opens the kitchen page, finds the review and presses 'Report' with reason 'Other' (ar from reportReasons.OTHER).
  - No staff second factor is needed (content.moderate).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-OPS) FX-STAFF-OPS: https://ops.<PREVIEW_DOMAIN> > 'Catalogue moderation'; scroll to the section 'Customer reviews' (ar 'تقييمات العملاء'); the segmented control shows 'Reported reviews' (ar 'تقييمات تم الإبلاغ عنها') and 'Hidden'.
  2. (FX-STAFF-OPS) In the card of the review (stars, 'تقييم تجريبي للاختبار', '1 report(s)') type reason 'محتوى غير مناسب - اختبار' and click 'Hide' (ar 'إخفاء').
  3. (FX-STAFF-OPS) Click 'Hidden' in the section and read the card; then FX-GUEST opens https://api.<PREVIEW_DOMAIN>/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/reviews.
  4. (FX-STAFF-OPS) FX-STAFF-OPS: in 'Hidden' type reason 'تمت المراجعة' and click 'Show' (ar 'إظهار'); FX-GUEST reload the reviews call.
- **Expected:**
  - UI: Step 1: the review appears under 'Reported reviews' with badge '1 report(s)' (ar '1 بلاغ'), the report reason, and 'Visible'.
  - UI: Step 2: it disappears from 'Reported reviews'. Step 3: it appears under 'Hidden' with 'Reason: محتوى غير مناسب - اختبار'.
  - UI: Step 4: after restore it is visible again on the public page.
  - API: Step 2: POST /api/v1/ops/reviews/{reviewId}/moderation {visibility:'HIDDEN',reason,expectedVersion} -> 200 visibility 'HIDDEN'.
  - API: Step 3: GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/reviews -> the hidden review is absent and the kitchen's rating count excludes it.
  - API: Step 4: {visibility:'VISIBLE'} -> 200; repeating a state already set -> 409 STATE_CONFLICT.
  - DB: SELECT visibility, moderation_reason FROM review WHERE order_id = <order id> -> HIDDEN / reason, later VISIBLE / NULL.
  - DB: SELECT count(*) FROM review_report WHERE review_id = <id> -> 1 (reports are kept, one per reporter).
  - DB: audit_event action 'review.moderation', permission 'content.moderate', object_type 'review', ALLOWED, reason = typed text (two rows).
- **Also covered by automated tests:** ReviewsAndFavoritesIntegrationTest
- **Unverified against code (confirm while executing):** That the queue 'Reported reviews' lists only reviews that have at least one report was read from the API-098 comment in Catalogue.tsx; ReviewService.queue was not read line by line.

### E2E-OPS-105 — Case closing rules: resolution needs a code and a reason, only resolved cases close, a closed case is final, and a customer reply reopens a resolved case

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, customer-web, api · **Actors:** FX-STAFF-SUPPORT, FX-CUST-A
- **Fixtures to provision:** FX-STAFF-SUPPORT
- **Traces to:** rules: BR-041; operations: PATCH /api/v1/ops/cases/{caseId}, POST /api/v1/support/cases/{caseId}/messages; partial: TC-124, TC-041
- **Prerequisites:**
  - FX-STAFF-SUPPORT (PROVISION): staff account with exactly one active grant, support.case. Provision as FX-STAFF-OPS (one INSERT into user_grant with permission 'support.case' after the first sign-in, or POST /api/v1/ops/staff/{userId}/grants by FX-STAFF-ADMIN with MFA).
  - Two OPEN cases of FX-CUST-A (category Quality) on orders that are NOT cancelled, created as in E2E-OPS-057 step 1: C1 and C2.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-SUPPORT) FX-STAFF-SUPPORT: Support > open C1. In 'Change state' (ar 'تغيير الحالة') choose 'Resolved' (ar 'تم الحل'), keep resolution 'Information provided', leave 'Reason' empty, click 'Save' (ar 'حفظ').
  2. (FX-STAFF-SUPPORT) Type reason 'تم الرد على العميل' and click 'Save'.
  3. (FX-CUST-A) FX-CUST-A: reply to C1 on the customer web with 'لم تُحل المشكلة بعد'.
  4. (FX-STAFF-SUPPORT) FX-STAFF-SUPPORT: open C2. Choose 'Closed' (ar 'مغلق') directly and Save with reason 'محاولة إغلاق'.
  5. (FX-STAFF-SUPPORT) On C2 choose 'Resolved', resolution 'Order cancelled' (ar 'تم إلغاء الطلب'), reason 'اختبار' and Save.
  6. (FX-STAFF-SUPPORT) On C2 choose 'Resolved' with 'No action' (ar 'لا إجراء'), reason 'لا يوجد إجراء مطلوب', Save; then choose 'Closed', reason 'إغلاق نهائي', Save.
  7. (FX-STAFF-SUPPORT) On the closed C2 try to send a message and change state (the form is hidden in the UI, so use the console helper): x('POST','/api/v1/ops/cases/<C2>/messages',{body:'x',visibility:'PUBLIC'}) and x('PATCH','/api/v1/ops/cases/<C2>',{state:'OPEN',expectedVersion:<v>}). — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
- **Expected:**
  - UI: Step 1: error 'Check the fields.' (ar 'تحقق من الحقول.'); state unchanged. Step 2: heading shows 'Resolved'.
  - UI: Step 3: after the customer reply C1 returns to 'Open' in the staff queue (filter 'Active').
  - UI: Step 4: error 'This action is no longer available. Refreshed.' (ar from errors.STATE_CONFLICT). Step 5: same error text.
  - UI: Step 6: C2 shows 'Closed' and the reply/update forms disappear.
  - API: Step 1: PATCH /api/v1/ops/cases/{id} {state:'RESOLVED',resolutionCode:'INFORMATION_PROVIDED',expectedVersion} -> 422 fieldErrors [{field:'reason', code:'REQUIRED'}].
  - API: Step 3: customer POST /api/v1/support/cases/{id}/messages -> 201 and the case state returns to 'OPEN' with resolutionCode null.
  - API: Step 4: 409 STATE_CONFLICT ('only resolved cases can be closed').
  - API: Step 5: 409 STATE_CONFLICT with fieldErrors [{field:'resolutionCode', code:'ORDER_NOT_CANCELLED'}].
  - API: Step 7: both calls -> 409 STATE_CONFLICT ('case is closed'). A stale expectedVersion -> 409 VERSION_CONFLICT.
  - DB: SELECT state, resolution_code, resolved_at FROM support_case WHERE id = <C2> -> CLOSED, NO_ACTION, set (DB constraint support_case_resolution_ck: resolved/closed need a resolution_code). audit_event action 'case.update' rows carry details from/to/resolutionCode.
- **Also covered by automated tests:** backend/app/src/test/java/com/matbakhy/app/support/cases/*

### E2E-OPS-106 — A delivery-fee change applies to new quotes only: a quote taken earlier is refused, the placed order keeps its fee, the new quote uses the new fee

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-STAFF-ADMIN-B · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** scenarios: TC-042; rules: BR-042, BR-020; decisions: D13; operations: POST /api/v1/ops/configuration/{key}/versions, POST /api/v1/quotes, POST /api/v1/orders; partial: TC-126, TC-154
- **Prerequisites:**
  - FX-STAFF-ADMIN-B (PROVISION): second administrator with the grants of fixtures.json / seed-owner-e2e.sql: staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset. Not in seed-preview.sql. For E2E-OPS-102 first grant it content.moderate.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - FX-COOK-A exists: SELECT k.id, k.operating_state FROM kitchen k JOIN app_user u ON u.id = k.owner_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000a1' returns one row (id 00000000-0000-4000-8000-0000000bd001, ACTIVE) - created by deploy/web-preview/seed-preview.sql
  - 'Test zone A' (id 00000000-0000-4000-8000-00000000a001, fee 30.00 EGP) is one of kitchen A's delivery zones. FX-CUST-A has a saved address in Test zone A (customer-web 'My account' > 'My addresses' > 'New address': name 'بيت الاختبار', recipient 'عميل تجريبي', governorate/city/street/building any synthetic text, zone Test zone A).
  - Step 1 and 3 must happen within the quote lifetime (300 seconds in the synthetic policy).
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-CUST-A) FX-CUST-A: add 'كشري تجريبي' (tomorrow's slot, quantity 1), cart > mode 'Delivery' > the saved address > 'Cash on receipt' > 'Calculate total'. Do NOT place the order. Read 'Delivery fee' and 'Total'. — data: Expected delivery fee 30.00, total 105.00.
  2. (FX-STAFF-ADMIN-B) FX-STAFF-ADMIN-B: 'Operating configuration' > card 'Test zone A' > 'Delivery fee (EGP)' 35.00, reason 'اختبار أثر الرسوم على العروض' > 'Save' (step-up if asked).
  3. (FX-CUST-A) FX-CUST-A: click tick 'I accept the terms and the cancellation policy' (ar 'أوافق على الشروط وسياسة الإلغاء'; Place order stays disabled until ticked) and click 'Place order' on the old quote.
  4. (FX-CUST-A) FX-CUST-A: click 'Calculate total' again and place the order.
  5. (FX-STAFF-ADMIN-B) Restore the fee: FX-STAFF-ADMIN-B sets Test zone A back to 30.00 with reason 'استعادة الرسوم'.
- **Expected:**
  - UI: Step 3: the order is NOT created; the cart shows a price-change notice (data-testid price-change in the customer web) or an error and asks to recalculate.
  - UI: Step 4: 'Delivery fee 35.00', 'Total 110.00'; the order is placed with these values.
  - API: Step 3: POST /api/v1/orders with the old quote -> 409, body contains fieldErrors code 'TOTAL_CHANGED' (the delivery-fee change invalidates the quote).
  - API: Step 4: POST /api/v1/quotes -> 201 deliveryFee {amountMinor:3500}; POST /api/v1/orders -> 201.
  - DB: SELECT delivery_fee_minor, total_minor FROM customer_order WHERE public_number = '<N>' -> 3500, 11000; any order placed before the change keeps 3000 / 10500.
  - DB: SELECT config_key, resulting_version FROM config_change WHERE config_key = 'zone:00000000-0000-4000-8000-00000000a001' -> rows for 35.00 and the restore.
- **Also covered by automated tests:** ConfigurationIntegrationTest.a_fee_change_keeps_placed_orders_invalidates_stale_quotes_and_applies_to_new_quotes; PriceChangeIntegrationTest
- **Unverified against code (confirm while executing):** The exact error code word the server returns for the stale quote (the integration test only asserts that the body contains TOTAL_CHANGED); the customer-web's reaction is described by Checkout.tsx's price-change notice, not observed.; Totals assume tax 0 and no extra fees (synthetic policy) - verify on the quote screen.

### E2E-OPS-107 — Commerce policy versions: commission must stay zero, a real policy needs an approval reference, scheduled changes are refused, a synthetic version is appended

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api · **Actors:** FX-STAFF-ADMIN-B · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-ADMIN-B
- **Traces to:** rules: BR-042, BR-033; decisions: D10; operations: POST /api/v1/ops/configuration/{key}/versions, GET /api/v1/ops/configuration; partial: TC-157, TC-126, TC-042
- **Prerequisites:**
  - FX-STAFF-ADMIN-B (PROVISION): second administrator with the grants of fixtures.json / seed-owner-e2e.sql: staff.grant, audit.read, config.read, config.write, security.mfa_policy, security.mfa_reset. Not in seed-preview.sql. For E2E-OPS-102 first grant it content.moderate.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Active policy: SELECT version, label, synthetic, status, commission_rate_bps FROM commerce_policy WHERE status = 'ACTIVE' -> version >= 1, synthetic true, commission 0. A new synthetic version is irreversible (history is kept) but changes nothing else if the same values are sent.
  - Console helper on the signed-in ops tab with a fresh MFA proof.
- **Steps:**
  1. (FX-STAFF-ADMIN-B) GET /api/v1/ops/configuration and keep commercePolicy.version = V. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-STAFF-ADMIN-B) POST /api/v1/ops/configuration/commerce-policy/versions with value {label:'SYNTHETIC e2e policy',synthetic:true,taxRateBps:0,commissionRateBps:100,maxItemQuantity:10,quoteTtlSeconds:300,acceptanceWindowMinutes:15,minAcceptanceMinutes:5,cancellationPolicyVersion:'synthetic-cancel-v1',termsVersion:'synthetic-terms-v1'}, reason 'اختبار العمولة', expectedVersion V.
  3. (FX-STAFF-ADMIN-B) Same body with commissionRateBps 0 and synthetic:false and no approvalReference.
  4. (FX-STAFF-ADMIN-B) Same body with synthetic:true, commissionRateBps 0 and effectiveAt '2099-01-01T00:00:00Z'.
  5. (FX-STAFF-ADMIN-B) Same body with synthetic:true, commissionRateBps 0, no effectiveAt, expectedVersion V-1 (stale). — data: Skip if V = 1 and use V+5 instead.
  6. (FX-STAFF-ADMIN-B) Same body, expectedVersion V, reason 'نسخة تجريبية للاختبار'.
  7. (FX-STAFF-ADMIN-B) GET /api/v1/ops/configuration again; open https://ops.<PREVIEW_DOMAIN> > 'Operating configuration' and read the policy card.
- **Expected:**
  - UI: Step 7: the card 'Current pricing policy v<V+1>' (ar 'سياسة التسعير الحالية') shows the label 'SYNTHETIC e2e policy - Synthetic policy (local only)' (ar 'سياسة تجريبية (محلية فقط)') and 'no platform commission (owner decision D10)' (ar 'بدون عمولة للمنصة (قرار المالك D10)').
  - API: Step 2: 422 VALIDATION_FAILED fieldErrors [{field:'value.commissionRateBps', code:'RANGE_0_0'}].
  - API: Step 3: 422 [{field:'approvalReference', code:'REQUIRED_FOR_REAL_POLICY'}].
  - API: Step 4: 422 [{field:'effectiveAt', code:'SCHEDULED_CHANGES_NOT_SUPPORTED'}].
  - API: Step 5: 409 VERSION_CONFLICT.
  - API: Step 6: 200 {key:'commerce-policy', version:V+1}.
  - API: Step 7: commercePolicy.version V+1, status ACTIVE; the previous version appears in policyHistory with status RETIRED.
  - DB: SELECT version, status, synthetic, commission_rate_bps FROM commerce_policy ORDER BY version DESC LIMIT 2 -> (V+1, ACTIVE, true, 0), (V, RETIRED, ...); exactly one ACTIVE row.
  - DB: SELECT count(*) FROM config_change WHERE config_key = 'commerce-policy' -> +1 (only the successful change); audit_event config.change ALLOWED once.
- **Also covered by automated tests:** ConfigurationIntegrationTest.a_new_policy_version_applies_to_new_quotes_only_and_a_real_policy_needs_an_approval_reference; ZeroCommissionIntegrationTest
- **Unverified against code (confirm while executing):** The approval-reference guard refers to open point O04 (approved commerce policy), an open item rather than a decision id, so it is not listed under refs.decisions.; Stale and approval-guard ordering (which error appears first when several apply) follows ConfigurationService.newPolicy: version check, then synthetic permission, then approval check.

### E2E-OPS-108 — Separation of duties: a person who is both a cook and a reviewer cannot decide their own application, operate their own kitchen or approve their own payment account

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs the updated application deployment
- **Surface:** operations-web, api · **Actors:** FX-MULTIROLE · **Needs:** updated deploy
- **Fixtures to provision:** FX-MULTIROLE, FX-COOK-B
- **Traces to:** rules: BR-007, BR-008, BR-009; operations: POST /api/v1/ops/kitchens/{kitchenId}/operating-state, POST /api/v1/ops/cooks/{applicationId}/decision, POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval; partial: TC-007, TC-112
- **Prerequisites:**
  - FX-MULTIROLE (PROVISION): ONE login that owns an APPROVED kitchen (like FX-COOK-B) AND holds active grants cook.review, finance.verify, finance.read. Not in seed. Provision: use FX-COOK-B's account and have FX-STAFF-ADMIN grant the three permissions (MFA). Verify: SELECT permission FROM user_grant WHERE user_id = <id> AND revoked_at IS NULL; SELECT id FROM kitchen WHERE owner_id = <id>.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - Own ids: application id (SELECT id FROM cook_application WHERE user_id = <id>), kitchen id, current versions.
- **Steps:**
  1. (FX-MULTIROLE) FX-MULTIROLE: open https://ops.<PREVIEW_DOMAIN>. The tab bar shows both the cook tabs (Home, Orders, ...) and 'Cook applications', 'Finance'.
  2. (FX-MULTIROLE) 'Cook applications' > filter 'Approved' (ar 'معتمد') > own application > card 'Kitchen' > reason 'اختبار الفصل بين المهام' > 'Pause' (ar 'إيقاف مؤقت'). Do the MFA step-up if asked, then press again.
  3. (FX-MULTIROLE) Console (helper): x('POST','/api/v1/ops/cooks/<own application id>/decision',{decision:'START_REVIEW',revision:1,expectedVersion:<version>}) — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  4. (FX-MULTIROLE) Settings > 'Payment': change 'Account address or number' to 'multirole@instapay.invalid', name 'Multi role (not a real account)', Save. Then 'Finance' > panel 'Accounts waiting for approval' > own row > reason 'محاولة موافقة ذاتية' > 'Approve account'.
- **Expected:**
  - UI: Step 2: error 'Not allowed.' (ar 'غير مسموح.' from errors.FORBIDDEN); the kitchen stays 'Active'.
  - UI: Step 4: error 'Not allowed.' on 'Approve account'; the row stays in the waiting list; the cook's payment status stays 'Waiting for staff approval...'.
  - API: Step 2: POST /api/v1/ops/kitchens/{ownKitchenId}/operating-state -> 403 FORBIDDEN ('own kitchen').
  - API: Step 3: 403 FORBIDDEN ('self review').
  - API: Step 4: POST /api/v1/ops/kitchens/{ownKitchenId}/payment-recipient/approval -> 403 FORBIDDEN.
  - DB: SELECT operating_state FROM kitchen WHERE owner_id = <id> -> ACTIVE; SELECT recipient_status FROM kitchen_payment_profile WHERE kitchen_id = <kitchen> -> NONE.
  - DB: SELECT action, permission, outcome, reason FROM audit_event WHERE actor_id = <id> AND outcome = 'DENIED' ORDER BY occurred_at -> kitchen.operating_state 'staff cannot change the state of their own kitchen'; cook.application.decision 'reviewer cannot decide own application'. (The recipient self-approval refusal writes no dedicated row - see unverified.)
- **Also covered by automated tests:** CookWorkflowIntegrationTest (a_reviewer_cannot_approve_their_own_application_and_the_attempt_is_audited, only_scoped_staff_change_operating_state_and_never_for_their_own_kitchen); DepositPaymentIntegrationTest
- **Unverified against code (confirm while executing):** PaymentService.approveRecipient throws FORBIDDEN for the own kitchen without writing an audit row (no audit.record before the throw); the case asserts the refusal, not an audit entry.; docs/product/06-permissions.md requires 'record when separation of duties could not be achieved'; no code path records that for a solo operator.

### E2E-OPS-109 — Application decisions are guarded: START_REVIEW first, current revision and version, reason required, one effect per idempotency key

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-OPS, FX-CUST-NEW · **Needs:** updated deploy
- **Fixtures to provision:** FX-STAFF-OPS, FX-CUST-NEW
- **Traces to:** rules: BR-008, BR-023; operations: POST /api/v1/ops/cooks/{applicationId}/decision; partial: TC-112, TC-008
- **Prerequisites:**
  - FX-STAFF-OPS (PROVISION): operations staff with the grants of docs/quality/owner-acceptance/fixtures.json / deploy/web-preview/seed-owner-e2e.sql: cook.review, content.moderate, order.ops, order.exception_cancel, order.handover_override (no support.case). Not in seed-preview.sql. Verify only the grants the case needs: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '<subject>' AND g.revoked_at IS NULL includes them.
  - Staff high-risk actions need a Keycloak second factor. On origin/develop (0eaf755) the preview realm has no OTP flow (docs/operations/06-web-preview.md line 173: 'staff actions that need MFA are not available, because no OTP is configured in the preview realm'; matbakhy-realm.preview.json at that ref has no authenticationFlows, acr.loa.map or amr mapper), so this case cannot pass there. PR #9 adds the 'matbakhy browser' step-up flow (deploy/web-preview/identity/matbakhy-realm.preview.json, migrate-mfa.sh) which must also be applied to the RUNNING realm.
  - A fresh SUBMITTED application (revision 1) from a disposable FX-CUST-NEW (E2E-COOK-048 steps 4-8); the fixture sheet should hold several such accounts. Console helper; every call is made as FX-STAFF-OPS with a fresh MFA proof.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-OPS) GET /api/v1/ops/cooks/<id> and note version V and currentRevision 1. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-STAFF-OPS) Decide APPROVE straight from SUBMITTED: POST /api/v1/ops/cooks/<id>/decision {decision:'APPROVE',revision:1,expectedVersion:V,reason:'اختبار'}
  3. (FX-STAFF-OPS) START_REVIEW with a stale version {decision:'START_REVIEW',revision:1,expectedVersion:V+7}; then with revision 2.
  4. (FX-STAFF-OPS) START_REVIEW with expectedVersion V and header Idempotency-Key 'key-1' twice (identical body).
  5. (FX-STAFF-OPS) Same Idempotency-Key 'key-1' with a different body (decision REJECT).
  6. (FX-STAFF-OPS) Now in UNDER_REVIEW: APPROVE without a reason; then with reason 'ab' (2 characters).
  7. (FX-STAFF-OPS) In the UI open the application: reason empty and click 'Reject'.
- **Expected:**
  - UI: Step 7: error 'Check the fields.' (ar 'تحقق من الحقول.') and the application stays 'Under review'.
  - API: Step 2: 409 STATE_CONFLICT. Step 3: stale version -> 409 VERSION_CONFLICT; revision 2 -> 409 STATE_CONFLICT ('stale revision').
  - API: Step 4: both calls return the same body (state UNDER_REVIEW) and version V+1 only once.
  - API: Step 5: 409 IDEMPOTENCY_CONFLICT.
  - API: Step 6: 422 VALIDATION_FAILED fieldErrors [{field:'reason', code:'LENGTH_3_1000'}] for both.
  - DB: SELECT state, version FROM cook_application WHERE id = <id> -> UNDER_REVIEW, V+1; SELECT count(*) FROM audit_event WHERE action = 'cook.application.decision' AND object_id = '<id>' AND outcome = 'ALLOWED' -> 1 (the replayed idempotent call wrote no second row).
- **Also covered by automated tests:** CookWorkflowIntegrationTest (decisions_need_reason_review_state_and_current_version, repeated_decision_with_same_key_applies_once_and_key_reuse_with_other_body_conflicts)

### E2E-OPS-110 — Audit trail reading: permission, filters, cursor paging, clamped limit; reading is itself audited; only the API offers it

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api, operations-web · **Actors:** FX-STAFF-ADMIN, FX-STAFF-NOGRANT, FX-CUST-A
- **Traces to:** rules: BR-007, BR-056; operations: GET /api/v1/ops/audit, GET /api/v1/ops/audit/export; partial: TC-007, TC-111, TC-139
- **Prerequisites:**
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - FX-STAFF-NOGRANT (EXISTS) = preview-ops-2, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f2: staff-style login with no grants. Its app_user row is created at its first sign-in (not in seed-preview.sql); its user id for API calls: SELECT id FROM app_user WHERE subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f2'.
  - At least 6 audit rows exist (earlier cases create them). The audit trail has NO screen in apps/operations-web (no file under src/ops reads /api/v1/ops/audit), so the administrator opens JSON URLs in the browser.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-ADMIN) FX-STAFF-ADMIN: open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?limit=2 and note nextCursor.
  2. (FX-STAFF-ADMIN) Open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?limit=2&cursor=<nextCursor>.
  3. (FX-STAFF-ADMIN) Open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?limit=1000 and count items.
  4. (FX-STAFF-ADMIN) Open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?action=staff.grant&objectType=user&limit=5, then ?action=no.such.action.
  5. (FX-STAFF-ADMIN) Open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?limit=3 once more and then ?action=audit.read&actorId=<admin id>&limit=3.
  6. (FX-STAFF-NOGRANT) FX-STAFF-NOGRANT and FX-CUST-A each open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit?limit=1 in their own signed-in browser.
  7. (FX-STAFF-NOGRANT) Open https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit/export?from=2026-01-01&to=2026-12-31.
- **Expected:**
  - API: Step 1: 200 {items:[2 rows newest first: occurredAt descending], nextCursor:<string>, hasMore:true}; row fields: id, occurredAt, actorId, action, permission, objectType, objectId, outcome, reason, requestId, details.
  - API: Step 2: the next two rows, strictly older, none repeated from step 1.
  - API: Step 3: at most 100 items (limit is clamped to 100).
  - API: Step 4: the filtered rows all have action 'staff.grant'; an unknown action returns items [] and hasMore false.
  - API: Step 5: the second query's rows include audit.read entries written by the earlier calls of this case (reading is audited after the query, so a page never lists its own read).
  - API: Step 6: both -> 403 FORBIDDEN.
  - API: Step 7: the contract lists API-095 but the endpoint is not implemented (contracts/endpoints.json 'implemented': false): expect 404/405, not a CSV.
  - DB: SELECT count(*) FROM audit_event WHERE action = 'audit.read' AND actor_id = <admin id> AND occurred_at > '<t0>' -> one row per audit call of the administrator in steps 1-5 (details contain only objectType/action/byActor/page flags, never the filter values for object ids).
- **Also covered by automated tests:** AuditAndCorrelationReadinessIntegrationTest; StaffAccessIntegrationTest.audit_is_append_only_and_redacted
- **Unverified against code (confirm while executing):** The exact HTTP status for the unimplemented export route (404 vs 405 vs 403) was not determined.

### E2E-OPS-111 — Without security.mfa_policy / security.mfa_reset, MFA policy and reset endpoints are refused; own security status is visible to everyone

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs the updated application deployment
- **Surface:** api, operations-web · **Actors:** FX-STAFF-ADMIN, FX-STAFF-NOGRANT · **Needs:** updated deploy
- **Traces to:** rules: BR-007; decisions: D18; operations: GET /api/v1/ops/security/mfa-policy, PUT /api/v1/ops/security/mfa-policy/{category}, POST /api/v1/ops/accounts/{accountId}/mfa-reset, GET /api/v1/me/security; partial: TC-007, TC-111
- **Prerequisites:**
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - FX-STAFF-NOGRANT (EXISTS) = preview-ops-2, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f2: staff-style login with no grants. Its app_user row is created at its first sign-in (not in seed-preview.sql); its user id for API calls: SELECT id FROM app_user WHERE subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f2'.
  - PR #9 only: the endpoints /api/v1/ops/security/mfa-policy, /api/v1/ops/accounts/{id}/mfa-reset and /api/v1/me/security do not exist on origin/develop (contracts/openapi.yaml diff).
  - This case is the negative authorization boundary only; the full MFA policy and reset behaviour belongs to the security/identity cases.
  - Use a separate browser profile (or private window) per actor: the API session cookie is shared across the ops and customer hosts, so signing in as a second actor in the same profile replaces the first session.
- **Steps:**
  1. (FX-STAFF-ADMIN) FX-STAFF-ADMIN (holds staff.grant and audit.read only): in the console GET /api/v1/me/security. — data: Run the helper once in the DevTools console of the signed-in tab https://ops.<PREVIEW_DOMAIN> (the API is on https://api.<PREVIEW_DOMAIN>; the session cookie and the XSRF-TOKEN cookie are set by the API). Helper: const x=(m,p,b)=>fetch('https://api.<PREVIEW_DOMAIN>'+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent((document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))||'=').split('=')[1]),'Idempotency-Key':'t-'+crypto.randomUUID()},body:b?JSON.stringify(b):undefined}).then(async r=>({status:r.status,body:await r.json().catch(()=>null)}));  // then: await x('GET','/api/v1/me')
  2. (FX-STAFF-ADMIN) GET /api/v1/ops/security/mfa-policy.
  3. (FX-STAFF-ADMIN) PUT /api/v1/ops/security/mfa-policy/STAFF {mode:'REQUIRED',reason:'اختبار صلاحية',expectedVersion:0}
  4. (FX-STAFF-NOGRANT) POST /api/v1/ops/accounts/<FX-STAFF-NOGRANT user id>/mfa-reset {reason:'اختبار صلاحية إعادة التعيين'}
  5. (FX-STAFF-ADMIN) FX-STAFF-ADMIN: open the tab 'Security' (ar from sec.tab) and read what is offered.
- **Expected:**
  - UI: Step 5: the tab shows the account's own status ('Two-step verification' On/Off, policy per category) and NO 'Two-step verification policy per category' or 'Reset a user's two-step verification' forms.
  - API: Step 1: 200 with canManagePolicy false and canResetMfa false.
  - API: Step 2-4: 403 FORBIDDEN (not STEP_UP_REQUIRED: the permission check runs first).
  - DB: SELECT mode, version FROM mfa_policy ORDER BY category -> unchanged (OPTIONAL, 0 on a fresh PREVIEW); SELECT count(*) FROM user_mfa_reset -> 0; audit_event rows 'denied GET/PUT/POST /api/v1/ops/...' for the administrator, outcome DENIED; the mfa-reset denial may also write 'mfa.reset' DENIED.
- **Also covered by automated tests:** MfaPolicyIntegrationTest; MfaResetIntegrationTest
- **Unverified against code (confirm while executing):** Whether the mfa-reset route audits a permission denial in addition to the generic 'denied POST ...' row: MfaAdminController writes 'mfa.reset' DENIED only for its own guards (self-reset, missing admin client); the PreAuthorize denial is audited by the generic handler.

### E2E-OPS-112 — Operations dashboard metrics and audit CSV export are in the contract but not implemented

- **Status:** BLOCKED — Both routes are specified in contracts/openapi.yaml but have no controller (grep for 'ops/dashboard' and 'audit/export' in backend/**/*.java finds nothing) and contracts/endpoints.json marks API-066 and API-095 implemented:false. · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-STAFF-ADMIN
- **Traces to:** operations: GET /api/v1/ops/dashboard, GET /api/v1/ops/audit/export; partial: TC-123
- **Prerequisites:**
  - FX-STAFF-ADMIN (EXISTS) = preview-ops-1, Keycloak subject 7d3f0a52-1c0e-4b8e-9a3e-0000000000f1: made the first staff administrator by STAFF_BOOTSTRAP_SUBJECT; holds exactly staff.grant and audit.read (GrantService.bootstrapAdministratorIfNone) and nothing else. Verify: SELECT permission FROM user_grant g JOIN app_user u ON u.id = g.user_id WHERE u.subject = '7d3f0a52-1c0e-4b8e-9a3e-0000000000f1' AND g.revoked_at IS NULL ORDER BY 1 -> audit.read, staff.grant.
  - contracts/endpoints.json: API-066 GET /api/v1/ops/dashboard and API-095 GET /api/v1/ops/audit/export carry "implemented": false.
- **Steps:**
  1. (FX-STAFF-ADMIN) FX-STAFF-ADMIN: in the browser open https://api.<PREVIEW_DOMAIN>/api/v1/ops/dashboard and https://api.<PREVIEW_DOMAIN>/api/v1/ops/audit/export?from=2026-01-01&to=2026-12-31. — data: Expected only to document that the feature is absent.
- **Expected:**
  - API: No JSON dashboard and no CSV file are returned (404/405 or an error JSON). Record the status; this documents a gap, it is not a defect in the shipped scope.
  - DB: none
- **Unverified against code (confirm while executing):** Whether the owner expects these in the MVP is not decided here; keep BLOCKED until a story implements them.
