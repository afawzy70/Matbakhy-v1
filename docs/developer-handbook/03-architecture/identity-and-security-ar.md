# الهوية والأمان

> **Owner role:** Security and Keycloak
> **Status:** PARTIALLY IMPLEMENTED — الـ Authentication والـ Authorization وسياسة الـ MFA وضوابط الـ Proxy متنفّذة؛ والـ CSP في وضع Report-Only، وتغييرات Keycloak الحيّة بيطبّقها المشغّل (Operator)، ومفيش تأمين خاص بالـ Production
> **Language / اللغة:** [English](identity-and-security.md) · العربية

منظور البيزنس للأدوار: [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md). مسارات تسجيل الدخول والـ MFA من ناحية المستخدم: [رحلة الـ Authentication والـ MFA](../02-user-journeys/authentication-and-mfa-ar.md). الرسومات: [OIDC](diagrams-ar.md#6-الـ-authentication-oidc)، [Step-up للـ MFA](diagrams-ar.md#7-رفع-الـ-mfa-step-up). تصميم الـ MFA المرجعي: `docs/security/mfa-policy.md`؛ والقواعد الأوسع: `docs/engineering/04-security.md`.

## المبادئ (زي ما هي متنفّذة)

1. **مزوّد الهوية بيعمل Authentication؛ والتطبيق بيعمل Authorization.** Keycloak بيثبت المستخدم مين. أما المسموح للمستخدم بيه فبيتحدد من قاعدة بيانات التطبيق بس (`app_user` و`user_grant` و`kitchen` و`mfa_policy`). الـ Role claims اللي في الـ Token بتتتجاهل (Javadoc بتاع `SecurityConfiguration`).
2. **الرفض هو الأصل (Deny by default).** سلسلتين الـ Filters بيخلصوا بـ `anyRequest().denyAll()`؛ و`/api/**` بيطلب صلاحية `ACCOUNT_ACTIVE` ما عدا قايمة GET عامة صريحة وEndpoint النشاط المجهول.
3. **فحوصات على مستوى الـ Object في الـ Services، و404 للكائنات الغريبة.** شوف [معمارية الـ Backend](backend-architecture-ar.md#الـ-validation-ومعالجة-الأخطاء).
4. **حالة السيرفر هي الصلاحية الوحيدة** للأسعار والسعة والانتقالات؛ والـ Clients عمرها ما بتبعتها.

## استخدام Keycloak

Keycloak 26.8.0 (مثبّت بالـ Digest) بيشتغل كـ Container: `start-dev --import-realm` محليًا (`infra/compose.yaml`)، و`start --import-realm` على المعاينة مع Database خاصة بيه اسمها `keycloak` (`deploy/web-preview/compose.yaml`). الـ Realm اسمه `matbakhy` وهو إعدادات كـ Code:

| الملف | بيستخدمه مين |
|---|---|
| `infra/identity/matbakhy-realm.json` | الـ Stack المحلي (بيتعمل له Render بـ `scripts/dev.sh` في مجلد متولّد متجاهَل في Git ومعاه أسرار محلية) |
| `deploy/web-preview/identity/matbakhy-realm.preview.json` | المعاينة (بيتعمل له Render بـ `deploy.sh render` في المجلد المتولّد على السيرفر) |

حقائق الـ Realm اللي اتحققنا منها في الملفات دي: التسجيل متعطّل، وحماية Brute-force شغّالة، و`sslRequired: external`، وعمر الـ Access token هو 300 ثانية، و Browser flow مخصص اسمه `matbakhy browser` بمستويات LoA (المستوى 1 كلمة سر، والمستوى 2 بيضيف OTP لما `acr` تكون `mfa`/`2`؛ و`acr.loa.map` هو `{"mfa": "2"}`).

| الـ Client | النوع | الغرض |
|---|---|---|
| `matbakhy-web` | Confidential، authorization code، PKCE S256، الـ direct grant مقفول | الـ API نفسه هو اللي بيسجّل دخول مستخدمي الويب نيابةً عن تطبيقي الويب (نمط backend-for-frontend) |
| `matbakhy-mobile` | Public، authorization code، PKCE S256 | تطبيق موبايل العميل. **موجود في الـ Realm المحلي؛ وغايب من ملف Realm المعاينة** (شوف الفجوات) |
| `matbakhy-admin` | Service account من نوع Confidential، من غير standard flow | بيستخدمه الـ API بس لإعادة ضبط الـ MFA وإنهاء الـ Sessions (الأدوار `view-users` و`manage-users`) |

Audience mapper بيضيف `matbakhy-api` للـ Access tokens؛ والـ API بيطلبه.

مستخدمو المزوّد مش هم مستخدمو التطبيق: عند أول طلب متوثّق، `AccountMapper` بينادي `AccountDirectory.resolve(ExternalIdentity)` اللي بيعمل أو بيلاقي صف `app_user` بـ `(issuer, subject)`. وإعداد `bootstrap-admin-subject` (`STAFF_BOOTSTRAP_SUBJECT`) بيدّي `staff.grant` و`audit.read` للـ Subject ده عند أول تسجيل دخول طالما مفيش أدمن موجود؛ وغير كده الموظفين بيتعملوا بس عن طريق الأدمنز الموجودين من خلال `StaffController` (صلاحية `staff.grant`، والـ Step-up مطلوب).

## الـ Claims المستخدمة من الـ Token

من الـ token/ID token بعد التحقق منه (`AccountMapper` و`StaffAssurance`): `sub` و`iss` و`email_verified` و`name`/`preferred_username` و`locale`، وClaims الضمان `acr` و`amr` و`auth_time`. مفيش حاجة تانية بيتوثق فيها. الـ API بيتحقق من التوقيع (JWKS بيتجاب من URL المُصدِر على الـ Back-channel)، والمُصدِر، وانتهاء الصلاحية (بـ `Clock` قابل للحقن)، والـ Audience.

## مسارين للـ Authentication

| | الويب (customer-web، operations-web) | الموبايل |
|---|---|---|
| السلسلة | `webChain` (ترتيب 2) | `bearerChain` (ترتيب 1، بيتم اختيارها بوجود Header اسمه `Authorization: Bearer`) |
| تسجيل الدخول | Spring Security `oauth2Login` على تسجيل الـ Client اسمه `keycloak`؛ بيرجع للصفحة المحددة في `returnTo` بس لو الـ Origin بتاعها في القايمة المسموحة (`WebReturnTo`، بيمنع الـ Open redirects) | التطبيق بيعمل PKCE بنفسه وبيبعت الـ Access token |
| الـ Session | على السيرفر في PostgreSQL (Spring Session JDBC)، والـ Cookie اسمها `MATBAKHY_SESSION`: HttpOnly و SameSite=Lax و Secure لما يتعمل Deployment، ومهلة 30 دقيقة (`WEB_SESSION_TIMEOUT`) | من غير Session؛ والتطبيق بيجدد الـ Token بتاعه بنفسه |
| الـ CSRF | مفعّل (`csrf.spa()` + مستودع Cookie): Cookie اسمها `XSRF-TOKEN` مقروءة، وHeader اسمه `X-XSRF-TOKEN` مع كل عملية تغيير؛ ومتحددة للدومين الأب عن طريق `WEB_COOKIE_DOMAIN` عشان `app.` و`ops.` يقدروا يقروا Cookie اتحطت من `api.` | معطّل (الـ Bearer tokens مبتتبعتش تلقائيًا) |
| الـ CORS | قايمة سماح دقيقة `WEB_ALLOWED_ORIGINS`، والـ Credentials مسموحة، والـ Methods هي GET/POST/PUT/PATCH/DELETE، والـ Headers هي `Content-Type, X-XSRF-TOKEN, Idempotency-Key, If-Match, X-Request-Id, Authorization` | نفس الإعداد بينطبق |
| تسجيل الخروج | `POST /api/v1/auth/logout` بيلغي الـ Session وبيمسح الـ Cookie وبيرجّع `idpLogoutUrl` عشان الـ Client يقدر ينهي Session بتاعة Keycloak كمان | التطبيق بيلغي الـ Refresh token (بأقصى جهد) وبيمسح التخزين الآمن |
| حالة الحساب | `WebAccountFilter` بيعيد بناء الـ Authentication مع كل طلب فالإيقاف (Suspension) بيسري فورًا | `JwtAuthenticationConverter` بينادي `AccountMapper` مع كل طلب |

حالة الحساب بتتحول لـ Authorities: `ACTIVE` بياخد `ACCOUNT_ACTIVE` بالإضافة لـ `PERM_<code>` واحد لكل `user_grant` فعّال وغير منتهي وغير ملغي؛ و`DELETION_REQUESTED` بياخد بس القدرة على تكرار طلب الحذف بتاعه؛ و`SUSPENDED` و`DELETED` مبياخدوش حاجة (`ACCOUNT_DISABLED` 403).

## نموذج الـ Authorization

* **العميل**: أي حساب فعّال. فحوصات الملكية بتستخدم `customer_id` في الـ SQL.
* **الطبّاخ**: حساب بيملك مطبخ (`KitchenDirectory.ownedBy`)؛ وEndpoints الطبّاخ تحت `/api/v1/cook/**` وبتفحص ملكية المطبخ في الـ Service (`kitchen_id` في شرط الـ `lock`).
* **الموظف**: أكواد الصلاحيات في الـ Enum اسمه `Permission` (`backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`): `staff.grant`، `audit.read`، `cook.review`، `content.moderate`، `order.ops`، `order.exception_cancel`، `order.handover_override`، `support.case`، `finance.read`، `finance.verify`، `finance.refund_approve`، `finance.settlement`، `config.read`، `config.write`، `integration.ops`، `security.mfa_policy`، `security.mfa_reset`. مفيش Super-admin: "Admin is not an implicit superuser". والأكواد دي كمان في قايمة سماح بقيد الـ Check على `user_grant.permission`، فأي كود جديد محتاج Migration (V31 هي النموذج).
* الـ Controllers بتستخدم `@PreAuthorize("hasAuthority('PERM_<code>')")` (الـ Method security مفعّل). ولما موظف يوصل لمورد حد تاني بيعدّي على `AccessPolicy.requireOwnerOrPermitted`، اللي بيسجّل الوصول في الـ Audit.
* حالات الرفض: 401 `UNAUTHENTICATED`، 403 `FORBIDDEN`، 403 `ACCOUNT_DISABLED`، 403 `STEP_UP_REQUIRED`، وكلها بتتكتب بواسطة `ApiSecurityErrors` في جسم الخطأ القياسي؛ وحالات الرفض بتتكتب في `audit_event` بنتيجة `DENIED`.

## سياسة الـ MFA: التخزين والفرض

قرار المالك D18 استبدل "الـ MFA للموظفين إجباري" بسياسة قابلة للضبط. Keycloak هو اللي بيعمل الـ Authentication (TOTP)؛ والتطبيق بيخزّن **السياسة والتفضيل** وبيفرض *وجود تأكيد MFA*.

* **التخزين** (`backend/app/src/main/resources/db/migration/V31__mfa_policy.sql`): الجدول `mfa_policy(category, mode, reason, updated_by, version)` بفئات `CUSTOMER` و`KITCHEN` و`STAFF` وأوضاع `DISABLED` و`OPTIONAL` و`REQUIRED` (متزرعة `OPTIONAL` للتلاتة: افتراضي المعاينة)؛ و`user_mfa_preference(user_id, enabled)`؛ و`user_mfa_reset(user_id, reset_at, reset_by)`.
* **القرار** (`MfaPolicyService.decide`): فئات الحساب بتتحسب لكل طلب (كل واحد هو `CUSTOMER`؛ وامتلاك مطبخ بيضيف `KITCHEN`؛ ووجود Grant فعّال بيضيف `STAFF`)؛ والوضع الفعلي هو الأكتر حماية من المنطبقين (`REQUIRED` > `OPTIONAL` > `DISABLED`)؛ والفئة اللي صف سياستها ناقص قيمتها الافتراضية `REQUIRED`؛ `required = REQUIRED or (OPTIONAL and the user's preference on)`؛ و`available = effective != DISABLED`.
* **الفرض** (`MfaEnforcementFilter`، على السلسلتين): لأي حساب فعّال قراره `required`، كل نداء محمي على `/api/**` لازم يبين تأكيد: `acr` ضمن `OIDC_MFA_ACR_VALUES` (الافتراضي `mfa,2`) أو `amr` فيه `mfa` أو `otp` أو `hwk`، والـ Authentication مش أقدم من آخر Reset للحساب. غير كده الـ Filter بيرمي `StepUpRequiredException` -> 403 `STEP_UP_REQUIRED`. المسارات المتاحة دايمًا: `/api/v1/me/security/**` وتسجيل الخروج والـ GETs العامة و`GET /api/v1/me` وEndpoint النشاط.
* **الـ Step-up لإجراءات الموظفين عالية الخطورة** (`StaffAssurance.recentMfa`): Endpoints زي صلاحيات الموظفين (Grants) وقرارات مراجعة الطبّاخين وكتابة الإعدادات وتجاوز التسليم (Handover override) والتحقق من العربون وإدارة الـ MFA بتضيف `and @staffAssurance.recentMfa(authentication)`: تأكيد MFA **و** `auth_time` ضمن `OIDC_STEP_UP_MAX_AGE` (الافتراضي 10 دقايق) **و** أحدث من آخر Reset. ده بينطبق مهما كان وضع السياسة، فالفئة STAFF لو كانت `DISABLED` مبتشيلوش (الـ Web step-up resolver بيفضل يطلب MFA لحسابات STAFF).
* **الـ Web step-up** (`stepUpResolver` في `SecurityConfiguration`): الرابط `/oauth2/authorization/keycloak?stepUp=true` بيضيف `acr_values` و`max_age=0` لطلب الـ Authorization، إلا لو فئات الحساب كلها `DISABLED`. وتطبيقات الويب بتعرض ده من خلال `stepUpUrl(...)` في `api.ts`.
* **إعدادات المستخدم**: `GET /api/v1/me/security` و`PUT /api/v1/me/security/mfa` (`MfaController.preference`). التفعيل بيترفض بخطأ حقل `MFA_UNAVAILABLE` (409) لما الوضع الفعلي للحساب يكون `DISABLED`، وغير كده محتاج Session شايلة تأكيد MFA صالح مش أقدم من آخر Reset (`Decision.satisfiedBy`؛ مفيش نافذة حداثة عامة، فكلمة "recent" مبتنطبقش هنا). والتعطيل ملوش أثر لو الـ MFA مش شغّال؛ ولو شغّال، بيترفض بـ `MFA_REQUIRED_BY_POLICY` تحت `REQUIRED`، وغير كده محتاج تأكيد صالح و`StaffAssurance.recentMfa` (MFA بالإضافة لـ `auth_time` ضمن `OIDC_STEP_UP_MAX_AGE`)، وإلا `STEP_UP_REQUIRED`. وإيقافه دايمًا بينهي Sessions التطبيق بتاعة المستخدم وبعدين بيحاول يعمل تسجيل خروج من المزوّد بأقصى جهد (شوف تحت).
* **الإدارة** (`MfaAdminController`): `GET/PUT /api/v1/ops/security/mfa-policy[/{category}]` (صلاحية `security.mfa_policy`، و`expectedVersion` للـ Optimistic، والسبب، ومسجّل في الـ Audit) و`POST /api/v1/ops/accounts/{accountId}/mfa-reset` (صلاحية `security.mfa_reset`، عمره ما يكون على حساب الشخص نفسه، وسبب من 10 حروف أو أكتر، وسجل الـ Audit بيتكتب قبل أي حاجة مدمّرة، وبعدها بيتمسحوا OTP credentials وبتنتهي Sessions بتاعة Keycloak والتطبيق عن طريق `KeycloakAdmin`).
* الـ Keycloak admin client اختياري (`OIDC_ADMIN_CLIENT_SECRET` فاضي). **إعادة ضبط** الـ MFA الإدارية بتفشل بشكل مقفول (fail closed) بـ 503 (متأكد منها في `backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java`). أما **تعطيل** التفضيل فمبيفشلش: `MfaController.endSessions` دايمًا بينهي Sessions التطبيق، وبعدين بيسجّل ويتخطى (أو بيسجّل تحذير عند فشل) تسجيل الخروج من المزوّد. السياسة والتفضيل بيفضلوا شغّالين في الحالتين.

الحالة: IMPLEMENTED في الكود واختبارات التكامل (`backend/app/src/test/java/com/matbakhy/app/mfa/MfaPolicyIntegrationTest.java` و`backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java` و`backend/app/src/test/java/com/matbakhy/app/identity/MfaResetIntegrationTest.java`). تطبيق Flow الـ Realm على Keycloak المعاينة *الشغّال* هو مهمة للمشغّل (`deploy/web-preview/migrate-mfa.sh` و`deploy/web-preview/identity/mfa-migration.kcadm.sh`؛ و`docs/operations/13-keycloak-mfa-migration.md`). وهل الـ Realm الحيّ متطبّق عليه الـ Flow دلوقتي أم لأ فهو NOT VERIFIED من الـ Repository.

## الـ Sessions

Sessions الويب هي صفوف في `SPRING_SESSION*` (V2)، فبتعدّي إعادة تشغيل الـ API وتعدد الـ Instances. تسجيل الخروج وإيقاف الـ MFA بيمسحوا صفوف. وبعد إعادة ضبط MFA إدارية، Sessions التطبيق بتاعة الحساب ده بتنتهي عن طريق `ApplicationSessions` (Sessions مفهرسة بـ Subject المزوّد). أما Sessions الموبايل فهي Sessions المزوّد بالإضافة لـ Refresh tokens محفوظة على الجهاز.

## تأمين المتصفح والـ Proxy

متعرّف في `deploy/web-preview/Caddyfile` (ومتختبر بـ `scripts/test_proxy_access.py`):

* **Headers الأمان** على كل Host: `Strict-Transport-Security max-age=31536000` و`X-Content-Type-Options: nosniff` و`Referrer-Policy: strict-origin-when-cross-origin` و`X-Robots-Tag: noindex, nofollow`، و Header اسمه `Server` متشال؛ وHosts الـ SPA بتضيف `X-Frame-Options: DENY`.
* **الـ CSP**: `Content-Security-Policy-Report-Only` على `app.` و`ops.`: `default-src 'self'`، السكريبتات والستايلات من النفس بس (`style-src-attr 'unsafe-inline'` لخصائص الـ Style الـ Inline بتاعة React)، والصور من النفس و`data:` وHost الميديا، و`connect-src` النفس وHost الـ API، و`object-src 'none'`، و`frame-ancestors 'none'`. **لسه مش مفروضة**؛ وخطة الترقية في `docs/security/csp-rollout.md` (SEC-004 مسجّلة كمخفّفة جزئيًا، ولسه مفتوحة).
* **قوايم السماح للشبكة (الرفض هو الأصل).** فئتين من IPs الـ Clients: `CUSTOMER_ALLOWED_IPS` (التطبيق، والـ API، والهوية، والميديا) و`STAFF_ALLOWED_IPS` (ويب العمليات، وعلى Host الـ API المسارات `/api/v1/ops/**` و`/api/v1/cook/**`). الاتنين قيمتهم الافتراضية `ALLOWED_CLIENT_IPS`؛ ومفيش حاجة عامة لحد ما المالك يظبط فئة العملاء على `0.0.0.0/0 ::/0`. العمليات ذات الصلاحيات عمرها ما بتتبع فئة العملاء. الغرباء بياخدوا `403 Not available`.
* **المسارات المكشوفة على Host الـ API:** `/api/*` و`/oauth2/*` و`/login/oauth2/*` و`/logout` بس؛ وأي حاجة تانية 404. الـ Actuator مش متاح من خلال الـ Proxy، وPrometheus بيشتغل على بورت إدارة منفصل مفيش Proxy بيوجّه له.
* **Host الهوية:** `id.<domain>` بيقدّم الـ Realm اسمه `matbakhy` وبيرد 404 على `/admin` و`/realms/master`.
* **حدود Admin console:** الـ Admin console بتاع Keycloak على `kc-admin.<domain>`، وهو عن قصد *مش* مقيّد بـ IP (موافقة المالك 2026-10-19، للمعاينة بس)، ومحمي بكلمة سر الـ master-realm بالإضافة لـ OTP إجباري، ومن غير password grant، ومع حماية Brute-force؛ بيتم توجيه `/admin` و`/realms/master` والأصول الثابتة بس؛ والـ Host كله بيرد 404 إلا لو ملف الـ Flag اسمه `/srv/flags/admin-console-public` موجود (fail closed). بيشغّله `deploy/web-preview/admin-console.sh`؛ والقواعد في `docs/operations/14-keycloak-admin-console.md`.
* الـ Containers: الـ Database وKeycloak والتخزين والـ API على شبكة Docker بـ `internal: true` (مفيش طريق للإنترنت)؛ والـ Proxy بس هو اللي بينشر بورتات.

## التعامل مع الأسرار

* مفيش أسرار بتتعمل لها Commit. `scripts/check_secrets.sh` وJob الـ Gitleaks في `.github/workflows/ci.yml` بيفحصوا التاريخ والشجرة.
* التطوير المحلي: `scripts/dev.sh up` بيولّد أسرار محلية عشوائية في ملف infra/.env المتجاهَل في Git وفي Realm متولّد؛ والقيم الافتراضية في `application.yaml` بادئتها `local-dev-only-`؛ و`ProviderConfiguration` بيرفض أسرار التطوير برّه LOCAL/CI (`DeploymentEnvironment.developmentSecrets()`)، بينما `EnvironmentGuard` بيرفض لوحده الـ Profile اسمه `dev` وMigrations الـ `devdata` برّه LOCAL/CI/PREVIEW.
* المعاينة: `./deploy.sh init-secrets` بيعمل `.env` على السيرفر (صلاحية 600) بقيم متولّدة؛ و`compose.yaml` بيعلّم الأسرار المطلوبة بـ `${VAR:?set VAR}`؛ والـ Realm وإعدادات التخزين اللي فيها أسرار بيتعمل لها Render في مجلد متولّد على السيرفر، مش في Git.
* مفاتيح ليها غرض مخصص: `PHONE_CODE_PEPPER` (أكواد التحقق من التليفون بتتخزّن كـ HMAC)، و`HANDOVER_HASH_KEY` و`HANDOVER_ENCRYPTION_KEY` (Hash كود التسليم والنص المشفّر بـ AES-GCM)، و`ACTIVITY_HASH_KEY` (Hash الزائر اليومي)، و`PUSH_TOKEN_HASH_KEY` و`PUSH_TOKEN_ENCRYPTION_KEY` (Tokens الأجهزة)، و`OIDC_WEB_CLIENT_SECRET` و`OIDC_ADMIN_CLIENT_SECRET`، ومفاتيح تخزين الـ Objects.
* القيم الحساسة بيتعمل لها Redaction من تفاصيل الـ Audit والـ Logs (`Redaction` و`RedactedThrowable`، والاختبارات `LogRedactionIntegrationTest` و`AuditAndCorrelationReadinessIntegrationTest`).
* الـ CI بيستخدم أسرار الـ Repository للوصول بـ SSH للمعاينة و`GITHUB_TOKEN` لـ GHCR؛ وخطوات الـ Deployment عمرها ما بتطبعهم (`.github/workflows/deploy-preview.yml`).

## ضوابط أمان تانية تستاهل تعرفها

* **كود التسليم (Handover code)**: سر من ستة أرقام بيظهر لصاحب الأوردر بس، ويتم التحقق منه بـ Hash بمفتاح، خمس محاولات وبعدها قفل، وحد ساعي لكل فاعل (30)، وبينتهي بعد ساعتين من نهاية الـ Slot؛ وتجاوز الموظف محتاج ملاحظة ومرجع دليل وصلاحية وStep-up (`OrderLifecycleService`).
* **التحقق من التليفون**: أكواد متخزنة بـ HMAC، وصلاحية خمس دقايق، وحدود للمحاولات وإعادة الإرسال (`application.yaml`).
* **الرفع (Uploads)**: PUT موقّع مسبقًا (presigned) مربوط بالنوع والحجم في Bucket خاص، وبوابة فحص بتفشل بشكل مقفول، ونسخ عامة للميديا الموافق عليها بس (`ObjectStorage` و`MalwareScanner`).
* **مدخلات صارمة**: خصائص الـ JSON غير المعروفة بتترفض، وBean Validation على الـ Records.
* **Audit بيتضاف عليه بس**: Triggers بالإضافة لصلاحيات مسحوبة من دور وقت التشغيل (شوف [معمارية قاعدة البيانات](database-architecture-ar.md#دور-الـ-database-وقت-التشغيل)).

## القيود المعروفة وفجوات التوثيق

* ملف Realm المعاينة مفيهوش Client اسمه `matbakhy-mobile` رغم إن تطبيق الموبايل متوقعه؛ وتسجيل دخول الموبايل على المعاينة المنشورة هو NOT VERIFIED.
* `docs/security/mfa-policy.md` بيقول إن Keycloak الحيّ ماتغيّرش ولا اتحقق منه بواسطة الـ Agent؛ والـ Repository مش قادر يبيّن الحالة الحيّة الحالية.
* الـ CSP في وضع Report-Only (SEC-004 مفتوحة)؛ ومفيش Rate limiting على مستوى التطبيق غير الحدود المحددة اللي فوق (التسليم، كود التليفون)؛ ومتلقاش Rate limiter عام للـ API (NOT VERIFIED بشكل شامل).
