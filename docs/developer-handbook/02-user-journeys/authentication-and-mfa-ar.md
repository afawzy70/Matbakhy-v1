# رحلات المستخدم: المصادقة وMFA

> **Owner role:** Security and Keycloak
> **Status:** PARTIALLY IMPLEMENTED — تسجيل الدخول بـ OIDC، والـ Step-up بـ TOTP المبني على سياسة، والتفضيل، وإعادة الضبط، وإنهاء الـ Sessions كلها متنفّذة؛ لكن تطبيق الـ Migration بتاع الـ Realm على الـ Realm الحي والتحقق على أجهزة الموبايل محتاجين موافقة المالك وحالتهم NOT VERIFIED.
> **Language / اللغة:** [English](authentication-and-mfa.md) · العربية

الـ Keycloak (الـ Realm اسمه `matbakhy`) هو اللي بيعمل الـ Authentication للناس، والـ Spring Boot هو اللي بيعمل الـ Authorization. الـ Backend مفيهوش أي Password ولا كود OTP. التصميم: [الهوية والأمان](../03-architecture/identity-and-security-ar.md). نص السياسة (هو المرجع في النيّة): `docs/security/mfa-policy.md`. لوحة إدارة Keycloak: `docs/operations/14-keycloak-admin-console.md`.

## مكونات الهوية الأساسية

| العنصر | المكان | الحقائق (من الكود والإعدادات) |
|---|---|---|
| قوالب الـ Realm | `infra/identity/matbakhy-realm.json` (محلي)، `deploy/web-preview/identity/matbakhy-realm.preview.json` (معاينة) | الـ Realm `matbakhy`؛ التسجيل الذاتي مقفول؛ التحقق من الإيميل مفعّل؛ الحماية من التخمين (Brute-force) مفعّلة؛ مدة صلاحية الـ Access Token هي 300 s؛ SSL مطلوب للخارج |
| عميل الويب | `matbakhy-web`، سري (Confidential)، Authorization Code بس، Direct Grants مقفولة، PKCE `S256`، `acr.loa.map {"mfa":"2"}`، الـ Mappers هي `api-audience` و`amr` | الـ Redirect URI هو الـ Callback بتاع الـ **API** `/login/oauth2/code/keycloak` (محلي `http://localhost:${API_PORT}/…`، معاينة `https://api.${PREVIEW_DOMAIN}/…`)؛ التحويل بعد تسجيل الخروج بيروح لمواقع الويب |
| عميل الموبايل | `matbakhy-mobile`، عام (Public)، PKCE `S256`، نفس خريطة الـ ACR ونفس الـ Mappers | الـ Redirect هو `matbakhy-dev://auth/callback` (تطوير). موجود في القالب المحلي بس؛ قالب المعاينة مفيهوش (NOT VERIFIED على الـ Realm الحي) |
| عميل الأدمن | `matbakhy-admin`، حساب خدمة (Service Account)، مفيش Flow تفاعلي | الـ Backend بس هو اللي بيستخدمه لإعادة ضبط الـ MFA وإنهاء Sessions المزوّد (`backend/app/src/main/java/com/matbakhy/app/mfa/KeycloakAdmin.java`)؛ السرّ بيتولّد لكل بيئة ومابيتعملوش Commit أبدًا |
| أدوار الـ Realm | مفيش أي دور معرّف في أي من القالبين | الـ Authorization مش مبني على أدوار Keycloak |
| مستخدمين تجريبيين (Synthetic) | محلي: `customer-a`، `customer-b`، `cook-a`، `ops-a`؛ معاينة: `preview-customer-1/2`، `preview-cook-1`، `preview-ops-1/2` | كلمات السر بتتولّد برّه الـ Repository |

### الـ Claims اللي الـ Backend بيستخدمها في الـ Token

فيه سلسلتين أمان (Security Chains) في `backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java`:

* **الـ Bearer Chain** (الـ Header `Authorization: Bearer`، للموبايل والأدوات): JWT بدون حالة (Stateless) بيتم التحقق من التوقيع (JWKS) والـ Issuer والانتهاء والـ **Audience** (`apiAudience`). مفيش Cookies ولا CSRF.
* **الـ Web Chain** (المتصفح): تسجيل دخول OIDC بـ Authorization Code، وSession على السيرفر في PostgreSQL (`SPRING_SESSION*`، V2)، والـ Cookie `MATBAKHY_SESSION` (HttpOnly، SameSite=Lax، وSecure لما يتعمل Deployment)، وCookie الـ CSRF Token اسمها `XSRF-TOKEN` وبيقدر الـ SPA يقراها، وقايمة سماح للـ CORS. مهلة الخمول الافتراضية 30 دقيقة (`WEB_SESSION_TIMEOUT`). الـ `returnTo` بيتقبل بس لو الـ Origin بتاعه في قايمة السماح (`WebReturnTo.java`).

الـ Claims اللي بيقراها `AccountMapper`: `iss` + `sub` (مفتاح الحساب؛ بيعمل أو بيلاقي `app_user`)، و`email_verified`، و`name` أو `preferred_username`، و`locale`، وClaims مستوى الضمان `acr` و`amr` و`auth_time`. **أي Claims للأدوار بتتجاهل.** صلاحيات التطبيق بتيجي من صفوف `user_grant`؛ و"الطبّاخ" معناه إنه مالك مطبخ موافَق عليه. شوف [إدارة الـ Backoffice](backoffice-admin-ar.md#مفيش-أدوار-للـ-staff-فيه-صلاحيات-ممنوحة-بس).

### نموذج الـ MFA (ملخّص؛ النص الكامل في `docs/security/mfa-policy.md`)

* الأوضاع لكل فئة: `OPTIONAL` و`REQUIRED` و`DISABLED`. الفئات: `CUSTOMER` (كل حساب)، و`KITCHEN` (مالك مطبخ موافَق عليه)، و`STAFF` (صلاحية فعّالة). بتتخزّن في `mfa_policy` (V31، والقيمة الأولية OPTIONAL للتلاتة). الحساب اللي في أكتر من فئة بياخد الوضع الأكتر حماية (REQUIRED فوق OPTIONAL فوق DISABLED)؛ والصف الناقص بيرجع لـ REQUIRED كإعداد آمن عند الفشل.
* `required = REQUIRED or (OPTIONAL and the user turned it on)`؛ `available = mode != DISABLED`.
* التطبيق: `MfaEnforcementFilter` (على السلسلتين) بيرجّع `403 STEP_UP_REQUIRED` لنداءات `/api/**` المحمية لما الـ MFA يبقى مطلوب والـ Token أو الـ Session مفيهوش إثبات MFA (`acr` ضمن `mfa-acr-values` المضبوطة، والافتراضي `mfa,2`، أو `amr` فيه `otp`/`mfa`/`hwk`). المسارات المتاحة دايمًا: `/api/v1/me/security[/**]` و`/api/v1/auth/logout` والقراءات العامة وقراءة الملف الشخصي.
* الإجراءات عالية الخطورة للـ Staff محتاجة MFA حديث **بغض النظر عن الوضع** (`StaffAssurance.recentMfa`، والنافذة `OIDC_STEP_UP_MAX_AGE` والافتراضي 10 دقايق).

## عناصر مشتركة بين الرحلات

**كلاسات الـ Backend.** `backend/app/src/main/java/com/matbakhy/app/mfa/` (`MfaController` و`MfaAdminController` و`MfaPolicyService` و`KeycloakAdmin` و`ApplicationSessions`) و`backend/app/src/main/java/com/matbakhy/app/security/`. **الجداول.** `app_user` و`user_grant` و`mfa_policy` و`user_mfa_preference` و`user_mfa_reset` و`SPRING_SESSION` و`audit_event`. **الاختبارات (كلها).** `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyResolveTest.java` و`MfaPolicyIntegrationTest.java` و`MfaResetAuditIntegrationTest.java`؛ اختبارات Keycloak الحقيقي `backend/app/src/test/java/com/matbakhy/app/identity/MfaIdpTest.java` و`MfaResetIntegrationTest.java` و`IdentityIntegrationTest.java` (الـ Realm الاختباري `backend/app/src/test/resources/keycloak/test-realm.json`)؛ `backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`؛ اختبارات الواجهة في `apps/customer-web/src/App.test.tsx` و`apps/operations-web/src/App.test.tsx` و`apps/customer-mobile/src/signIn.test.ts`. النتايج للـ Commit الحالي: NOT VERIFIED هنا.

## AM-1 تسجيل الدخول العام (ويب وموبايل)

1. **الهدف.** تسجيل الدخول بـ Authorization Code Flow مع PKCE؛ ومايشوفش ولا يخزّن Passwords في التطبيقات أبدًا.
2. **الطرف.** أي شخص عنده حساب Keycloak.
3. **المتطلبات المسبقة.** الحساب موجود (مفيش تسجيل ذاتي في القوالب)؛ مواقع الويب موجودة في قوايم سماح الـ CORS والـ `returnTo`.
4. **البداية.** الويب: رابط "Sign in" ← `{API}/oauth2/authorization/keycloak?returnTo=<url>`. الموبايل: تبويب الحساب "Sign in".
5. **الإجراءات.** الـ Authentication عند Keycloak (Password؛ مفيش OTP في المستوى 1 لتسجيل الدخول العادي).
6. **الاستجابات.** الويب: الـ Callback `/login/oauth2/code/keycloak`، واتعملت Session، وتحويل لـ `returnTo` بعد التحقق منه (أو لرابط ما بعد تسجيل الدخول المضبوط). الموبايل: التطبيق بيستلم `<scheme>://auth/callback?code&state`، وفحص صارم، وتبادل الـ Token، والـ Tokens في تخزين آمن.
7. **الـ API / الـ Endpoints.** مسارات الـ Framework هي `/oauth2/authorization/keycloak` و`/login/oauth2/code/keycloak`؛ و`GET /api/v1/me`.
8. **الـ Handlers.** `SecurityConfiguration` (`webChain` و`bearerChain`) و`AccountMapper` و`WebAccountFilter` و`WebReturnTo`؛ وللموبايل `apps/customer-mobile/src/auth.ts` و`redirect.ts`.
9. **قاعدة البيانات.** `app_user` و`SPRING_SESSION`.
10. **الانتقالات.** مجهول ← مصادَق عليه؛ لازم `status` للحساب يبقى `ACTIVE` عشان صلاحية `ACCOUNT_ACTIVE` (الحساب اللي طلب حذف ممكن بس يكرر طلب الحذف).
11. **الأخطاء.** الـ Redirect URI غير المسجّل أو `state`/الكود المزوّر بيترفض (CFG-003 وTC-131)؛ الـ Access Token المنتهي ← 401؛ الحساب المعطّل ← `ACCOUNT_DISABLED`.
12. **الاختبارات.** `IdentityIntegrationTest.java` و`apps/customer-mobile/src/auth.test.ts` و`signIn.test.ts`.
13. **اختبارات المالك E2E.** CFG-001 وCFG-002 وCFG-003 وCFG-004 وCFG-006 وCFG-036 وCFG-037 وE2E-DISC-003؛ J0.
14. **الحالة.** IMPLEMENTED.
15. **القيود.** الـ Lockout بتاع Keycloak هو الوحيد اللي بيحدّ من محاولات تسجيل الدخول (CFG-039)؛ والـ CSP في وضع التقرير بس (CFG-038).

## AM-2 تسجيل الخروج وإنهاء الـ Session

1. الهدف: إنهاء Session التطبيق وSession المزوّد. 2. الطرف: شخص مسجّل دخول. 3. المتطلب المسبق: Session.
4. البداية: "Sign out". 5. الإجراءات: الويب بيعمل POST على `/api/v1/auth/logout` مع CSRF؛ والموبايل بيلغي الـ Refresh Token (بأفضل جهد) وبيمسح التخزين الآمن.
6. الاستجابات: الويب بياخد `{"idpLogoutUrl": …}` وبيروح عليه عشان تسجيل الدخول الجاي يطلب بيانات الدخول؛ والموبايل مابيحتفظش بأي Cache خاص.
7. الـ API: `/api/v1/auth/logout` (تسجيل خروج الـ Framework؛ مش موجود في `contracts/endpoints.json`). 8. الـ Handler: إعدادات الخروج في `SecurityConfiguration` (`idpLogout`)، و`endSession` في `AppContext.tsx`. 9. قاعدة البيانات: صفوف الـ Session بتتحذف.
10. الانتقالات: Session فعّالة ← منتهية. حالات إنهاء تانية: إيقاف الـ MFA بتاع الشخص نفسه، وإعادة الضبط الإدارية (`ApplicationSessions` بيلاقي صفوف Spring Session بالـ Subject بتاع المزوّد)، وحذف صف Session (CFG-004).
11. الأخطاء: السيرفر مش متاح ← الويب مابيمسحش الحالة المحلية. 12. الاختبارات: بلوك MBQA-003 في ويب العميل؛ `MfaPolicyIntegrationTest.java`. 13. CFG-002 وCFG-004 وE2E-DISC-003 وE2E-DISC-004. 14. IMPLEMENTED. 15. الـ Access Tokens اللي اتصدرت قبل كده تفضل صالحة لحد مدة صلاحيتها (≤ 5 دقايق) بعد إعادة الضبط أو الإيقاف (`docs/security/mfa-policy.md`، المخاطر المتبقية).

## AM-3 تسجيل TOTP والـ Step-up

1. الهدف: الحصول على إثبات بمستوى MFA، مع تسجيل تطبيق Authenticator أول مرة.
2. الطرف: أي حساب لازم أو عايز يستخدم MFA.
3. المتطلبات المسبقة: السياسة مش DISABLED للحساب (أو الحساب معاه صلاحية Staff: فالـ Step-up بتاع الـ Staff يفضل متاح)؛ والـ Realm فيه Flow اسمه `matbakhy browser` بمستوى الـ OTP و`acr.loa.map` (الـ Migration محتاج موافقة المالك `deploy/web-preview/identity/mfa-migration.kcadm.sh`، الحالات من CFG-028 لـ CFG-032).
4. البداية: الويب `Account` ← لوحة الأمان ("verify now"/"turn on")، أو رابط التحقق في ويب العمليات، أو طلب تلقائي بعد `403 STEP_UP_REQUIRED`. الموبايل: بانر الـ Step-up.
5. الإجراءات: افتح `/oauth2/authorization/keycloak?stepUp=true&returnTo=…`؛ الـ Resolver بيضيف `acr_values` (من `OIDC_MFA_ACR_VALUES`) و`max_age=0`. الموبايل بيضيف `acr_values=mfa 2` و`prompt=login`. Keycloak بيطلب الـ OTP، أو بياخد الحساب اللي ملوش عامل تحقق لتسجيل TOTP (`CONFIGURE_TOTP`).
6. الاستجابات: Session/Token جديد فيه `acr=mfa`؛ والنداءات المحمية بتنجح.
7. الـ API: `/oauth2/authorization/keycloak?stepUp=true` و`GET /api/v1/me/security` (112).
8. الـ Handlers: `stepUpResolver` في `SecurityConfiguration` و`MfaPolicyService.decide` و`StaffAssurance`. 9. قاعدة البيانات: `mfa_policy` و`user_mfa_preference`.
10. الانتقالات: مستوى الضمان 1 ← 2 جوه الـ Session. 11. الأخطاء: الـ OTP الغلط أو المستخدم قبل كده بيترفض من Keycloak مع الحماية من التخمين (CFG-022)؛ الفئة DISABLED: الـ API مابيطلبش عامل تحقق تاني (CFG-012).
12. الاختبارات: `MfaIdpTest.java` (تسجيل دخول عادي، Step-up، كود غلط، تسجيل، SSO Session).
13. اختبارات المالك E2E: CFG-102 وCFG-030 وCFG-033 وCFG-104 وCFG-024 لـ CFG-026 وCFG-108 وCFG-109؛ J7، J8.
14. الحالة: IMPLEMENTED في الكود؛ الـ Migration على الـ Realm الحي والتشغيل على الأجهزة NOT VERIFIED (بيتفحصوا بس من مجموعة اختبارات المالك).
15. القيود: TOTP بس؛ مفيش Recovery Codes ولا WebAuthn. الموبايل مفيهوش شاشة تسجيل: التسجيل بيحصل في متصفح النظام أثناء الـ Step-up.

## AM-4 تفضيل الـ MFA الخاص بالشخص (سياسة OPTIONAL)

1. الهدف: الشخص يشغّل أو يوقّف الـ MFA لحسابه. 2. الطرف: أي حساب مسجّل دخول. 3. المتطلب المسبق: الوضع OPTIONAL.
4. البداية: صفحة الحساب في الويب، وتبويب Security في ويب العمليات. 5. الإجراءات: التشغيل (محتاج Session فيها إثبات MFA صالح؛ بيترفض بـ `MFA_UNAVAILABLE` لما الوضع يبقى DISABLED)؛ الإيقاف (محتاج MFA حديث).
6. الاستجابات: الإيقاف كمان بينهي Sessions التطبيق الخاصة بالمستخدم، ولو عميل الأدمن مضبوط، بينهي Sessions المزوّد كمان (جزء المزوّد بأفضل جهد).
7. الـ API: `GET /api/v1/me/security` (112)، و`PUT /api/v1/me/security/mfa` والـ Body هو `{enabled}` (113). 8. `MfaController` و`MfaPolicyService` و`ApplicationSessions` و`KeycloakAdmin`. 9. `user_mfa_preference` و`audit_event`.
10. الانتقالات: التفضيل false ↔ true. 11. الأخطاء: أكواد الحقول `MFA_REQUIRED_BY_POLICY` (REQUIRED)، و`MFA_UNAVAILABLE` (التشغيل وهو DISABLED)، و`STEP_UP_REQUIRED` (HTTP 403).
12. الاختبارات: `MfaPolicyIntegrationTest.java`. 13. CFG-008 وCFG-011 وCFG-012 وCFG-120؛ J7. 14. IMPLEMENTED (ويب بس). 15. مفيش شاشة تفضيلات في الموبايل.

## AM-5 الأدمن بيضبط سياسة الـ MFA

1. الهدف: تغيير فئة بين OPTIONAL وREQUIRED وDISABLED. 2. الطرف: `security.mfa_policy` + MFA حديث. 3. المتطلب المسبق: الـ `version` الحالي؛ سبب.
4. البداية: تبويب Security في ويب العمليات. 5. الإجراءات: اختيار الفئة والوضع، والسبب، وحفظ.
6. الاستجابات: التأثير على الطلب الجاي لكل حساب متأثر (القرار بيتحسب من جديد من قاعدة البيانات كل مرة)؛ ومتسجّل في الـ Audit.
7. الـ API: `GET /api/v1/ops/security/mfa-policy` (114)، و`PUT /api/v1/ops/security/mfa-policy/{category}` (115). 8. `MfaAdminController` و`MfaPolicyService`. 9. `mfa_policy` و`audit_event`. 10. الوضع والـ Version بيتغيّروا. 11. الأخطاء: `VERSION_CONFLICT`، والسبب الناقص، و`STEP_UP_REQUIRED`، وسلّم الرفض للضيوف والعملاء والـ Staff اللي معهمش صلاحية والـ Staff من غير MFA حديث (CFG-016).
12. الاختبارات: `MfaPolicyIntegrationTest.java`. 13. CFG-014 وCFG-015 وCFG-016 وCFG-122 وE2E-OPS-111؛ J7. 14. IMPLEMENTED. 15. ماتضبطش CUSTOMER أو KITCHEN على REQUIRED لحد ما يبقى فيه دليل من أجهزة حقيقية على الـ Step-up في الموبايل (`docs/security/mfa-policy.md`).

## AM-6 إعادة ضبط الـ MFA إداريًا

1. الهدف: استرجاع حساب ضاع منه الـ Authenticator. 2. الطرف: `security.mfa_reset` + MFA حديث. 3. المتطلب المسبق: سبب لا يقل عن 10 حروف؛ مش حساب الشخص نفسه؛ عميل الأدمن مضبوط، وإلا 503 (CFG-023).
4. البداية: تبويب Security. 5. الإجراءات: إدخال معرّف الحساب والسبب، والتأكيد.
6. الاستجابات: بيتسجّل وقت إعادة الضبط الأول عشان إثباتات الـ MFA القديمة تبطل تتحسب؛ وبتتحذف بيانات اعتماد الـ OTP عند Keycloak، وبتنتهي Sessions المزوّد، وبتنتهي Sessions الويب للتطبيق؛ والمستخدم بيسجّل من جديد عند الـ Step-up الجاي. بيتكتب سجل Audit باسم `mfa.reset.attempt` قبل أي حاجة مدمّرة؛ والفشل الجزئي بيتسجّل في الـ Audit، وإعادة المحاولة بتوصل لنفس النتيجة (CFG-020).
7. الـ API: `POST /api/v1/ops/accounts/{accountId}/mfa-reset` (116). 8. `MfaAdminController` و`KeycloakAdmin` و`ApplicationSessions`. 9. `user_mfa_reset` و`audit_event`. 10. وقت إعادة الضبط بيزيد بس (Monotonic) لكل حساب.
11. الأخطاء: الصلاحية، والـ MFA الحديث، وطول السبب، وإعادة ضبط الحساب الشخصي، والحساب المجهول (CFG-018). مفيش أدمن يقدر يقرا سرّ OTP.
12. الاختبارات: `MfaResetIntegrationTest.java` و`MfaResetAuditIntegrationTest.java`. 13. من CFG-018 لـ CFG-021 وCFG-023؛ J7. 14. IMPLEMENTED. 15. الـ Access Tokens قبل إعادة الضبط (≤ 5 دقايق) تفضل صالحة لأغراض غير الـ MFA.

## AM-7 لوحة إدارة Keycloak (للمشغّلين، مستوى عالي)

من وقت موافقة المالك بتاريخ 2026-10-19 لوحة إدارة Keycloak منشورة على Hostname عام مخصص، `kc-admin.<preview domain>`، **في المعاينة (Preview) بس** (مش معتمدة للـ Staging ولا الـ Production). الملف `deploy/web-preview/Caddyfile` بيمرّر `/admin` و`/realms/master` والأصول الثابتة بس لهناك؛ والـ Realm `matbakhy` والـ Health والـ Metrics بترد 404، والـ `id.*` لسه بيرد 404 على `/admin` و`/realms/master`. الـ Host كله مقفول بشكل آمن عند الفشل (Fail-closed): بيرد 404 إلا لو ملف التبديل `admin-console-public` موجود، واللي `admin-console.sh enable` مابيعملوش إلا بعد التقوية والتحقق الحي (الـ Workflow بتاع الـ Deployment بيشغّله بعد كل إصدار؛ و`./deploy.sh admin-console disable` بيشيله فورًا).

فيه نظامين مختلفين للـ MFA ولازم ماتخلطش بينهم. حسابات التطبيق (CUSTOMER وKITCHEN وSTAFF) بتمشي على سياسة قاعدة البيانات في `mfa_policy`، والقيمة الأولية **OPTIONAL** للتلاتة (V31، AM-5). أما أدمنز الـ Master Realm للوحة فعندهم **Password زائد OTP إجباري**: الـ Flow اسمه `matbakhy master browser` بيضبط نموذج الـ Password ونموذج الـ OTP الاتنين على REQUIRED (`deploy/web-preview/identity/admin_console.py`، ومتفحوص بـ `scripts/test_keycloak_admin_console.py`)، ومفيش تسجيل OTP عام، والـ Password Grant مقفول لعملاء الـ Master.

المشغّلين بيستخدموها لفحص المستخدمين والـ Flow بتاع الـ Realm، ومش لمشاركة كلمات السر في الـ Repository أبدًا. الإجراء والأدوات وسكريبتات التحقق: `docs/operations/14-keycloak-admin-console.md`؛ خطوات الـ MFA Migration: `docs/operations/13-keycloak-mfa-migration.md`؛ والسكريبتات تحت `deploy/web-preview/identity`. الحالة: IMPLEMENTED كأداة للمشغّلين (معاينة بس)؛ التغييرات الحية محتاجة موافقة المالك. هل التبديل مفعّل على السيرفر الشغّال: NOT VERIFIED.

## القيود المعروفة وفجوات التوثيق

* حالات المالك CFG-001 وCFG-121 (`docs/quality/owner-acceptance/cases/config-security.json`) دلوقتي بتنص على إن لوحة الإدارة مش منشورة على Host هوية العملاء `id.<PREVIEW_DOMAIN>`؛ و الـ Host المنفصل `kc-admin` متغطّي في `docs/operations/14-keycloak-admin-console.md`. لسه مفيش حالة مالك مخصصة لتسجيل الدخول للوحة بـ Password زائد OTP (فجوة).
* الخطوة 5 من خطة الإطلاق في `docs/security/mfa-policy.md` بتقول إن تطبيق الموبايل مابيتعاملش مع `STEP_UP_REQUIRED`؛ لكن قسم المخاطر المتبقية والكود (`apps/customer-mobile/src/AppContext.tsx`) بيوضّحوا إن فيه بانر موجود. وكمان بتشير لملف خطة اختبار أجهزة iOS تحت مجلد مستندات موبايل مش موجود في الـ Repository.
* قالب المعاينة للـ Realm مفيهوش عميل `matbakhy-mobile`.
* مفيش أدوار Realm في Keycloak مستخدمة، رغم كلمة "role" العامة في المستندات الأقدم؛ الصلاحيات موجودة في `user_grant`.
