# رحلات المستخدم: المالية والدعم

> **Owner role:** Business documentation
> **Status:** PARTIALLY IMPLEMENTED — مطابقة العربون، والاستردادات، والتسويات، وكشوف الحساب، وحالات الدعم ليها API وشاشات؛ والتحقق من تقرير الكاش له API ومفيش له شاشة؛ كل الفلوس بيانات تجريبية والفلوس الحية مقفولة بشروط.
> **Language / اللغة:** [English](finance-and-support.md) · العربية

قواعد الفلوس اللي لازم تفتكرها: صفر عمولة لمطبخي ومفيش رسوم منصة على العميل في الـ MVP؛ مطبخي عمره ما بيمسك فلوس العميل؛ والعربون تحويل يدوي مباشر من العميل للمستلم المعتمد عند المطبخ، والمطبخ **بيبلّغ** عن الاستلام (عمره ما يتقال "متحقق منه بنكيًا"). الخلفية: [التسعير والدفع](../01-business/pricing-and-payments-ar.md)، `docs/product/09-deposit-payment-states.md`، [تنفيذ الدفع](../04-codebase/payment-implementation-ar.md). صلاحيات الـ Staff: [إدارة الـ Backoffice](backoffice-admin-ar.md).

عناصر مشتركة. **الطرف:** الـ Staff اللي معاهم الصلاحية المذكورة لكل رحلة؛ والطبّاخ والعميل لنصفهم هم. **وسيلة النقل:** Session على الويب + CSRF. **الـ Audit:** كل قرار Staff بيكتب `audit_event`. **الاختبارات (كلها):** `backend/app/src/test/java/com/matbakhy/app/finance/CashAndLedgerIntegrationTest.java`، `FinanceOperationsIntegrationTest.java`، `ZeroCommissionIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/payments/DepositPaymentIntegrationTest.java`، `PaymentReconciliationIntegrationTest.java`، `backend/app/src/test/java/com/matbakhy/app/support/cases/SupportIntegrationTest.java`، `apps/operations-web/src/App.test.tsx`، `apps/operations-web/e2e/money-reflow.e2e.ts`. النتايج للـ Commit الحالي: NOT VERIFIED هنا.

## FS-1 الدفع كاش عند الاستلام: التقرير والتحقق

1. **الهدف.** تسجيل إن المطبخ حصّل الكاش المستحق عند التسليم، والمالية تتحقق من المبلغ بالظبط.
2. **الطرف.** الطبّاخ بيبلّغ؛ والـ Staff اللي معاهم `finance.verify` بيتحققوا؛ و`finance.read` ممكن يعرض القايمة.
3. **المتطلبات المسبقة.** الأوردر `COMPLETED`، والتزام الكاش `DUE`.
4. **البداية.** الطبّاخ: كارت الأوردر ("cash report"). المالية: **مفيش شاشة** (API بس).
5. **الإجراءات.** الطبّاخ بيبعت المبلغ والعملة ووقت التحصيل والطريقة والمرجع الخارجي مرة واحدة لكل أوردر. المالية بتعرض التقارير وبتتحقق أو بتعترض (Dispute) بالمبلغ المتحقق منه.
6. **الاستجابات.** الـ VERIFY لازم يساوي المبلغ المستحق، وإلا لازم يبقى DISPUTE بسبب لا يقل عن 3 حروف (`REQUIRED_FOR_EXCEPTIONS`، `MUST_EQUAL_AMOUNT_DUE_OR_DISPUTE`). الشخص اللي بلّغ مايقدرش يتحقق أبدًا (`FORBIDDEN separation of duties`، والرفض متسجّل في الـ Audit).
7. **الـ API.** `POST /api/v1/cook/orders/{orderId}/cash-reports` (063)، `GET /api/v1/ops/finance/cash-reports` (082)، `POST /api/v1/ops/finance/cash-reports/{reportId}/verification` (083).
8. **الـ Backend.** `backend/modules/finance/src/main/java/com/matbakhy/finance/web/CashController.java` → `internal/CashService.java` (`report`، `verify`، `refuseSelfVerification`).
9. **قاعدة البيانات.** `cash_obligation` (V11)، `cash_report` (V13).
10. **الانتقالات.** التزام الكاش `DUE → REPORTED → VERIFIED | DISPUTED`؛ و`NOT_DUE` للأوردرات المرفوضة أو المنتهية ولإلغاء الطبّاخ قبل التحضير. إلغاء العمليات الاستثنائي (بعد بداية التحضير) بيسيب التزام الكاش من غير تغيير للمراجعة اليدوية من المالية (`OrderLifecycleService.opsExceptionCancel`).
11. **الأخطاء.** `STATE_CONFLICT` (تقرير قبل التسليم، اتبلّغ قبل كده، اتقرر قبل كده)، `VERSION_CONFLICT`.
12. **الاختبارات.** `CashAndLedgerIntegrationTest.java`، `FinanceOperationsIntegrationTest.java`.
13. **اختبارات المالك E2E.** E2E-FIN-038، E2E-FIN-043، E2E-FIN-102، E2E-FIN-114؛ J2.
14. **الحالة.** الـ API IMPLEMENTED؛ واجهة التحقق المالي مش موجودة (اتدوّر في `apps/operations-web/src/ops`: مفيش نداء لـ `cash-reports`).
15. **القيود.** حالات المالك اللي بتتحقق من الكاش لازم تستخدم الـ API.

## FS-2 مدفوعات العربون والمطابقة

1. **الهدف.** التعامل مع عربون يدوي 50% (يضبطه المطبخ) من تسجيل الأوردر لحد المطابقة.
2. **الطرف.** العميل (بيبلّغ عن التحويل)، والطبّاخ (بيبلّغ عن الاستلام، والمشكلة، والاسترداد)، وموظفو المالية `finance.verify` (بيوافقوا على المستلم، وبيقرروا في النزاعات).
3. **المتطلبات المسبقة.** المطبخ عنده مستلم دفع **معتمد**؛ غير كده العربون مابيتعرضش والكاش يفضل شغّال (E2E-FIN-105). المستلم اللي اتغيّر بيفضل مخفي لحد ما الـ Staff يوافقوا (E2E-FIN-103).
4. **البداية.** صفحة أوردر العميل؛ كارت أوردر الطبّاخ وإعداداته (`apps/operations-web/src/cook/Payments.tsx`)؛ لوحات الدفع في تبويب المالية (`apps/operations-web/src/ops/Payments.tsx`).
5. **الإجراءات.** العميل: "I sent the deposit" بمرجع اختياري. الطبّاخ: "received" (المبلغ لازم يساوي عربون السيرفر)، "not received"، "different amount"، وبعدين "refund sent" بمرجع. المالية: الموافقة على المستلم بسبب؛ عرض الطابور بالحالة `DISPUTED | RECONCILE_PENDING | REFUND_OWED`؛ والقرار `REFUND_OWED` أو `CLOSED_NO_FUNDS` بعد ما الأوردر ينتهي.
6. **الاستجابات.** أوردر العربون مايقدرش ينتقل لـ `PREPARING` لحد ما المطبخ يبلّغ بالاستلام (القبول منفصل، E2E-FIN-106). "Not received" بيفتح حالة دعم PAYMENT واحدة لكل أوردر (V28) وبيسيب الأوردر من غير تغيير.
7. **الـ API.** 100، 101، 102، 103، 104، 105، 106، 107، 108، 109 (`POST /api/v1/orders/{orderId}/payment/transfer-report`، `POST /api/v1/cook/orders/{orderId}/payment/receipt|problem|refund`، `GET/PUT /api/v1/cook/payment-profile`، `POST /api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval`، `GET /api/v1/ops/payments`، `POST /api/v1/ops/orders/{orderId}/payment/decision`، `GET /api/v1/ops/payment-recipients`).
8. **الـ Backend.** `backend/modules/orders/src/main/java/com/matbakhy/orders/web/PaymentController.java` → `internal/PaymentService.java` (`reportTransfer`، `kitchenReceipt`، `reportProblem`، `decide`، `reportRefund`، `requireReadyForPreparation`، `approveRecipient`).
9. **قاعدة البيانات.** `kitchen_payment_profile`، `order_payment`، `payment_event` (V24)، إضافات المطابقة (V25)، ربط `support_case` (V28).
10. **الانتقالات.** `NOT_REQUIRED` (COD) أو `WAITING_FOR_DEPOSIT` → `CUSTOMER_TRANSFER_REPORTED` → `KITCHEN_RECEIPT_REPORTED`؛ المشاكل → `DISPUTED`؛ الأوردرات المنتهية → `RECONCILE_PENDING`، `EXPIRED`، `REFUND_OWED`، `REFUNDED`، `CLOSED_NO_FUNDS`. القايمة بالظبط: قيد CHECK في V24 و`docs/product/09-deposit-payment-states.md` (المستند للسياق؛ والـ Migration مع `PaymentService` هم المرجع).
11. **الأخطاء.** `ORDER_ENDED`، `NO_DEPOSIT_WAITING`، `NOTHING_TO_DISPUTE`، `NOTHING_TO_DECIDE`، `ORDER_ACTIVE`، المستلم `MISSING`؛ العملاء مايقدروش يفرضوا مبالغ (E2E-FIN-041).
12. **الاختبارات.** `DepositPaymentIntegrationTest.java`، `PaymentReconciliationIntegrationTest.java`، وفي الويب `App.test.tsx` (بلوكات MB-053 في ويب العميل والموبايل).
13. **اختبارات المالك E2E.** من E2E-FIN-039 لـ E2E-FIN-042، E2E-FIN-045، E2E-FIN-101، E2E-FIN-103، من E2E-FIN-106 لـ E2E-FIN-109، E2E-FIN-113، E2E-FIN-115، E2E-COOK-108؛ J3، J4.
14. **الحالة.** IMPLEMENTED بمستلمين تجريبيين؛ والفلوس الحقيقية DISABLED بقرار المالك (E2E-FIN-047).
15. **القيود.** مفيش API بنك، ومفيش مطابقة تلقائية: الإنسان هو اللي بيطابق.

## FS-3 الاستردادات والتسويات وكشوف الحساب (المالية)

1. **الهدف.** تسجيل استرداد المطبخ للعميل بالكامل للكاش المتحقق منه والمحصّل عند المطبخ (مطبخي عمره ما بيمسك الكاش)، وتسجيل تسويات سداد العمولة (المطبخ بيدفع للمنصة؛ خاملة عند صفر عمولة)، وقراءة كشوف الحساب.
2. **الطرف.** `finance.refund_approve`، `finance.settlement`، `finance.read`.
3. **المتطلبات المسبقة.** الاسترداد: حالة الكاش `VERIFIED` بمبلغ متحقق منه؛ ومقدّم الطلب والموافق مختلفين.
4. **البداية.** تبويب Finance: أدخل UUID مطبخ عشان تحمّل كشف حسابه؛ ونماذج الاسترداد والتسوية تحته (`apps/operations-web/src/ops/Finance.tsx`).
5. **الإجراءات.** طلب استرداد كامل (معرّف الأوردر، مرجع الحالة، المبلغ، السبب)؛ وبعدين APPROVE، PAYMENT_RECORDED (مرجع الدليل)، VERIFY أو REJECT؛ ودفعات التسوية REVIEW، PAYMENT_RECORDED، RECONCILE، CANCEL.
6. **الاستجابات.** المبلغ القابل للاسترداد بالظبط هو المقبول بس (`FULL_REFUND_REQUIRED`، `OVER_REFUND`)؛ كشوف الحساب مابتعرضش عمولة تحت سياسة صفر العمولة (`hasCommission` في `apps/operations-web/src/api.ts`)؛ وتحت صفر عمولة التسويات خاملة، والقيود ذات المبلغ صفر مايتحطش في دفعات (E2E-FIN-110).
7. **الـ API.** 084، 085، 086، 087، 088 (`/api/v1/ops/finance/refunds`، `…/refunds/{refundId}/action`، `…/statements`، `…/settlements`، `…/settlements/{batchId}/action`)؛ وجانب الطبّاخ `GET /api/v1/cook/statements` (064).
8. **الـ Backend.** `FinanceOpsController.java` → `FinanceOpsService.java`؛ `StatementController.java` → `StatementService.java`؛ `LedgerService.java`، `LedgerPostingJob.java`.
9. **قاعدة البيانات.** `refund`، `settlement_batch`، `settlement_line` (V18)، `ledger_group` (الأنواع `COMMISSION` و`COMMISSION_REVERSAL`، V18)، `ledger_entry` (V13)، `cash_report`.
10. **الانتقالات.** الاسترداد `REQUESTED → APPROVED → PAID → VERIFIED` أو `REJECTED` (الموافق ≠ مقدّم الطلب؛ المتحقق ≠ الدافع). التسوية `DRAFT → REVIEWED → PAYMENT_RECORDED → RECONCILED | CANCELLED`.
11. **الأخطاء.** `VERSION_CONFLICT`، `STATE_CONFLICT`، `FORBIDDEN` (فصل المهام)، `VALIDATION_FAILED`.
12. **الاختبارات.** `FinanceOperationsIntegrationTest.java`، `ZeroCommissionIntegrationTest.java`، `apps/operations-web/e2e/money-reflow.e2e.ts`.
13. **اختبارات المالك E2E.** E2E-FIN-044، E2E-FIN-045، E2E-FIN-046، E2E-FIN-110، E2E-FIN-111، E2E-FIN-112.
14. **الحالة.** IMPLEMENTED (دفتر قيود خامل تحت صفر عمولة).
15. **القيود.** البحث عن كشف حساب محتاج UUID مطبخ بيتكتب بإيد؛ ومفيش بحث عن المطابخ في واجهة المالية. ظهور تبويب المالية قايم على الفحص (شوف [إدارة الـ Backoffice](backoffice-admin-ar.md)).

## FS-4 حالات الدعم (جانب العميل وجانب الـ Staff)

1. **الهدف.** حل مشكلة عميل عن أوردر بمحادثة ممكن تتتبّع.
2. **الطرف.** العميل (حالاته هو)؛ الـ Staff اللي معاهم `support.case` (كل الحالات).
3. **المتطلبات المسبقة.** أوردر عميل؛ التصنيفات `LATE, MISSING_ITEM, QUALITY, FOOD_SAFETY, CASH, HANDOVER, OTHER`؛ طلبات الإلغاء بعد القبول بتعمل تصنيف `CANCELLATION_REQUEST`.
4. **البداية.** العميل: صفحة الحساب / صفحة الأوردر؛ الـ Staff: تبويب Cases (`apps/operations-web/src/ops/Cases.tsx`).
5. **الإجراءات.** العميل بيفتح حالة وبيرد. الـ Staff بيفلتروا (`state, category, severity, mine, overdue`)، وبيفتحوا حالة (كل عرض من الـ Staff متسجّل في الـ Audit)، وبينشروا رسالة PUBLIC أو STAFF_ONLY، وبيسندوا الحالة لنفسهم، وبيغيّروا الحالة، وبيحطوا كود حل (`INFORMATION_PROVIDED`، `ORDER_CANCELLED`، `CANCELLATION_DECLINED`، `REFUND_REQUESTED`، `KITCHEN_FOLLOW_UP`، `DUPLICATE`، `NO_ACTION`، `OTHER`) مع سبب.
6. **الاستجابات.** العملاء بيشوفوا الرسائل العامة بس؛ والملاحظات الداخلية بتفضل مخفية (E2E-OPS-057). رد العميل بيعيد فتح حالة محلولة؛ والحالات المحلولة بس هي اللي ممكن تتقفل؛ والمقفولة نهائية (E2E-OPS-105).
7. **الـ API.** العميل من 035 لـ 038 بالإضافة لـ `POST /api/v1/orders/{orderId}/cancellation-requests` (028)؛ الـ Staff من 078 لـ 081.
8. **الـ Backend.** `backend/modules/support/src/main/java/com/matbakhy/support/web/SupportController.java`، `StaffCaseController.java` (على مستوى الكلاس `PERM_support.case`) → `internal/SupportService.java`.
9. **قاعدة البيانات.** `support_case`، `case_message` (V17)؛ ربط الدفع V28.
10. **الانتقالات.** `OPEN → IN_PROGRESS | WAITING_ON_CUSTOMER → RESOLVED → CLOSED`.
11. **الأخطاء.** حالة عميل تاني → 404؛ المرفقات مش مقبولة (`attachmentIds` أقصى حجم 0)؛ حالة أو Version غلط → تعارضات.
12. **الاختبارات.** `SupportIntegrationTest.java`، `App.test.tsx` بتاع ويب العميل (MBQA-004).
13. **اختبارات المالك E2E.** E2E-ORDER-118، E2E-OPS-057، E2E-OPS-105، E2E-ORDER-109.
14. **الحالة.** IMPLEMENTED. 15. **القيود.** مفيش مرفقات؛ ومفيش إشعارات SLA غير فلتر `overdue`.

## القيود المعروفة وفجوات التوثيق

* `docs/product/06-permissions.md` بيذكر `finance.settlement_verify`؛ والكود بيستخدم `finance.settlement`.
* الدفتر (Ledger) بيدعم الأنواع `COMMISSION` و`COMMISSION_REVERSAL` (بتتكتب لما استرداد يتحقق منه؛ `FinanceOpsService`، ومتأكّد منها في `FinanceOperationsIntegrationTest`). تحت صفر عمولة الاتنين مبالغهم صفر، فالمحاسبة موجودة لكنها مابتحركش فلوس. اتجاه التسوية هو `COOK_PAYS_PLATFORM` (المطبخ بيسدد العمولة للمنصة)، وعمره ما يبقى دفع للمطبخ. العربون عمره ما بيدخل الدفتر (E2E-FIN-045).
* واجهة التحقق من تقرير الكاش ناقصة (FS-1).
