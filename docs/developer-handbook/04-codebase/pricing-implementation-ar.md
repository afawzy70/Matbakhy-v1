# شرح تنفيذ التسعير

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — التسعير على السيرفر ونسخ الوجبات غير القابلة للتعديل والتحقق من الـ Quote ورفض الـ Quote القديم وإعادة تأكيد العميل موجودين؛ القيم التجارية (الضريبة، الحدود) اصطناعية (Synthetic) لحد قرار المالك O04
> **Language / اللغة:** [English](pricing-implementation.md) · العربية

مين اللي بيحدد السعر، وهو متخزّن فين، وبيتفحص إزاي، وإيه اللي بيحصل لما يتغيّر بين "العميل شاف السعر" و"العميل داس أوردر". الصورة البيزنس: [التسعير والدفع](../01-business/pricing-and-payments-ar.md). القرارات: D10 (مفيش عمولة ولا رسوم منصة على العميل)، D13 (التعامل مع الـ Quote القديم، `docs/product/rq-004-stale-quote.md`)، D14 (تاريخ أسعار المطبخ).

## المبادئ في الكود

- **السيرفر هو اللي بيملك كل مبلغ.** أجسام طلبات الـ Quote (`QuoteController.Body`، `backend/modules/orders/src/main/java/com/matbakhy/orders/web/QuoteController.java:36`) فيها أرقام الوجبات والكميات والـ Slot وطريقة الاستلام/التوصيل والعنوان وطريقة الدفع بس — مفيش أسعار ولا رسوم ولا إجماليات ولا أدوار؛ وخصائص الـ JSON غير المعروفة بتترفض (`StrictJsonConfiguration`). الاختبار: `QuoteIntegrationTest` (الحقول المزوّرة بتترفض، TC-019).
- **فلوس بأرقام صحيحة.** القروش في `long`؛ والحساب بيستخدم `Math.multiplyExact`/`addExact`. الضريبة بتتقرّب لأقرب قرش صحيح (half up) (`QuoteService.java:269`). في جهة العميل: `packages/api-client/src/money.ts` بيعمل Parse لـ `{amountMinor, currency}`.
- **الأسعار بتتنسخ في الأوردرات، مبتتشاورش عليها.** الـ Quotes بتخزّن `unit_price_minor` و`meal_revision`؛ والأوردرات بتخزّن السعر المتسعّر في الـ Quote مع Snapshot الوجبة (`OrderService.java:206-213`).

## السعر بييجي منين

| المكوّن | مصدر الحقيقة | الكود |
|---|---|---|
| سعر وحدة الوجبة | `meal.price_minor` (الحالي) و`meal_revision.price_minor` (التاريخ)؛ رقم صحيح من 1 لـ 1,000,000 قرش | بيتفحص في `MealService` (`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/meal/MealService.java:476`) |
| نسخة السعر | `meal.revision` — كل تغيير في المحتوى مختلف عن المحتوى الحالي بيدخّل صف `meal_revision` جديد غير قابل للتعديل (`MealService.update` `:163`، `writeContent` `:420`)؛ التعديلات اللي مفيهاش تغيير فعلي مبتضيفش حاجة (`:173`) | الجدول V7، وTrigger للإضافة بس اسمه `meal_revision_append_only` |
| رسوم التوصيل | `coalesce(kitchen_zone.fee_override_minor, service_zone.delivery_fee_minor)` لمنطقة مفعّلة ونشطة | `JdbcMealCatalog.deliveryFee` (`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/JdbcMealCatalog.java:44`) |
| الضريبة | `commerce_policy.tax_rate_bps` للسياسة الفعّالة (اصطناعية 0 لحد O04) | `CommercePolicies.active` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/CommercePolicies.java`) |
| العمولة | `commerce_policy.commission_rate_bps`؛ **0** عند الإطلاق؛ الـ CHECK في قاعدة البيانات `commerce_policy_launch_commission_ck` (V23) بيسمح بقيمة غير صفرية للسياسات الاصطناعية بس؛ بيتعملها Snapshot على الأوردر بس مبتتعرضش للعملاء أبدًا | `OrderService.java:194` |
| الحدود | `max_item_quantity` (لكل سطر)، 20 سطر (`CommercePolicies.MAX_LINES_PER_ORDER`)، `quote_ttl_seconds` (سياسة التطوير 300 = 5 دقايق، `backend/app/src/main/resources/db/devdata/R__dev_commerce_policy.sql`) | `QuoteService.create` `:92` |
| العربون | لكل مطبخ، `kitchen_payment_profile.deposit_bps` | [تنفيذ الدفع](payment-implementation-ar.md) |

السياسة الفعّالة هي الصف الوحيد اللي `status = 'ACTIVE'` (فهرس جزئي فريد `commerce_policy_one_active_uq`، V10). النسخ الجديدة بتتعمل من خلال `ConfigurationService.newVersion` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ConfigurationService.java:85`، `POST /api/v1/ops/configuration/{key}/versions`، السبب + مرجع الموافقة + MFA حديث)؛ بيقفل الصف الفعّال `FOR UPDATE` (`:139`) وبيوقّفه. الأوردرات اللي اتسجّلت بتحتفظ بالـ Snapshot بتاعها. التسجيل ممكن يتوقّف بالـ Feature Flag `ordering-enabled`؛ والسياسة الناقصة أو الاصطناعية برّه LOCAL/CI/PREVIEW (`DeploymentEnvironment.synthetic()`) بتمنع التسجيل (`NO_APPROVED_POLICY`).

## إنشاء الـ Quote — `QuoteService.create` (`QuoteService.java:92`)

1. `contact.requireVerifiedContact` — إيميل وتليفون متحقَّق منهم (`CONTACT_VERIFICATION_REQUIRED`).
2. `policies.active()` — السياسة ومفتاح التسجيل.
3. فحوصات المدخلات: من 1 لـ 20 سطر مختلف، والكمية من 1 لـ `maxItemQuantity` (`invalid("items", ...)` -> `VALIDATION_FAILED`).
4. `terms(...)` (`:206`) بيعيد حساب كل حاجة من قاعدة البيانات:
   - الـ Slot لازم يكون موجود وقابل للطلب بوقت السيرفر (`SLOT_CLOSED`)؛ والمطبخ لازم يكون قادر يبيع (`KITCHEN_UNAVAILABLE`)؛
   - `acceptBy = min(now + acceptanceWindowMinutes, slot cutoff)` ولازم يفضل على الأقل `minAcceptanceMinutes` (`ACCEPTANCE_WINDOW_TOO_SHORT`)؛
   - كل وجبة لازم تكون منشورة وظاهرة، وتابعة لمطبخ الـ Slot (طبّاخ واحد وSlot واحد لكل سلة، `MIXED_KITCHENS_OR_SLOT`)، ومعروضة في الـ Slot ده، وفيها حصص متبقية كفاية (`STOCK_CHANGED`) — الـ Quote مبيحجزش مخزون؛
   - `line = unitPrice x quantity`، `subtotal = sum`؛ الاستلام لازم يكون مفعّل ومابياخدش عنوان؛ والتوصيل محتاج عنوان مملوك وقابل للخدمة وبياخد رسوم المنطقة؛
   - `tax = round_half_up((subtotal + fee) x taxRateBps / 10000)`، `total = subtotal + fee + tax`.
5. `payments.terms(...)` بيستنتج شروط deposit/COD للطريقة المختارة (`COD_NOT_ELIGIBLE`، `DEPOSIT_NOT_OFFERED`).
6. بيدخّل صفوف `quote` و`quote_item` (غير قابلة للتعديل بـ Trigger، V10) وبيرجّع `QuoteResponse` فيه `expiresAt` ونسخ السياسات والتحذيرات (`SYNTHETIC_POLICY`).

## التحقق من الـ Quote عند التسجيل — `QuoteService.requireValid` (`:146`)

بينادي عليها `OrderService.placeOnce` جوه Transaction التسجيل ([تنفيذ الأوردر](order-implementation-ar.md)).

1. تحميل الـ Quote بتاع المستدعي (`NOT_FOUND` لو بتاع حد تاني).
2. **التسلسل ضد التعديلات المتزامنة (D13).** `SELECT version FROM commerce_policy WHERE status='ACTIVE' FOR SHARE` (`:152`) و`MealCatalog.lockPricing` (`JdbcMealCatalog.java:35`: `FOR SHARE` على صفوف الوجبات وصف `kitchen_zone` وصف `service_zone`). تعديل سعر من الطبّاخ بياخد صف الوجبة `FOR UPDATE` (`MealService.lockOwn`، `:407`) وتغيير السياسة بياخد السياسة الفعّالة `FOR UPDATE`، يعني: التعديل اللي يتأكّد (Commit) الأول بيتشاف والـ Quote القديم بيترفض؛ والتعديل اللي يوصل بعد كده بيستنى لحد ما التسجيل يتأكّد والأوردر بيحتفظ بسعره المتحقَّق منه. الاختبارات: `PriceChangeIntegrationTest.when_the_price_update_takes_effect_first_the_waiting_placement_is_refused` و`..._when_the_placement_commits_first_the_order_keeps_its_validated_price_...`.
3. **الانتهاء.** `!now.isBefore(expires_at)` -> `QUOTE_EXPIRED` (`:157`).
4. التحقق من جهة الاتصال تاني؛ ونسخة السياسة لازم تكون ما اتغيّرتش (`PRICE_CHANGED` والسبب `POLICY_CHANGED`).
5. إعادة حساب `terms(...)` من البيانات الحالية والمقارنة: نسخة الوجبة أو سعر الوحدة مختلف -> `PRICE_CHANGED` / `MEAL_CHANGED` (`:174`)؛ نسخة العنوان أو المنطقة مختلفة -> `STATE_CONFLICT` / `ADDRESS_CHANGED` (`:178`)؛ رسوم التوصيل أو الإجمالي مختلف -> `PRICE_CHANGED` / `TOTAL_CHANGED` (`:181`)؛ شروط العربون أو المستلم مختلفة -> `PRICE_CHANGED` / `PAYMENT_CHANGED` (`:191`).
6. بيرجّع `ValidQuote` (`backend/modules/orders/src/main/java/com/matbakhy/orders/api/QuoteDirectory.java`) اللي شايل المبالغ **المتسعّرة في الـ Quote** للأوردر.

ترتيب الفشلين الممكنين مهم للعميل: `QUOTE_EXPIRED` و`PRICE_CHANGED` الاتنين HTTP 409 بـ `code` ثابت؛ ومفيش حاجة بتتكتب لما أي منهم يحصل.

## إعادة تأكيد الـ Quote القديم في جهة العميل (D13)

القاعدة: السعر المعروض أو الـ Quote اللي لسه ما انتهاش **مش** ضمان للسعر؛ العميل لازم يشوف إيه اللي اتغيّر ويوافق عليه قبل ما Quote جديد يتسجّل.

| الخطوة | الويب | الموبايل |
|---|---|---|
| التسجيل بيرد `PRICE_CHANGED` | `Checkout.place` بيمسح المفاتيح المعلّقة وبينادي `requote` (`apps/customer-web/src/Checkout.tsx:286-289`، `:245`) | `Cart.submit` -> `requote` (`apps/customer-mobile/src/screens/Cart.tsx:341-343`) |
| حساب الفرق | `priceChanges(previous, next)` في `packages/api-client/src/priceChanges.ts:31` | نفس الدالة |
| النسب | تغييرات سعر الوجبة بتتنسب للمطبخ؛ ورسوم التوصيل والضريبة بتتبلّغ منفصلين؛ و`unexplained` بيطلّع إشعار عام | نفس الحاجة |
| القبول | `PriceChangeNotice` (`Checkout.tsx:444`) بيطلب قبول صريح قبل ما يفعّل التسجيل | `apps/customer-mobile/src/screens/Cart.tsx:460-463` |
| إعادة الإرسال | الـ Quote الجديد بياخد `clientRequestId` و`Idempotency-Key` جداد؛ ومفيش إعادة إرسال تلقائية أبدًا | نفس الحاجة |

اختبارات الوحدة: `packages/api-client/test/priceChanges.test.ts`؛ واختبارات الواجهة في `apps/customer-web/src/App.test.tsx` و`apps/customer-mobile/src/App.test.tsx` (TC-154).

## تاريخ الأسعار للمطابخ (D14)

الـ `MealService.priceHistory` (`MealService.java:186`، المسار `GET /api/v1/cook/meals/{mealId}/price-history`) بيستنتج التاريخ من `meal_revision.price_minor`: عنصر للنسخة الأولى المتسعّرة وعنصر لكل نسخة بعد كده سعرها مختلف؛ التعديلات اللي في الوصف بس مبتضيفش حاجة؛ ومحدود بالمالك (وجبة مطبخ تاني 404). الاختبار: `PriceHistoryIntegrationTest` (TC-153)؛ حالة المالك E2E-COOK-103.

## المناطق المحتاجة مراجعة

قواعد التقريب، وزوج الأقفال `FOR SHARE`/`FOR UPDATE` بين `requireValid` و`MealService.lockOwn`/`ConfigurationService.newVersion`، وقيد CHECK بتاع العمولة. تغيير أي منهم من غير تحديث `PriceChangeIntegrationTest` و`ZeroCommissionIntegrationTest` بيرجّع انحراف السعر الصامت أو عمولة غير صفرية فعلية (D10).

## القيود المعروفة وفجوات التوثيق

- معاملة الضريبة لسه متقرّرتش (O04، `planning/decisions.json`)؛ الكود بيدعم نسبة ضريبة بس السياسة الاصطناعية بتستخدم 0 ومفيش منطق فواتير. PLANNED.
- مدة الـ Quote قيمة سياسة؛ والقاعدة `BR-020` بتقول 5 دقايق، وده بيساوي سياسة التطوير الاصطناعية (300 ثانية) بس القيمة الفعلية NOT VERIFIED لأن مفيش سياسة فعلية موافَق عليها في الـ Repository.

## تغيير مدة الـ Quote

المدة بيانات مش كود: `commerce_policy.quote_ttl_seconds` (فحص في قاعدة البيانات من 60 لـ 3600 في الـ Migration `V10__quotes.sql`). التسجيل بيقارن `now` بـ `expires_at` بتاع الـ Quote (`QuoteService.requireValid`)، ومبيحسبش عمر. عشان تغيّر "الـ 5 دقايق" الموثّقة: حدّث الـ Seed بتاع السياسة `backend/app/src/main/resources/db/devdata/R__dev_commerce_policy.sql` وسياسة الاختبار، والـ Javadoc في `QuoteService`، والاختبارات اللي بتأكّد الانتهاء، وكل صفحة بتذكر القيمة (دوّر في الدليل على "5 minutes"، بالإضافة لـ `docs/product/03-business-rules.md` BR-020، و`docs/product/04-state-machines.md`، و`docs/product/rq-004-stale-quote.md`). القيمة الفعلية محتاجة قرار مالك موافَق عليه (O04).
