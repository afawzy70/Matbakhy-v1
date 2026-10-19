# Owner E2E cases — discovery-basket

Generated from `cases/*.json` by `scripts/owner_e2e_catalog.py`; do not edit. Every case is NOT_RUN or BLOCKED until a run records evidence.

### E2E-DISC-001 — Anonymous visitor discovers published meals and kitchens in Arabic and English without signing in

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-GUEST
- **Traces to:** scenarios: TC-150; rules: BR-016, BR-017, BR-046; decisions: D12; operations: getMeals, getKitchensById, getConfigurationPublic; partial: TC-016, TC-017, TC-046, TC-116
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Use a private/incognito window with no cookies for https://app.<PREVIEW_DOMAIN> (no session).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-GUEST) Open https://app.<PREVIEW_DOMAIN>/#/ in the private window. — data: Default language is Arabic.
  2. (FX-GUEST) Read the page chrome: the note banner, the document direction, the list heading and the cards. — data: Banner (role=note) title 'بيئة تجريبية'. Heading 'أكلات بيتية متاحة للحجز'. Top bar buttons: السلة (link), English, تسجيل الدخول. Bottom nav shows only 'الرئيسية' (no orders/account without a session).
  3. (FX-GUEST) Read the three food cards (title, price, kitchen link, portion pill, time pill, action). — data: كشري تجريبي / ٧٥٫٠٠ ج.م / مطبخ المعاينة / 'علبة كبيرة'; محشي تجريبي / ١٨٠٫٠٠ ج.م / مطبخ المعاينة / 'طبق لشخصين'; ملوخية تجريبية / ١٢٠٫٢٥ ج.م / مطبخ تجريبي تاني / 'طبق لشخص'. Each time pill = 13:00 Cairo on the first seeded day; each card has the button 'عرض التفاصيل'.
  4. (FX-GUEST) Press the mode button 'المطابخ'. — data: Heading becomes 'مطابخ بيتية'; two kitchen cards: 'مطبخ المعاينة' with text '2 أكلة في النتائج' and 'مطبخ تجريبي تاني' with '1 أكلة في النتائج'.
  5. (FX-GUEST) Press 'عرض المطبخ' on 'مطبخ المعاينة'. — data: Route becomes #/kitchen/preview-kitchen. Shows the description 'أكل بيتي تجريبي للمعاينة', the line 'المطبخ متراجع تشغيليًا من فريق مطبخي؛ ده مش شهادة سلامة غذاء.' and the heading 'أكلات متاحة للحجز' with the two meals of that kitchen only.
  6. (FX-GUEST) While still on the kitchen page (#/kitchen/preview-kitchen), press the top-bar button 'English'. — data: Kitchen page in English: heading 'Meals to preorder' listing only 'Preview koshari' and 'Preview mahshi' (kitchen A's meals); document is LTR (lang=en dir=ltr).
  7. (FX-GUEST) Press the bottom-nav link 'Home' (English), then read the heading and cards. — data: Heading 'Home-cooked meals to preorder' with all three cards: 'Preview koshari', 'Preview mahshi', 'Preview molokhia'.
  8. (FX-GUEST) Open DevTools console and run: await (await fetch('https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50')).json() — data: No cookies, no headers.
- **Expected:**
  - UI: Arabic: <html lang=ar dir=rtl>; English: lang=en dir=ltr. Every price is shown with currency EGP (ج.م in Arabic).
  - UI: Only the three published seeded meals (plus any meal other testers published) are listed; no draft, paused, archived or hidden meal appears.
  - UI: The kitchen page shows no phone number and no pickup location text ('Synthetic location 1' never appears).
  - API: GET /api/v1/meals?limit=50 -> 200 {items:[...], hasMore:false, nextCursor:null}; each item has id, kitchen{id,name,slug}, title, description, photo(null for seed), portionText, price{amountMinor,currency:'EGP'}, nextSlots[<=5], availability 'AVAILABLE'.
  - API: The JSON text contains neither 'Synthetic location' nor '+20100' nor '"status"' (no private kitchen data).
  - API: No Authorization/Cookie header is needed; the request returns 200 (public route).
  - DB: Read-only: SELECT m.id, m.status, m.title_ar, k.operating_state FROM meal m JOIN kitchen k ON k.id=m.kitchen_id WHERE m.id IN ('00000000-0000-4000-8000-0000000bb001','00000000-0000-4000-8000-0000000bb002','00000000-0000-4000-8000-0000000bb003'); -> three rows, status PUBLISHED, operating_state ACTIVE; the API list equals the set of PUBLISHED meals of ACTIVE kitchens that have an orderable slot or are shown as unavailable.
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'home follows the approved hierarchy...'; backend DiscoveryIntegrationTest.unpublished_meals_and_meals_of_paused_or_suspended_kitchens_are_excluded
- **Unverified against code (confirm while executing):** Exact Arabic-Indic digit/spacing rendering of Intl 'ar-EG' EGP currency (e.g. ٧٥٫٠٠ ج.م.) depends on the browser ICU; compare digits and currency only.; Whether the card time pill reads '1:00 م' or '١:٠٠ م' (Intl dateStyle medium/timeStyle short, Africa/Cairo).

### E2E-DISC-002 — New customer self-registration through the identity provider

- **Status:** BLOCKED — deploy/web-preview/identity/matbakhy-realm.preview.json:6 and infra/identity/matbakhy-realm.json:6 set registrationAllowed=false; verifyEmail=true (line 9) with no SMTP server in the preview realm, and the phone code is only kept in memory by FakePhoneVerificationSender.java:14-18 (never logged or returned). Self-sign-up cannot be exercised by a tester in any current environment. The verification gate itself is covered by E2E-DISC-105. · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web · **Actors:** FX-CUST-NEW
- **Traces to:** rules: BR-001, BR-003; operations: getMe; partial: TC-003, TC-109, TC-110
- **Prerequisites:**
  - BLOCKED: neither the preview realm nor the local realm allows self-registration, and a new account could not complete the e-mail or phone verification afterwards.
- **Steps:**
  1. (FX-CUST-NEW) Open https://app.<PREVIEW_DOMAIN>/#/ and press 'تسجيل الدخول'; on the identity-provider page look for a registration link. — data: Expected only after the owner enables registration and an e-mail/SMS provider.
- **Expected:**
  - UI: When unblocked: the new account lands on the app with only customer capabilities; GET /api/v1/me shows phoneVerified=false and no staff/kitchen permission.
  - API: When unblocked: GET /api/v1/me -> 200 with emailVerified and phoneVerified flags and no role/permission list.
  - DB: none until unblocked
- **Unverified against code (confirm while executing):** Keycloak registration page labels (no registration page exists today).

### E2E-DISC-003 — Customer signs in and out; private data and the session are gone after sign-out

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** scenarios: TC-109; rules: BR-001, BR-002, BR-045; operations: getMe, getOrders, getMeFavorites; partial: TC-001, TC-002
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - FX-CUST-A = Keycloak user 'preview-customer-1' (app_user id 00000000-0000-4000-8000-0000000b0001, phone verified). Fresh browser profile or private window.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
- **Steps:**
  1. (FX-CUST-A) Open https://app.<PREVIEW_DOMAIN>/#/ and press 'تسجيل الدخول' (top bar). — data: Browser goes to https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?returnTo=... and then to https://id.<PREVIEW_DOMAIN>/realms/matbakhy login page.
  2. (FX-CUST-A) Enter username preview-customer-1 and the password from the fixture sheet; submit.
  3. (FX-CUST-A) After return to the app, open the account page via the bottom nav 'حسابي', then run in the console: await call('GET','/api/v1/me') — data: Account page headings include 'البيانات الشخصية', 'المفضلة', 'الإشعارات', 'الدعم', 'عناويني', 'طلب حذف الحساب'.
  4. (FX-CUST-A) Press 'تسجيل الخروج' (top bar). — data: The browser is sent to the identity provider's logout and back; no password prompt is skipped on the next sign-in.
  5. (FX-CUST-A) After sign-out open https://app.<PREVIEW_DOMAIN>/#/account, then https://app.<PREVIEW_DOMAIN>/#/orders, then press the browser Back button three times. — data: Do this in the same tab.
  6. (FX-CUST-A) Run in a fresh console after redefining the helper: await call('GET','/api/v1/me'); await call('GET','/api/v1/orders'); await call('GET','/api/v1/me/favorites')
- **Expected:**
  - UI: After step 2 the top bar shows 'تسجيل الخروج' instead of 'تسجيل الدخول'; bottom nav shows 'الرئيسية', 'طلباتي', 'حسابي'.
  - UI: After sign-out the top bar shows 'تسجيل الدخول' again and the bottom nav shows only 'الرئيسية'.
  - UI: #/account shows only the link 'تسجيل الدخول' (no profile fields). #/orders shows no order number, total or item from FX-CUST-A (by code the list stays on 'جارٍ التحميل…'; record what you actually see).
  - UI: Pressing Back never restores a page with FX-CUST-A's name, orders, basket or addresses (a restored bfcache page reloads itself).
  - API: Step 3: GET /api/v1/me -> 200 {displayName:'Preview Customer 1', phoneVerified:true, emailVerified:true, version, capabilities...}; body has no 'roles' or 'permissions' of staff.
  - API: Sign-out: POST /api/v1/auth/logout with X-XSRF-TOKEN -> 200/204 and (when the IdP session exists) a body with idpLogoutUrl.
  - API: After sign-out: GET /api/v1/me, /api/v1/orders and /api/v1/me/favorites -> 401 with ApiError.code 'UNAUTHENTICATED' and no private data.
  - DB: Read-only: SELECT count(*) FROM spring_session WHERE principal_name='7d3f0a52-1c0e-4b8e-9a3e-000000000c01'; before step 4 >=1, after step 4 = 0 (web session removed). SELECT status FROM app_user WHERE id='00000000-0000-4000-8000-0000000b0001' -> ACTIVE (sign-out does not disable the account).
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'ends the server session with the CSRF token...'; backend IdentityIntegrationTest.logout_revokes_only_the_intended_session_and_needs_csrf
- **Unverified against code (confirm while executing):** Keycloak login form field labels (not in this repo).; Where the IdP logout lands (post-logout redirect target not verified here).; spring_session.principal_name equals the OIDC subject (inferred from IdentityIntegrationTest.web_sessions_are_indexed_by_the_provider_subject).; The loading-forever state on #/orders after sign-out is inferred from apps/customer-web/src/shared.tsx:40-43 (401 returns without setting state); if confirmed it contradicts BR-045 'denied state with actionable recovery' and should be logged as a defect.

### E2E-DISC-004 — Switching from customer A to customer B leaves no trace of A's basket, orders or profile

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** rules: BR-006, BR-044, BR-001; operations: getOrdersById, getOrdersByRequestById, postQuotes, postOrders, postOrdersCancel; partial: TC-006, TC-109
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - FX-CUST-A (preview-customer-1) and FX-CUST-B (preview-customer-2) both phone-verified. Same browser profile for both (that is the point).
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Cook FX-COOK-A will be needed afterwards only to release stock: A cancels its own order in step 6 so no cook action is required.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+3 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Sign in as preview-customer-1. Open meal 'كشري تجريبي', pick the first slot radio, quantity 1, press 'أضف إلى السلة' (label shows the line total). — data: Message 'تمت الإضافة إلى السلة.'
  2. (FX-CUST-A) Open السلة (#/checkout). Choose 'استلام من المطبخ', leave 'الدفع عند الاستلام', press 'احسب الإجمالي', tick 'أوافق على الشروط وسياسة الإلغاء', press 'تأكيد الطلب'. — data: Browser moves to #/orders/<A-order-id>. Note the order number MB-1xxxxx and id.
  3. (FX-CUST-A) Add 'محشي تجريبي' (same slot) to the basket again so the basket is non-empty, then press 'تسجيل الخروج'. — data: No pending-order warning appears because the order was confirmed.
  4. (FX-CUST-B) In the same window sign in as preview-customer-2. Open #/checkout, #/orders and #/account. — data: Basket shows 'السلة فارغة.'; orders show 'لا توجد طلبات بعد.' (or only B's own); account name field shows 'Preview Customer 2'.
  5. (FX-CUST-B) With the helper run: await call('GET','/api/v1/orders/<A-order-id>'); await call('GET','/api/v1/orders/by-request/<A-clientRequestId-if-known>') — data: Replace <A-order-id> with the id from step 2 (visible in the URL).
  6. (FX-CUST-B) Press the browser Back button until the A order page URL is reached again; also inspect DevTools > Application > Local Storage for the app origin. — data: Keys matbakhy.cart and matbakhy.pendingPlacement must not hold A's data.
  7. (FX-CUST-A) Cleanup: sign out B, sign in A, open the order and press 'إلغاء الطلب' (visible while the state is 'بانتظار قبول المطبخ'). — data: State becomes 'ملغي' and the slot stock is released.
- **Expected:**
  - UI: Step 4: B sees an empty basket, no A order, B's own name; nothing from A (name 'Preview Customer 1', order number, items) is rendered at any point.
  - UI: Step 6: Back to A's order URL shows the not-found text 'غير موجود.' (or the sign-in prompt), never A's items.
  - UI: localStorage matbakhy.cart is absent or empty after A signs out and B signs in.
  - API: Step 5: GET /api/v1/orders/<A-order-id> as B -> 404 ApiError.code 'NOT_FOUND' (not 403, no A data). GET /api/v1/orders/by-request/<A clientRequestId> as B -> 404.
  - API: GET /api/v1/orders as B returns only orders whose customer is B.
  - DB: Read-only: SELECT customer_id, state FROM customer_order WHERE id='<A-order-id>'; customer_id = '00000000-0000-4000-8000-0000000b0001' (A), never B. SELECT count(*) FROM customer_order WHERE customer_id='00000000-0000-4000-8000-0000000b0002' AND created_at > now() - interval '1 hour'; = 0 unless B placed orders itself. After cleanup: stock_allocation rows of the order have state RELEASED.
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'ends the server session...'; backend OrderPlacementIntegrationTest.another_customer_cannot_find_an_order_by_its_client_request_id
- **Unverified against code (confirm while executing):** Exact wording of the 'not found' state for an order URL of another customer (errors.NOT_FOUND = 'غير موجود.' is verified in i18n.ts; whether the page shows it or the sign-in link depends on session timing).; Order ids are visible only in the URL; the clientRequestId of A is held in A's localStorage until the order is confirmed and then cleared, so step 5 second request is optional.

### E2E-DISC-005 — Draft meal and paused kitchen are not browsable or purchasable by customers

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-GUEST, FX-COOK-A, FX-CUST-A
- **Traces to:** scenarios: TC-009; rules: BR-009, BR-017; operations: getMealsById, getMeals, getKitchensById, postQuotes, patchCookSettings, postCookMeals; partial: TC-017, TC-116
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - FX-COOK-A = 'preview-cook-1' (kitchen 'مطبخ المعاينة'). Pausing the kitchen blocks every tester of kitchen 1: announce a 10 minute window and resume at the end.
  - A tiny PNG file on the tester machine to upload as a photo if a published test meal is wanted (not needed for this case).
  - Before pausing, capture the koshari slot id: GET https://api.<PREVIEW_DOMAIN>/api/v1/meals/00000000-0000-4000-8000-0000000bb001 -> nextSlots[0].id (needed by step 6).
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-COOK-A) Open https://ops.<PREVIEW_DOMAIN>, sign in as preview-cook-1, tab 'الأطباق' (Meals), form 'طبق جديد (مسودة)': Arabic name 'مسودة اختبار DR1', English name 'Draft test DR1', price (EGP) 50, portion/ingredients filled, allergens 'لا يوجد ما يُعلن عنه'; press 'إنشاء'. Do NOT press 'نشر'. — data: Meal appears with status badge 'مسودة'. Copy its id from the page/network tab (POST /api/v1/cook/meals response id).
  2. (FX-GUEST) In a private window open https://app.<PREVIEW_DOMAIN>/#/ and search (field 'ابحث عن طبق أو مطبخ') for 'DR1'; then open https://app.<PREVIEW_DOMAIN>/#/meal/<draft-id> directly.
  3. (FX-GUEST) Console: await (await fetch('https://api.<PREVIEW_DOMAIN>/api/v1/meals/<draft-id>')).status; and fetch('https://api.<PREVIEW_DOMAIN>/api/v1/meals?q=DR1').
  4. (FX-COOK-A) In operations web tab 'الإعدادات' (Settings) press 'إيقاف البيع مؤقتًا' (Pause new sales). Note the time. — data: Heading of the state card changes from 'نشط' to 'متوقف مؤقتًا'.
  5. (FX-GUEST) Reload https://app.<PREVIEW_DOMAIN>/#/ ; open https://app.<PREVIEW_DOMAIN>/#/kitchen/preview-kitchen ; open https://app.<PREVIEW_DOMAIN>/#/meal/00000000-0000-4000-8000-0000000bb001 ; then in the console fetch /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and /api/v1/kitchens/preview-kitchen.
  6. (FX-CUST-A) While the kitchen is still paused, sign in as preview-customer-1 and run in the console: await call('POST','/api/v1/quotes',{items:[{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}],slotId:'<koshari slot id captured before pausing>',fulfillmentMode:'PICKUP',paymentMethod:'COD'}) — data: Capture the slot id BEFORE pausing: GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 -> nextSlots[0].id (do this as a preparation read in step 1 time frame). Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  7. (FX-COOK-A) Resume: Settings > button 'استئناف البيع' (English 'Resume sales'), then reload the customer home. — data: Both seeded meals of kitchen 1 reappear.
- **Expected:**
  - UI: Step 2: search for DR1 shows 'لا توجد أطباق متاحة لهذا البحث.'; the direct draft URL shows the error line 'غير موجود.' with a retry button; the draft never appears in any list.
  - UI: Step 5 (kitchen paused): the home list no longer contains 'كشري تجريبي' or 'محشي تجريبي' (only 'ملوخية تجريبية'); #/kitchen/preview-kitchen still opens and shows 'المطبخ مش بياخد طلبات دلوقتي.' and an empty meals section 'لا توجد أطباق متاحة لهذا البحث.'; the direct meal URL shows 'غير موجود.'.
  - UI: After resume both meals are back in the list.
  - API: GET /api/v1/meals/<draft-id> -> 404 ApiError.code 'NOT_FOUND'; GET /api/v1/meals?q=DR1 -> 200 items [].
  - API: Paused: GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 -> 404 NOT_FOUND; GET /api/v1/kitchens/preview-kitchen -> 200 with available=false.
  - API: Step 6: POST /api/v1/quotes for the paused kitchen -> 409 ApiError.code 'KITCHEN_UNAVAILABLE' (no quote row).
  - DB: Read-only: SELECT status FROM meal WHERE id='<draft-id>' -> DRAFT; SELECT operating_state FROM kitchen WHERE id='00000000-0000-4000-8000-0000000bd001' -> PAUSED during step 5 and ACTIVE after resume; SELECT count(*) FROM quote WHERE kitchen_id='00000000-0000-4000-8000-0000000bd001' AND created_at > '<step 6 time>' -> 0.
- **Also covered by automated tests:** backend DiscoveryIntegrationTest.unpublished_meals_and_meals_of_paused_or_suspended_kitchens_are_excluded; backend QuoteIntegrationTest.paused_archived_and_hidden_meals_cannot_be_quoted_even_inside_an_open_slot
- **Unverified against code (confirm while executing):** Cook meal form field labels other than titleAr/titleEn/price/portion/ingredients/allergens/photo (create requires only draft-valid fields: titles 2-120 chars, descriptions >=10 chars when given).; Whether the meal page shows 'غير موجود.' or another message for a 404 (errors.NOT_FOUND in customer i18n).

### E2E-DISC-104 — A suspended or not-yet-approved cook's kitchen is invisible and unquotable

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready after the extra synthetic fixtures are provisioned
- **Surface:** operations-web, customer-web, api · **Actors:** FX-COOK-B, FX-COOK-PENDING, FX-STAFF-OPS, FX-GUEST, FX-CUST-A
- **Fixtures to provision:** FX-COOK-B, FX-COOK-PENDING, FX-STAFF-OPS
- **Traces to:** scenarios: TC-017; rules: BR-008, BR-009, BR-017; operations: postOpsKitchensOperatingState, getKitchensById, getMeals, postQuotes; partial: TC-008, TC-112, TC-123
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - FX-COOK-B has an APPROVED kitchen with one published meal and an OPEN slot (PROVISION: not in the seed; the seed's second kitchen 'مطبخ تجريبي تاني' has no login).
  - FX-COOK-PENDING has a submitted but undecided cook application (no kitchen row).
  - FX-STAFF-OPS holds the kitchen operating-state permission; staff actions need recent MFA step-up (see the MFA domain cases).
  - Never suspend FX-COOK-A (shared kitchen).
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-GUEST) Record FX-COOK-B's meal id, kitchen slug and a slot id: GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?q=<COOK-B meal title> and the item's nextSlots[0].id.
  2. (FX-STAFF-OPS) In the operations web catalogue/kitchen view open FX-COOK-B's kitchen and use the kitchen action 'إيقاف' (Suspend, in the operations web ops catalogue) with reason 'E2E suspend test' (API: POST /api/v1/ops/kitchens/<kitchenId>/operating-state {action:'SUSPEND',reason:'E2E suspend test',expectedVersion:<current>}).
  3. (FX-GUEST) Reload https://app.<PREVIEW_DOMAIN>/#/ , search the meal title, open the kitchen URL #/kitchen/<slug>.
  4. (FX-CUST-A) Console: POST /api/v1/quotes with FX-COOK-B's meal, the recorded slot id, fulfillmentMode PICKUP, paymentMethod COD. — data: Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  5. (FX-GUEST) Look up the would-be public address of FX-COOK-PENDING: GET /api/v1/kitchens/<slug guessed from the application name> and GET /api/v1/meals?q=<pending cook name>. — data: A pending application has no kitchen, therefore nothing to find.
  6. (FX-STAFF-OPS) Restore FX-COOK-B: kitchen action 'استعادة' (Restore) with reason 'E2E restore'. — data: The meal list shows the meal again.
- **Expected:**
  - UI: Suspended: the cook's meals are absent from the home list and from search; the kitchen page is still addressable and shows 'المطبخ مش بياخد طلبات دلوقتي.'.
  - UI: Pending cook: no kitchen page and no meals anywhere.
  - UI: After RESTORE the meal reappears.
  - API: Suspended: GET /api/v1/meals/<id> -> 404 NOT_FOUND; GET /api/v1/kitchens/<slug> -> 200 available=false; POST /api/v1/quotes -> 409 'KITCHEN_UNAVAILABLE'.
  - API: Pending: GET /api/v1/kitchens/<slug> -> 404 NOT_FOUND.
  - DB: Read-only: SELECT operating_state FROM kitchen WHERE id='<kitchenId>' -> SUSPENDED then ACTIVE; SELECT action, outcome FROM audit_event WHERE resource_id='<kitchenId>' ORDER BY created_at DESC LIMIT 2 shows the staff suspend/restore entries (verify column names with \d audit_event first).
- **Also covered by automated tests:** backend DiscoveryIntegrationTest (SUSPENDED kitchen excluded)
- **Unverified against code (confirm while executing):** Which operations-web screen lists kitchens with the Suspend/Restore buttons (labels kitchenAction PAUSE/SUSPEND/RESTORE = 'إيقاف مؤقت'/'إيقاف'/'استعادة' confirmed in i18n.ts:249; screen not opened).; audit_event column names and the resource key used by operating-state audits.

### E2E-DISC-006 — Search, price ceiling, sort and reset return server-side results that match the seed prices

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-GUEST
- **Traces to:** rules: BR-016; decisions: D11; operations: getMeals; partial: TC-016, TC-116
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - No other tester has published additional meals (otherwise adjust counts).
- **Steps:**
  1. (FX-GUEST) Open https://app.<PREVIEW_DOMAIN>/#/ . In the search field 'ابحث عن طبق أو مطبخ' type كشري and press the search button. — data: Query sent: GET /api/v1/meals?limit=50&sort=NEWEST&q=كشري
  2. (FX-GUEST) Replace the text with كشرى (ending in ى, alef maqsura) and search again. — data: Arabic letter variants must match.
  3. (FX-GUEST) Search Preview mahshi (English words), then search the kitchen name مطبخ تجريبي تاني, then search %.
  4. (FX-GUEST) Clear the search text, press 'تصفية النتائج', set 'أقصى سعر (ج.م)' = 100 and press 'عرض النتائج'.
  5. (FX-GUEST) Open the filters again, set max price 121 -> show; then 200 -> show; then type abc. — data: abc must show the inline error and disable the button.
  6. (FX-GUEST) With max price 200 open 'رتّب النتائج' and choose 'الأقل سعرًا', then 'الأعلى سعرًا'.
  7. (FX-GUEST) Choose max price 1 -> show. In the empty state press 'مسح الفلاتر'. — data: Empty state card appears because a filter is active.
- **Expected:**
  - UI: Step 1: exactly one card 'كشري تجريبي'. Step 2: same single card (variant spelling matches). Step 3: 'محشي تجريبي' only; then the kitchen-name search returns only 'ملوخية تجريبية'; '%' returns 'لا توجد أطباق متاحة لهذا البحث.' (wildcards are literal).
  - UI: Max 100: only 'كشري تجريبي' (75.00). Max 121: 'كشري تجريبي' and 'ملوخية تجريبية' (120.25) but not 'محشي تجريبي' (180.00). Max 200: all three.
  - UI: Input 'abc' shows 'اكتب السعر بالأرقام، زي 150 أو 150.50' and the 'عرض النتائج' button is disabled.
  - UI: Sort 'الأقل سعرًا': 75.00, 120.25, 180.00 in that order; 'الأعلى سعرًا': 180.00, 120.25, 75.00.
  - UI: Max 1: empty state with text 'لا توجد أطباق متاحة لهذا البحث.' and the button 'مسح الفلاتر'; pressing it restores all three meals and the NEWEST sort.
  - API: Each UI change issues a new GET /api/v1/meals with q / maxPriceMinor / sort query parameters (check Network): maxPriceMinor=10000, 12100, 20000, 100; sort=PRICE_ASC / PRICE_DESC. The browser never filters locally.
  - API: GET /api/v1/meals?q=%25 -> 200 items []. GET /api/v1/meals?q=<81 characters> -> 422 VALIDATION_FAILED with fieldErrors[{field:'q',code:'MAX_80'}] (the web input is capped at 80 characters, so test this one in the console).
  - API: GET /api/v1/meals?maxPriceMinor=12100 returns items whose price.amountMinor are all <= 12100.
  - DB: none (read-only API); optional: SELECT id, price_minor FROM meal WHERE status='PUBLISHED' ORDER BY price_minor to compare with the order shown.
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'home follows the approved hierarchy and sends every filter to the server'; backend DiscoveryIntegrationTest.arabic_search_ignores_diacritics_and_letter_variants_and_english_titles_are_searchable
- **Unverified against code (confirm while executing):** Exact currency string format for the empty-state price tokens is not asserted; compare numeric digits only.; The web client never sends minPriceMinor, date or slotId: those API filters have no UI (see findings).

### E2E-DISC-101 — Zone filter shows only kitchens that deliver to the chosen zone, in dishes and kitchens views

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-GUEST
- **Traces to:** rules: BR-016, BR-021; operations: getZones, getMeals; partial: TC-016, TC-021, TC-116
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Seed facts: only kitchen 'مطبخ المعاينة' delivers to 'Test zone A' (id 00000000-0000-4000-8000-00000000a001, fee 3000); no kitchen delivers to 'Test zone B' (id 00000000-0000-4000-8000-00000000a002, fee 4500); 'Test zone (inactive)' (id 00000000-0000-4000-8000-00000000a003) is inactive.
- **Steps:**
  1. (FX-GUEST) Open https://app.<PREVIEW_DOMAIN>/#/ , press 'تصفية النتائج', open the select 'منطقتك' and read all options.
  2. (FX-GUEST) Select 'منطقة تجريبية أ' and press 'عرض النتائج'.
  3. (FX-GUEST) Press 'المطابخ'.
  4. (FX-GUEST) Reopen filters, select 'منطقة تجريبية ب' and press 'عرض النتائج'.
  5. (FX-GUEST) Reopen filters, select 'اختر المنطقة' (empty) and press 'عرض النتائج'.
  6. (FX-GUEST) Console: for each of the three zone ids fetch https://api.<PREVIEW_DOMAIN>/api/v1/meals?zoneId=<id> and read items.length. — data: Use the ids from the prerequisites.
- **Expected:**
  - UI: Zone options: 'اختر المنطقة', 'منطقة تجريبية أ', 'منطقة تجريبية ب' (the inactive zone is NOT offered).
  - UI: Zone A: 'كشري تجريبي' and 'محشي تجريبي' only (not 'ملوخية تجريبية'). Kitchens view: one kitchen 'مطبخ المعاينة' with '2 أكلة في النتائج'.
  - UI: Zone B: empty state 'لا توجد أطباق متاحة لهذا البحث.'. Cleared zone: all three meals again.
  - API: GET /api/v1/zones -> 200 items include test-zone-a and test-zone-b and exclude test-zone-off. GET /api/v1/meals?zoneId=00000000-0000-4000-8000-00000000a001 -> 2 items; zoneId=00000000-0000-4000-8000-00000000a002 -> 0 items; zoneId=00000000-0000-4000-8000-00000000a003 -> 0 items.
  - DB: Read-only: SELECT zone_id FROM kitchen_zone WHERE kitchen_id='00000000-0000-4000-8000-0000000bd001' -> only 00000000-0000-4000-8000-00000000a001; SELECT count(*) FROM kitchen_zone WHERE kitchen_id='00000000-0000-4000-8000-0000000bd002' -> 0.
- **Also covered by automated tests:** backend DiscoveryIntegrationTest.zone_date_and_slot_filters_return_only_serviceable_meals_with_their_availability; backend DiscoveryIntegrationTest.zones_list_active_zones_localized_with_stable_paging
- **Unverified against code (confirm while executing):** Behaviour of zoneId for an inactive zone id (assumed to return no meals; the zone list excludes it).

### E2E-DISC-102 — Category chips filter by the meal's category; meals without a category match no chip

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Isolated disposable stack only
- **Surface:** customer-web, api · **Actors:** FX-GUEST · **Needs:** isolated stack
- **Traces to:** rules: BR-016; operations: getMeals; partial: TC-016, TC-116
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - FX-ISOLATED disposable stack with the same seed. The preview seed leaves meal.category NULL for all three meals (seed-preview.sql INSERT has no category column) and the cook UI has no category control, so on the shared preview every chip returns nothing.
  - Fixture to set in FX-ISOLATED only: UPDATE meal SET category='MAIN_DISHES' WHERE id='00000000-0000-4000-8000-0000000bb001'; UPDATE meal SET category='DESSERTS' WHERE id='00000000-0000-4000-8000-0000000bb002'; (molokhia stays NULL).
- **Steps:**
  1. (FX-GUEST) On the shared preview first: open the home page and press each chip 'أطباق رئيسية', 'حلويات' (one at a time). — data: Expected: empty state for both.
  2. (FX-GUEST) On FX-ISOLATED after the UPDATEs: reload home, press chip 'أطباق رئيسية'.
  3. (FX-GUEST) Press chip 'حلويات', then 'الكل'.
  4. (FX-GUEST) Console: fetch /api/v1/meals?categoryId=SOUPS and /api/v1/meals?categoryId=NOPE.
- **Expected:**
  - UI: Shared preview (all categories NULL): both chips show 'لا توجد أطباق متاحة لهذا البحث.'.
  - UI: Isolated: 'أطباق رئيسية' -> only 'كشري تجريبي'; 'حلويات' -> only 'محشي تجريبي'; 'الكل' -> all three (molokhia appears only under 'الكل').
  - UI: The pressed chip has aria-pressed=true.
  - API: GET /api/v1/meals?categoryId=MAIN_DISHES -> 200 with exactly the koshari id; categoryId=SOUPS -> 200 []; categoryId=NOPE -> 400 (enum binding failure; record the actual code - expected MALFORMED_REQUEST).
  - DB: Read-only: SELECT id, category FROM meal WHERE id IN ('00000000-0000-4000-8000-0000000bb001','00000000-0000-4000-8000-0000000bb002','00000000-0000-4000-8000-0000000bb003');
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx (categoryId=SOUPS request asserted at line 367)
- **Unverified against code (confirm while executing):** Error code for an invalid categoryId enum value (not covered by a test).

### E2E-DISC-103 — Meal listing paginates with a cursor and rejects out-of-range limits

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-GUEST
- **Traces to:** rules: BR-016; operations: getMeals; partial: TC-016
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - At least 3 meals visible (seed has exactly 3 when no tester added more). The web client requests limit=50 and offers no 'load more' control, so paging is an API-level check on the preview.
- **Steps:**
  1. (FX-GUEST) Console: const p1=await (await fetch('https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=2')).json()
  2. (FX-GUEST) Console: const p2=await (await fetch('https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=2&cursor='+encodeURIComponent(p1.nextCursor))).json()
  3. (FX-GUEST) Compare ids of p1.items and p2.items; then request limit=0, limit=101 and cursor=garbage and read status plus body.code.
  4. (FX-GUEST) Request p1 again with sort=PRICE_DESC and page through all pages; list the prices in order.
- **Expected:**
  - UI: n/a (API-only case).
  - API: p1: 2 items, hasMore=true, nextCursor non-null. p2: 1 item, hasMore=false, nextCursor null; p1 and p2 ids are disjoint and together equal the full list.
  - API: limit=0 and limit=101 -> 422 VALIDATION_FAILED fieldErrors[{field:'limit',code:'RANGE_1_100'}]. cursor=garbage -> 422 VALIDATION_FAILED (field 'cursor', code expected 'INVALID').
  - API: PRICE_DESC pages yield 18000, 12025, 7500 across pages with no duplicate or gap.
  - DB: none
- **Also covered by automated tests:** backend DiscoveryIntegrationTest.every_sort_pages_through_all_eligible_meals_exactly_once; backend DiscoveryIntegrationTest.a_matching_meal_beyond_the_first_page_is_found_by_a_fresh_filtered_query
- **Unverified against code (confirm while executing):** Exact fieldError for an invalid public-listing cursor (the cook/ops listings use code INVALID; the public DiscoveryService was not read to the end).

### E2E-DISC-007 — Delivery to a zone the kitchen does not serve is refused at quote time; pickup still works

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** scenarios: TC-021; rules: BR-021, BR-005; operations: postQuotes, postMeAddresses, deleteMeAddressesById, getZones; partial: TC-117, TC-005
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - FX-CUST-A signed in with a non-empty basket: koshari x1, first slot (do E2E-BASKET-009 steps 1-4 first). Kitchen 'مطبخ المعاينة' delivers only to Test zone A.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Open #/checkout, keep 'توصيل' selected, press 'عنوان جديد', fill: اسم العنوان = Zone B test; اسم المستلم = Test Recipient; المحافظة = Cairo; المدينة = Test city; الشارع = Test street 1; رقم المبنى = 7; رقم الموبايل المصري = +201000000101; منطقتك = 'منطقة تجريبية ب'; press 'حفظ العنوان'. — data: Address saved: it appears as a radio 'Zone B test · Test street 1 7'.
  2. (FX-CUST-A) Select that address radio and press 'احسب الإجمالي'.
  3. (FX-CUST-A) Press the mode button 'استلام من المطبخ' and press 'احسب الإجمالي' again.
  4. (FX-CUST-A) Console: POST /api/v1/me/addresses with zoneId '00000000-0000-4000-8000-00000000a003' (inactive zone) and the other required fields (label, recipientName, phone '01012345678', governorate, city, street, building).
  5. (FX-CUST-A) Cleanup: account page 'عناويني' > press 'حذف' next to 'Zone B test'.
- **Expected:**
  - UI: Step 2: no price summary appears; a red alert line 'تحقق من البيانات.' is shown (the UI has no serviceability-specific message; record this as an observation against the 'explainable feedback' goal). 'تأكيد الطلب' is not offered.
  - UI: Step 3: price summary shows قيمة الأطباق 75.00, رسوم التوصيل 0.00, الضريبة 0.00, الإجمالي 75.00.
  - UI: Cleanup: the address disappears from the account list.
  - API: Step 2: POST /api/v1/quotes -> 422 VALIDATION_FAILED fieldErrors[{field:'addressId',code:'NOT_SERVICEABLE'}]; no quote row.
  - API: Step 3: POST /api/v1/quotes (PICKUP) -> 201 with deliveryFee.amountMinor=0, total.amountMinor=7500.
  - API: Step 4: POST /api/v1/me/addresses -> 422 VALIDATION_FAILED fieldErrors[{field:'zoneId',code:'ZONE_NOT_SERVED'}].
  - DB: Read-only: SELECT count(*) FROM quote WHERE customer_id='00000000-0000-4000-8000-0000000b0001' AND address_id=(SELECT id FROM address WHERE label='Zone B test' AND user_id='00000000-0000-4000-8000-0000000b0001'); -> 0. After cleanup SELECT archived_at IS NOT NULL FROM address WHERE label='Zone B test' -> true (soft delete).
- **Also covered by automated tests:** backend QuoteIntegrationTest.unserviceable_delivery_and_disabled_pickup_are_denied_without_conversion; backend ContactIntegrationTest.address_validation_and_versioning
- **Unverified against code (confirm while executing):** Address list item text in checkout is '{label} · {street} {building}' (Checkout.tsx) - confirmed in code; the Arabic labels for the address fields come from i18n addressFields (confirmed).; Whether the phone field on the address form needs the +20 prefix: both +2010... and 010... normalise (EgyptianPhone.normalize).

### E2E-DISC-008 — Language toggle switches content, direction and numerals and sends the chosen language with every request

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-GUEST, FX-CUST-A
- **Traces to:** scenarios: TC-150; rules: BR-046; operations: getMealsById, getKitchensById, patchMe, postQuotes; partial: TC-046, TC-128
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
- **Steps:**
  1. (FX-GUEST) Open https://app.<PREVIEW_DOMAIN>/#/ (Arabic). In DevTools Network keep 'Preserve log'. Press 'English'.
  2. (FX-GUEST) Open meal 'Preview koshari' (card title link). Read the slot label and the allergen text.
  3. (FX-GUEST) Press 'العربية'. Re-read the same page.
  4. (FX-GUEST) Open #/kitchen/preview-kitchen in English then Arabic; read the verification note.
  5. (FX-CUST-A) Sign in, add koshari to the basket while in English, open #/checkout and press 'Calculate total'; then press 'العربية' and read the basket. — data: The basket line title stays as stored at add time; labels change language.
  6. (FX-CUST-A) Account page > 'Preferred language for messages' = العربية/English, change to English, press 'Save'. Reload with the top-bar language set to Arabic. — data: This preference is for messages (server side), separate from the screen language.
- **Expected:**
  - UI: English: <html lang=en dir=ltr>; meal page shows 'Preview koshari', price 'EGP 75.00', slot 'N left · Pickup from the kitchen / Delivery', allergen line 'Contains: Gluten'. Arabic: dir=rtl, 'كشري تجريبي', allergen 'فيه: جلوتين', slot 'متبقٍ ١٠ · استلام من المطبخ / توصيل' (Arabic digits).
  - UI: Kitchen note: EN 'This kitchen is operationally verified by Matbakhy staff; this is not a food-safety certification.'; AR 'المطبخ متراجع تشغيليًا من فريق مطبخي؛ ده مش شهادة سلامة غذاء.'
  - UI: No mixed-language fragments: every label in the page changes at once; numbers/prices stay left-to-right inside RTL text (bdi).
  - API: Every XHR after pressing the toggle carries Accept-Language: en (or ar); the meal detail response title is 'Preview koshari' under en and 'كشري تجريبي' under ar.
  - API: PATCH /api/v1/me {expectedVersion, displayName, locale:'en'} -> 200 with locale 'en' and version incremented by 1.
  - DB: Read-only: SELECT locale, version FROM app_user WHERE id='00000000-0000-4000-8000-0000000b0001'; -> 'en' after step 6 (restore to 'ar' at the end).
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'browse: switching Arabic -> English -> Arabic requests and shows each language'; 'a slow answer for a language the customer already left is never shown'
- **Unverified against code (confirm while executing):** Exact ICU output of EGP currency in en-EG ('EGP 75.00' vs 'E£75.00').

### E2E-DISC-113 — Keyboard-only path: browse, choose slot, add to basket, and the replacement dialog returns focus

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** customer-web · **Actors:** FX-GUEST
- **Traces to:** rules: BR-047; partial: TC-047, TC-129
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Desktop Chrome or Firefox, mouse not used. Screen reader optional (VoiceOver/NVDA).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-GUEST) From the home page press Tab repeatedly and note focus order; reach the 'عرض التفاصيل' link of 'كشري تجريبي' and press Enter. — data: Visible focus ring on every stop.
  2. (FX-GUEST) On the meal page Tab to the slot radios, select the first with Space/arrow keys, Tab to the quantity group (decrease, number input, increase), press the 'أضف إلى السلة' button with Enter. — data: Message 'تمت الإضافة إلى السلة.' is announced (role=status).
  3. (FX-GUEST) Go back to home, open 'ملوخية تجريبية' (other kitchen), choose its slot with the keyboard and press the add button. — data: The replacement dialog opens.
  4. (FX-GUEST) Press Tab and observe that initial focus is on 'خليك على سلتك الحالية'; press Escape.
  5. (FX-GUEST) Repeat the add, this time press Enter on 'خليك على سلتك الحالية'.
- **Expected:**
  - UI: Dialog: role=alertdialog titled 'تستبدل السلة؟' with text 'سلتك فيها أطباق من مطبخ تاني أو ميعاد تاني. الطلب الواحد من مطبخ واحد وميعاد واحد.'; initial focus on the Keep button; Escape closes it and shows 'سلتك زي ما هي.'; focus returns to the add-to-cart button.
  - UI: Every control has an accessible name (aria-label/legend); quantity buttons are labelled 'قلّل' / 'زوّد'; the quantity field is labelled 'الكمية'.
  - API: none
  - DB: none
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'is labelled in English after a language switch and reachable by keyboard'; apps/operations-web/e2e/keyboard.e2e.ts
- **Unverified against code (confirm while executing):** Screen-reader announcements and 200 % text-size layout (browser zoom 200 %) are observations to record, not asserted by code.

### E2E-DISC-105 — Unverified phone blocks quoting; wrong or repeated codes never verify and are rate limited

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Isolated disposable stack only
- **Surface:** customer-web, api · **Actors:** FX-CUST-B · **Needs:** isolated stack · **Destructive**
- **Traces to:** rules: BR-003, BR-004, BR-054; operations: postMePhoneChallenges, postMePhoneChallengesVerify, postQuotes; partial: TC-003, TC-004, TC-054, TC-110
- **Prerequisites:**
  - FX-ISOLATED stack (destructive to the fixture account). In the shared preview the seeded customers are already verified and a changed number could never be re-verified (the SMS code is not retrievable by testers).
  - Fixture: make FX-CUST-B unverified in the isolated database: UPDATE app_user SET phone_verified_at=NULL WHERE id='00000000-0000-4000-8000-0000000b0002'; (or use the staging fixture channel deploy/web-preview/e2e-fixtures/customer_phone_verified.sql in reverse). Basket has koshari x1.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
- **Steps:**
  1. (FX-CUST-B) Sign in as preview-customer-2 and open #/checkout with a koshari basket. — data: The checkout form is replaced by the section 'التحقق من رقم الهاتف'.
  2. (FX-CUST-B) Console: await call('POST','/api/v1/quotes',{items:[{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}],slotId:'<koshari slot id>',fulfillmentMode:'PICKUP',paymentMethod:'COD'}) — data: Slot id from GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001.
  3. (FX-CUST-B) In 'رقم الموبايل المصري' type +20223456789 (landline) and press 'إرسال الرمز'.
  4. (FX-CUST-B) Type +201000000102 and press 'إرسال الرمز'. Immediately press it again. — data: Second press within 60 s.
  5. (FX-CUST-B) Type the wrong code 000000 and press 'تحقق' five times (use 111111 if the real code happens to be 000000). — data: The real code is not visible to testers; every code you type is wrong.
  6. (FX-CUST-B) Press 'تحقق' a sixth time.
- **Expected:**
  - UI: Step 1: heading 'التحقق من رقم الهاتف', fields 'رقم الموبايل المصري' and, after sending, 'رمز التحقق'.
  - UI: Step 3: error line 'تحقق من البيانات.' and no code field appears.
  - UI: Step 4: code field appears after the first press; the second press shows the generic error line (rate limited).
  - UI: Steps 5-6: first five attempts show 'الرمز غير صحيح.'; the sixth also fails (rate-limited challenge); the account stays unverified and checkout never appears.
  - API: Step 2: POST /api/v1/quotes -> 409 ApiError.code 'CONTACT_VERIFICATION_REQUIRED'.
  - API: Step 3: POST /api/v1/me/phone/challenges {phoneE164:'+20223456789'} -> 422 VALIDATION_FAILED fieldErrors[{field:'phoneE164',code:'EGYPTIAN_MOBILE'}] (no SMS).
  - API: Step 4: first send -> 201 {id, expiresAt (+5 min), resendAvailableAt (+60 s)}; second send within 60 s -> 429 RATE_LIMITED retryable=true.
  - API: Steps 5-6: five POST .../verify {code:'000000'} -> 422 VERIFICATION_FAILED each; sixth -> 429 RATE_LIMITED. A 7th request with a correct code would still be refused (challenge locked).
  - DB: Read-only: SELECT attempts, consumed_at FROM phone_challenge WHERE user_id='00000000-0000-4000-8000-0000000b0002' ORDER BY created_at DESC LIMIT 1 -> attempts=5, consumed_at NULL; code_hmac is a 64-hex string (the code itself is never stored). SELECT phone_verified_at FROM app_user WHERE id='00000000-0000-4000-8000-0000000b0002' -> NULL. Count of phone_challenge rows for the invalid number = 0.
- **Also covered by automated tests:** backend ContactIntegrationTest (correct_code_verifies_once_and_replay_fails, challenge_locks_after_five_failed_attempts, resend_interval_and_hourly_limits_are_enforced, invalid_numbers_are_rejected_without_sending, changing_a_verified_phone_requires_verifying_the_new_number)
- **Unverified against code (confirm while executing):** The UI shows the generic error for a 429 (errors map has no RATE_LIMITED entry -> 'حدث خطأ.' / 'Something went wrong.'); record the exact text.; Completing a successful verification is impossible for a manual tester in every environment: FakePhoneVerificationSender keeps the code in memory only. Use the DB fixture customer_phone_verified.sql to re-verify.

### E2E-DISC-106 — Addresses are private to their owner; invalid input is rejected; deletion archives

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** scenarios: TC-005; rules: BR-005, BR-006; operations: postMeAddresses, getMeAddresses, patchMeAddressesById, deleteMeAddressesById; partial: TC-006, TC-110
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Both customers phone-verified. Keep two browser profiles (A and B) so the helper is defined once in each.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) Console (A): const r=await call('POST','/api/v1/me/addresses',{label:'E2E home',recipientName:'E2E Recipient',phone:'01012345678',governorate:'Cairo',city:'Test city',zoneId:'00000000-0000-4000-8000-00000000a001',street:'E2E street 1',building:'4',latitude:30.04442,longitude:31.235712}); r — data: Keep r.body.id as ADDR.
  2. (FX-CUST-B) Console (B): await call('GET','/api/v1/me/addresses'); await call('PATCH','/api/v1/me/addresses/<ADDR>',{expectedVersion:0,street:'hijack'}); await call('DELETE','/api/v1/me/addresses/<ADDR>')
  3. (FX-CUST-A) Console (A): create invalid variants: phone '0223456789'; zoneId '00000000-0000-4000-8000-00000000a003'; latitude 30.1 without longitude; no building; extra field userId:'00000000-0000-4000-8000-0000000b0002'. — data: One POST per variant, other fields as in step 1.
  4. (FX-CUST-A) Console (A): PATCH /api/v1/me/addresses/<ADDR> {expectedVersion:0,floor:'3'} then repeat the same PATCH with expectedVersion:0 again.
  5. (FX-CUST-A) Open the account page, section 'عناويني', and press 'حذف' next to 'E2E home'. Then call GET /api/v1/me/addresses.
- **Expected:**
  - UI: Step 5: the row 'E2E home · E2E street 1 4' disappears from 'عناويني' and from the checkout address list.
  - UI: B never sees A's address in checkout or account.
  - API: Step 1: 201 Address {id, label, recipientName, phoneE164:'+201012345678', zoneId, version:0}.
  - API: Step 2: B's list does not contain ADDR; PATCH -> 404 NOT_FOUND (body contains no 'E2E Recipient'); DELETE -> 404 NOT_FOUND.
  - API: Step 3: phone -> 422 (code EGYPTIAN_MOBILE); inactive zone -> 422 fieldErrors zoneId ZONE_NOT_SERVED; latitude only -> 422 code PAIR_REQUIRED; missing building -> 422; extra field userId -> 400 MALFORMED_REQUEST.
  - API: Step 4: first PATCH -> 200 {floor:'3', version:1}; second PATCH with stale expectedVersion 0 -> 409 VERSION_CONFLICT.
  - API: Step 5: DELETE -> 204; GET list no longer contains ADDR.
  - DB: Read-only: SELECT user_id, street, archived_at IS NOT NULL AS archived FROM address WHERE id='<ADDR>'; -> user_id=00000000-0000-4000-8000-0000000b0001, street 'E2E street 1' (unchanged by B), archived=true after step 5 (row retained: soft delete).
- **Also covered by automated tests:** backend ContactIntegrationTest.addresses_are_private_to_their_owner; backend ContactIntegrationTest.address_validation_and_versioning
- **Unverified against code (confirm while executing):** Which fieldError field name the phone failure uses (test asserts only status 422; address phone field is 'phone' in the request).

### E2E-DISC-107 — Favorites are per customer, idempotent, and flagged unavailable instead of disappearing

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B, FX-GUEST, FX-COOK-A
- **Traces to:** scenarios: TC-040; rules: BR-040, BR-017; operations: getMeFavorites, putMeFavoritesById, deleteMeFavoritesById; partial: TC-128, TC-017
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Neither customer has favorites at the start (account page section 'المفضلة' empty).
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-GUEST) Open meal 'كشري تجريبي' while signed out and look for a favorite button. Console: fetch('https://api.<PREVIEW_DOMAIN>/api/v1/me/favorites').
  2. (FX-CUST-A) Sign in, open the same meal, press 'أضف للمفضلة'. — data: Button becomes pressed with text 'إزالة من المفضلة'.
  3. (FX-CUST-A) Console: await call('PUT','/api/v1/me/favorites/00000000-0000-4000-8000-0000000bb001'); await call('PUT','/api/v1/me/favorites/00000000-0000-4000-8000-0000000bb001'); await call('GET','/api/v1/me/favorites') — data: Idempotent add.
  4. (FX-CUST-B) Sign in as B in another profile; open account section 'المفضلة'.
  5. (FX-COOK-A) Pause the kitchen (operations web Settings > 'إيقاف البيع مؤقتًا'). — data: Coordinate: this blocks kitchen 1 for other testers.
  6. (FX-CUST-A) Reload account page > 'المفضلة'.
  7. (FX-COOK-A) Resume sales (button 'استئناف البيع').
  8. (FX-CUST-A) Remove the favorite with the 'حذف' button in the list, then console: await call('DELETE','/api/v1/me/favorites/00000000-0000-4000-8000-0000000bb001'); await call('DELETE','/api/v1/me/favorites/00000000-0000-4000-8000-0000000bb001') — data: Second delete must also succeed.
- **Expected:**
  - UI: Signed out: no favorite button on the meal page. B's 'المفضلة' list is empty while A has one favorite.
  - UI: Paused kitchen: A's list still lists the favorite as plain text (not a link) with the badge 'غير متاح حاليًا' (the title/price may be blank for an unavailable meal: record what is shown). After resume it is a link again.
  - UI: After removal the list is empty.
  - API: Guest: GET /api/v1/me/favorites -> 401 UNAUTHENTICATED.
  - API: PUT twice -> 200 both, same Favorite {mealId, title, priceMinor:7500, kitchenName:'مطبخ المعاينة', available:true}; GET shows exactly one item.
  - API: Paused kitchen: GET list item has available=false (title/price retained).
  - API: DELETE twice -> 204 both.
  - DB: Read-only: SELECT count(*) FROM favorite WHERE user_id='00000000-0000-4000-8000-0000000b0001' AND meal_id='00000000-0000-4000-8000-0000000bb001' -> 1 after step 3 (primary key (user_id, meal_id)), 0 after the last step; same query for B -> 0 throughout.
- **Also covered by automated tests:** backend ReviewsAndFavoritesIntegrationTest.favorites_are_idempotent_annotate_unavailable_meals_and_orders_stay_private; apps/customer-web/src/App.test.tsx 'toggles a favorite on the meal page...'
- **Unverified against code (confirm while executing):** Whether Favorite.title and priceMinor stay populated for an unavailable meal (both are nullable in the contract; the integration test asserts only available=false).; Arabic digits in the price of the favorites list (formatMoney ar-EG).

### E2E-DISC-108 — Customer inbox lists order updates once, marks read, and is private

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-CUST-B, FX-COOK-A
- **Traces to:** rules: BR-037, BR-038; operations: getNotifications, putNotificationsRead; partial: TC-037, TC-038, TC-121, TC-128
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - FX-CUST-A has one PENDING_ACCEPTANCE pickup order (koshari x1, COD) placed in this case's step 1. The notification dispatcher runs in the API (outbox poll); allow up to a minute for inbox rows.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+2 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Place a pickup COD order for koshari x1 (see E2E-BASKET-009 steps 1-4 then #/checkout: 'استلام من المطبخ' > 'احسب الإجمالي' > terms checkbox > 'تأكيد الطلب'). Note the order number MB-1xxxxx.
  2. (FX-COOK-A) Operations web, tab 'الطلبات': open the order and press 'قبول'. — data: Order moves to ACCEPTED.
  3. (FX-CUST-A) Account page > 'الإشعارات'. Wait up to 60 s and reload.
  4. (FX-CUST-A) Press 'تمت القراءة' on the entry. Reload.
  5. (FX-CUST-A) Console: const n=await call('GET','/api/v1/notifications?limit=20'); n; await call('GET','/api/v1/notifications?unreadOnly=true')
  6. (FX-CUST-B) Console (B): await call('PUT','/api/v1/notifications/<A notification id>/read')
  7. (FX-CUST-A) Cleanup: open the order; if still cancellable use 'إلغاء الطلب', otherwise ask FX-COOK-A to continue/cancel via the cook workflow.
- **Expected:**
  - UI: One inbox row: '<date, time> · تم القبول MB-1xxxxx' (the state label, then the order number as a link to #/orders/<id>), initially with a 'تمت القراءة' button; after pressing it the row is greyed and the button disappears.
  - UI: No row appears for the order placement itself (the placement notification goes to the cook, not the customer).
  - API: GET /api/v1/notifications -> 200 items[{id,type:'ORDER_ACCEPTED',orderId,params:{orderNumber,state:'ACCEPTED'},createdAt,readAt:null}], newest first; after the read call readAt is set; unreadOnly=true excludes it.
  - API: B reading A's notification -> 404 NOT_FOUND (not 200).
  - DB: Read-only: SELECT type, read_at IS NOT NULL FROM notification WHERE recipient_id='00000000-0000-4000-8000-0000000b0001' ORDER BY created_at DESC LIMIT 3; exactly one ORDER_ACCEPTED row per order (UNIQUE (recipient_id, dedup_key)). SELECT channel, status FROM notification_delivery WHERE notification_id='<id>'; -> EMAIL with status SENT or SKIPPED (preview provider sends nothing), no PUSH row while push is disabled.
- **Also covered by automated tests:** backend NotificationIntegrationTest.inbox_pages_marks_read_per_owner_and_filters_unread; backend NotificationIntegrationTest.two_workers_and_duplicate_events_produce_one_inbox_record_and_one_send
- **Unverified against code (confirm while executing):** Whether the dispatcher puts payload.state=ACCEPTED in params (the web renders t.state[params.state] else the type name 'ORDER_ACCEPTED'); record which text you see.; Status B receives when reading A's notification (404 inferred from 'marks read per owner' test name only).

### E2E-DISC-109 — Profile update uses optimistic concurrency and rejects privileged fields

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-003, BR-006; operations: getMe, patchMe; partial: TC-110, TC-128
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Display name of FX-CUST-A is 'Preview Customer 1' (restore it at the end).
- **Steps:**
  1. (FX-CUST-A) Account page > 'البيانات الشخصية': set 'الاسم' to 'E2E Name' and press 'حفظ'.
  2. (FX-CUST-A) Console: const me=(await call('GET','/api/v1/me')).body; await call('PATCH','/api/v1/me',{expectedVersion:me.version-1,displayName:'stale'}) — data: Stale version.
  3. (FX-CUST-A) Console: await call('PATCH','/api/v1/me',{expectedVersion:me.version,displayName:'x',status:'ACTIVE',phoneE164:'+201000000999'}) — data: Privileged and unknown fields.
  4. (FX-CUST-A) Console: await call('PATCH','/api/v1/me',{expectedVersion:me.version,displayName:'a'.repeat(121)})
  5. (FX-CUST-A) Restore the name to 'Preview Customer 1' in the UI.
- **Expected:**
  - UI: Step 1: 'تم الحفظ.' appears and the name persists after reload.
  - API: Step 2: 409 VERSION_CONFLICT. Step 3: 400 MALFORMED_REQUEST (unknown fields rejected by strict JSON). Step 4: 422 VALIDATION_FAILED with fieldErrors[{field:'displayName',code:'Size'}] (Bean Validation code via ApiExceptionHandler.invalid).
  - DB: Read-only: SELECT display_name, version, phone_e164, status FROM app_user WHERE id='00000000-0000-4000-8000-0000000b0001'; -> display_name 'E2E Name' after step 1, version +1 per successful save, phone_e164 and status unchanged by step 3.
- **Also covered by automated tests:** backend StaffAccessIntegrationTest.role_fields_in_customer_requests_are_rejected
- **Unverified against code (confirm while executing):** The 121-character case is not covered by a test; 422 is derived from ProfileController @Size(max=120) and ApiExceptionHandler.invalid (web/ApiExceptionHandler.java:41-47).

### E2E-DISC-110 — Only the owner of a COMPLETED order can review it, once; the rating shows on the meal page

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B, FX-COOK-A
- **Traces to:** scenarios: TC-039; rules: BR-039; operations: postOrdersReviews, getKitchensReviews; partial: TC-128
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - A COMPLETED pickup order of FX-CUST-A at kitchen 'مطبخ المعاينة' exists (cook accepted, prepared, marked ready and confirmed handover with the customer's handover code: follow the cook-workflow cases) and has no review yet. Record its id as ORDER.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-B) Console (B): await call('POST','/api/v1/orders/<ORDER>/reviews',{rating:5}) — data: Not the owner.
  2. (FX-CUST-A) Open #/orders/<ORDER>. In 'قيّم طلبك' choose التقييم = 4, comment 'E2E review text', press 'إرسال التقييم'.
  3. (FX-CUST-A) Console: await call('POST','/api/v1/orders/<ORDER>/reviews',{rating:5}); await call('POST','/api/v1/orders/<ORDER>/reviews',{rating:6})
  4. (FX-GUEST) Open https://app.<PREVIEW_DOMAIN>/#/meal/00000000-0000-4000-8000-0000000bb001 (signed out) and read the 'التقييمات' section.
  5. (FX-CUST-A) Place and leave a PENDING order of A, then call POST /api/v1/orders/<pending id>/reviews {rating:5}. — data: Order not completed.
- **Expected:**
  - UI: Step 2: the form is replaced by 'شكرًا على تقييمك.'.
  - UI: Step 4: summary line '٤٫٠ من 5 (1 تقييم)' (4.0 of 5, 1 review) and the row '★★★★ E2E review text <date>' with no customer name and no order number.
  - API: Step 1: 404 NOT_FOUND. Step 2: 201. Step 3: first call 409 STATE_CONFLICT with fieldErrors[{field:'orderId',code:'ALREADY_REVIEWED'}]; second call 422 VALIDATION_FAILED (rating must be 1..5). Step 5: 409 STATE_CONFLICT with fieldErrors[{field:'state',code:'PENDING_ACCEPTANCE'}].
  - API: GET /api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/reviews -> summary.count=1, averageTenths=40 when this is the only visible review.
  - DB: Read-only: SELECT rating, visibility, customer_id FROM review WHERE order_id='<ORDER>'; -> rating 4, VISIBLE, customer_id=00000000-0000-4000-8000-0000000b0001; exactly one row (UNIQUE order_id).
- **Also covered by automated tests:** backend ReviewsAndFavoritesIntegrationTest.only_the_owner_of_a_completed_order_reviews_once_and_hidden_reviews_do_not_count; apps/customer-web/src/App.test.tsx 'reviews a completed order once and offers ordering again at a new price'
- **Unverified against code (confirm while executing):** Status code for a rating outside 1..5 (assumed 422 VALIDATION_FAILED).; Average rendering: ratingOf uses toLocaleString; exact string in each language not asserted.

### E2E-DISC-111 — A signed-in customer can report a visible review once, with a reason

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-B, FX-GUEST
- **Traces to:** rules: BR-039; operations: postReviewsReports, getKitchensReviews; partial: TC-039, TC-128
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - A visible review exists on kitchen 'مطبخ المعاينة' (output of E2E-DISC-110); FX-CUST-B is not its author.
- **Steps:**
  1. (FX-GUEST) Open the meal page signed out and look at the review row. — data: No report control.
  2. (FX-CUST-B) Sign in, open https://app.<PREVIEW_DOMAIN>/#/meal/00000000-0000-4000-8000-0000000bb001, in the review row open the select 'إبلاغ' and choose 'مسيء'.
  3. (FX-CUST-B) Console: const id=(await call('GET','/api/v1/kitchens/00000000-0000-4000-8000-0000000bd001/reviews')).body.items[0].id; await call('POST','/api/v1/reviews/'+id+'/reports',{reasonCode:'SPAM'}) — data: Second report by the same customer.
  4. (FX-CUST-B) Console: await call('POST','/api/v1/reviews/'+id+'/reports',{reasonCode:'NOPE'})
- **Expected:**
  - UI: Step 2: the select is replaced by 'تم الإبلاغ. شكرًا لك.'; the review stays visible to everyone (reports do not hide it).
  - API: Step 2: POST /api/v1/reviews/{id}/reports -> 204. Step 3: repeat by the same reporter -> 204 again, no second row (ReviewService.report inserts ON CONFLICT DO NOTHING). Step 4: 422 VALIDATION_FAILED fieldErrors[{field:'reasonCode',code:'ALLOWED_NOT_ABOUT_ORDER|OFFENSIVE|OTHER|PRIVATE_INFO|SPAM'}]. More than the hourly cap of reports -> 429 RATE_LIMITED.
  - DB: Read-only: SELECT reason_code FROM review_report WHERE review_id='<id>' AND reporter_id='00000000-0000-4000-8000-0000000b0002'; exactly one row; SELECT visibility FROM review WHERE id='<id>'; -> VISIBLE.
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'toggles a favorite on the meal page and reports a review...'
- **Unverified against code (confirm while executing):** The hourly report cap (REPORTS_PER_HOUR) value was not read.

### E2E-DISC-112 — Organic browsing activity is counted once per visitor per day without any identity

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-GUEST
- **Traces to:** scenarios: TC-164; rules: BR-019; decisions: D17; operations: postActivity
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Private window (new sessionStorage = new anonymous visitor id). Read-only DB access to the preview database.
  - Product facts (D17, docs/product/10): kinds MEAL_IMPRESSION, MEAL_VIEW, KITCHEN_VIEW, ADD_TO_CART; once per visitor, kind, subject and Cairo day; visitor id kept in sessionStorage only. There is NO consent banner or opt-out in the web app (privacy notice is open item O06).
- **Steps:**
  1. (FX-GUEST) Record DB baseline: SELECT kind, count(*) FROM activity_daily WHERE day=(now() AT TIME ZONE 'Africa/Cairo')::date AND subject_id IN ('00000000-0000-4000-8000-0000000bb001','00000000-0000-4000-8000-0000000bd001') GROUP BY kind;
  2. (FX-GUEST) Open the home page (dishes list shows koshari), then open meal 'كشري تجريبي', then pick a slot and press 'أضف إلى السلة'. Wait 3 s. — data: Events are batched after 1 s: DevTools Network shows POST /api/v1/activity {visitorId, events:[...]}.
  3. (FX-GUEST) Reload the meal page three times and add the meal to the basket again.
  4. (FX-GUEST) Open #/kitchen/preview-kitchen once.
  5. (FX-GUEST) Re-run the baseline SQL, then run \d activity_daily to list the table columns.
  6. (FX-GUEST) Console: await fetch('https://api.<PREVIEW_DOMAIN>/api/v1/activity',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({visitorId:'e2e-bot-visitor-1',events:[{kind:'MEAL_VIEW',subjectId:'00000000-0000-4000-8000-000000000999'}]})}).then(r=>r.status) — data: Unknown subject; CSRF may require the X-XSRF-TOKEN header: reuse the helper if the call returns 403.
- **Expected:**
  - UI: No cookie banner, consent dialog or tracking-related prompt appears at any point (record as an observation against the pending privacy notice).
  - API: POST /api/v1/activity -> 202 for the batch, for repeated batches and for an unknown subject (the response never reveals whether an event counted).
  - DB: After steps 2-4 compared with baseline: +1 row each for MEAL_IMPRESSION and MEAL_VIEW and ADD_TO_CART for the koshari id, +1 KITCHEN_VIEW for the kitchen id; reloads and repeated add-to-cart add 0 rows (primary key (day, kind, subject_id, visitor_hash)). Table activity_daily has only day, kind, subject_id, kitchen_id, visitor_hash (32-byte hash): no user id, IP, device or free-text column. No row exists for subject ...0999.
- **Also covered by automated tests:** backend ActivityInsightsIntegrationTest.activity_is_deduplicated_per_visitor_and_day_bots_are_ignored_and_no_visitor_identity_is_stored; apps/customer-web/src/App.test.tsx 'sends one batch for a meal view and an add to cart...'; apps/operations-web/e2e/customer-journey.e2e.ts (meal_activity_kinds)
- **Unverified against code (confirm while executing):** Whether the anonymous POST needs the XSRF header on the preview (the SPA sends it when the cookie exists).

### E2E-DISC-114 — Account deletion request disables the account immediately and is idempotent

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** customer-web, api · **Actors:** FX-CUST-A · **Needs:** isolated stack · **Destructive**
- **Traces to:** rules: BR-055, BR-001; operations: postMeDeletionRequests, getMe; partial: TC-055, TC-128, TC-139
- **Prerequisites:**
  - FX-ISOLATED stack only; consumes the fixture account (restore with a fresh stack or UPDATE app_user SET status='ACTIVE' and delete the deletion_request row).
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
- **Steps:**
  1. (FX-CUST-A) Account page > 'طلب حذف الحساب': without the checkbox the button 'طلب حذف الحساب' is disabled; tick 'أؤكد طلب حذف حسابي' and press the red button.
  2. (FX-CUST-A) Read the status text, then reload the page.
  3. (FX-CUST-A) Console (before reload if possible): await call('POST','/api/v1/me/deletion-requests',{confirm:false,policyVersion:'retention-policy-draft'}); await call('POST','/api/v1/me/deletion-requests',{confirm:true,policyVersion:'retention-policy-draft'}); await call('GET','/api/v1/me')
- **Expected:**
  - UI: After confirmation: 'تم استلام طلب الحذف؛ تم إيقاف الحساب.' is shown; on reload the app treats the user as unavailable (no profile data).
  - API: confirm:false -> 422 VALIDATION_FAILED. confirm:true -> 200 DeletionRequest with the same id when repeated. Subsequent GET /api/v1/me -> 403 ACCOUNT_DISABLED with no profile data.
  - DB: Read-only: SELECT status FROM app_user WHERE id='00000000-0000-4000-8000-0000000b0001' -> DELETION_REQUESTED; SELECT state, policy_version FROM deletion_request WHERE user_id='00000000-0000-4000-8000-0000000b0001' -> exactly one row state RECEIVED (partial unique index); order and ledger rows are untouched.
- **Also covered by automated tests:** backend ContactIntegrationTest.deletion_request_disables_access_and_is_idempotent; apps/customer-web/src/App.test.tsx 'shows the account page only when signed in and sends the deletion request...'
- **Unverified against code (confirm while executing):** The policyVersion sent by the web ('retention-policy-draft') is a draft string; the retention policy is not approved (open item O06).

### E2E-DISC-117 — A persistent demo-environment notice is visible in both languages on the preview

- **Status:** NOT_RUN · **Priority:** P2 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-GUEST
- **Traces to:** rules: BR-045; decisions: D10; operations: getConfigurationPublic; partial: TC-157
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
- **Steps:**
  1. (FX-GUEST) Open https://app.<PREVIEW_DOMAIN>/#/ and read the note at the top (role=note).
  2. (FX-GUEST) Press 'English' and read it again; navigate to #/kitchen/preview-kitchen and #/checkout.
  3. (FX-GUEST) Console: await (await fetch('https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public')).json()
- **Expected:**
  - UI: AR: 'بيئة تجريبية' + 'بيانات تجريبية فقط. الطلبات والعربون والمدفوعات هنا محاكاة، ولا يُرسل أي إشعار حقيقي.' EN: 'Demo environment' + 'Test data only. Orders, deposits and payments here are simulated, and no real notification is sent.' The note stays on every route.
  - API: GET /api/v1/configuration/public -> 200 {orderingAvailable:true, orderLimits:{maxQuantityPerLine:10,maxLinesPerOrder:20,policyVersion:1}, environment:{name:'PREVIEW',demo:true}}; no commission/tax/staff field.
  - DB: none
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'shows a persistent note in Arabic and in English when the API reports a demo environment'; backend PublicConfigurationIntegrationTest
- **Unverified against code (confirm while executing):** maxLinesPerOrder value 20 (QuoteService MAX_ITEMS / CommercePolicies.MAX_LINES_PER_ORDER); policyVersion 1 assumes the preview kept the synthetic policy version 1.

### E2E-DISC-115 — Native app lists meals from the server, searches, and says plainly when it is offline

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-DEVICE-ANDROID, FX-DEVICE-IOS, FX-GUEST · **Needs:** physical device
- **Traces to:** rules: BR-049, BR-051, BR-052; operations: getMeals, getMealsById; partial: TC-049, TC-132, TC-134, TC-135, TC-051, TC-052
- **Prerequisites:**
  - A signed device build pointing at the preview API (physical device; CI simulator evidence is not equivalent). Seed as in the web cases. Test once on each platform. Device language set to Arabic, then English.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-DEVICE-ANDROID) Launch the app (signed out). Read the Discover tab ('اكتشف'). — data: testID discover, search field label 'ابحث عن أكلة أو مطبخ'.
  2. (FX-DEVICE-ANDROID) Search for كشري and press the search button. — data: Search testID 'search'.
  3. (FX-DEVICE-ANDROID) Tap the meal card (testID meal-<id>) and read slots, quantity, ingredients/allergens.
  4. (FX-DEVICE-ANDROID) Switch the device to airplane mode, pull to refresh or reopen Discover.
  5. (FX-DEVICE-ANDROID) Disable airplane mode, tap 'إعادة المحاولة'/Retry if offered.
  6. (FX-DEVICE-IOS) Repeat steps 1-5 on iOS.
- **Expected:**
  - UI: Discover shows 'كشري تجريبي' etc. with Arabic RTL layout; tabs 'اكتشف', 'المفضلة', 'طلباتي', 'حسابي'.
  - UI: Offline: banner 'لا يوجد اتصال بالإنترنت. ما يظهر ليس حالة الطبخ أو الكميات الحالية.' (EN: "You're offline. What you see is not current cooking status or stock."); no order can be placed offline; no stale stock presented as current.
  - API: Requests are GET /api/v1/meals?limit=50&sort=NEWEST[&q=...] with Accept-Language matching the app language; no filters other than search exist in the app.
  - DB: none
- **Also covered by automated tests:** apps/customer-mobile/src/App.test.tsx (component tests only, no device run)
- **Unverified against code (confirm while executing):** Behaviour of pull-to-refresh vs reopen (not read); English search label is 'Search dishes or kitchens'.; The Arabic offline banner wording was read from apps/customer-mobile/src/i18n.ts:17 only.

### E2E-BASKET-009 — Basket lines can be added, edited and removed; estimates use integer piastres and survive reload

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web · **Actors:** FX-CUST-A
- **Traces to:** scenarios: TC-149; rules: BR-018, BR-019; operations: getMealsById, postActivity; partial: TC-018, TC-147, TC-151
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - FX-CUST-A signed in with an empty basket (matbakhy.cart absent in Local Storage; page '#/checkout' shows 'السلة فارغة.').
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Open https://app.<PREVIEW_DOMAIN>/#/meal/00000000-0000-4000-8000-0000000bb001. Without choosing a slot, read the add button. — data: Button 'أضف إلى السلة · ٧٥٫٠٠ ج.م' is disabled.
  2. (FX-CUST-A) Select the first slot radio (earliest 13:00 slot). Press 'زوّد' once so the quantity input shows 2. Press 'أضف إلى السلة'. — data: Button label now ends with ١٥٠٫٠٠ ج.م before pressing.
  3. (FX-CUST-A) Press the top-bar link 'السلة'. Read kitchen, slot pill, the line, the estimate and the note.
  4. (FX-CUST-A) In the line press 'زيادة الكمية: كشري تجريبي' once (quantity 3), then 'تقليل الكمية: كشري تجريبي' once (2), then increase to 3 again.
  5. (FX-CUST-A) Go to meal 'محشي تجريبي' (00000000-0000-4000-8000-0000000bb002), choose the SAME first slot, quantity 1, press add. Return to السلة. — data: No replacement dialog.
  6. (FX-CUST-A) Press browser reload (F5) on #/checkout.
  7. (FX-CUST-A) Open 'كشري تجريبي' again, choose the same slot, quantity 1, press add. — data: Re-adding the same meal.
  8. (FX-CUST-A) In السلة press 'حذف' ('إزالة محشي تجريبي') then 'حذف' on the koshari line.
- **Expected:**
  - UI: Step 3: 'المطبخ: مطبخ المعاينة'; one line 'كشري تجريبي' with quantity 2 and line amount ١٥٠٫٠٠ ج.م; 'الإجمالي التقديري للأطباق' = ١٥٠٫٠٠ ج.م; note 'ده تقدير من الأسعار اللي ظهرت وقت الإضافة. الإجمالي النهائي بالسعر الحالي بيظهر لما تضغط "احسب الإجمالي".'
  - UI: Step 4: quantity 3 -> line ٢٢٥٫٠٠ and estimate ٢٢٥٫٠٠; back to 2 -> ١٥٠٫٠٠. The 'زيادة' button is disabled at the limit (min(10, portions left)); 'تقليل' disabled at 1.
  - UI: Step 5: two lines; estimate = 225.00 (koshari at quantity 3, left by step 4) + 180.00 = ٤٠٥٫٠٠ ج.م.
  - UI: Step 6: the same two lines and estimate ٤٠٥٫٠٠ ج.م after reload (state kept in Local Storage key matbakhy.cart).
  - UI: Step 7: koshari line is REPLACED, not summed: quantity 1, line ٧٥٫٠٠, estimate = 75.00 + 180.00 = ٢٥٥٫٠٠ ج.م.
  - UI: Step 8: after removing both lines the page shows 'السلة فارغة.' and a button 'الرئيسية'.
  - API: No POST /api/v1/quotes and no POST /api/v1/orders is sent during steps 1-8 (Network tab): the basket is client-side only. Allowed requests: GET /api/v1/meals/{id}, GET /api/v1/configuration/public, POST /api/v1/activity (ADD_TO_CART).
  - DB: Read-only: SELECT count(*) FROM quote WHERE customer_id='00000000-0000-4000-8000-0000000b0001' AND created_at > '<case start>' -> 0; SELECT reserved, allocated FROM meal_slot WHERE meal_id IN ('00000000-0000-4000-8000-0000000bb001','00000000-0000-4000-8000-0000000bb002') -> unchanged from the start of the case (basket reserves no stock).
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'edits quantities and removes lines with correct integer arithmetic, then shows the empty state'; 'adds another meal of the same kitchen and slot without asking, and re-adding a meal updates its line instead of duplicating it (BR-018)'
- **Unverified against code (confirm while executing):** Exact label of the Arabic increase/decrease buttons on the checkout rows: 'زيادة الكمية: {title}' / 'تقليل الكمية: {title}' per i18n increaseOf/decreaseOf (the meal page uses 'زوّد'/'قلّل').; Intl rendering of currency digits.

### E2E-BASKET-010 — A second kitchen or slot never merges silently: the customer must confirm replacing the basket

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web · **Actors:** FX-CUST-A
- **Traces to:** scenarios: TC-147; rules: BR-018; operations: getMealsById; partial: TC-018
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - At least two seeded slots for kitchen 'مطبخ المعاينة' (day +1 and day +2) and one for 'مطبخ تجريبي تاني'. FX-CUST-A basket empty.
  - Slot day for this case: seeded day+1 and 2 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Add 'كشري تجريبي' x1 in the day+1 slot to the basket (as in E2E-BASKET-009).
  2. (FX-CUST-A) Open 'ملوخية تجريبية' (other kitchen), select its slot, press 'أضف إلى السلة'. — data: A dialog opens.
  3. (FX-CUST-A) Press 'خليك على سلتك الحالية'. Open السلة.
  4. (FX-CUST-A) Add molokhia again; this time press 'استبدال السلة'. Open السلة.
  5. (FX-CUST-A) Empty the basket, add 'كشري تجريبي' x1 in the day+1 slot, then open the same meal and select the DAY+2 slot and press add. — data: Same kitchen, different slot.
  6. (FX-CUST-A) Press 'استبدال السلة'. Open السلة.
  7. (FX-CUST-A) Add 'محشي تجريبي' x1 in the same day+2 slot. — data: Same kitchen and slot.
- **Expected:**
  - UI: Steps 2 and 5: role=alertdialog 'تستبدل السلة؟' with text 'سلتك فيها أطباق من مطبخ تاني أو ميعاد تاني. الطلب الواحد من مطبخ واحد وميعاد واحد.' and two buttons 'استبدال السلة' / 'خليك على سلتك الحالية'; nothing is written to the basket before a choice.
  - UI: Step 3: message 'سلتك زي ما هي.'; the basket still holds only koshari (day+1 slot).
  - UI: Step 4: message 'تمت الإضافة إلى السلة.'; the basket holds only 'ملوخية تجريبية' with 'المطبخ: مطبخ تجريبي تاني'; koshari is gone.
  - UI: Step 6: basket holds koshari with the slot pill of day+2 13:00. Step 7: no dialog; two lines in one slot.
  - API: No server call changes anything; POST /api/v1/activity may send ADD_TO_CART only after a confirmed add (not after 'keep').
  - DB: none (basket is client-side).
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'asks before replacing a basket of another kitchen or slot and keeps it when the customer cancels'; 'all four replacement paths keep or replace the basket and its estimate exactly (TC-147)'; apps/operations-web/e2e/customer-journey.e2e.ts (TC-147 part)
- **Unverified against code (confirm while executing):** The dialog is rendered inline (role=alertdialog), not as a modal overlay; focus trapping is not implemented in code, only initial focus on 'keep' and focus return.

### E2E-BASKET-021 — The server refuses quotes that mix kitchens, slots, duplicate lines or meals not offered in the slot

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** scenarios: TC-018; rules: BR-018, BR-019; operations: postQuotes; partial: TC-117
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Console variables: const K=(await call('GET','/api/v1/meals/00000000-0000-4000-8000-0000000bb001')).body.nextSlots[0].id (kitchen 1 slot), const M=(await call('GET','/api/v1/meals/00000000-0000-4000-8000-0000000bb003')).body.nextSlots[0].id (kitchen 2 slot).
  - For step 4 FX-COOK-A creates a slot of kitchen 1 that offers ONLY 'محشي تجريبي' (capacity for koshari left empty).
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], M) — data: Meal of kitchen 1 with the slot of kitchen 2.
  2. (FX-CUST-A) await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1},{mealId:'00000000-0000-4000-8000-0000000bb003',quantity:1}], K) — data: Two kitchens in one request.
  3. (FX-CUST-A) await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1},{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:2}], K) — data: Duplicate meal line.
  4. (FX-COOK-A) Create and open the slot described in the prerequisites (FX-COOK-A creates slots in the operations web (https://ops.<PREVIEW_DOMAIN>, tab 'المواعيد', form 'موعد جديد'): fields التاريخ (date), البداية (start HH:MM), النهاية (end), أقصى عدد حصص, مهلة الطلب (دقائق) (minimum 30), then 'حصص لكل طبق' per published meal; press 'إنشاء' then 'فتح' on the new card. Slots must start at a time not already used by that kitchen (unique start per kitchen) - use D+4 or a time other than 13:00.) and copy its id as ONLYMAHSHI (from GET /api/v1/meals/00000000-0000-4000-8000-0000000bb002 nextSlots).
  5. (FX-CUST-A) await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], ONLYMAHSHI) — data: Koshari is not offered in that slot.
  6. (FX-CUST-A) await call('POST','/api/v1/quotes',{items:[],slotId:K,fulfillmentMode:'PICKUP'}); await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1},{mealId:'00000000-0000-4000-8000-0000000bb002',quantity:1}], K) — data: Empty list, then a valid two-meal basket of one kitchen and slot.
- **Expected:**
  - UI: n/a (API-only).
  - API: Step 1 and 2: 422 VALIDATION_FAILED fieldErrors[{field:'items',code:'MIXED_KITCHENS_OR_SLOT'}]. Step 3: 422 fieldErrors[{field:'items',code:'DUPLICATE_OR_MISSING_MEAL'}]. Step 5: 422 fieldErrors[{field:'items',code:'MEAL_NOT_OFFERED_IN_SLOT'}].
  - API: Step 6: empty items -> 422 VALIDATION_FAILED (items size 1..20); the valid basket -> 201 with items[2], subtotal.amountMinor=25500, deliveryFee 0, total 25500, expiresAt = createdAt + 300 s.
  - DB: Read-only: SELECT count(*) FROM quote WHERE customer_id='00000000-0000-4000-8000-0000000b0001' AND created_at > '<case start>' -> 1 (only the valid quote); the refused calls left no rows.
- **Also covered by automated tests:** backend QuoteIntegrationTest.mixed_kitchens_unoffered_meals_and_invalid_baskets_are_rejected
- **Unverified against code (confirm while executing):** Status/code for items:[] (Bean Validation @Size(min=1) -> expected 422 VALIDATION_FAILED, code 'Size').

### E2E-BASKET-011 — Per-line quantity limit comes from the server (10 on the preview) and is enforced at the quote

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** scenarios: TC-152; rules: BR-019, BR-020; operations: getConfigurationPublic, postQuotes; partial: TC-117, TC-019
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Limit check: GET /api/v1/configuration/public -> orderLimits.maxQuantityPerLine = 10.
  - Seed slots give koshari exactly 10 portions, so the limit and the stock coincide. To prove the limit is independent of stock, FX-COOK-A creates a slot with koshari capacity 15 (max portions 15).
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Meal page of koshari, select the seeded first slot. Press 'زوّد' until it stops; then type 99 into 'الكمية' and press Tab.
  2. (FX-CUST-A) Press 'أضف إلى السلة', open السلة and press 'احسب الإجمالي' (pickup). — data: Quantity 10.
  3. (FX-CUST-A) Console: await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:11}], K) ; await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:0}], K) ; await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:10}], K) — data: K = seeded kitchen-1 slot id (GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001).
  4. (FX-COOK-A) Create the 15-portion slot (FX-COOK-A creates slots in the operations web (https://ops.<PREVIEW_DOMAIN>, tab 'المواعيد', form 'موعد جديد'): fields التاريخ (date), البداية (start HH:MM), النهاية (end), أقصى عدد حصص, مهلة الطلب (دقائق) (minimum 30), then 'حصص لكل طبق' per published meal; press 'إنشاء' then 'فتح' on the new card. Slots must start at a time not already used by that kitchen (unique start per kitchen) - use D+4 or a time other than 13:00.) with 'أقصى عدد حصص' = 15 and koshari 'حصص لكل طبق' = 15.
  5. (FX-CUST-A) Meal page, select the new slot (label shows 'متبقٍ ١٥'), press 'زوّد' repeatedly; then console quote of quantity 11 for that slot.
- **Expected:**
  - UI: Step 1: the number stops at 10 and 'زوّد' is disabled; the add button reads 'أضف إلى السلة · ٧٥٠٫٠٠ ج.م'.
  - UI: Step 2: quote summary قيمة الأطباق ٧٥٠٫٠٠, رسوم التوصيل ٠٫٠٠, الضريبة ٠٫٠٠, الإجمالي ٧٥٠٫٠٠.
  - UI: Step 5: even with 15 portions left the control still stops at 10.
  - API: quantity 11 -> 422 VALIDATION_FAILED fieldErrors[{field:'items.quantity',code:'RANGE_1_TO_10'}]; quantity 0 -> same code; quantity 10 -> 201 with items[0].lineTotal.amountMinor=75000 and total 75000. Quantity 11 on the 15-portion slot -> the same 422 RANGE_1_TO_10 (not STOCK_CHANGED).
  - DB: Read-only: SELECT quantity, line_total_minor FROM quote_item WHERE quote_id='<valid quote>' -> 10, 75000; no quote rows for the refused requests.
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'caps the meal quantity at the policy limit when the slot has more portions, and at the portions when fewer'; backend QuoteIntegrationTest.mixed_kitchens_unoffered_meals_and_invalid_baskets_are_rejected
- **Unverified against code (confirm while executing):** Preview policy limit 10 is stated in docs/operations/06-web-preview.md 'Verified locally'; confirm with step 0 on the live preview.

### E2E-BASKET-029 — A quote reserves no stock and is immutable; only a placed order reserves portions

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** rules: BR-020, BR-025; operations: postQuotes, postOrders, postOrdersCancel; partial: TC-020, TC-025, TC-118
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+2 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Note remaining portions of koshari in the first slot (meal page 'متبقٍ ١٠'). Console: const q=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:10}], K)).body; q.total — data: K = first slot id of koshari.
  2. (FX-CUST-B) Reload the koshari meal page; read the first slot's remaining portions; console quote for B of 10 portions. — data: Both customers can hold quotes for the same last portions.
  3. (FX-CUST-A) Place A's quote: await place(q).
  4. (FX-CUST-B) Reload the meal page, then place B's quote.
  5. (FX-CUST-A) Cleanup: cancel A's order from the order page ('إلغاء الطلب').
- **Expected:**
  - UI: Step 1-2: the meal page still shows 'متبقٍ ١٠' for the first slot after both quotes. Step 4 for B: after A's order the slot shows 'متبقٍ ٠' and B's placement fails (record the UI text; by code 'حدث خطأ.').
  - API: Quotes: two 201 responses with different quoteId, each total 75000. A's place -> 201. B's place -> 409 STOCK_CHANGED fieldErrors[{field:'items',code:'INSUFFICIENT_PORTIONS'}] (and no order).
  - DB: Read-only before step 3: SELECT reserved FROM meal_slot WHERE id='<koshari first meal_slot>' -> 0 with two quotes outstanding; after step 3 -> 10; after cleanup -> 0 and stock_allocation state RELEASED. UPDATE on quote is refused by trigger quote_immutable (not testable read-only).
- **Also covered by automated tests:** backend QuoteIntegrationTest.quotes_hold_no_stock_and_cannot_exceed_remaining_portions
- **Unverified against code (confirm while executing):** UI text B sees for STOCK_CHANGED: the customer errors map has no STOCK_CHANGED entry, so 'حدث خطأ.' / 'Something went wrong.' is expected.

### E2E-BASKET-012 — A sold-out slot cannot be added or quoted while other slots stay orderable

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** scenarios: TC-151; rules: BR-013, BR-017, BR-029; operations: getMealsById, postQuotes, postOrders, postOrdersCancel; partial: TC-013, TC-025, TC-029
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Two browser profiles: A and B. A places and later cancels a real pickup COD order (the stock is released on cancellation while the order is 'بانتظار قبول المطبخ').
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Add 'كشري تجريبي' x10 in the day+1 slot to the basket; السلة > 'استلام من المطبخ' > 'احسب الإجمالي' > tick terms > 'تأكيد الطلب'. — data: Order total ٧٥٠٫٠٠ ج.م.
  2. (FX-CUST-B) Open the koshari meal page. Read the first slot label. Select its radio.
  3. (FX-CUST-B) Select the day+2 slot radio.
  4. (FX-CUST-B) Console: await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], <day+1 slot id>)
  5. (FX-CUST-B) Open home. Read the koshari card and the mahshi card.
  6. (FX-CUST-A) Open the order and press 'إلغاء الطلب'. B reloads the meal page.
- **Expected:**
  - UI: Step 2: first slot label ends with 'متبقٍ ٠'; after selecting it the notice 'الميعاد ده خلص. اختار ميعاد تاني.' appears and 'أضف إلى السلة' is disabled.
  - UI: Step 3: day+2 slot is selectable; the add button is enabled.
  - UI: Step 5: the koshari card still shows availability 'متاح' (other slots have stock); the mahshi card is available.
  - UI: Step 6: after cancellation the day+1 slot shows 'متبقٍ ١٠' again.
  - API: Step 4: 409 STOCK_CHANGED fieldErrors[{field:'items.quantity',code:'INSUFFICIENT_PORTIONS'}]; no quote row for B.
  - DB: Read-only: SELECT ms.capacity, ms.reserved FROM meal_slot ms WHERE ms.id='<koshari day+1 meal_slot>' -> 10,10 after step 1; SELECT reserved FROM cook_slot WHERE id='<day+1 slot>' -> 10 (aggregate 20). After step 6: reserved 0 and stock_allocation.state='RELEASED' with released_at set.
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'refuses the exhausted slot without writing the basket, and adds the slot that has stock up to its portions'; backend OrderPlacementIntegrationTest.twenty_customers_racing_for_the_last_portion_produce_exactly_one_order
- **Unverified against code (confirm while executing):** Whether the home card for a meal sold out in every slot shows the pill 'نفدت الكمية' (code path availability SOLD_OUT) - not exercised here because stock remains in other slots.

### E2E-BASKET-023 — Near and after the slot cutoff the server refuses new quotes and the slot disappears from the meal page

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-COOK-A, FX-CUST-A
- **Traces to:** rules: BR-014, BR-015, BR-020, BR-027; operations: postCookSlots, patchCookSlotsById, postQuotes, postOrders, getMealsById; partial: TC-014, TC-015, TC-115, TC-020
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Plan 25 minutes. Cutoff = slot start minus lead minutes (cook_slot_cutoff_ck). Use clock of the server: compare with the Date response header of GET /api/v1/configuration/public.
  - Policy facts (synthetic policy v1): quote lifetime 300 s; acceptance window = min(now+15 min, cutoff); a quote needs at least 5 minutes before cutoff.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-COOK-A) Create a slot for TODAY (Cairo) starting about 45 minutes from now: date today, البداية = now+45 min rounded to the minute, النهاية = start+60 min, أقصى عدد حصص 5, مهلة الطلب = 30, 'كشري تجريبي' capacity 5; press 'إنشاء' and 'فتح'. Write CUTOFF = start - 30 min. — data: Cutoff is 15 minutes from creation.
  2. (FX-CUST-A) More than 6 minutes before CUTOFF: console quote 1 koshari for the new slot id (GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001, nextSlots[0] is the earliest). — data: Note acceptanceDeadlinePreview.
  3. (FX-CUST-A) Between CUTOFF-4:59 and CUTOFF-0:01: quote again for the same slot.
  4. (FX-CUST-A) After CUTOFF: reload the koshari meal page; then quote again; then place the quote from step 2 (it is older than 5 minutes by now or has expired at CUTOFF).
  5. (FX-COOK-A) In 'المواعيد' read the state of the slot card.
- **Expected:**
  - UI: Step 4: the new slot is no longer in the slot list (the earliest slot is the next day's 13:00).
  - UI: Step 5: the slot card still says 'مفتوح' (no automatic state change); only the cutoff time blocks ordering.
  - API: Step 2: 201 with acceptanceDeadlinePreview <= CUTOFF.
  - API: Step 3: 409 SLOT_CLOSED fieldErrors[{field:'slotId',code:'ACCEPTANCE_WINDOW_TOO_SHORT'}].
  - API: Step 4: new quote -> 409 SLOT_CLOSED fieldErrors[{field:'slotId',code:'SLOT_CLOSED'}]; placing the old quote -> 409 QUOTE_EXPIRED (quote expiry is checked before the slot) with no order. GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 nextSlots excludes the slot.
  - DB: Read-only: SELECT state, cutoff_at < now() AS past FROM cook_slot WHERE id='<slot>' -> OPEN, true; SELECT count(*) FROM customer_order WHERE slot_id='<slot>' -> 0; SELECT reserved FROM cook_slot WHERE id='<slot>' -> 0.
- **Also covered by automated tests:** backend QuoteIntegrationTest.contact_verification_kitchen_state_and_cutoff_are_enforced_by_server_time; backend SlotIntegrationTest.server_time_reaching_cutoff_closes_ordering_and_capacity_edits
- **Unverified against code (confirm while executing):** Slot creation fields 'localDate/localStart/localEnd/timezone/maxPortions/leadMinutes/mealCapacities' (cook Slots.tsx) are confirmed; the cook UI's response when the chosen time is within an existing slot is not.; The web UI text for SLOT_CLOSED: the customer errors map has no SLOT_CLOSED entry (generic 'حدث خطأ.').

### E2E-BASKET-013 — An unplaced quote expires after 5 minutes and cannot be placed

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-020; operations: postQuotes, postOrders; partial: TC-020, TC-117
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Basket with 'كشري تجريبي' x1 (day+1 slot). Policy quote lifetime: 300 s (commerce_policy.quote_ttl_seconds). No test clock exists on the preview: the tester waits in real time.
  - Slot day for this case: seeded day+3 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) السلة > 'استلام من المطبخ' > 'احسب الإجمالي'. Note the clock time T0 and, in Network, the response field expiresAt of POST /api/v1/quotes.
  2. (FX-CUST-A) Leave the page open and untouched for 5 minutes 10 seconds. — data: Do not reload.
  3. (FX-CUST-A) Tick 'أوافق على الشروط وسياسة الإلغاء' and press 'تأكيد الطلب'.
  4. (FX-CUST-A) Press 'احسب الإجمالي' again, tick terms, press 'تأكيد الطلب'.
- **Expected:**
  - UI: Step 3: the price summary and the confirm button disappear and the alert 'انتهت صلاحية العرض. احسب الإجمالي مرة أخرى.' is shown; the basket is unchanged; no 'إعادة المحاولة بنفس الطلب' button appears (a definite refusal clears the stored keys).
  - UI: Step 4: a fresh quote is shown and the order is created; the browser opens #/orders/<id> with state 'بانتظار قبول المطبخ'.
  - API: Step 1: expiresAt = created time + 300 s. Step 3: POST /api/v1/orders -> 409 QUOTE_EXPIRED fieldErrors[{field:'quoteId',code:'QUOTE_EXPIRED'}]. Step 4: 201 with Location /api/v1/orders/<id>.
  - DB: Read-only: SELECT expires_at - created_at FROM quote WHERE id='<old quote>' -> 00:05:00; SELECT count(*) FROM customer_order WHERE quote_id='<old quote>' -> 0; the meal_slot.reserved value did not change between steps 2 and 3.
- **Also covered by automated tests:** backend OrderPlacementIntegrationTest.stale_expired_foreign_or_unaccepted_quotes_and_forged_fields_are_refused; apps/customer-web/src/App.test.tsx (QUOTE_EXPIRED text)
- **Unverified against code (confirm while executing):** Quote expiry is not shown as a countdown anywhere in the web UI (only acceptanceDeadlinePreview in the deposit text).

### E2E-BASKET-014 — A price changed by the kitchen after the quote is shown, explained and must be accepted before placing

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** scenarios: TC-154; rules: BR-019, BR-020, BR-012; decisions: D13, D14; operations: postQuotes, postOrders, patchCookMealsById, getCookMealsPriceHistory; partial: TC-155, TC-020, TC-153
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - FX-COOK-A publishes a test meal 'اختبار سعر PX1' (English 'Price test PX1') in the operations web: tab 'الأطباق' > form 'طبق جديد (مسودة)': Arabic and English names, السعر (جنيه) = 40, portion, ingredients, allergens 'لا يوجد ما يُعلن عنه', attach any small PNG in 'صورة الطبق', press 'إنشاء', then 'نشر'. A photo is mandatory: the seeded meals have no photo, so editing their price through the cook UI is refused (MealService.update validates published meals as complete: photos APPROVED_PHOTO_REQUIRED). Then create a slot (FX-COOK-A creates slots in the operations web (https://ops.<PREVIEW_DOMAIN>, tab 'المواعيد', form 'موعد جديد'): fields التاريخ (date), البداية (start HH:MM), النهاية (end), أقصى عدد حصص, مهلة الطلب (دقائق) (minimum 30), then 'حصص لكل طبق' per published meal; press 'إنشاء' then 'فتح' on the new card. Slots must start at a time not already used by that kitchen (unique start per kitchen) - use D+4 or a time other than 13:00.) on day+4 at 17:00 with PX1 capacity 5.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) Add 'اختبار سعر PX1' x2 (day+4 17:00 slot) to the basket; السلة > 'استلام من المطبخ' > 'احسب الإجمالي'. Read the total. Keep the tab open. — data: Total ٨٠٫٠٠ ج.م (8000).
  2. (FX-COOK-A) Operations web 'الأطباق' > PX1 > section 'تغيير السعر' > 'السعر الجديد (جنيه)' = 50 > press 'تغيير السعر'. — data: Price history shows 'السعر الحالي' 50 and previous 40.
  3. (FX-CUST-A) In the still-open tab tick 'أوافق على الشروط وسياسة الإلغاء' and press 'تأكيد الطلب'.
  4. (FX-CUST-A) Without pressing the accept button, tick the terms box again and try to press 'تأكيد الطلب'.
  5. (FX-CUST-A) Press 'أوافق على الإجمالي الجديد', tick terms, press 'تأكيد الطلب'.
  6. (FX-CUST-A) Open the order page and the list #/orders.
- **Expected:**
  - UI: Step 3: NO order is created. A box (data-testid=price-change, role=alert) titled 'تغيّر السعر' says 'المطبخ حدّث سعر «اختبار سعر PX1» من ٤٠٫٠٠ ج.م إلى ٥٠٫٠٠ ج.م.' and 'الإجمالي كان ٨٠٫٠٠ ج.م وأصبح ١٠٠٫٠٠ ج.م. راجع الإجمالي الجديد قبل تأكيد الطلب.' and offers 'أوافق على الإجمالي الجديد'; the summary below shows الإجمالي ١٠٠٫٠٠ and the terms box is cleared.
  - UI: Step 4: 'تأكيد الطلب' stays disabled until the accept button is pressed.
  - UI: Step 5: status 'وافقت على الإجمالي الجديد. أكّد الطلب لإرساله.'; then the order is placed and its page shows 2 × اختبار سعر PX1 and total ١٠٠٫٠٠ ج.م.
  - UI: Step 6: exactly one order for this basket.
  - API: Step 3: POST /api/v1/orders -> 409 PRICE_CHANGED fieldErrors[{field:'quoteId',code:'MEAL_CHANGED'}]; the UI immediately sends a new POST /api/v1/quotes -> 201 total.amountMinor=10000. Step 5: POST /api/v1/orders with the new quoteId -> 201.
  - DB: Read-only: after step 3 SELECT count(*) FROM customer_order WHERE quote_id='<old quote>' -> 0 and SELECT reserved FROM meal_slot (PX1) -> 0. After step 5: SELECT unit_price_minor, line_total_minor, meal_revision FROM order_item WHERE order_id='<order>' -> 5000, 10000, 2; SELECT total_minor FROM customer_order -> 10000; SELECT revision, price_minor FROM meal_revision WHERE meal_id='<PX1>' ORDER BY revision -> (1,4000),(2,5000).
- **Also covered by automated tests:** backend PriceChangeIntegrationTest.the_real_cook_price_edit_refuses_a_stale_quote_and_never_touches_a_committed_order; backend PriceChangeIntegrationTest.a_raised_then_lowered_meal_price_refuses_the_stale_quote_without_side_effects_and_the_fresh_quote_places; apps/customer-web/src/App.test.tsx 'shows the kitchen's increase and decrease on two lines with the old and new totals, and places only after explicit acceptance (Arabic)'
- **Unverified against code (confirm while executing):** Operations-web UI path for changing the price (labels 'تغيير السعر' / 'السعر الجديد (جنيه)' come from i18n.ts; the screen layout was not opened).; Variant to run after this case: lower the price (50 -> 45): the same notice must appear with 'الإجمالي كان … وأصبح …' (decrease also needs confirmation, D13).

### E2E-BASKET-015 — A placed order keeps its agreed prices after the kitchen changes the price

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-COOK-A
- **Traces to:** rules: BR-012, BR-022; decisions: D13; operations: getOrdersById, patchCookMealsById; partial: TC-156, TC-153, TC-022
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Output of E2E-BASKET-014: FX-CUST-A has one order of 2 x PX1 at 50.00 (total 100.00), state 'بانتظار قبول المطبخ' or later. Record its id as ORDER.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Run after E2E-BASKET-014 and reuse its recorded PX1 meal id, PX1 slot id and ORDER id (2 x PX1 at 50.00, total 100.00). No meal or slot is created here.
- **Steps:**
  1. (FX-CUST-A) Open #/orders/<ORDER>; read the line and total. Console: const before=(await call('GET','/api/v1/orders/<ORDER>')).body
  2. (FX-COOK-A) Change PX1's price to 60 ('تغيير السعر').
  3. (FX-CUST-A) Reload #/orders/<ORDER> and #/orders; console: await call('GET','/api/v1/orders/<ORDER>')
  4. (FX-CUST-A) Press 'اطلب مرة أخرى (بالسعر الحالي)' on the order page. — data: Link goes to the meal page.
- **Expected:**
  - UI: Order page still shows '2 × اختبار سعر PX1 — ١٠٠٫٠٠ ج.م' and 'الإجمالي: ١٠٠٫٠٠ ج.م'; the list row shows ١٠٠٫٠٠ ج.م.
  - UI: The meal page opened from 'اطلب مرة أخرى' shows the current price ٦٠٫٠٠ ج.م (not the old one).
  - API: GET /api/v1/orders/<ORDER> -> 200 with items[0].unitPrice.amountMinor=5000, lineTotal 10000, total.amountMinor=10000 before and after the price change (identical JSON for those fields); GET /api/v1/meals/<PX1> -> price.amountMinor=6000.
  - DB: Read-only: SELECT total_minor, subtotal_minor FROM customer_order WHERE id='<ORDER>' -> 10000, 10000; SELECT unit_price_minor, meal_revision FROM order_item WHERE order_id='<ORDER>' -> 5000, 2; SELECT price_minor, revision FROM meal WHERE id='<PX1>' -> 6000, 3. order_item rows cannot be updated (trigger order_item_immutable).
- **Also covered by automated tests:** backend PriceChangeIntegrationTest.the_real_cook_price_edit_refuses_a_stale_quote_and_never_touches_a_committed_order; backend OrderPlacementIntegrationTest.order_history_keeps_the_snapshot_after_the_meal_changes
- **Unverified against code (confirm while executing):** Field names unitPrice/lineTotal inside OrderView items are taken from the web code (Orders.tsx uses lineTotal.amountMinor); unitPrice name not read.

### E2E-BASKET-016 — Two customers race for the last portion: exactly one order, no oversell

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Isolated disposable stack only
- **Surface:** api, customer-web · **Actors:** FX-CUST-A, FX-CUST-B, FX-COOK-A · **Needs:** isolated stack · **Destructive**
- **Traces to:** scenarios: TC-025; rules: BR-025, BR-013; operations: postQuotes, postOrders; partial: TC-137, TC-118
- **Prerequisites:**
  - FX-ISOLATED disposable stack (race test; do not run on the shared preview).
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Race technique: in each customer's DevTools console define a timer so both fire in the same second: const T=<same epoch milliseconds ~30 s ahead>; setTimeout(async()=>console.log(await place(q)), T-Date.now()). Run it in both consoles before T. Repeat the whole case 3 times with a fresh slot each time.
  - FX-COOK-A creates slot LAST1: Cairo date D+1 (write the YYYY-MM-DD) at 18:00; LAST2 on D+2 and LAST3 on D+3, also 18:00 (distinct dates because overlapping non-cancelled slots are rejected), أقصى عدد حصص 1, 'كشري تجريبي' capacity 1, then 'فتح'. Copy its id.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) Console (profile A): const qa=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], LAST1)).body
  2. (FX-CUST-B) Console (profile B): const qb=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], LAST1)).body — data: Both quotes are accepted because quotes hold no stock.
  3. (FX-CUST-A) Schedule place(qa) at the common time T (see prerequisites).
  4. (FX-CUST-B) Schedule place(qb) at the same T.
  5. (FX-CUST-A) Read both console outputs; then open the koshari meal page (any profile) and read the LAST1 slot label.
  6. (FX-CUST-A) Repeat steps 1-5 with new slots LAST2 and LAST3, swapping who clicks first.
- **Expected:**
  - UI: After each round the LAST slot shows 'متبقٍ ٠' and exactly one of the two customers has a new order in 'طلباتي'.
  - API: Per round: one response 201 (OrderView with state PENDING_ACCEPTANCE) and one 409 STOCK_CHANGED fieldErrors[{field:'items',code:'INSUFFICIENT_PORTIONS'}]; never two 201 and never a 5xx.
  - DB: Read-only per round: SELECT count(*) FROM customer_order WHERE slot_id='<LAST>' -> 1; SELECT reserved, capacity FROM meal_slot WHERE cook_slot_id='<LAST>' -> 1,1; SELECT reserved, max_portions FROM cook_slot WHERE id='<LAST>' -> 1,1; SELECT sum(quantity) FROM stock_allocation sa JOIN customer_order o ON o.id=sa.order_id WHERE o.slot_id='<LAST>' AND sa.state='RESERVED' -> 1. No negative or over-capacity value (checks cook_slot_capacity_ck / meal_slot_capacity_ck).
- **Also covered by automated tests:** backend OrderPlacementIntegrationTest.twenty_customers_racing_for_the_last_portion_produce_exactly_one_order; backend SlotIntegrationTest.the_last_portion_is_bounded_by_both_the_meal_and_the_slot_aggregate
- **Unverified against code (confirm while executing):** Browser-timer synchronisation is accurate to tens of milliseconds only; the automated 20-customer race is the stronger proof.

### E2E-BASKET-017 — The cook-slot total caps two different meals placed at the same time

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Isolated disposable stack only
- **Surface:** api · **Actors:** FX-CUST-A, FX-CUST-B, FX-COOK-A · **Needs:** isolated stack · **Destructive**
- **Traces to:** scenarios: TC-025; rules: BR-013, BR-025; operations: postQuotes, postOrders; partial: TC-013, TC-137
- **Prerequisites:**
  - FX-ISOLATED disposable stack.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Race technique: in each customer's DevTools console define a timer so both fire in the same second: const T=<same epoch milliseconds ~30 s ahead>; setTimeout(async()=>console.log(await place(q)), T-Date.now()). Run it in both consoles before T. Repeat the whole case 3 times with a fresh slot each time.
  - FX-COOK-A creates slot AGG1: Cairo date D+1 (write the YYYY-MM-DD) at 19:00; further rounds AGG2/AGG3 on D+2/D+3 at 19:00, أقصى عدد حصص 1, capacity 1 for BOTH 'كشري تجريبي' and 'محشي تجريبي', then 'فتح'.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) Console (A): await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1},{mealId:'00000000-0000-4000-8000-0000000bb002',quantity:1}], AGG1) — data: Two portions in a one-portion slot.
  2. (FX-CUST-A) Console (A): const qa=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], AGG1)).body
  3. (FX-CUST-B) Console (B): const qb=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb002',quantity:1}], AGG1)).body
  4. (FX-CUST-A) Schedule place(qa) at time T.
  5. (FX-CUST-B) Schedule place(qb) at the same T.
  6. (FX-CUST-A) Compare outputs and repeat the race 3 times with new AGG slots.
- **Expected:**
  - UI: n/a (API-only).
  - API: Step 1: 409 STOCK_CHANGED fieldErrors[{field:'items.quantity',code:'INSUFFICIENT_PORTIONS'}] at quote time (aggregate check). Steps 4-5: one 201 and one 409 STOCK_CHANGED (field 'items', code INSUFFICIENT_PORTIONS).
  - DB: Read-only: SELECT reserved, max_portions FROM cook_slot WHERE id='<AGG1>' -> 1,1; SELECT m.title_ar, ms.reserved FROM meal_slot ms JOIN meal m ON m.id=ms.meal_id WHERE ms.cook_slot_id='<AGG1>' -> one meal 1, the other 0 (sum 1); SELECT count(*) FROM customer_order WHERE slot_id='<AGG1>' -> 1.
- **Also covered by automated tests:** backend QuoteIntegrationTest.lines_of_different_meals_together_cannot_exceed_the_cook_slot_total; backend OrderPlacementIntegrationTest.a_two_meal_order_is_refused_without_partial_reservation_once_the_kitchen_slot_total_is_used_up

### E2E-BASKET-018 — A two-line order where one line no longer fits places nothing (all or nothing)

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A, FX-CUST-B, FX-COOK-A
- **Traces to:** rules: BR-022, BR-025; operations: postQuotes, postOrders; partial: TC-022, TC-025, TC-118
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - FX-COOK-A creates slot LIM: day+4 at 20:00, أقصى عدد حصص 6, capacity 'كشري تجريبي' 5 and 'محشي تجريبي' 1, then 'فتح' (FX-COOK-A creates slots in the operations web (https://ops.<PREVIEW_DOMAIN>, tab 'المواعيد', form 'موعد جديد'): fields التاريخ (date), البداية (start HH:MM), النهاية (end), أقصى عدد حصص, مهلة الطلب (دقائق) (minimum 30), then 'حصص لكل طبق' per published meal; press 'إنشاء' then 'فتح' on the new card. Slots must start at a time not already used by that kitchen (unique start per kitchen) - use D+4 or a time other than 13:00.). Copy its id.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) Add 'كشري تجريبي' x1 and 'محشي تجريبي' x1, both in slot LIM, to the web basket; السلة > 'استلام من المطبخ' > 'احسب الإجمالي'. — data: Total ٢٥٥٫٠٠ ج.م (75.00 + 180.00).
  2. (FX-CUST-B) Console (B): const qb=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb002',quantity:1}], LIM)).body; await place(qb) — data: B takes the last mahshi portion.
  3. (FX-CUST-A) Tick terms, press 'تأكيد الطلب'.
  4. (FX-CUST-A) Reload السلة and read the koshari line; then open the koshari meal page and read the LIM slot label.
  5. (FX-CUST-B) Cleanup: cancel B's order.
- **Expected:**
  - UI: Step 3: no order page opens; the quote summary disappears and an alert line is shown (by code the generic 'حدث خطأ.' because STOCK_CHANGED has no customer message; record the exact text). The basket still has both lines.
  - UI: Step 4: the koshari line is unchanged and LIM still offers 'متبقٍ ٥' for koshari.
  - API: Step 2: 201 for B. Step 3: POST /api/v1/orders -> 409 STOCK_CHANGED fieldErrors[{field:'items',code:'INSUFFICIENT_PORTIONS'}] and no Location header.
  - DB: Read-only after step 3: SELECT reserved FROM meal_slot WHERE meal_id='00000000-0000-4000-8000-0000000bb001' AND cook_slot_id='<LIM>' -> 0 (the fitting line was not kept); SELECT reserved FROM cook_slot WHERE id='<LIM>' -> 1 (B only); SELECT count(*) FROM customer_order WHERE customer_id='00000000-0000-4000-8000-0000000b0001' AND slot_id='<LIM>' -> 0; SELECT count(*) FROM stock_allocation sa JOIN customer_order o ON o.id=sa.order_id WHERE o.customer_id='00000000-0000-4000-8000-0000000b0001' AND o.slot_id='<LIM>' -> 0; quote of A is still unused (SELECT count(*) FROM customer_order WHERE quote_id='<A quote>' -> 0).
- **Also covered by automated tests:** backend OrderPlacementIntegrationTest.an_injected_failure_after_the_stock_update_rolls_everything_back_and_a_retry_completes_once; backend OrderPlacementIntegrationTest.a_two_meal_order_is_refused_without_partial_reservation_once_the_kitchen_slot_total_is_used_up
- **Unverified against code (confirm while executing):** Exact UI text after a STOCK_CHANGED refusal (see above).

### E2E-BASKET-019 — Changing fulfilment, address or payment method discards the shown total and the server recalculates

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web, api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-019, BR-021, BR-020; decisions: D10, D16; operations: postQuotes, postMeAddresses; partial: TC-117, TC-019, TC-157, TC-163
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Kitchen 'مطبخ المعاينة' offers pickup, delivery to 'Test zone A' (fee 3000) and a 50 % deposit profile (INSTAPAY, demo recipient). FX-CUST-A has no address yet or an old one in Test zone A. Basket: 'كشري تجريبي' x2 (day+1 slot).
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) السلة > 'استلام من المطبخ' > 'احسب الإجمالي'. Read the summary.
  2. (FX-CUST-A) Press 'توصيل'. Read what happened to the previous summary. Press 'عنوان جديد', fill اسم العنوان = Zone A home, اسم المستلم = Test Recipient, المحافظة = Cairo, المدينة = Test city, الشارع = Test street 2, رقم المبنى = 9, رقم الموبايل المصري = +201000000101, منطقتك = 'منطقة تجريبية أ', 'حفظ العنوان'; select the new address radio; 'احسب الإجمالي'.
  3. (FX-CUST-A) Select payment 'عربون يُحوّل للمطبخ مباشرة' (group 'طريقة الدفع'); read that the summary vanished; 'احسب الإجمالي'.
  4. (FX-CUST-A) Press '+' on the line to quantity 3 in the basket (top section).
  5. (FX-CUST-A) Cleanup: account page > 'عناويني' > 'حذف' for 'Zone A home'.
- **Expected:**
  - UI: Step 1 summary: قيمة الأطباق ١٥٠٫٠٠ ج.م · رسوم التوصيل ٠٫٠٠ · الضريبة ٠٫٠٠ · الإجمالي ١٥٠٫٠٠ and the line 'الدفع نقدًا عند الاستلام.'; also 'سياسة تجريبية (بيئة اختبار)'.
  - UI: Step 2: after pressing 'توصيل' the summary disappears (a new quote is required); the new summary shows رسوم التوصيل ٣٠٫٠٠ and الإجمالي ١٨٠٫٠٠.
  - UI: Step 3: summary shows 'العربون الآن: ٩٠٫٠٠ ج.م' and 'الباقي عند الاستلام: ٩٠٫٠٠ ج.م', recipient 'إنستاباي: demo-kitchen@instapay.invalid · Demo kitchen (not a real account)' and the note that the platform never holds money.
  - UI: Step 4: the quote and the form are reset (the order form is re-keyed by the basket contents): a new 'احسب الإجمالي' is required.
  - API: Three POST /api/v1/quotes responses (201): (subtotal 15000, deliveryFee 0, tax 0, total 15000, method COD); (15000, 3000, 0, 18000, COD); (15000, 3000, 0, 18000, DEPOSIT with payment.deposit.amountMinor=9000 and remainder 9000). Each has a distinct quoteId; the request bodies contain no amounts.
  - DB: Read-only: SELECT fulfillment_mode, payment_method, subtotal_minor, delivery_fee_minor, tax_minor, total_minor, deposit_minor FROM quote WHERE customer_id='00000000-0000-4000-8000-0000000b0001' ORDER BY created_at DESC LIMIT 3 -> (DELIVERY,DEPOSIT,15000,3000,0,18000,9000), (DELIVERY,COD,15000,3000,0,18000,0), (PICKUP,COD,15000,0,0,15000,0); total_minor = subtotal + delivery + tax holds on every row (quote_total_ck).
- **Also covered by automated tests:** backend QuoteIntegrationTest.the_server_computes_exact_totals_and_forged_amounts_are_rejected; apps/customer-web/src/App.test.tsx 'an edit discards a quote already shown, so only a fresh server quote can be accepted...'
- **Unverified against code (confirm while executing):** Arabic label of the deposit recipient method 'إنستاباي' is from i18n pay.methods; the identifier shown is the seed's demo-kitchen@instapay.invalid.

### E2E-BASKET-020 — Client-supplied prices, totals and identities are rejected; the order total equals the server quote

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A, FX-CUST-B
- **Traces to:** scenarios: TC-019; rules: BR-019, BR-053; operations: postQuotes, postOrders; partial: TC-053, TC-136
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - K = a seeded kitchen-1 slot id.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
  - Slot day for this case: seeded day+3 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) Forged quote fields, one request each: await call('POST','/api/v1/quotes',{total:{amountMinor:1,currency:'EGP'},items:[{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}],slotId:K,fulfillmentMode:'PICKUP'}); the same with deliveryFee:0; with unitPriceMinor:1; and with an item {mealId,quantity:1,unitPrice:1}.
  2. (FX-CUST-A) const q=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], K)).body — data: Valid quote; total 7500.
  3. (FX-CUST-A) Forged placement fields: await call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),total:{amountMinor:1,currency:'EGP'},acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':'e2e-forge-1'}); the same with state:'COMPLETED' and with customerId:'00000000-0000-4000-8000-0000000b0002'.
  4. (FX-CUST-A) Missing key and wrong policy: POST /api/v1/orders without the Idempotency-Key header; then with acceptedPolicyVersions {terms:'old-terms',cancellation:q.cancellationPolicyVersion}.
  5. (FX-CUST-B) Console (B): await place(q) using A's quote object. — data: Foreign quote.
  6. (FX-CUST-A) const o=(await place(q)).body; o.total
  7. (FX-CUST-A) Cleanup: cancel the order from its page.
- **Expected:**
  - UI: After step 6 the order page shows ٧٥٫٠٠ ج.م and state 'بانتظار قبول المطبخ'.
  - API: Step 1: each request -> 400 ApiError.code 'MALFORMED_REQUEST' (unknown JSON properties rejected). Step 3: each -> 400 MALFORMED_REQUEST. Step 4: missing header -> 400 (record the code); wrong terms -> 409 STATE_CONFLICT fieldErrors[{field:'acceptedPolicyVersions',code:'POLICY_NOT_ACCEPTED'}]. Step 5: 404 NOT_FOUND. Step 6: 201 with total.amountMinor=7500.
  - DB: Read-only: SELECT count(*) FROM quote WHERE customer_id='00000000-0000-4000-8000-0000000b0001' AND created_at > '<case start>' -> 1; SELECT total_minor, customer_id FROM customer_order WHERE quote_id='<q>' -> 7500, 00000000-0000-4000-8000-0000000b0001 (never B, never 1); exactly one order row.
- **Also covered by automated tests:** backend QuoteIntegrationTest.the_server_computes_exact_totals_and_forged_amounts_are_rejected; backend OrderPlacementIntegrationTest.stale_expired_foreign_or_unaccepted_quotes_and_forged_fields_are_refused
- **Unverified against code (confirm while executing):** Error code for the missing Idempotency-Key header (test asserts status 400 only).

### E2E-BASKET-028 — One quote can create at most one order, even with a new request reference

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Ready to run manually now
- **Surface:** api · **Actors:** FX-CUST-A
- **Traces to:** rules: BR-022, BR-024; operations: postOrders; partial: TC-022, TC-024
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - Place-order console helper (after the base helper): const place=(q,key)=>call('POST','/api/v1/orders',{quoteId:q.quoteId,clientRequestId:crypto.randomUUID(),acceptedPolicyVersions:{terms:q.termsVersion,cancellation:q.cancellationPolicyVersion}},{'Idempotency-Key':key||('e2e-'+crypto.randomUUID())}); const quote=(items,slotId,mode='PICKUP',extra={})=>call('POST','/api/v1/quotes',{items,slotId,fulfillmentMode:mode,paymentMethod:'COD',...extra});
  - Slot day for this case: seeded day+3 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-CUST-A) const q=(await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:1}], K)).body; const o=await place(q); o.status
  2. (FX-CUST-A) await place(q) — data: New clientRequestId and new Idempotency-Key, same quote.
  3. (FX-CUST-A) Cleanup: cancel the order from its page.
- **Expected:**
  - UI: n/a (API-only).
  - API: Step 1: 201. Step 2: 409 STATE_CONFLICT fieldErrors[{field:'quoteId',code:'QUOTE_ALREADY_USED'}].
  - DB: Read-only: SELECT count(*) FROM customer_order WHERE quote_id='<q>' -> 1 (customer_order.quote_id is UNIQUE); SELECT sum(quantity) FROM stock_allocation sa JOIN customer_order o ON o.id=sa.order_id WHERE o.quote_id='<q>' -> 1.
- **Also covered by automated tests:** backend OrderPlacementIntegrationTest.reusing_a_key_or_request_reference_with_another_body_conflicts_and_leaves_the_order_unchanged

### E2E-BASKET-030 — A guest keeps the basket through sign-in and returns to checkout

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Ready to run manually now
- **Surface:** customer-web · **Actors:** FX-GUEST, FX-CUST-A
- **Traces to:** rules: BR-001, BR-018; operations: getMe; partial: TC-109, TC-127
- **Prerequisites:**
  - Preview reachable: opening https://api.<PREVIEW_DOMAIN>/api/v1/configuration/public in a browser returns 200 JSON with environment.name='PREVIEW', environment.demo=true and orderLimits.maxQuantityPerLine=10.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Private window, no session.
  - Slot day for this case: seeded day+1 slot (13:00 Cairo, 10 koshari / 10 mahshi portions); if its availablePortions is lower than needed, use another seeded day and record it. Cases run in parallel by different testers must use different days.
- **Steps:**
  1. (FX-GUEST) Add 'كشري تجريبي' x2 (day+1 slot) to the basket signed out; press 'السلة'.
  2. (FX-GUEST) Read the section below the basket and press its 'تسجيل الدخول' button.
  3. (FX-CUST-A) Sign in as preview-customer-1 at the identity provider.
  4. (FX-CUST-A) Read the URL and the checkout page.
- **Expected:**
  - UI: Step 2: text 'سجّل الدخول لإتمام الطلب.' and the sign-in button; no quote/order controls are offered.
  - UI: Step 4: URL ends with #/checkout; the basket still has koshari x2 (estimate ١٥٠٫٠٠ ج.م) and the form with 'توصيل'/'استلام من المطبخ' and 'احسب الإجمالي' (phone already verified).
  - API: The sign-in link is https://api.<PREVIEW_DOMAIN>/oauth2/authorization/keycloak?returnTo=<url-encoded checkout URL>; after login GET /api/v1/me -> 200.
  - DB: none
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'asks a signed-out customer to sign in with a return address before ordering'; apps/operations-web/e2e/customer-journey.e2e.ts

### E2E-BASKET-022 — A delivery-fee change after the quote is attributed to the fee, not the kitchen

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-STAFF-ADMIN · **Needs:** isolated stack · **Destructive**
- **Traces to:** scenarios: TC-154; rules: BR-019, BR-020, BR-042; decisions: D13; operations: postOpsConfigurationVersions, postQuotes, postOrders; partial: TC-042, TC-126
- **Prerequisites:**
  - FX-ISOLATED stack (changes the shared zone fee).
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - FX-STAFF-ADMIN has config.write and a fresh MFA step-up (staff configuration changes need recent MFA). Current fee of Test zone A = 3000 (service_zone.delivery_fee_minor). A has a saved address in Test zone A and a basket of koshari x1 for delivery.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) السلة > 'توصيل' > select the address > 'احسب الإجمالي'. Read the total. — data: Total ١٠٥٫٠٠ ج.م (7500 + 3000).
  2. (FX-STAFF-ADMIN) POST /api/v1/ops/configuration/zone:00000000-0000-4000-8000-00000000a001/versions {value:{active:true,deliveryFeeMinor:3500},reason:'E2E fee change',expectedVersion:<zone version>} from the operations web console session (zone version: SELECT version FROM service_zone WHERE id='00000000-0000-4000-8000-00000000a001').
  3. (FX-CUST-A) Tick terms, press 'تأكيد الطلب'.
  4. (FX-CUST-A) Press 'أوافق على الإجمالي الجديد', tick terms, press 'تأكيد الطلب'.
  5. (FX-STAFF-ADMIN) Restore: same call with deliveryFeeMinor 3000 and expectedVersion = the new version.
- **Expected:**
  - UI: Step 3: notice 'تغيّر السعر' with the single line 'رسوم التوصيل تغيّرت من ٣٠٫٠٠ ج.م إلى ٣٥٫٠٠ ج.م.' (no sentence blaming the kitchen) and 'الإجمالي كان ١٠٥٫٠٠ ج.م وأصبح ١١٠٫٠٠ ج.م. …'.
  - UI: Step 4: order total ١١٠٫٠٠ ج.م.
  - API: Step 2: 200 PolicyVersion for key zone:00000000-0000-4000-8000-00000000a001. Step 3: POST /api/v1/orders -> 409 PRICE_CHANGED fieldErrors[{field:'quoteId',code:'TOTAL_CHANGED'}] (not MEAL_CHANGED). Step 4: 201 with deliveryFee.amountMinor=3500, total.amountMinor=11000.
  - DB: Read-only: SELECT total_minor, delivery_fee_minor FROM customer_order WHERE id='<order>' -> 11000, 3500; SELECT resulting_version, value FROM config_change WHERE config_key='zone:00000000-0000-4000-8000-00000000a001' ORDER BY created_at DESC LIMIT 2 shows both changes with the reason; an audit_event row 'config.change' exists for each.
- **Also covered by automated tests:** backend PriceChangeIntegrationTest.a_delivery_fee_change_alone_refuses_the_stale_quote_as_a_total_change; apps/customer-web/src/App.test.tsx 'attributes a delivery-fee-only change to the fee, not the kitchen (English)'
- **Unverified against code (confirm while executing):** How a tester obtains the staff session for a direct API call (staff web uses the operations web session); the operations web Configuration screen (ops/Configuration.tsx) may offer this change without the console.; Whether zone:{id} changes are permitted for the PREVIEW profile (synthetic-policy restriction applies only to commerce-policy).

### E2E-BASKET-024 — A basket line above a lowered per-line limit blocks the quote until reduced

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Isolated disposable stack only
- **Surface:** customer-web, operations-web, api · **Actors:** FX-CUST-A, FX-STAFF-ADMIN · **Needs:** isolated stack · **Destructive**
- **Traces to:** scenarios: TC-152; rules: BR-019, BR-042, BR-020; operations: postOpsConfigurationVersions, getConfigurationPublic, postQuotes; partial: TC-042, TC-020
- **Prerequisites:**
  - FX-ISOLATED stack (a new commerce policy applies to every user).
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Paste this once in the browser DevTools console of the signed-in app tab (origin https://app.<PREVIEW_DOMAIN>) to define call(method,path,body,extraHeaders): const API='https://api.<PREVIEW_DOMAIN>';const xsrf=()=>decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN=')).slice(11));const call=async(m,p,b,h={})=>{const r=await fetch(API+p,{method:m,credentials:'include',headers:{'Content-Type':'application/json','Accept-Language':'en',...(m==='GET'?{}:{'X-XSRF-TOKEN':xsrf()}),...h},body:b?JSON.stringify(b):undefined});const t=await r.text();return {status:r.status,body:t?JSON.parse(t):null}}; Each call returns {status, body}. If document.cookie has no XSRF-TOKEN yet, reload the page once and retry.
  - FX-STAFF-ADMIN has config.write and recent MFA. Current active commerce policy version = 1 (maxItemQuantity 10). The synthetic flag is allowed only in LOCAL/CI profiles. A's basket: 'كشري تجريبي' x8.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-CUST-A) Open السلة with koshari x8 and keep the tab open.
  2. (FX-STAFF-ADMIN) POST /api/v1/ops/configuration/commerce-policy/versions {value:{label:'E2E limit 5',synthetic:true,taxRateBps:0,commissionRateBps:0,maxItemQuantity:5,quoteTtlSeconds:300,acceptanceWindowMinutes:15,minAcceptanceMinutes:5,cancellationPolicyVersion:'synthetic-cancel-v1',termsVersion:'synthetic-terms-v1'},reason:'E2E limit test',expectedVersion:1}
  3. (FX-CUST-A) Navigate to #/ then back to #/checkout (the limit is re-read when the cart opens). Read the line and the quote button.
  4. (FX-CUST-A) Console: await quote([{mealId:'00000000-0000-4000-8000-0000000bb001',quantity:8}], K)
  5. (FX-CUST-A) Press 'تقليل الكمية: كشري تجريبي' until 5, then 'احسب الإجمالي'.
  6. (FX-STAFF-ADMIN) Restore with a new version carrying maxItemQuantity 10 (expectedVersion = the active version).
- **Expected:**
  - UI: Step 3: the line shows the alert 'أقصى كمية لكل طبق دلوقتي ٥. قلّل الكمية علشان تكمل.' and 'احسب الإجمالي' is disabled; the 'زيادة' button is disabled.
  - UI: Step 5: at quantity 5 the quote is available and the total is ٣٧٥٫٠٠ ج.م.
  - API: GET /api/v1/configuration/public -> orderLimits.maxQuantityPerLine=5 after step 2. Step 4: 422 VALIDATION_FAILED fieldErrors[{field:'items.quantity',code:'RANGE_1_TO_5'}]. An unplaced quote made before step 2 fails placement with 409 PRICE_CHANGED (POLICY_CHANGED).
  - DB: Read-only: SELECT version, status, max_item_quantity FROM commerce_policy ORDER BY version -> v1 RETIRED, v2 ACTIVE 5 (then v3 ACTIVE 10 after restore); exactly one ACTIVE row at any time (unique index).
- **Also covered by automated tests:** apps/customer-web/src/App.test.tsx 'explains a basket line above a lowered limit and offers no quote until it is reduced'; 're-reads the limit when the cart opens, so a policy lowered after the page loaded blocks the quote at once'
- **Unverified against code (confirm while executing):** Staff session mechanics for the direct API call (see BASKET-022).

### E2E-BASKET-026 — Native app confirms replacing the basket and shows the price-change notice before placing

- **Status:** NOT_RUN · **Priority:** P1 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-DEVICE-ANDROID, FX-DEVICE-IOS, FX-CUST-A, FX-COOK-A · **Needs:** physical device
- **Traces to:** scenarios: TC-148; rules: BR-018, BR-049, BR-051, BR-052; decisions: D13; operations: postQuotes, postOrders; partial: TC-154, TC-132, TC-134, TC-135
- **Prerequisites:**
  - Signed device build pointing at the preview; FX-CUST-A signed in on the device (system-browser sign-in). PX1 test meal and slot as in E2E-BASKET-014.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-DEVICE-ANDROID) Discover > open 'كشري تجريبي' (testID meal-<id>) > select a slot (testID slot-0) > 'أضف للسلة' (testID add-to-cart). — data: Message 'تمت الإضافة إلى السلة.'
  2. (FX-DEVICE-ANDROID) Open 'ملوخية تجريبية' > slot > 'أضف للسلة'.
  3. (FX-DEVICE-ANDROID) Tap 'احتفظ بالسلة'. Open Cart (testID open-cart / go-to-cart). Repeat the add and tap 'استبدل السلة'.
  4. (FX-DEVICE-ANDROID) Add PX1 x2, open Cart, tap 'احسب الإجمالي' (testID get-quote). Then FX-COOK-A changes the PX1 price in the operations web. Tick terms (testID accept-terms) and tap 'تأكيد الطلب' (testID place-order).
  5. (FX-DEVICE-ANDROID) Tap 'موافق على الإجمالي الجديد' (testID accept-new-total), then tap place-order again.
  6. (FX-DEVICE-IOS) Repeat steps 1-5 on the iOS device.
- **Expected:**
  - UI: Step 2: dialog 'استبدال السلة؟' with 'السلة فيها أكل من مطبخ أو موعد آخر. الطلب الواحد من مطبخ واحد وموعد واحد.' Step 3: 'السلة كما هي.' after keep; replaced basket after replace.
  - UI: Step 4: a notice (testID price-change, price-change-total) 'تغيّر السعر' with the kitchen-attributed sentence and the old and new totals; placing is not possible until the new total is accepted. Step 5: the order is created at the new price.
  - API: Same calls as the web: POST /api/v1/quotes -> 201, POST /api/v1/orders -> 409 PRICE_CHANGED then a new quote and 201.
  - DB: Read-only: same checks as E2E-BASKET-014 (no order from the refused attempt; order_item.unit_price_minor = new price).
- **Also covered by automated tests:** apps/customer-mobile/src/App.test.tsx (replacement and price-change component tests); apps/customer-mobile/src/ordering.test.ts
- **Unverified against code (confirm while executing):** Platform differences in the system sign-in sheet.; Arabic wording of the native price-change sentences (mobile i18n lines 66-80 not fully read; the English texts equal the web ones).

### E2E-BASKET-027 — Native account switch clears the previous person's basket, pending order and screens

- **Status:** NOT_RUN · **Priority:** P0 · **Readiness:** Needs a physical iOS/Android build
- **Surface:** customer-mobile · **Actors:** FX-DEVICE-ANDROID, FX-DEVICE-IOS, FX-CUST-A, FX-CUST-B · **Needs:** updated deploy, physical device
- **Traces to:** rules: BR-048, BR-006, BR-049; operations: getMe, getOrders, getMeFavorites; partial: TC-048, TC-131, TC-006, TC-148
- **Prerequisites:**
  - Signed device build of PR #9 (the epoch/ownership guards of accountEpoch and bindShopping exist only there: commits 5e0ce79, 89ec820). Both customers phone-verified.
  - Seed loaded (deploy/web-preview/seed-preview.sql): GET https://api.<PREVIEW_DOMAIN>/api/v1/meals?limit=50 contains meals 'كشري تجريبي' (price.amountMinor 7500, id 00000000-0000-4000-8000-0000000bb001), 'محشي تجريبي' (18000, id 00000000-0000-4000-8000-0000000bb002) of kitchen 'مطبخ المعاينة' (slug preview-kitchen, id 00000000-0000-4000-8000-0000000bd001) and 'ملوخية تجريبية' (12025, id 00000000-0000-4000-8000-0000000bb003) of kitchen 'مطبخ تجريبي تاني' (slug preview-kitchen-two, id 00000000-0000-4000-8000-0000000bd002). Other testers may have added meals: record the actual list at the start.
  - Seeded slots are still in the future: the seed created one OPEN slot per kitchen at 13:00 Africa/Cairo on each of the three calendar days after the seed date (20 max portions per slot, 10 per meal, cutoff 11:00 same day). Open GET /api/v1/meals/00000000-0000-4000-8000-0000000bb001 and confirm nextSlots has at least one entry; if the three days have passed, FX-COOK-A must create new OPEN slots (operations web, tab المواعيد/Slots, form 'موعد جديد'/'New slot', then button 'فتح'/'Open') with 10 portions for each seeded meal before any basket case.
  - Sign-in password comes from the private fixture sheet (PREVIEW_USER_PASSWORD); never write it into results.
  - Two or more human actors: use a separate browser profile (or browser) per actor; sessions share cookies per profile, so never sign two actors into one profile at once unless the case says so. Before the case, read GET /api/v1/meals/<id> and check nextSlots[].availablePortions of the slot you will use is at least what the case needs (seed: 10 per meal; preparation consumes stock permanently, so prefer cancelling test orders).
- **Steps:**
  1. (FX-DEVICE-ANDROID) Sign in as FX-CUST-A. Add 'كشري تجريبي' x1 to the cart; open Orders and Favorites tabs so they load.
  2. (FX-DEVICE-ANDROID) Account tab > 'تبديل الحساب' (Switch account); in the system browser sign in as FX-CUST-B.
  3. (FX-DEVICE-ANDROID) Look at Cart, Orders, Favorites and Account (greeting).
  4. (FX-DEVICE-ANDROID) With B signed in and an empty cart, kill and relaunch the app; open Cart.
  5. (FX-DEVICE-IOS) Repeat steps 1-4 on iOS.
- **Expected:**
  - UI: After the switch: greeting 'أهلًا Preview Customer 2'; the cart shows 'السلة فارغة.' (never A's koshari line); Orders and Favorites show only B's data; nothing of A is visible at any time, including after relaunch.
  - API: B's session calls GET /api/v1/orders and /api/v1/me/favorites with B's bearer token only; no request of B carries A's token.
  - DB: Read-only: SELECT customer_id FROM customer_order WHERE created_at > '<case start>' -> none created by this case; A's quotes belong to A.
- **Also covered by automated tests:** apps/customer-mobile/src/App.test.tsx (account-switch tests added in PR #9); apps/customer-mobile/src/signIn.test.ts
- **Unverified against code (confirm while executing):** Whether the earlier (develop) native build already cleared the cart on switch; the PR #9 hardening concerns late responses and stored pending placements.
