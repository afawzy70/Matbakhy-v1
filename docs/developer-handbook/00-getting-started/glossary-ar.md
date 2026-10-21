# المصطلحات

> **Owner role:** Business documentation
> **Status:** REFERENCE — المصطلحات المستخدمة في الكود والواجهة ومستندات المنتج، مع النصوص العربية الموجودة فعلًا في ملفات الـ i18n بتاعة الـ Front-end.
> **Language / اللغة:** [English](glossary.md) · العربية

النصوص العربية اللي تحت اتنسخت من ملفات الـ i18n واتراجعت هناك: `apps/customer-web/src/i18n.ts` (ويب العميل)، و`apps/customer-mobile/src/i18n.ts` (موبايل العميل)، و`apps/operations-web/src/i18n.ts` (مساحة الطبّاخ والـ Backoffice). أي مصطلح مالوش نص في الواجهة متعلّم بعبارة "no UI string". الفلوس دايمًا أرقام صحيحة بالقرش (`1 EGP = 100 piastres`). شوف كمان [نظرة عامة على المشروع](project-overview-ar.md) و[قواعد العمل](../01-business/business-rules-ar.md).

## مصطلحات البيزنس والواجهة (عربي / إنجليزي)

| المصطلح بالإنجليزي | النص العربي في الواجهة | مكان الاستخدام | المعنى |
|---|---|---|---|
| Basket / cart | السلة | ويب العميل `nav.cart`، الموبايل `replaceBasket` | قائمة على جهاز العميل فيها وجبات من مطبخ واحد وSlot واحد؛ مبتحجزش حاجة |
| Replace basket | استبدال السلة | ويب العميل `replaceTitle` | تأكيد بيظهر لما العميل يضيف من مطبخ أو Slot تاني |
| My orders | طلباتي | ويب العميل `nav.orders` | قائمة أوردرات العميل |
| Home / browse | الرئيسية | ويب العميل `nav.browse` | صفحة الاستكشاف |
| Kitchen | مطبخ (المطبخ) | ويب العمليات `settings: "إعدادات المطبخ"` | الهوية اللي بيبيع بيها الطبّاخ المعتمد؛ الجدول `kitchen` |
| Cook / chef | الطاهي | ويب العمليات `section: "مساحة الطاهي"` (مساحة الطبّاخ)؛ نص `handoverCode` في ويب العميل | الشخص اللي بيملك المطبخ |
| Meal / dish | الأكلات (عنوان القائمة) | ويب العميل `home.dishes` | عنصر منشور له Revisions؛ الجدول `meal` |
| Slot | مفيش مصطلح مخصّص في الواجهة؛ بيتعرض كتاريخ ونافذة وقت | `SlotView` في `OrderService` | نافذة بتاريخ محدّد وعدد حصص محدود؛ الجدول `cook_slot` |
| Quote | العرض / عروض الأسعار | ويب العميل `QUOTE_EXPIRED` ("انتهت صلاحية العرض")؛ ويب العمليات `newQuotesOnly` ("عروض الأسعار الجديدة") | عرض سعر السيرفر بيحسبه، غير قابل للتعديل، وصالح 5 دقايق |
| Total | الإجمالي | ويب العميل `newTotalReview` | المجموع الفرعي + رسوم التوصيل + الضريبة |
| Price changed | تغيّر السعر | ويب العميل `priceChangeTitle` | عنوان الإشعار اللي بيظهر بعد `PRICE_CHANGED` (D13) |
| Accept new total | أوافق على الإجمالي الجديد | ويب العميل `acceptNewTotal` | موافقة صريحة جديدة مطلوبة قبل تسجيل الأوردر |
| Cash on receipt (COD) | الدفع عند الاستلام | ويب العميل `payment.cod` | العميل بيدفع المبلغ كله كاش للمطبخ وقت التسليم |
| Deposit | عربون | ويب العميل `payment.deposit` ("عربون يُحوّل للمطبخ مباشرة") | جزء من الإجمالي بيتحوّل مباشرة للحساب المعتمد بتاع المطبخ |
| I transferred the deposit | حوّلت العربون | ويب العميل `payment.iSent` | بلاغ من العميل؛ معلومة مش دفع |
| Waiting for deposit | في انتظار تحويل العربون | `WAITING_FOR_DEPOSIT` | حالة الدفع |
| Kitchen reported receipt | المطبخ أبلغ باستلام العربون | `KITCHEN_RECEIPT_REPORTED` | إفادة المطبخ؛ مش "متحقق منها بنكيًا" أبدًا |
| Refund owed | المطبخ عليه رد العربون | `REFUND_OWED` | الموظفين قرّروا إن المطبخ لازم يرجّع العربون |
| Handover code | رمز التسليم | ويب العميل `handoverCode` | كود من ست أرقام بيظهر للعميل، والطبّاخ بيدخله وقت التسليم |
| Waiting for the kitchen | بانتظار قبول المطبخ | `PENDING_ACCEPTANCE` | حالة الأوردر |
| Accepted | تم القبول | `ACCEPTED` | حالة الأوردر |
| Preparing | قيد التحضير | `PREPARING` | حالة الأوردر |
| Ready | جاهز | `READY` | حالة الأوردر |
| On the way | في الطريق إليك | `OUT_FOR_DELIVERY` | حالة الأوردر |
| Delivered / completed | تم التسليم | `COMPLETED` | حالة الأوردر (صيغة الواجهة هي نفسها للاستلام) |
| Rejected by the kitchen | رفضه المطبخ | `REJECTED` | حالة الأوردر |
| Not accepted in time | انتهت مهلة القبول | `EXPIRED` | حالة الأوردر |
| Cancelled | ملغي | `CANCELLED` | حالة الأوردر |
| Delivery failed | تعذر التوصيل | `DELIVERY_FAILED` | حالة الأوردر |
| Support case | حالات الدعم | ويب العميل `supportCases` | شكوى أو طلب مرتبط بأوردر معيّن |
| Cancellation request | طلب إلغاء | ويب العميل `caseCategories.CANCELLATION_REQUEST` | حالة بيفتحها العميل بعد القبول |
| Deposit review | مراجعة العربون | `caseCategories.PAYMENT` | حالة دفع بيفتحها السيستم |
| Food safety | سلامة الغذاء | `caseCategories.FOOD_SAFETY` | فئة دعم حرجة |
| Accept / Reject / Start preparation | قبول / رفض / بدء التحضير | ويب العمليات `ACCEPT`، `REJECT`، `PREPARE` | إجراءات الطبّاخ |
| Kitchen performance | أداء المطبخ | ويب العمليات `title` بتاع بلوك الـ Insights | صفحة Insights مجانية للطبّاخين |
| Staff permissions | صلاحيات الموظفين | ويب العمليات | شاشة منح الصلاحيات للموظفين في الـ Backoffice |
| Finance | المالية | ويب العمليات | قسم المالية في الـ Backoffice |
| Support | الدعم | ويب العمليات `cases` | طابور الحالات في الـ Backoffice |
| Start review / Approve / Request changes / Reject | بدء المراجعة / اعتماد / طلب تعديلات / رفض | ويب العمليات `decision` | قرارات طلبات الطبّاخين |
| Demo environment | بيئة تجريبية | ويب العميل `demoNoticeTitle` | إشعار دائم إن البيانات والفلوس محاكاة |
| Sign in / Sign out | تسجيل الدخول / تسجيل الخروج | ويب العميل | الـ Authentication |
| Intent tiles | وجبة ليا أو لينا / أكمل سفرتي / أجهز عزومة | ويب العميل `home.intents` | نقاط الدخول في الصفحة الرئيسية: وجبة ليا أو لينا، أكمل سفرتي، أجهز عزومة |

## مصطلحات الـ Domain والمصطلحات التقنية

| المصطلح | المعنى | مكانه |
|---|---|---|
| Piastre / minor unit | 1/100 جنيه؛ كل الفلوس بتتخزّن كأرقام صحيحة `*_minor` | سجلات `Money` في `backend/modules/orders` |
| Egypt time | `Africa/Cairo`؛ اللحظات بتتخزّن بتوقيت UTC | `backend/modules/availability/src/main/java/com/matbakhy/availability/internal/CairoTime.java` |
| Quote | صف في `quote` (+ `quote_item`)؛ مبيحجزش مخزون؛ بينتهي؛ غير قابل للتعديل | `QuoteService` |
| Stale quote | Quote اتغيّر فيها السعر أو السياسة أو العنوان أو شروط الدفع من وقت ما اتحسبت؛ تسجيل الأوردر بيترفض بـ `PRICE_CHANGED` | [التسعير والدفع](../01-business/pricing-and-payments-ar.md) |
| Snapshot | نسخة مجمّدة من محتوى الوجبة والأسعار وإصدارات السياسات وبيانات التواصل والعنوان، متخزّنة على الأوردر | `order_item.meal_snapshot`، `customer_order` |
| Meal revision | إصدار غير قابل للتعديل من محتوى الوجبة، شامل السعر | `meal_revision` |
| Commerce policy | إعدادات بيزنس بإصدارات: الضريبة، العمولة، مدة صلاحية الـ Quote، نوافذ القبول، وإصدارات الشروط والإلغاء | `commerce_policy`، `CommercePolicies` |
| Synthetic policy | سياسة وهمية مسموحة بس في local/CI/preview؛ السياسات الحقيقية محتاجة مرجع موافقة | `SyntheticPolicyPermission` |
| Reservation / allocation / consumption | مراحل المخزون التلاتة: الأوردر اتسجّل، الأوردر اتقبل، التحضير بدأ | `SlotInventory.Stage` |
| Cutoff | بداية الـ Slot ناقص مهلة الطلب المسبق (lead time)؛ بعده مفيش أوردرات جديدة ولا تعديل للسعة | `cook_slot.cutoff_at` |
| Acceptance deadline (`acceptBy`) | الوقت اللي الأوردر المعلّق بينتهي فيه | `customer_order.accept_by` |
| Idempotency key | الـ Header اسمه `Idempotency-Key`؛ النطاق هو المنفّذ + الأمر + المفتاح؛ `expires_at` = +24 h بيتسجّل بس مش بيتطبّق | `JdbcIdempotentCommands` |
| `clientRequestId` | معرّف بيولّده العميل بيحدد محاولة تسجيل الأوردر بشكل دائم | `customer_order_request_uq` |
| Request fingerprint | SHA-256 على الـ Quote ومعرّف الطلب وإصدارات الشروط والملاحظة، عشان يكتشف إعادة المحاولة المتغيّرة | `OrderService.fingerprint` |
| Optimistic version / `expectedVersion` | الأوامر بتبعت الإصدار اللي شافته؛ القديمة بترجّع `VERSION_CONFLICT` | كل أوامر دورة الحياة |
| Outbox | الجدول `outbox_event` بيتكتب في نفس الـ Transaction بتاعة التغيير؛ وفيه Dispatcher بيحوّل الأحداث لإشعارات | `OutboxDispatcher` |
| Notification / inbox | رسالة داخل التطبيق لكل مستلم، من غير تكرار | `notification`، `notification_delivery` (V14) |
| Grant (permission grant) | صف في `user_grant` بيدّي حساب صلاحية موظف واحدة باسمها، وممكن تنتهي | `GrantService` |
| Permission | واحدة من 17 قيمة، مثلًا `finance.verify`؛ بتتحوّل لصلاحية `PERM_<code>` | `Permission` |
| Bootstrap administrator | أول أدمن بيتعمل من إعدادات البيئة؛ معاه `staff.grant` و`audit.read` | `GrantService.bootstrapAdministratorIfNone` |
| Object-level authorization | فحص حق المستخدم في السجل المحدّد، مش الدور بس؛ السجلات الغريبة بترجّع 404 | [الأطراف والصلاحيات](../01-business/actors-and-permissions-ar.md) |
| Separation of duties | خطوتين لازم يعملهم ناس مختلفة (البلاغ مقابل التحقق، الطلب مقابل الموافقة) | `CashService`، `FinanceOpsService` |
| MFA policy | الوضع `DISABLED` أو `OPTIONAL` أو `REQUIRED` لكل فئة `CUSTOMER` و`KITCHEN` و`STAFF` | `MfaPolicyService`، `docs/security/mfa-policy.md` |
| Step-up | إعادة Authentication بعامل تاني بيطلبها الـ API (`STEP_UP_REQUIRED`) | `MfaEnforcementFilter`، `StaffAssurance` |
| Recent MFA | MFA أكّده الـ Provider ضمن نافذة الـ Step-up، بعد آخر إعادة ضبط من الأدمن | `StaffAssurance.recentMfa` |
| OIDC / PKCE | تسجيل دخول OpenID Connect عن طريق Keycloak؛ الـ PKCE بيحمي تبادل الكود في التطبيقات الـ Native | `SecurityConfiguration` |
| Handover challenge | سجل على السيرفر لكود التسليم (Hash بمفتاح + قيمة العرض مشفّرة، المحاولات، الانتهاء) | `handover_challenge` |
| Cash obligation | المبلغ اللي العميل مدين بيه كاش وقت التسليم (الإجمالي ناقص العربون) | `cash_obligation` |
| Cash report | إقرار الطبّاخ بالكاش اللي اتحصّل على أوردر مكتمل | `cash_report` |
| Verification | موظف المالية بيأكّد بلاغ الكاش (مش اللي قدّم البلاغ أبدًا) | `CashService.verify` |
| Deposit recipient | بيانات InstaPay / المحفظة / البنك بتاعة المطبخ، والموظفين بيعتمدوها قبل ما العملاء يشوفوها | `kitchen_payment_profile` |
| Reconciliation | مقارنة بشرية بين البلاغات وكشوف الحساب؛ الحالات `RECONCILE_PENDING` و`DISPUTED` | `PaymentService` |
| Ledger | قيود عمولة متوازنة وبتتضاف بس (مبالغها صفر وقت الإطلاق) | `ledger_group`، `ledger_entry` |
| Settlement | دفعة من بنود دفتر العمولة اللي المطبخ بيحوّلها للمنصة (الاتجاه `COOK_PAYS_PLATFORM`، مش دفع للمطبخ)؛ خامل تحت نظام صفر عمولة | `FinanceOpsService` |
| Operational approval | موظفين راجعوا طلب الطبّاخ؛ التسمية `OPERATIONALLY_VERIFIED`؛ مش ضمان لسلامة الغذاء | `KitchenService` |
| Moderation (`HIDDEN`/`VISIBLE`) | علامة ظهور بيحطها الموظفين على الوجبات والتقييمات، مستقلة عن حالة الطبّاخ نفسه | `MealService`، `ReviewService` |
| Owner E2E case | حالة قبول مرقّمة، يدوية أو آلية، مثلًا `E2E-ORDER-021` | `docs/quality/owner-acceptance/cases/*.json` |
| Synthetic data | حسابات ومناطق وسياسات ومستلمين دفع وهميين بشكل واضح، مسموحين بس في local/CI/preview | `backend/app/src/main/resources/db/devdata/` |
| BR / TC / MB | معرّفات قاعدة العمل، وسيناريو الاختبار، وقصة الـ Backlog | `planning/rules.json`، `planning/scenarios.json`، `planning/backlog.json` |
| D-number | معرّف قرار المالك (D10 صفر عمولة، D13 تغيّر السعر، D14 تاريخ الأسعار، D16 المدفوعات، D18 الـ MFA) | `docs/product/01-context-and-decisions.md` |

## القيود المعروفة وفجوات التوثيق

- مفيش مصطلح عربي للـ "Slot" بيظهر للعميل في الملفات اللي اتراجعت؛ الواجهة بتعرض التواريخ والأوقات. NOT VERIFIED لو فيه تسمية في Component مااتراجعش.
- المصطلحات العربية للحالات الخاصة بالموظفين بس (مثلًا `RECONCILE_PENDING`) موجودة في `apps/operations-web/src/i18n.ts` لكن ماتنسخوش كلهم هنا.
