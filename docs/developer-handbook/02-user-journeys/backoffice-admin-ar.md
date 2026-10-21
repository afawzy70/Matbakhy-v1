# رحلات المستخدم: الـ Backoffice (العمليات، الدعم، عرض المالية، الأدمن)

> **Owner role:** Frontend and mobile
> **Status:** PARTIALLY IMPLEMENTED — معظم أقسام الـ Staff موجودة في ويب العمليات؛ لكن سجل الـ Audit، وعمليات التكامل/الـ Outbox، والتحقق من تقارير الكاش، ولوحة العمليات، وتصدير الـ Audit، مفيهاش شاشة أو مفيهاش Endpoint.
> **Language / اللغة:** [English](backoffice-admin.md) · العربية

الـ Backoffice هو المجموعة التانية من تبويبات `apps/operations-web` (نفس التطبيق بتاع [مساحة عمل الطبّاخ](cook-operations-ar.md)). مسارات الفلوس بالتحديد موجودة في [المالية والدعم](finance-and-support-ar.md)؛ وتسجيل الدخول والـ MFA في [المصادقة وMFA](authentication-and-mfa-ar.md).

## مفيش "أدوار" للـ Staff، فيه صلاحيات ممنوحة بس

أسماء العمليات والدعم والمالية والأدمن هي أوصاف وظيفية. في الكود الوحدة الوحيدة للـ Authorization هي **الصلاحية** الممنوحة لحساب في جدول `user_grant` (`backend/app/src/main/resources/db/migration/V4__grants_and_audit.sql`، و V31 وسّعته). الـ Enum هو `backend/modules/identity/src/main/java/com/matbakhy/identity/api/Permission.java`؛ و`AccountMapper` (`backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java`) بيقرا الصلاحيات الفعّالة **مع كل طلب** وبيحوّل كل واحدة لصلاحية Spring اسمها `PERM_<code>`. الـ Claims بتاعة الأدوار في الـ Token بتتجاهل. الأدمن مش مستخدم خارق (Superuser): الأدمن الأولي (Bootstrap) بياخد `staff.grant` و`audit.read` بس (`GrantService.bootstrapAdministratorIfNone`، بيتحرك بالـ Subject المضبوط للـ Bootstrap في `IdentityProperties`). نيّة المنتج: `docs/product/06-permissions.md`.

الإجراءات عالية الخطورة بتحتاج كمان **MFA حديث** عن طريق حارس SpEL اسمه `@staffAssurance.recentMfa(authentication)` (`backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java`): قيمة `acr` بمستوى MFA (أو `amr` فيه `mfa`/`otp`/`hwk`) و`auth_time` ضمن `OIDC_STEP_UP_MAX_AGE` (الافتراضي 10 دقايق)، ومش أقدم من آخر إعادة ضبط إدارية للـ MFA للحساب. غياب الإثبات بيرجّع `403 STEP_UP_REQUIRED`.

### مصفوفة الصلاحيات (متراجعة على الـ Annotations بتاعة `@PreAuthorize` وعلى فحوصات الواجهة)

| كود الصلاحية | الـ Endpoints اللي بتفتحها (معرّف الـ API) | محتاجة MFA حديث | تبويب ويب العمليات |
|---|---|---|---|
| `cook.review` | قايمة/تفاصيل الطلبات (067، 068)؛ القرار (069)؛ حالة تشغيل المطبخ (070) | القرار وحالة التشغيل: أيوه | المراجعات (`ops/Reviews.tsx`) |
| `content.moderate` | قايمة الكتالوج (071)، إشراف الوجبات (072)، قايمة المراجعات (098)، إشراف المراجعات (073) | لأ | الكتالوج (`ops/Catalogue.tsx`، وفيه إشراف المراجعات) |
| `order.ops` | قايمة/تفاصيل أوردرات العمليات (074، 075) | لأ | الأوردرات (`ops/OpsOrders.tsx`) |
| `order.exception_cancel` | الإلغاء الاستثنائي (076) | لأ (الـ Annotation بيظهر الصلاحية بس) | الأوردرات |
| `order.handover_override` | تجاوز التسليم (077) | أيوه | الأوردرات |
| `support.case` | طابور الحالات، التفاصيل، الرسالة، التحديث (من 078 لـ 081) | لأ | الحالات (`ops/Cases.tsx`) |
| `finance.read` | قايمة تقارير الكاش (082)، كشوف الحساب (086) | لأ | المالية (`ops/Finance.tsx`) |
| `finance.verify` | التحقق من تقرير الكاش (083)؛ مستلمو الدفع وطابور الدفع (104، 106، 107، 109) | 104 و107: أيوه؛ 083: الـ Annotation مفيهوش حارس MFA | المالية → لوحة المدفوعات (`ops/Payments.tsx`) |
| `finance.refund_approve` | طلب/إجراء الاسترداد (084، 085)، كشوف الحساب | لأ | المالية |
| `finance.settlement` | التسويات (087، 088)، كشوف الحساب | لأ | المالية |
| `config.read` / `config.write` | عرض الإعدادات (089)؛ نسخة جديدة (090، كتابة بس) | الكتابة: أيوه | الإعدادات (`ops/Configuration.tsx`) |
| `staff.grant` | قايمة الـ Staff (091)؛ المنح (092)؛ السحب (093) | المنح والسحب: أيوه | الـ Staff (`ops/Staff.tsx`) |
| `audit.read` | سجل الـ Audit (094) | لأ | **مفيش** (API بس) |
| `integration.ops` | قايمة التكاملات (096)، إعادة محاولة الـ Outbox (097) | لأ | **مفيش** (API بس) |
| `security.mfa_policy` | قراءة سياسة الـ MFA (114) وتحديثها (115) | التحديث: أيوه | تبويب Security (`ops/Security.tsx`) |
| `security.mfa_reset` | إعادة ضبط الـ MFA إداريًا (116) | أيوه | تبويب Security |

المصادر: `ConfigurationController` و`OpsOrderController` و`PaymentController` و`ReviewController` و`AuditController` تحت `backend/modules/*/src/main/java/com/matbakhy/*/web/`، بالإضافة لـ `backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java` و`backend/modules/identity/src/main/java/com/matbakhy/identity/web/StaffController.java` و`backend/modules/cooks/src/main/java/com/matbakhy/cooks/web/CookReviewController.java` و`backend/modules/finance/src/main/java/com/matbakhy/finance/web/CashController.java` و`FinanceOpsController.java` و`backend/modules/support/src/main/java/com/matbakhy/support/web/StaffCaseController.java` و`backend/modules/notifications/src/main/java/com/matbakhy/notifications/web/NotificationController.java`.

### إزاي الواجهة بتقرر تعرض أنهي تبويبات

`checkSession` في `apps/operations-web/src/App.tsx` **مابيقراش** الصلاحيات. بيجرّب Endpoint واحد لكل قسم بـ `limit: 1` وبيعرض التبويب بس لو النداء رجع 2xx: `GET /api/v1/ops/cooks` و`/ops/catalogue` و`/ops/cases` و`/ops/orders` و`/ops/configuration` و`/ops/staff`. بالنسبة للمالية بينادي `GET /api/v1/ops/finance/statements` بمعرّف مطبخ كله أصفار وبيعتبر **404 معناها مسموح** (403 = مش مسموح). تبويب Security بيظهر لما `GET /api/v1/me/security` يقول `canManagePolicy` أو `canResetMfa`. التحكم في الظهور من الواجهة للراحة بس؛ والـ API بيفحص كل نداء (E2E-OPS-055 وE2E-FIN-112 وE2E-OPS-058).

نتايج لقيناها أثناء التتبّع ده (مستنتجة من الكود، مش من اختبار):

* فحص المالية بيتقبل لـ `finance.read` أو `finance.settlement` أو `finance.refund_approve` (الـ Annotation على API-086). الحساب اللي معاه `finance.verify` **بس** بياخد 403 على الفحص، فمش بيشوف تبويب المالية مع إن لوحة المدفوعات جواه محتاجة `finance.verify`. NOT VERIFIED باختبار؛ راجع `FinanceOpsController.java` سطر ~72 و`App.tsx`.
* اللي معاهم `audit.read` و`integration.ops` مش بيشوفوا تبويب ليهم.

## عناصر مشتركة بين الرحلات

**الطرف.** حساب Staff معاه الصلاحية الموضحة في كل رحلة؛ ونفس الحساب ممكن يبقى عميل أو طبّاخ كمان. **وسيلة النقل.** Cookie الـ Session على الويب وHeader الـ CSRF. **الـ Audit.** قراءات وكتابات الـ Staff بتسجّل صفوف `audit_event` (للإضافة بس بواسطة Trigger، V4). **الاختبارات (كلها).** `apps/operations-web/src/App.test.tsx` (أقسام الـ Staff بتتجرّب جوه "operations-web cook workspace" و"two-step verification policy (D18)")؛ وفي الـ Backend `backend/app/src/test/java/com/matbakhy/app/identity/StaffAccessIntegrationTest.java` و`backend/app/src/test/java/com/matbakhy/app/security/StaffAssuranceTest.java`؛ واختبارات Playwright في `apps/operations-web/e2e/operations-layout.e2e.ts` ونهاية الرحلة في `customer-journey.e2e.ts`. النتايج للـ Commit الحالي: NOT VERIFIED هنا.

## BO-1 مراجعة الطبّاخ (العمليات)

1. الهدف: التحقق من طلبات الطبّاخين وإدارة إتاحة المطابخ. 2. الطرف: `cook.review`؛ القرارات وتغييرات الحالة محتاجة MFA حديث. 3. المتطلب المسبق: طلب مقدَّم.
4. البداية: تبويب Reviews. 5. الإجراءات: فتح الطابور (فلترة بالحالة/الاسم)، وفتح طلب، وSTART_REVIEW، واتخاذ القرار، وبعدين لو لزم إيقاف مؤقت / تعليق / استعادة مطبخ مع سبب. 6. الاستجابات: تحديث الطابور، وتسجيل القرار مع المراجع؛ ولوحة المطبخ بتعرض تأثير الإجراء على الأوردرات الفعّالة. 7. الـ API: من 067 لـ 070 بالإضافة لـ `GET /api/v1/uploads/{uploadId}` (المستندات الخاصة للمراجع). 8. الـ Backend: `CookReviewService` و`KitchenService` (التفاصيل في [عمليات الطبّاخ](cook-operations-ar.md) CO-2 وCO-7). 9. قاعدة البيانات: `cook_application*` و`kitchen` و`audit_event`. 10. الانتقالات: State Machines بتاعة الطلب والمطبخ في CO-1/CO-7. 11. الأخطاء: مراجعة الشخص لنفسه `FORBIDDEN`، وRevision/Version قديم، و`STEP_UP_REQUIRED` (الواجهة بتعرض رابط التحقق من فحص الـ Session وأسطر الأخطاء). 12. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/cooks/CookWorkflowIntegrationTest.java`. 13. اختبارات المالك E2E: E2E-COOK-049 وE2E-OPS-108 وE2E-OPS-109 وE2E-COOK-050؛ J6. 14. IMPLEMENTED. 15. القيد: المراجع لازم يقرا المستندات من خلال ويب العمليات؛ ومفيش عارض مستندات جوه الـ Repository موصوف غير نداء الجلب.

## BO-2 الإشراف على الكتالوج والمراجعات

1. الهدف: إخفاء الوجبات غير المناسبة أو المراجعات المبلّغ عنها من غير تغيير حالة الطبّاخ. 2. الطرف: `content.moderate`. 3. المتطلب المسبق: وجبات منشورة / مراجعات مبلّغ عنها.
4. البداية: تبويب Catalogue. 5. الإجراءات: فلترة الكتالوج، وإخفاء وجبة بسبب، واستعادتها؛ وعرض المراجعات المبلّغ عنها، وإخفاء مراجعة أو استعادتها بسبب. 6. الاستجابات: الوجبة بتخرج من البيع للعملاء فورًا؛ وحالة الطبّاخ نفسه ماتتغيرش؛ والمراجعة المخفية بتخرج من صفحة المطبخ العامة.
7. الـ API: `GET /api/v1/ops/catalogue` و`POST /api/v1/ops/meals/{mealId}/moderation` و`GET /api/v1/ops/reviews` و`POST /api/v1/ops/reviews/{reviewId}/moderation` (من 071 لـ 073، 098). 8. الـ Backend: `backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/ModerationController.java` و`backend/modules/orders/src/main/java/com/matbakhy/orders/web/ReviewController.java`. 9. قاعدة البيانات: `meal.moderation_state` (V16) و`review` و`review_report` (V20). 10. `VISIBLE ↔ HIDDEN`. 11. الأخطاء: السبب مطلوب؛ تعارض في الـ Version. 12. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/catalog/ModerationIntegrationTest.java` و`ReviewsAndFavoritesIntegrationTest.java`. 13. E2E-OPS-056 وE2E-OPS-104. 14. IMPLEMENTED. 15. القيد: مفيش إجراءات جماعية.

## BO-3 متابعة الأوردرات والاستثناءات

1. الهدف: فحص أي أوردر وحل الاستثناءات من غير تجاوز الـ State Machine. 2. الطرف: `order.ops` (قراءة)؛ `order.exception_cancel`؛ `order.handover_override` + MFA حديث. 3. المتطلب المسبق: الأوردر موجود؛ الإلغاء الاستثنائي محتاج مرجع حالة مش فاضي (قواعد المنتج بتشترط إنه يكون حالة دعم مربوطة، لكن شوف القيود المعروفة: حاليًا هو نص حر متسجّل في الـ Audit وغير مفروض) وحالة `PREPARING` أو `READY`؛ وتجاوز التسليم محتاج ملاحظة لا تقل عن 10 حروف ومرجع دليل. المقصود بيه التسليم المقفول أو العالق، لكن `OrderLifecycleService` مابيفحصش الشرط ده (أي أوردر في حالة تنفيذ مؤهلة للتسليم ممكن الـ Staff المصرّح لهم يتجاوزوه؛ ضيف الفحص واختبارات الحالات السلبية لو لازم يتفرض).
4. البداية: تبويب Orders (`ops/OpsOrders.tsx`). 5. الإجراءات: فلترة/عرض، وفتح أوردر (الجدول الزمني، الإجماليات، بيانات تواصل مخفية جزئيًا؛ وفتح بيانات تواصل العميل بيتسجّل في الـ Audit)، والإلغاء الاستثنائي بسبب (`SAFETY` أو `OPERATIONAL` أو `OTHER`) ومعرّف الحالة، أو تجاوز التسليم بمرجع دليل.
6. الاستجابات: كود التسليم مابيتعرضش للـ Staff أبدًا (`OrderService.staffView`). 7. الـ API: `GET /api/v1/ops/orders` و`GET /api/v1/ops/orders/{orderId}` و`POST …/exception-cancel` و`POST …/handover-override` (من 074 لـ 077). 8. الـ Backend: `OpsOrderController.java` → `OpsOrderService.java`، و`OrderLifecycleService.opsExceptionCancel / opsHandoverOverride`. 9. قاعدة البيانات: `customer_order` و`order_event` و`handover_challenge` و`stock_allocation` و`audit_event`. 10. `PREPARING | READY → CANCELLED` (السعة المستهلكة بتفضل محسوبة، E2E-ORDER-109)؛ وتجاوز التسليم `→ COMPLETED` بالسبب `HANDOVER_OVERRIDE`. 11. الأخطاء: `VALIDATION_FAILED` (`caseId` و`evidenceRef` مطلوبين)، و`STATE_CONFLICT`، و`STEP_UP_REQUIRED`. 12. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/orders/OrderLifecycleIntegrationTest.java`. 13. E2E-ORDER-108 وE2E-ORDER-109 وE2E-ORDER-119 وE2E-OPS-103؛ J1. 14. IMPLEMENTED. 15. القيد: مقاييس لوحة العمليات API-066 NOT IMPLEMENTED (`contracts/endpoints.json`؛ E2E-OPS-112).

## BO-4 طابور الدعم

شوف [المالية والدعم](finance-and-support-ar.md#fs-4-حالات-الدعم-جانب-العميل-وجانب-الـ-staff).

## BO-5 الإعدادات وسياسة التجارة

1. الهدف: تغيير سياسة التجارة (رسوم التوصيل، حدود الأوردر، العمولة ثابتة عند صفر) والـ Feature Flags مع الـ Versioning. 2. الطرف: `config.read` (عرض) / `config.write` + MFA حديث. 3. المتطلب المسبق: رقم الـ Version الحالي.
4. البداية: تبويب Configuration. 5. الإجراءات: قراءة المفاتيح والـ Versions؛ وإرسال Version جديد بقيمة وسبب ومرجع موافقة و`expectedVersion`. 6. الاستجابات: بيتضاف Version جديد، ومابيتعدلش في مكانه أبدًا؛ جلستين من نفس الـ Version بينتجوا تعارض في الـ Version ونموذج متحدّث (E2E-OPS-061). الرسوم الجديدة بتنطبق على عروض الأسعار الجديدة بس (E2E-OPS-106). العمولة لازم تفضل صفر؛ والسياسة الحقيقية محتاجة مرجع موافقة؛ والتغييرات المجدولة بتترفض (E2E-OPS-107).
7. الـ API: `GET /api/v1/ops/configuration` و`POST /api/v1/ops/configuration/{key}/versions` (089، 090). 8. الـ Backend: `ConfigurationController.java` → `backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ConfigurationService.java` و`CommercePolicies.java`. 9. قاعدة البيانات: `commerce_policy` (V10) و`feature_flag` و`config_change` (V19). 10. Versions السياسة للإضافة بس. 11. الأخطاء: `VERSION_CONFLICT` و`VALIDATION_FAILED` و`STEP_UP_REQUIRED` (الواجهة بتخفي سطر الخطأ العادي وبتعتمد على رابط التحقق). 12. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/orders/ConfigurationIntegrationTest.java` و`backend/app/src/test/java/com/matbakhy/app/config/CommerceConfigurationTest.java`. 13. E2E-OPS-061 وE2E-OPS-106 وE2E-OPS-107. 14. IMPLEMENTED. 15. القيد: `GET /api/v1/policies/{key}` (API-008) مش متنفّذ.

## BO-6 صلاحيات الـ Staff (الأدمن)

1. الهدف: منح وسحب الصلاحيات مع المساءلة. 2. الطرف: `staff.grant`؛ المنح والسحب محتاجين MFA حديث. 3. المتطلب المسبق: الحساب المستهدف فعّال؛ وسبب من 3 لـ 500 حرف.
4. البداية: تبويب Staff. 5. الإجراءات: عرض الأعضاء وصلاحياتهم؛ واختيار صلاحية وكتابة سبب للمنح؛ وسحب صلاحية بسبب. 6. الاستجابات: التأثير على الطلب الجاي للمستهدف (الصلاحيات بتتقرا من جديد مع كل طلب؛ والصلاحيات المسحوبة أو المنتهية بتقف فورًا، E2E-OPS-102).
7. الـ API: `GET /api/v1/ops/staff` و`POST /api/v1/ops/staff/{userId}/grants` و`POST …/grant-revocations` (من 091 لـ 093). 8. الـ Backend: `StaffController.java` → `GrantService.grant / revoke`. 9. قاعدة البيانات: `user_grant` و`audit_event`. 10. صلاحية فعّالة → مسحوبة؛ صلاحية فعّالة واحدة فقط لكل مستخدم وصلاحية (Partial Unique Index). 11. الأخطاء: رفع الصلاحيات الذاتي `FORBIDDEN` (متسجّل في الـ Audit)، وصلاحية مجهولة، وانتهاء في الماضي، وصلاحية فعّالة مكررة (`STATE_CONFLICT`)، وسحب آخر حامل لـ `staff.grant` بيترفض (`last administrator`). 12. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/identity/StaffAccessIntegrationTest.java`. 13. E2E-OPS-055 وE2E-OPS-058 وE2E-OPS-059 وE2E-OPS-060 وE2E-OPS-101 وE2E-OPS-102 وCFG-104 وCFG-105؛ J6، J7. 14. IMPLEMENTED. 15. القيود: قايمة الـ Staff بتعرض الحسابات اللي معاها صلاحية فعلًا بس (`GrantService.staff()`)، فالمنح لحساب جديد محتاج معرّف المستخدم الخاص بيه من مصدر تاني؛ وواجهة المنح بتبعت `permission` و`reason` بس (مفيش `expiresAt` في `apps/operations-web/src/ops/Staff.tsx`)، مع إن الـ API بيقبل تاريخ انتهاء.

## BO-7 إدارة الأمان (سياسة الـ MFA وإعادة الضبط)

1. الهدف: ضبط OPTIONAL / REQUIRED / DISABLED لكل فئة وإعادة ضبط الـ Authenticator بتاع مستخدم. 2. الطرف: `security.mfa_policy` و`security.mfa_reset`، والاتنين بـ MFA حديث للكتابة. 3. المتطلب المسبق: عميل أدمن Keycloak مضبوط لإعادة الضبط، وإلا 503 (`docs/security/mfa-policy.md`).
4. البداية: تبويب Security (`ops/Security.tsx`)، ظاهر لكل حساب مسجّل دخول لإعداداته الشخصية. 5. الإجراءات: قراءة سياسات الفئات التلاتة، وتغيير واحدة بـ `expectedVersion` وسبب؛ وإعادة ضبط حساب بالمعرّف مع سبب لا يقل عن 10 حروف.
6–15: شوف [المصادقة وMFA](authentication-and-mfa-ar.md) (AM-5، AM-6). الـ API من 114 لـ 116؛ والـ Backend هو `MfaAdminController.java` و`MfaPolicyService.java` و`KeycloakAdmin.java`؛ والاختبارات `MfaPolicyIntegrationTest.java` و`MfaResetIntegrationTest.java` و`MfaResetAuditIntegrationTest.java`؛ واختبارات E2E هي CFG-015 وCFG-016 وCFG-018 لـ CFG-023 وCFG-122 وE2E-OPS-111؛ J7. IMPLEMENTED.

## BO-8 سجل الـ Audit والتكاملات

1. الهدف: قراءة مين عمل إيه؛ وإعادة محاولة أحداث الـ Outbox العالقة. 2. الطرف: `audit.read`؛ `integration.ops`. 3. المتطلب المسبق: صلاحية.
4. البداية: **مفيش شاشة**. 5. الإجراءات: نداءات API بس. 6. الاستجابات: صفحة Audit بفلاتر `actorId, objectType, objectId, action`، وتقسيم بالـ Cursor، والـ Limit محصور من 1 لـ 100؛ وقراءة سجل الـ Audit نفسها بتتسجّل في الـ Audit. 7. الـ API: `GET /api/v1/ops/audit` (094) و`GET /api/v1/ops/integrations` (096) و`POST /api/v1/ops/outbox/{eventId}/retry` (097)؛ و`GET /api/v1/ops/audit/export` (095) NOT IMPLEMENTED. 8. الـ Backend: `backend/modules/operations/src/main/java/com/matbakhy/operations/web/AuditController.java` و`NotificationController.java` و`OutboxDispatcher.java`. 9. قاعدة البيانات: `audit_event` و`outbox_event`. 10. الـ Outbox `PENDING → SENT | FAILED`. 11. الأخطاء: 403 من غير صلاحية (الرفض بيتسجّل في الـ Audit). 12. الاختبارات: `backend/app/src/test/java/com/matbakhy/app/observability/AuditAndCorrelationReadinessIntegrationTest.java` و`backend/app/src/test/java/com/matbakhy/app/notifications/NotificationIntegrationTest.java`. 13. E2E-OPS-110 وE2E-OPS-112. 14. PARTIALLY IMPLEMENTED (API بس). 15. القيد: مفيش واجهة؛ والتصدير مش متبني.

## تغطية قبول المالك

| المجال | معرّفات E2E بتاعة المالك |
|---|---|
| حدود الأدوار | E2E-OPS-055، E2E-OPS-058، E2E-OPS-062، CFG-005 |
| الصلاحيات الممنوحة | E2E-OPS-059، E2E-OPS-060، E2E-OPS-101، E2E-OPS-102، CFG-105 |
| نهاية رحلة العمليات | E2E-OPS-103 (المجموعة الأساسية J1) |
| بنود غير متنفّذة متسجّلة كحالات | E2E-OPS-112 |

## القيود المعروفة وفجوات التوثيق

* GAP (متطلب غير مفروض): الإلغاء الاستثنائي المفروض يكون مربوط بحالة دعم، لكن `OrderLifecycleService.opsExceptionCancel` بيتأكد بس إن المرجع مش فاضي وبينسخه في سجل الـ Audit (`caseRef`). مابيحمّلش حالة دعم ولا بيتأكد إنها تخص الأوردر، و`OrderLifecycleIntegrationTest` بينجح بـ `"case-77"` مخترعة. الربط محتاج تحقق من وجود الحالة وإنها لنفس الأوردر مع اختبارات للحالات السلبية.
* `docs/product/06-permissions.md` بيذكر أعمدة أدوار و`finance.settlement_verify`؛ والكود مفيهوش كائنات أدوار والصلاحية هي `finance.settlement`. الكود هو الأصح.
* قيد CHECK على `user_grant` في V4 بيسرد 15 كود صلاحية؛ و V31 بيضيف كودين أمان (شوف `backend/app/src/main/resources/db/migration/V31__mfa_policy.sql`).
* ظهور تبويبات الأقسام قايم على الفحص (فوق)؛ ومفيش Endpoint مخصص لـ "صلاحياتي".
