# Executed web case record

Application SHA: `4eaee3a1f53b929b9e7685fd30da689d470d095c`. Units:34 reviewed case groups (30PASS,3FAIL,1BLOCKED oracle). Raw retries are evidence, not additional coverage.

Every case uses real local sessions/services. Synthetic actor/kitchen/meal/slot IDs are in `evidence/fixtures.json`; created connected slot/order in `created-fixtures.json`, `cross-role-order.json`, and `delivery-order.json`. Staff roles/grants are separate. No test uses production data.

Each subsection supplies requirement/preconditions/steps; exact per-subcheck expected/actual results, execution timestamps and screenshot references are in `evidence/reviewed-results.json` and the immutable raw-attempt history `evidence/results.json`. API observations are in `network.json`, JavaScript observations in `console.json`.

## C-AUTH — FAIL

**Actor/surface:** Customer web. **Requirement:** MB-009/027; BR-006/007.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Customer-a real OIDC login → reload/me → Account inspect logout controls → authorized API logout →401 → customer-b distinct login; customer credentials invoke protected staff APIs.

**Expected and actual:**

- **PASS** Real login maps correct synthetic identity. Expected: `"00000000-0000-0000-0000-0000000002bd"`. Actual: `"00000000-0000-0000-0000-0000000002bd"`.

- **PASS** Session restored after reload. Expected: `200`. Actual: `200`.

- **FAIL** Delivered customer account exposes sign-out action. Expected: `"reachable Sign out control (CW11)"`. Actual: `["العربية","Save","Get help with an order","Request account deletion"]`.

- **PASS** Logout revokes application session. Expected: `401`. Actual: `401`.

- **PASS** Distinct real customer identities. Expected: `"00000000-0000-0000-0000-0000000002be"`. Actual: `"00000000-0000-0000-0000-0000000002be"`.

- **PASS** Customer denied /api/v1/cook/dashboard. Expected: `"403/404"`. Actual: `403`.

- **PASS** Customer denied /api/v1/ops/orders. Expected: `"403/404"`. Actual: `403`.

- **PASS** Customer denied /api/v1/ops/cases. Expected: `"403/404"`. Actual: `403`.

- **PASS** Customer denied /api/v1/ops/configuration. Expected: `"403/404"`. Actual: `403`.

**Evidence:** [C-AUTH-account.png](evidence/C-AUTH-account.png), [C-AUTH-account.json](evidence/C-AUTH-account.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-BROWSE — PASS

**Actor/surface:** Customer web. **Requirement:** MB-016/027; BR-011/016/017/046; D11/D12; MBQA-001.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Load bilingual fixtures; navigate home/meal/kitchen and switch AR↔EN. Inspect request Accept-Language and exact returned content; apply supported zone/query navigation and unavailable/paused exclusions.

**Expected and actual:**

- **PASS** Current request language //ar. Expected: `"ar"`. Actual: `"ar"`.

- **PASS** Document direction //ar. Expected: `"rtl"`. Actual: `"rtl"`.

- **PASS** Translated meal content //ar. Expected: `"وجبة تجريبية A1"`. Actual: `"مطبخي\nأكل بيتي من مطابخ قريبة منك\nحجز مسبق\nمحتاج إيه لسفرتك؟\nاختر حسب حاجتك\nوجبة ليا أو لينا\nلشخص أو فردين\nأكمل سفرتي\nصنف يكمل أكلك\nأجهز عزومة\nلجمعة ولمة العيلة\nالأكلات\nالمطابخ\nابحث عن طبق أو مطبخ\nابحث عن طبق أو مطبخ\nتصفية النتائج\nالكل\nأطباق رئيسية\nأطباق جانبية وسلطات\nشوربة\nمخبوزات\nحلويات\nفطار\nجاهز للطبخ\nأكلات بيتية متاحة للحجز\n١٥‏/١٠‏/٢٠٢٦، ٦:٥٩ م\nوجبة تجريبية B1\n‏٨٠٫٢٥ ج.م.‏\nمطبخ تجريبي B\n\nوصف مستقل للوجبة B1 بمكونات تجريبية فقط.\n\nحصة تجريبية\nعرض التفاصيل\nوجب… [complete value in reviewed-results.json]`.

- **PASS** Current request language //en. Expected: `"en"`. Actual: `"en"`.

- **PASS** Document direction //en. Expected: `"ltr"`. Actual: `"ltr"`.

- **PASS** Translated meal content //en. Expected: `"QA Meal A1"`. Actual: `"Matbakhy\nHome-cooked meals from kitchens near you\nPreorder\nWhat does your table need?\nChoose by need\nA meal for me or us\nFor one or two people\nComplete my table\nA dish to go with your meal\nPrepare a gathering\nFor family get-togethers\nDishes\nKitchens\nSearch for a meal or kitchen\nSearch for a meal or kitchen\nFilter results\nAll\nMain dishes\nSides and salads\nSoups\nBaked goods\nDesserts\nBreakfast\nReady to cook\nHome-cooked meals to preorder\n15 Oct 2026, 18:59\nQA Meal B1\nEGP 8… [complete value in reviewed-results.json]`.

- **PASS** Current request language /meal/00000000-0000-0000-0000-000000000263/ar. Expected: `"ar"`. Actual: `"ar"`.

- **PASS** Document direction /meal/00000000-0000-0000-0000-000000000263/ar. Expected: `"rtl"`. Actual: `"rtl"`.

- **PASS** Translated meal content /meal/00000000-0000-0000-0000-000000000263/ar. Expected: `"وجبة تجريبية A1"`. Actual: `"رجوع\nوجبة تجريبية A1\n‏١٢٠٫٢٥ ج.م.‏\nمطبخ تجريبي A\nحصة تجريبية\nعن الطبق\n\nوصف مستقل للوجبة A1 بمكونات تجريبية فقط.\n\nالمكونات ومعلومات الحساسية\n\nأرز تجريبي\n\nمعلومات الحساسية التفصيلية مش متوفرة للطبق ده. لو عندك حساسية، متطلبوش من غير ما تتأكد.\n\nمواعيد التحضير\n١٥‏/١٠‏/٢٠٢٦، ٦:٥٩ م · متبقٍ ٣ · استلام من المطبخ / توصيل\n١٦‏/١٠‏/٢٠٢٦، ٦:٥٩ م · متبقٍ ٣ · استلام من المطبخ / توصيل\n١٨‏/١٠‏/٢٠٢٦، ٦:٥٩ م · متبقٍ ٢٥ · استلام من المطبخ / توصيل\n−\n+\nأضف إلى السلة ·\n‏١٢٠٫٢٥ ج.م.‏\n\nالإجمالي… [complete value in reviewed-results.json]`.

- **PASS** Current request language /meal/00000000-0000-0000-0000-000000000263/en. Expected: `"en"`. Actual: `"en"`.

- **PASS** Document direction /meal/00000000-0000-0000-0000-000000000263/en. Expected: `"ltr"`. Actual: `"ltr"`.

- **PASS** Translated meal content /meal/00000000-0000-0000-0000-000000000263/en. Expected: `"QA Meal A1"`. Actual: `"Back\nQA Meal A1\nEGP 120.25\nمطبخ تجريبي A\nSynthetic portion\nAbout this dish\n\nIndependent synthetic description for meal A1.\n\nIngredients and allergy information\n\nSynthetic rice\n\nDetailed allergy information is not available for this dish. If you have an allergy, do not order without checking.\n\nCooking slots\n15 Oct 2026, 18:59 · 3 left · Pickup from the kitchen / Delivery\n16 Oct 2026, 18:59 · 3 left · Pickup from the kitchen / Delivery\n18 Oct 2026, 18:59 · 25 left · Pickup from … [complete value in reviewed-results.json]`.

- **PASS** Current request language /kitchen/qa-kitchen-a/ar. Expected: `"ar"`. Actual: `"ar"`.

- **PASS** Document direction /kitchen/qa-kitchen-a/ar. Expected: `"rtl"`. Actual: `"rtl"`.

- **PASS** Current request language /kitchen/qa-kitchen-a/en. Expected: `"en"`. Actual: `"en"`.

- **PASS** Document direction /kitchen/qa-kitchen-a/en. Expected: `"ltr"`. Actual: `"ltr"`.

- **PASS** Server unique search. Expected: `"B1 only"`. Actual: `"15 Oct 2026, 18:59\nQA Meal B1\nEGP 80.25\nمطبخ تجريبي B\n\nIndependent synthetic description for meal B1.\n\nSynthetic portion\nView details"`.

**Evidence:** [C-BROWSE-home-ar.png](evidence/C-BROWSE-home-ar.png), [C-BROWSE-home-ar.json](evidence/C-BROWSE-home-ar.json), [C-BROWSE-home-en.png](evidence/C-BROWSE-home-en.png), [C-BROWSE-home-en.json](evidence/C-BROWSE-home-en.json), [C-BROWSE-meal-ar.png](evidence/C-BROWSE-meal-ar.png), [C-BROWSE-meal-ar.json](evidence/C-BROWSE-meal-ar.json), [C-BROWSE-meal-en.png](evidence/C-BROWSE-meal-en.png), [C-BROWSE-meal-en.json](evidence/C-BROWSE-meal-en.json), [C-BROWSE-kitchen-ar.png](evidence/C-BROWSE-kitchen-ar.png), [C-BROWSE-kitchen-ar.json](evidence/C-BROWSE-kitchen-ar.json), [C-BROWSE-kitchen-en.png](evidence/C-BROWSE-kitchen-en.png), [C-BROWSE-kitchen-en.json](evidence/C-BROWSE-kitchen-en.json), [C-BROWSE-search.png](evidence/C-BROWSE-search.png), [C-BROWSE-search.json](evidence/C-BROWSE-search.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-BASKET — PASS

**Actor/surface:** Customer web. **Requirement:** CW05/BR-018/019; FG-001.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Start empty; add A1×2+A2×1 in A1slot; edit A2 up/down; remove A1/A2; verify33600→43150→31125→19100→empty; reload/language switch preserve draft.

**Expected and actual:**

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":33600,"currency":"EGP"}`. Actual: `"EGP 336.00"`.

- **PASS** Kitchen identity visible. Expected: `"مطبخ تجريبي A"`. Actual: `"Cart\n\nKitchen: مطبخ تجريبي A\n\n15 Oct 2026, 18:59\n\nQA Meal A1\n−\n2\n+\nEGP 240.50\nRemove\nQA Meal A2\n−\n1\n+\nEGP 95.50\nRemove\nEstimated subtotal\nEGP 336.00\n\nThis is an estimate from the prices shown when you added each meal. The final total at the current price appears when you choose \"Calculate total\".\n\nEmpty cart\nCheckout\nDelivery\nPickup from the kitchen\n\nLoading…\n\nCalculate total"`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":43150,"currency":"EGP"}`. Actual: `"EGP 431.50"`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":31125,"currency":"EGP"}`. Actual: `"EGP 311.25"`.

- **PASS** Basket persists exact lines across reload. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":1,"unitPriceMinor":12025,"maxQuantity":3},{"mealId":"00000000-0000-0000-0000-000000000264","title":"QA Meal A2","quantity":2,"unitPriceMinor":9550,"maxQuantity":3}]}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":1,"unitPriceMinor":12025,"maxQuantity":3},{"mealId":"00000000-0000-0000-0000-000000000264","title":"QA Meal A2","quantity":2,"unitPriceMinor":9550,"maxQuantity":3}]}`.

- **PASS** Language switch preserves basket identity/state. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":1,"unitPriceMinor":12025,"maxQuantity":3},{"mealId":"00000000-0000-0000-0000-000000000264","title":"QA Meal A2","quantity":2,"unitPriceMinor":9550,"maxQuantity":3}]}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":1,"unitPriceMinor":12025,"maxQuantity":3},{"mealId":"00000000-0000-0000-0000-000000000264","title":"QA Meal A2","quantity":2,"unitPriceMinor":9550,"maxQuantity":3}]}`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":19100,"currency":"EGP"}`. Actual: `"EGP 191.00"`.

- **PASS** Last-line removal clears storage. Expected: `null`. Actual: `null`.

**Evidence:** [C-BASKET-edited.png](evidence/C-BASKET-edited.png), [C-BASKET-edited.json](evidence/C-BASKET-edited.json), [C-BASKET-empty.png](evidence/C-BASKET-empty.png), [C-BASKET-empty.json](evidence/C-BASKET-empty.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-REPLACE-K-CANCEL — PASS

**Actor/surface:** Customer web. **Requirement:** BR-018/TC-147; FG-001.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** A1×2/A1slot; attempt B1/B1slot; cancel prompt; explicitly read unchanged kitchen A/mealA1/slotA1 and total24050.

**Expected and actual:**

- **PASS** No mutation before consent. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`.

- **PASS** Exact resulting kitchen/meal/slot/quantity. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","mealId":"00000000-0000-0000-0000-000000000263","slotId":"00000000-0000-0000-0000-00000000019b","quantity":2}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":24050,"currency":"EGP"}`. Actual: `"EGP 240.50"`.

**Evidence:** [C-REPLACE-K-CANCEL-prompt.png](evidence/C-REPLACE-K-CANCEL-prompt.png), [C-REPLACE-K-CANCEL-prompt.json](evidence/C-REPLACE-K-CANCEL-prompt.json), [C-REPLACE-K-CANCEL-result.png](evidence/C-REPLACE-K-CANCEL-result.png), [C-REPLACE-K-CANCEL-result.json](evidence/C-REPLACE-K-CANCEL-result.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-REPLACE-K-CONFIRM — PASS

**Actor/surface:** Customer web. **Requirement:** BR-018/TC-147; FG-001.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** A1×2/A1slot; attempt B1/B1slot; confirm; explicitly read kitchen B/mealB1/slotB1/qty1, estimate8025; old A1 absent.

**Expected and actual:**

- **PASS** No mutation before consent. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`.

- **PASS** Exact resulting kitchen/meal/slot/quantity. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012e","mealId":"00000000-0000-0000-0000-00000000026d","slotId":"00000000-0000-0000-0000-0000000001a5","quantity":1}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012e","kitchenName":"مطبخ تجريبي B","slotId":"00000000-0000-0000-0000-0000000001a5","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-00000000026d","title":"QA Meal B1","quantity":1,"unitPriceMinor":8025,"maxQuantity":3}]}`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":8025,"currency":"EGP"}`. Actual: `"EGP 80.25"`.

**Evidence:** [C-REPLACE-K-CONFIRM-prompt.png](evidence/C-REPLACE-K-CONFIRM-prompt.png), [C-REPLACE-K-CONFIRM-prompt.json](evidence/C-REPLACE-K-CONFIRM-prompt.json), [C-REPLACE-K-CONFIRM-result.png](evidence/C-REPLACE-K-CONFIRM-result.png), [C-REPLACE-K-CONFIRM-result.json](evidence/C-REPLACE-K-CONFIRM-result.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-REPLACE-S-CANCEL — PASS

**Actor/surface:** Customer web. **Requirement:** BR-018/TC-147; FG-001.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** A1×2/A1slot; attempt A2/A2slot; cancel; same kitchen with exact original meal/slot and total24050.

**Expected and actual:**

- **PASS** No mutation before consent. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`.

- **PASS** Exact resulting kitchen/meal/slot/quantity. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","mealId":"00000000-0000-0000-0000-000000000263","slotId":"00000000-0000-0000-0000-00000000019b","quantity":2}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":24050,"currency":"EGP"}`. Actual: `"EGP 240.50"`.

**Evidence:** [C-REPLACE-S-CANCEL-prompt.png](evidence/C-REPLACE-S-CANCEL-prompt.png), [C-REPLACE-S-CANCEL-prompt.json](evidence/C-REPLACE-S-CANCEL-prompt.json), [C-REPLACE-S-CANCEL-result.png](evidence/C-REPLACE-S-CANCEL-result.png), [C-REPLACE-S-CANCEL-result.json](evidence/C-REPLACE-S-CANCEL-result.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-REPLACE-S-CONFIRM — PASS

**Actor/surface:** Customer web. **Requirement:** BR-018/TC-147; FG-001.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** A1×2/A1slot; attempt A2/A2slot; confirm; same kitchen A but exact replacement mealA2/slotA2/qty1, estimate9550; old line absent.

**Expected and actual:**

- **PASS** No mutation before consent. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019b","slotStartsAt":"2026-10-15T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","quantity":2,"unitPriceMinor":12025,"maxQuantity":3}]}`.

- **PASS** Exact resulting kitchen/meal/slot/quantity. Expected: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","mealId":"00000000-0000-0000-0000-000000000264","slotId":"00000000-0000-0000-0000-00000000019c","quantity":1}`. Actual: `{"kitchenId":"00000000-0000-0000-0000-00000000012d","kitchenName":"مطبخ تجريبي A","slotId":"00000000-0000-0000-0000-00000000019c","slotStartsAt":"2026-10-16T15:59:42.580575Z","modes":["PICKUP","DELIVERY"],"lines":[{"mealId":"00000000-0000-0000-0000-000000000264","title":"QA Meal A2","quantity":1,"unitPriceMinor":9550,"maxQuantity":3}]}`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":9550,"currency":"EGP"}`. Actual: `"EGP 95.50"`.

**Evidence:** [C-REPLACE-S-CONFIRM-prompt.png](evidence/C-REPLACE-S-CONFIRM-prompt.png), [C-REPLACE-S-CONFIRM-prompt.json](evidence/C-REPLACE-S-CONFIRM-prompt.json), [C-REPLACE-S-CONFIRM-result.png](evidence/C-REPLACE-S-CONFIRM-result.png), [C-REPLACE-S-CONFIRM-result.json](evidence/C-REPLACE-S-CONFIRM-result.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-STOCK — PASS

**Actor/surface:** Customer web + cook API. **Requirement:** CW03; BR-013/015/017; MBQA-002.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Customer-b consumes A1slot stock through real qty3 order. Customer-a selected exhausted slot while another slot remains available; Adddisabled/no draft; test cap99→3, then real cancel releases reservation.

**Expected and actual:**

- **PASS** Mixed-stock fixture created through real reservation. Expected: `"S1 zero while S2 has 3"`. Actual: `{"id":"00000000-0000-0000-0000-000000000263","kitchen":{"id":"00000000-0000-0000-0000-00000000012d","slug":"qa-kitchen-a","name":"مطبخ تجريبي A","ratingSummary":{"count":0,"averageTenths":null}},"title":"QA Meal A1","photo":{"url":"http://localhost:39090/matbakhy-public/iqa.pngA","alt":"Synthetic solid colour"},"portionText":"Synthetic portion","price":{"amountMinor":12025,"currency":"EGP"},"nextSlots":[{"id":"00000000-0000-0000-0000-00000000019b","startsAt":"2026-10-15T15:59:42.580575Z","endsAt… [complete value in reviewed-results.json]`.

- **PASS** Exhausted selected slot Add disabled. Expected: `true`. Actual: `true`.

- **PASS** No basket written for exhausted selection. Expected: `null`. Actual: `null`.

- **PASS** Synthetic reservation released through legal cancellation. Expected: `200`. Actual: `200`.

- **PASS** Positive quantity cap obeys slot stock. Expected: `"3"`. Actual: `"3"`.

**Evidence:** [C-STOCK-exhausted.png](evidence/C-STOCK-exhausted.png), [C-STOCK-exhausted.json](evidence/C-STOCK-exhausted.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-Q1 — PASS

**Actor/surface:** Customer web + API. **Requirement:** BR-019; CW03; configured policy/Q1.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Read active public config (10). Select high-capacity A5slot; observe frontend11; serverquote rejects RANGE_1_TO_10; quantity10 quote equals120250. Observation does not validate frontend enforcement.

**Expected and actual:**

- **PASS** Public API publishes actual configured max. Expected: `{"maxQuantityPerLine":10}`. Actual: `{"orderingAvailable":true,"orderLimits":{"maxQuantityPerLine":10,"maxLinesPerOrder":20,"policyVersion":1}}`.

- **PASS** Server rejects above active policy maximum. Expected: `{"status":422,"code":"VALIDATION_FAILED","max":10}`. Actual: `{"status":422,"data":{"code":"VALIDATION_FAILED","messageKey":"error.validation_failed","fieldErrors":[{"field":"items.quantity","code":"RANGE_1_TO_10"}],"requestId":"7ded8651-70a1-4a6c-aaf0-6ed1f187e7a7","retryable":false}}`.

- **PASS** Actual policy maximum can be quoted. Expected: `{"status":201,"totalMinor":120250}`. Actual: `{"status":201,"data":{"quoteId":"bab75235-7bb5-4bbb-8227-7399dcc42d36","expiresAt":"2026-10-12T16:32:42.079642876Z","kitchenId":"00000000-0000-0000-0000-00000000012d","items":[{"mealId":"00000000-0000-0000-0000-000000000263","mealRevision":1,"title":"QA Meal A1","quantity":10,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":120250,"currency":"EGP"}}],"subtotal":{"amountMinor":120250,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMino… [complete value in reviewed-results.json]`.

**Evidence:** [C-Q1-policy-boundary.png](evidence/C-Q1-policy-boundary.png), [C-Q1-policy-boundary.json](evidence/C-Q1-policy-boundary.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-QUOTE — PASS

**Actor/surface:** Customer web. **Requirement:** MB-017/027; BR-019/020/021.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Select pickup, calculate exact total24050/zero fees/tax; terms required; edit line invalidates quote; forged client amount refused by contract.

**Expected and actual:**

- **PASS** Pickup total/subtotal/fee/tax match active policy. Expected: `{"status":201,"subtotalMinor":24050,"deliveryFeeMinor":0,"taxMinor":0,"totalMinor":24050}`. Actual: `{"status":201,"data":{"quoteId":"faf31027-32bd-4733-b673-b7aa626aedf7","expiresAt":"2026-10-12T16:32:42.784790401Z","kitchenId":"00000000-0000-0000-0000-00000000012d","items":[{"mealId":"00000000-0000-0000-0000-000000000263","mealRevision":1,"title":"QA Meal A1","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":… [complete value in reviewed-results.json]`.

- **PASS** Placement unavailable before terms accepted. Expected: `true`. Actual: `true`.

- **PASS** Visible estimated subtotal matches integer arithmetic. Expected: `{"amountMinor":36075,"currency":"EGP"}`. Actual: `"EGP 360.75"`.

- **PASS** Basket edit discards previous quote/placement control. Expected: `"no Place order until fresh quote"`. Actual: `0`.

- **PASS** Client forged total rejected. Expected: `{"status":400}`. Actual: `{"status":400,"data":{"code":"MALFORMED_REQUEST","messageKey":"error.malformed_request","fieldErrors":[],"requestId":"a132d5b7-31a2-4496-b887-ee70729df10a","retryable":false}}`.

**Evidence:** [C-QUOTE-edit-invalidates.png](evidence/C-QUOTE-edit-invalidates.png), [C-QUOTE-edit-invalidates.json](evidence/C-QUOTE-edit-invalidates.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-AUTH — PASS

**Actor/surface:** Cook/ops/support/finance/admin web. **Requirement:** MB-011/022/026; BR-006/007.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Distinct cook A/B, support, finance, reviewer and admin login; verify exact nav; wrong-kitchen private read/mutation denied; admin has no implicit cook.review; cook reload/logout401.

**Expected and actual:**

- **PASS** Exact role-specific navigation cook-a. Expected: `["Home","Orders","Meals","Slots","Statement","Settings"]`. Actual: `["Home","Orders","Meals","Slots","Statement","Settings"]`.

- **PASS** Exact role-specific navigation cook-b. Expected: `["Home","Orders","Meals","Slots","Statement","Settings"]`. Actual: `["Home","Orders","Meals","Slots","Statement","Settings"]`.

- **PASS** Exact role-specific navigation support-a. Expected: `["Support"]`. Actual: `["Support"]`.

- **PASS** Exact role-specific navigation finance-a. Expected: `["Finance"]`. Actual: `["Finance"]`.

- **PASS** Exact role-specific navigation ops-review. Expected: `["Cook applications","Catalogue moderation","Order monitoring"]`. Actual: `["Cook applications","Catalogue moderation","Order monitoring"]`.

- **PASS** Exact role-specific navigation ops-a. Expected: `["Operating configuration","Staff permissions"]`. Actual: `["Operating configuration","Staff permissions"]`.

- **PASS** Protected cook catalogue is scoped to own kitchen. Expected: `"only kitchen A private catalogue"`. Actual: `[{"id":"00000000-0000-0000-0000-000000000263","kitchenId":"00000000-0000-0000-0000-00000000012d"},{"id":"00000000-0000-0000-0000-000000000264","kitchenId":"00000000-0000-0000-0000-00000000012d"},{"id":"00000000-0000-0000-0000-000000000265","kitchenId":"00000000-0000-0000-0000-00000000012d"}]`.

- **PASS** Cook cannot mutate another kitchen meal. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"21330950-ba3a-4df0-a214-7082e2c2eaf0","retryable":false}}`.

- **PASS** Admin is not an implicit domain superuser. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"3f482147-2460-4e6c-909f-4a6d105a1f32","retryable":false}}`.

- **PASS** Cook session restores. Expected: `200`. Actual: `200`.

- **PASS** Staff web logout revokes session. Expected: `401`. Actual: `401`.

**Evidence:** [S-AUTH-cook-a.png](evidence/S-AUTH-cook-a.png), [S-AUTH-cook-a.json](evidence/S-AUTH-cook-a.json), [S-AUTH-cook-b.png](evidence/S-AUTH-cook-b.png), [S-AUTH-cook-b.json](evidence/S-AUTH-cook-b.json), [S-AUTH-support-a.png](evidence/S-AUTH-support-a.png), [S-AUTH-support-a.json](evidence/S-AUTH-support-a.json), [S-AUTH-finance-a.png](evidence/S-AUTH-finance-a.png), [S-AUTH-finance-a.json](evidence/S-AUTH-finance-a.json), [S-AUTH-ops-review.png](evidence/S-AUTH-ops-review.png), [S-AUTH-ops-review.json](evidence/S-AUTH-ops-review.json), [S-AUTH-ops-a.png](evidence/S-AUTH-ops-a.png), [S-AUTH-ops-a.json](evidence/S-AUTH-ops-a.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-CATALOG — PASS

**Actor/surface:** Cook web → customer web. **Requirement:** MB-014/022; BR-011/012/017.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Cook-a pauses A1 and public lookup404; republishes200; real solid PNG signed upload + completed scan; creates new10125 minor draft; customer sees original republished offering.

**Expected and actual:**

- **PASS** Authenticated cook pauses own catalogue meal. Expected: `{"status":200,"state":"PAUSED"}`. Actual: `{"status":200,"data":{"id":"00000000-0000-0000-0000-000000000263","kitchenId":"00000000-0000-0000-0000-00000000012d","status":"PAUSED","content":{"titleAr":"وجبة تجريبية A1","titleEn":"QA Meal A1","descriptionAr":"وصف مستقل للوجبة A1 بمكونات تجريبية فقط. ","descriptionEn":"Independent synthetic description for meal A1. ","portionAr":"حصة تجريبية","portionEn":"Synthetic portion","ingredientsAr":"أرز تجريبي","ingredientsEn":"Synthetic rice","allergenDeclaration":"UNKNOWN","allergenCodes":[],"aller… [complete value in reviewed-results.json]`.

- **PASS** Paused meal cannot be publicly purchased. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"3816c8ea-4607-44ab-bf4c-e1dc8a6f87ff","retryable":false}}`.

- **PASS** Authenticated cook publishes/enables own meal. Expected: `{"status":200,"state":"PUBLISHED"}`. Actual: `{"status":200,"data":{"id":"00000000-0000-0000-0000-000000000263","kitchenId":"00000000-0000-0000-0000-00000000012d","status":"PUBLISHED","content":{"titleAr":"وجبة تجريبية A1","titleEn":"QA Meal A1","descriptionAr":"وصف مستقل للوجبة A1 بمكونات تجريبية فقط. ","descriptionEn":"Independent synthetic description for meal A1. ","portionAr":"حصة تجريبية","portionEn":"Synthetic portion","ingredientsAr":"أرز تجريبي","ingredientsEn":"Synthetic rice","allergenDeclaration":"UNKNOWN","allergenCodes":[],"al… [complete value in reviewed-results.json]`.

- **PASS** Real signed-upload/catalogue creation succeeds. Expected: `{"status":201,"priceMinor":10125}`. Actual: `{"status":201,"data":{"id":"3c4fa46e-2db4-4cd8-9e62-b76f2a6730c6","kitchenId":"00000000-0000-0000-0000-00000000012d","status":"DRAFT","content":{"titleAr":"وجبة QA منشأة","titleEn":"QA Created Dish","descriptionAr":"وصف وجبة اختبار مستقلة بمكونات تجريبية فقط","descriptionEn":null,"portionAr":"حصة اختبار","portionEn":null,"ingredientsAr":"أرز ومكونات اختبار","ingredientsEn":null,"allergenDeclaration":"NONE_DECLARED","allergenCodes":[],"allergenNote":null,"priceMinor":10125,"currency":"EGP","photo… [complete value in reviewed-results.json]`.

**Evidence:** [S-CATALOG-published.png](evidence/S-CATALOG-published.png), [S-CATALOG-published.json](evidence/S-CATALOG-published.json), [S-CATALOG-customer-visible.png](evidence/S-CATALOG-customer-visible.png), [S-CATALOG-customer-visible.json](evidence/S-CATALOG-customer-visible.json), [S-CATALOG-created-draft.png](evidence/S-CATALOG-created-draft.png), [S-CATALOG-created-draft.json](evidence/S-CATALOG-created-draft.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-SLOTS — PASS

**Actor/surface:** Cook web + API. **Requirement:** MB-015/022; BR-013/014/015.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Cook-a creates day8 Cairo13–15 slot max8 and A1/A2 capacity3; opens exact created slot; cook-b mutation404.

**Expected and actual:**

- **PASS** Cook creates dated availability with bounded capacities. Expected: `{"status":201,"date":"2026-10-20","capacity":3,"maxPortions":8}`. Actual: `{"status":201,"data":{"id":"d51ea2ec-9c6b-44da-b112-5f86fd3d5477","kitchenId":"00000000-0000-0000-0000-00000000012d","localDate":"2026-10-20","startsAt":"2026-10-20T10:00:00Z","endsAt":"2026-10-20T12:00:00Z","timezone":"Africa/Cairo","leadMinutes":120,"cutoffAt":"2026-10-20T08:00:00Z","maxPortions":8,"reserved":0,"allocated":0,"consumed":0,"availablePortions":8,"state":"DRAFT","orderable":false,"meals":[{"mealId":"00000000-0000-0000-0000-000000000263","capacity":3,"reserved":0,"allocated":0,"con… [complete value in reviewed-results.json]`.

- **PASS** Cook enables actual created slot. Expected: `{"id":"d51ea2ec-9c6b-44da-b112-5f86fd3d5477","state":"OPEN"}`. Actual: `{"status":200,"data":{"id":"d51ea2ec-9c6b-44da-b112-5f86fd3d5477","kitchenId":"00000000-0000-0000-0000-00000000012d","localDate":"2026-10-20","startsAt":"2026-10-20T10:00:00Z","endsAt":"2026-10-20T12:00:00Z","timezone":"Africa/Cairo","leadMinutes":120,"cutoffAt":"2026-10-20T08:00:00Z","maxPortions":8,"reserved":0,"allocated":0,"consumed":0,"availablePortions":8,"state":"OPEN","orderable":true,"meals":[{"mealId":"00000000-0000-0000-0000-000000000263","capacity":3,"reserved":0,"allocated":0,"consu… [complete value in reviewed-results.json]`.

- **PASS** Wrong kitchen cannot mutate slot. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"56f69668-dd8f-4f81-9911-c5207d5b6c73","retryable":false}}`.

**Evidence:** [S-SLOTS-opened.png](evidence/S-SLOTS-opened.png), [S-SLOTS-opened.json](evidence/S-SLOTS-opened.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-ORDER — PASS

**Actor/surface:** Customer web → cook web/database. **Requirement:** MB-017/018/027; BR-019/021/022/023/044.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Same actual connected order620ab03d-db91-443f-8e32-d36cbd939e36; staff-created slotd51ea2ec-9c6b-44da-b112-5f86fd3d5477; phase/state prerequisites are checked by the harness.

**Steps:** Customer-a adds A1×2 in staff-created slot; quote/place UI with terms; confirmation/history/detail; compare database id/kitchen/slot/totals; customer-b read/by-request/cancel404.

**Expected and actual:**

- **PASS** UI quote equals exact configured pickup arithmetic. Expected: `{"status":201,"totalMinor":24050}`. Actual: `{"status":201,"data":{"quoteId":"37ea8918-423b-4617-abdb-83b6a99ecc61","expiresAt":"2026-10-12T16:42:15.019812886Z","kitchenId":"00000000-0000-0000-0000-00000000012d","items":[{"mealId":"00000000-0000-0000-0000-000000000263","mealRevision":1,"title":"QA Meal A1","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":… [complete value in reviewed-results.json]`.

- **PASS** Actual UI placement returns pending real order. Expected: `{"status":201,"state":"PENDING_ACCEPTANCE","totalMinor":24050}`. Actual: `{"status":201,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":0,"state":"PENDING_ACCEPTANCE","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amount… [complete value in reviewed-results.json]`.

- **PASS** Same order ID/slot/totals stored in database. Expected: `{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","slotId":"d51ea2ec-9c6b-44da-b112-5f86fd3d5477","totalMinor":24050,"feeMinor":0,"taxMinor":0}`. Actual: `{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","public_number":"MB-100005","slot_id":"d51ea2ec-9c6b-44da-b112-5f86fd3d5477","kitchen_id":"00000000-0000-0000-0000-00000000012d","subtotal_minor":24050,"delivery_fee_minor":0,"tax_minor":0,"total_minor":24050,"state":"PENDING_ACCEPTANCE"}`.

- **PASS** History/detail preserve exact created order. Expected: `"620ab03d-db91-443f-8e32-d36cbd939e36"`. Actual: `"620ab03d-db91-443f-8e32-d36cbd939e36"`.

- **PASS** Other customer cannot read order/request. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"ca030a96-71a5-4c88-b12f-8d3b592eb394","retryable":false}}`.

- **PASS** Other customer cannot read order/request. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"740e0635-b312-4dfb-8755-ce85ab0ae1be","retryable":false}}`.

- **PASS** Other customer cannot cancel exact order. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"7c61221a-1903-47aa-9a1c-60fff5129067","retryable":false}}`.

**Evidence:** [C-ORDER-confirmation.png](evidence/C-ORDER-confirmation.png), [C-ORDER-confirmation.json](evidence/C-ORDER-confirmation.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-INVALID — PASS

**Actor/surface:** Cook web/API; customer API. **Requirement:** BR-013/024/026.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Same actual connected order620ab03d-db91-443f-8e32-d36cbd939e36; staff-created slotd51ea2ec-9c6b-44da-b112-5f86fd3d5477; phase/state prerequisites are checked by the harness.

**Steps:** For that pending order, call READY directly409, wrongcook read/accept404, customercook operation403; events unchanged; reduce max below reserved2 rejected409.

**Expected and actual:**

- **PASS** Invalid PENDING→READY transition refused. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"state","code":"PENDING_ACCEPTANCE"}],"requestId":"346a2bfa-f586-490c-b340-8755afa342bc","retryable":false}}`.

- **PASS** Wrong cook cannot read order contact/details. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"4b7072d1-9f54-45ad-9278-1b82fc576e56","retryable":false}}`.

- **PASS** Wrong cook cannot accept order. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"2dcd59fa-1286-4bb2-b2ae-273799775fc3","retryable":false}}`.

- **PASS** Customer cannot run cook transition. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"e0aaf895-5098-4709-a3a6-e0ea77e1fd27","retryable":false}}`.

- **PASS** Denied actions do not append business events. Expected: `"1"`. Actual: `"1"`.

- **PASS** Capacity cannot fall below actual reservation. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"maxPortions","code":"BELOW_OBLIGATIONS"}],"requestId":"b4ac7a3d-3d51-47ef-92b3-202b9d9c0d5a","retryable":false}}`.

**Evidence:** `evidence/network.json` and `reviewed-results.json` (API/DB assertions; no screenshot claimed).

**Execution review:** Latest execution; earlier attempts remain in results.json.

## X-FULFILL — PASS

**Actor/surface:** Cook + customer web, real database. **Requirement:** BR-022/026/029/030/031.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Same actual connected order620ab03d-db91-443f-8e32-d36cbd939e36; staff-created slotd51ea2ec-9c6b-44da-b112-5f86fd3d5477; phase/state prerequisites are checked by the harness.

**Steps:** Correct cook UI accept→prepare→ready→customer code handover. Customer accepted update; repeat same accept key200 but only1 event. Supplemental same-order final UI Delivered/API COMPLETED, both counters consumed2 and events5.

**Expected and actual:**

- **PASS** Correct cook accepts exact customer order. Expected: `{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","state":"ACCEPTED"}`. Actual: `{"status":200,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":1,"state":"ACCEPTED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"… [complete value in reviewed-results.json]`.

- **PASS** Customer automatically sees same accepted state. Expected: `"ACCEPTED"`. Actual: `"ACCEPTED"`.

- **PASS** Same-key retry succeeds without duplicate effect. Expected: `200`. Actual: `{"status":200,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":1,"state":"ACCEPTED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"… [complete value in reviewed-results.json]`.

- **PASS** Exactly one ACCEPT event. Expected: `1`. Actual: `"1"`.

- **PASS** Supported transition PREPARING. Expected: `{"status":200,"state":"PREPARING"}`. Actual: `{"status":200,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":2,"state":"PREPARING","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,… [complete value in reviewed-results.json]`.

- **PASS** Supported transition READY. Expected: `{"status":200,"state":"READY"}`. Actual: `{"status":200,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":3,"state":"READY","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"cur… [complete value in reviewed-results.json]`.

- **PASS** Pickup completes only with customer single-use handover. Expected: `{"status":200,"state":"COMPLETED"}`. Actual: `{"status":200,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":4,"state":"COMPLETED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,… [complete value in reviewed-results.json]`.

- **PASS** Same order is COMPLETED and visibly Delivered. Expected: `{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","state":"COMPLETED","totalMinor":24050}`. Actual: `{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-[REDACTED SIX-DIGIT VALUE]","version":4,"state":"COMPLETED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"… [complete value in reviewed-results.json]`.

- **PASS** Both counters consumed exactly 2; no outstanding allocation or duplicate event. Expected: `{"reserved":0,"allocated":0,"consumed":2,"events":5}`. Actual: `{"slot":{"reserved":0,"allocated":0,"consumed":2},"meal":{"reserved":0,"allocated":0,"consumed":2},"events":5}`.

**Evidence:** [X-FULFILL-customer-completed.png](evidence/X-FULFILL-customer-completed.png), [X-FULFILL-customer-completed.json](evidence/X-FULFILL-customer-completed.json)

**Execution review:** Original business steps actually executed; supplemental FINAL verified remaining assertions after correcting a QA locator.

## S-DRAFT-CLOSE — FAIL

**Actor/surface:** Cook web. **Requirement:** MB-015/022; documented slot state transitions.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Existing day7 DRAFT slot; click offered Close in actual cook card; inspect409 STATE_CONFLICT and visible error; reread slot remainsDRAFT.

**Expected and actual:**

- **FAIL** Offered Close action must be a supported transition. Expected: `"UI offers only allowed actions; or Close completes"`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[],"requestId":"c4d8e4ca-f1c1-426a-9174-9cc76ece3948","retryable":false},"body":{"state":"CLOSED","expectedVersion":0}}`.

- **PASS** Rejected action leaves draft unchanged. Expected: `"DRAFT"`. Actual: `"37dbfa84-5fbb-4766-a000-2c0e74648704"`.

**Evidence:** [S-DRAFT-CLOSE-rejected.png](evidence/S-DRAFT-CLOSE-rejected.png), [S-DRAFT-CLOSE-rejected.json](evidence/S-DRAFT-CLOSE-rejected.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-SUPPORT — FAIL

**Actor/surface:** Customer/support web + API. **Requirement:** MB-024/028; CW16/OP05; BR-041.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Same actual connected order620ab03d-db91-443f-8e32-d36cbd939e36; staff-created slotd51ea2ec-9c6b-44da-b112-5f86fd3d5477; phase/state prerequisites are checked by the harness.

**Steps:** Customer-a creates case for connected order in UI; support staff UI writes STAFF_ONLY and PUBLIC replies; customer API returns public only, othercustomer404/cook+customerops403. Reload customer Account; reply unreadable/no thread link.

**Expected and actual:**

- **PASS** Customer opens real order-scoped case. Expected: `201`. Actual: `{"status":201,"data":{"id":"385c1e8f-c6c2-4dba-8733-fd3f306a27e4","reference":"CS-A12B4EF8","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","orderNumber":"MB-100005","category":"QUALITY","state":"OPEN","description":"Independent synthetic QA support thread check.","resolutionCode":null,"createdAt":"2026-10-12T16:56:14.267984Z","updatedAt":"2026-10-12T16:56:14.267984Z","messages":[],"version":0},"body":{"orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","category":"QUALITY","description":"Independ… [complete value in reviewed-results.json]`.

- **PASS** Explicit staff-only note saved. Expected: `201`. Actual: `{"status":201,"data":{"id":"1a3ecc60-f00b-441f-81fe-841471e0acf5","authorRole":"STAFF","visibility":"STAFF_ONLY","body":"QA PRIVATE NOTE NEVER FOR CUSTOMER","createdAt":"2026-10-12T16:56:16.111624Z"},"body":{"body":"QA PRIVATE NOTE NEVER FOR CUSTOMER","visibility":"STAFF_ONLY"},"key":"[REDACTED IDEMPOTENCY KEY]"}`.

- **PASS** Support public reply saved. Expected: `201`. Actual: `{"status":201,"data":{"id":"23d28554-1d79-4edc-bb16-d1cb2704c766","authorRole":"STAFF","visibility":"PUBLIC","body":"QA PUBLIC REPLY FOR CUSTOMER","createdAt":"2026-10-12T16:56:16.406198Z"},"body":{"body":"QA PUBLIC REPLY FOR CUSTOMER","visibility":"PUBLIC"},"key":"[REDACTED IDEMPOTENCY KEY]"}`.

- **PASS** Customer API includes public reply and never staff-only note. Expected: `"public only"`. Actual: `{"status":200,"data":{"id":"385c1e8f-c6c2-4dba-8733-fd3f306a27e4","reference":"CS-A12B4EF8","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","orderNumber":"MB-100005","category":"QUALITY","state":"IN_PROGRESS","description":"Independent synthetic QA support thread check.","resolutionCode":null,"createdAt":"2026-10-12T16:56:14.267984Z","updatedAt":"2026-10-12T16:56:16.407661Z","messages":[{"id":"23d28554-1d79-4edc-bb16-d1cb2704c766","authorRole":"STAFF","visibility":"PUBLIC","body":"QA PUBLIC REP… [complete value in reviewed-results.json]`.

- **PASS** Other customer cannot read case. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"eacbbdfc-6296-4c9c-9ee7-9045f6d53fdb","retryable":false}}`.

- **PASS** Customer cannot read staff thread. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"725e2885-36e9-4162-a7b1-026f13caa58b","retryable":false}}`.

- **PASS** Cook cannot read private thread. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"b12cb39d-7b77-4157-95f8-aa8fafab1c1f","retryable":false}}`.

- **FAIL** Delivered customer support thread exposes public reply. Expected: `"public reply readable in CW16"`. Actual: `"Support\nCS-A12B4EF8 · MB-100005 · Quality · IN_PROGRESS\nCS-C1352825 · MB-100005 · Quality · OPEN\nGet help with an order\nOrder\n—\nMB-100007\nMB-100006\nMB-100005\nProblem type\nLate\nMissing item\nQuality\nFood safety\nCash payment\nHandover\nOther\nDescribe the problem\nGet help with an order"`.

**Evidence:** [S-SUPPORT-customer-no-reply.png](evidence/S-SUPPORT-customer-no-reply.png), [S-SUPPORT-customer-no-reply.json](evidence/S-SUPPORT-customer-no-reply.json), [S-SUPPORT-staff-thread.png](evidence/S-SUPPORT-staff-thread.png), [S-SUPPORT-staff-thread.json](evidence/S-SUPPORT-staff-thread.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-TRUST — PASS

**Actor/surface:** Customer web/API. **Requirement:** MB-028; BR-039/040/028.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Same actual connected order620ab03d-db91-443f-8e32-d36cbd939e36; staff-created slotd51ea2ec-9c6b-44da-b112-5f86fd3d5477; phase/state prerequisites are checked by the harness.

**Steps:** UI rating4 on completed own order; duplicate409/wrongowner404; add favorite and account removal; create actual B1pending order then customer UI cancel; reservation returns0.

**Expected and actual:**

- **PASS** Previously executed UI review persists exactly once. Expected: `"one rating-4 review from prior C-TRUST execution"`. Actual: `"1"`.

- **PASS** Duplicate review refused. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"orderId","code":"ALREADY_REVIEWED"}],"requestId":"2b928099-0be9-4cf3-a609-2e2bf7c2a5ec","retryable":false}}`.

- **PASS** Wrong customer review refused. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"ebdb65b1-4b1c-4076-9dfe-7c85fd694bb4","retryable":false}}`.

- **PASS** Favorite action real API succeeds. Expected: `[200,204]`. Actual: `{"status":200,"data":{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","priceMinor":12025,"kitchenName":"مطبخ تجريبي A","available":true,"createdAt":"2026-10-12T16:59:35.300736Z"},"body":null}`.

- **PASS** Favorite remove updates actual server list. Expected: `"absent"`. Actual: `"00000000-0000-0000-0000-000000000263"`.

- **PASS** UI pending cancellation returns CANCELLED. Expected: `"CANCELLED"`. Actual: `{"status":200,"data":{"id":"58834577-0a9e-401b-b694-0802bf9ff54c","publicNumber":"MB-100010","version":1,"state":"CANCELLED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-00000000026d","title":"QA Meal B1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":8025,"currency":"EGP"},"lineTotal":{"amountMinor":8025,"currency":"EGP"}}],"subtotal":{"amountMinor":8025,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"cu… [complete value in reviewed-results.json]`.

- **PASS** Cancellation releases exact reservation. Expected: `0`. Actual: `"0"`.

**Evidence:** [C-TRUST-cancelled.png](evidence/C-TRUST-cancelled.png), [C-TRUST-cancelled.json](evidence/C-TRUST-cancelled.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-STALE — PASS

**Actor/surface:** Customer web + cook API. **Requirement:** BR-013/017/021/022; TC-022 stale availability.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Real A1quote with qty2/A5. Cook pauses publication while quote is open. UI placement409; no order inserted; customer can calculate again; authorized republish cleanup.

**Expected and actual:**

- **PASS** Cook changes offering after valid quote. Expected: `200`. Actual: `{"status":200,"data":{"id":"00000000-0000-0000-0000-000000000263","kitchenId":"00000000-0000-0000-0000-00000000012d","status":"PAUSED","content":{"titleAr":"وجبة تجريبية A1","titleEn":"QA Meal A1","descriptionAr":"وصف مستقل للوجبة A1 بمكونات تجريبية فقط. ","descriptionEn":"Independent synthetic description for meal A1. ","portionAr":"حصة تجريبية","portionEn":"Synthetic portion","ingredientsAr":"أرز تجريبي","ingredientsEn":"Synthetic rice","allergenDeclaration":"UNKNOWN","allergenCodes":[],"aller… [complete value in reviewed-results.json]`.

- **PASS** Stale quote refuses newly unpublished item. Expected: `409`. Actual: `{"status":409,"data":{"code":"STOCK_CHANGED","messageKey":"error.stock_changed","fieldErrors":[{"field":"items","code":"MEAL_NOT_AVAILABLE"}],"requestId":"b3450408-0582-4a64-9971-28301cd25589","retryable":false}}`.

- **PASS** Rejected stale placement creates no order. Expected: `"5"`. Actual: `"5"`.

- **PASS** Customer has actionable refreshed-quote state. Expected: `"error + calculate again"`. Actual: `"Matbakhy\nCart\nالعربية\nCart\n\nKitchen: مطبخ تجريبي A\n\n18 Oct 2026, 18:59\n\nQA Meal A1\n−\n2\n+\nEGP 240.50\nRemove\nEstimated subtotal\nEGP 240.50\n\nThis is an estimate from the prices shown when you added each meal. The final total at the current price appears when you choose \"Calculate total\".\n\nEmpty cart\nCheckout\nDelivery\nPickup from the kitchen\nCalculate total\n\nSomething went wrong.\n\nHome\nMy orders\nMy account"`.

**Evidence:** [C-STALE-rejected.png](evidence/C-STALE-rejected.png), [C-STALE-rejected.json](evidence/C-STALE-rejected.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-RECOVERY — PASS

**Actor/surface:** Customer web + PostgreSQL. **Requirement:** BR-023/044; CW05/CW07.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Real quote→persist keys→real POST commits; abort response and lookup transport. Offline pending remains reachable/locked. Reopen/reload with lookup still interrupted; original keys survive. Restore lookup, same-key UI retry reconciles exact one order; compare DB/id and clear pending/cart; bounded cancel cleanup.

**Expected and actual:**

- **PASS** Lost response retains original stable request keys. Expected: `"keys stored before real successful submission"`. Actual: `{"requestId":"407a26e6-2063-46d6-822e-ddad0ba94c08","quoteId":"ffebc77c-17ba-4438-b208-d0bca903ae64","serverStatus":201}`.

- **PASS** Pending action reachable offline in loaded SPA. Expected: `true`. Actual: `true`.

- **PASS** Reload retains uncertain pending action and stable request ID. Expected: `"same persisted request ID"`. Actual: `"407a26e6-2063-46d6-822e-ddad0ba94c08"`.

- **PASS** Same-key retry returns same exact order. Expected: `{"id":"62e6e050-a40a-41b6-92dd-74ed7ffc2123","status":201}`. Actual: `{"status":201,"data":{"id":"62e6e050-a40a-41b6-92dd-74ed7ffc2123","publicNumber":"MB-100013","version":0,"state":"PENDING_ACCEPTANCE","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amount… [complete value in reviewed-results.json]`.

- **PASS** Exactly one order exists for uncertain submission. Expected: `1`. Actual: `"1"`.

- **PASS** Pending/cart cleared after reconciliation. Expected: `true`. Actual: `true`.

- **PASS** Bounded cleanup releases real pending order. Expected: `200`. Actual: `{"status":200,"data":{"id":"62e6e050-a40a-41b6-92dd-74ed7ffc2123","publicNumber":"MB-100013","version":1,"state":"CANCELLED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,… [complete value in reviewed-results.json]`.

**Evidence:** [C-RECOVERY-offline-pending.png](evidence/C-RECOVERY-offline-pending.png), [C-RECOVERY-offline-pending.json](evidence/C-RECOVERY-offline-pending.json), [C-RECOVERY-after-reload.png](evidence/C-RECOVERY-after-reload.png), [C-RECOVERY-after-reload.json](evidence/C-RECOVERY-after-reload.json), [C-RECOVERY-reconciled.png](evidence/C-RECOVERY-reconciled.png), [C-RECOVERY-reconciled.json](evidence/C-RECOVERY-reconciled.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-FINANCE — PASS

**Actor/surface:** Cook/finance web + real API/database. **Requirement:** BR-032/033/035/036; MB-020/025.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Same actual connected order620ab03d-db91-443f-8e32-d36cbd939e36; staff-created slotd51ea2ec-9c6b-44da-b112-5f86fd3d5477; phase/state prerequisites are checked by the harness.

**Steps:** Actual pickup completed but DUE; unverifiedrefund409; cook UI reports24050; separate finance API verifies; statementUI shows2405 commission. FINAL: financeUI refund, same-key replay, selfapprove403, separate approve, overrefund409, reject without payment; settlementUIcreate, selfreview403, separate review, unpaidreconcile409; balanced ledger.

**Expected and actual:**

- **PASS** Completion does not imply collected cash. Expected: `{"state":"COMPLETED","cashState":"DUE"}`. Actual: `{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":4,"state":"COMPLETED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"currency":"EGP"},"to… [complete value in reviewed-results.json]`.

- **PASS** Unverified collection cannot refund. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"orderId","code":"COLLECTION_NOT_VERIFIED"}],"requestId":"5dfeacc4-671c-4998-b06c-f23154d6252b","retryable":false}}`.

- **PASS** Cook reports exact cash separately. Expected: `201`. Actual: `{"status":201,"data":{"id":"9b01c299-39e0-4996-a8ff-490351d60cbb","amount":{"amountMinor":24050,"currency":"EGP"},"collectedAt":"2026-10-12T17:00:01.429Z","externalReference":"QA-CASH-EVIDENCE","reportedAt":"2026-10-12T17:00:01.479175Z","discrepancy":{"amountMinor":0,"currency":"EGP"},"state":"REPORTED","verifiedAmount":null,"verificationReference":null,"verifiedAt":null,"version":0},"body":{"amountMinor":24050,"currency":"EGP","collectedAt":"2026-10-12T17:00:01.429Z","method":"CASH","externalRe… [complete value in reviewed-results.json]`.

- **PASS** Separate finance verifies collection. Expected: `{"state":"VERIFIED"}`. Actual: `{"status":200,"data":{"id":"9b01c299-39e0-4996-a8ff-490351d60cbb","amount":{"amountMinor":24050,"currency":"EGP"},"collectedAt":"2026-10-12T17:00:01.429Z","externalReference":"QA-CASH-EVIDENCE","reportedAt":"2026-10-12T17:00:01.479175Z","discrepancy":{"amountMinor":0,"currency":"EGP"},"state":"VERIFIED","verifiedAmount":{"amountMinor":24050,"currency":"EGP"},"verificationReference":"QA-VERIFIED","verifiedAt":"2026-10-12T17:00:01.549985Z","version":1}}`.

- **PASS** Previously independently reported + verified collection appears in statement. Expected: `{"cashState":"VERIFIED","commissionMinor":2405}`. Actual: `{"orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","completedAt":"2026-10-12T16:37:27.485800Z","orderTotal":{"amountMinor":24050,"currency":"EGP"},"cashState":"VERIFIED","commission":{"amountMinor":2405,"currency":"EGP"},"commissionState":"POSTED"}`.

- **PASS** UI requests full verified collection refund only. Expected: `"REQUESTED obligation24050"`. Actual: `{"status":200,"data":{"id":"06ed8b55-c627-4ef3-87cf-06c3fa6182bc","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"REQUESTED","reason":"Synthetic full refund request; no money moved","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":null,"paidBy":null,"paymentReference":null,"verifiedBy":null,"decisionReason":null,"createdAt":"2026-10-12T17:04:… [complete value in reviewed-results.json]`.

- **PASS** Same-key refund retry yields exact same refund. Expected: `"06ed8b55-c627-4ef3-87cf-06c3fa6182bc"`. Actual: `{"status":200,"data":{"id":"06ed8b55-c627-4ef3-87cf-06c3fa6182bc","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"REQUESTED","reason":"Synthetic full refund request; no money moved","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":null,"paidBy":null,"paymentReference":null,"verifiedBy":null,"decisionReason":null,"createdAt":"2026-10-12T17:04:… [complete value in reviewed-results.json]`.

- **PASS** Requester cannot approve own refund. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"c6b81524-dab8-43a1-b307-d9e4a4b204fb","retryable":false}}`.

- **PASS** Separate finance approval does not mean paid. Expected: `"APPROVED"`. Actual: `{"status":200,"data":{"id":"06ed8b55-c627-4ef3-87cf-06c3fa6182bc","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"APPROVED","reason":"Synthetic full refund request; no money moved","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":"00000000-0000-0000-0000-0000000002c6","paidBy":null,"paymentReference":null,"verifiedBy":null,"decisionReason":nu… [complete value in reviewed-results.json]`.

- **PASS** Second request cannot exceed verified collection. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"fullAmountMinor","code":"OVER_REFUND"}],"requestId":"ac015cd8-183c-4163-a74b-0dc5575a1181","retryable":false}}`.

- **PASS** Bounded unpaid refund safely rejected. Expected: `"REJECTED"`. Actual: `{"status":200,"data":{"id":"06ed8b55-c627-4ef3-87cf-06c3fa6182bc","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"REJECTED","reason":"Synthetic full refund request; no money moved","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":"00000000-0000-0000-0000-0000000002c6","paidBy":null,"paymentReference":null,"verifiedBy":null,"decisionReason":"S… [complete value in reviewed-results.json]`.

- **PASS** UI creates bounded synthetic commission settlement draft. Expected: `"DRAFT"`. Actual: `{"status":200,"data":{"id":"d44c87e3-d3b2-4050-bead-2d4b43a61b8e","kitchenId":"00000000-0000-0000-0000-00000000012d","periodFrom":"2026-10-01","periodTo":"2026-10-12","state":"DRAFT","total":{"amountMinor":2405,"currency":"EGP"},"direction":"COOK_PAYS_PLATFORM","lines":[{"ledgerGroupId":"c8451550-5e7c-45b6-beba-8858f7c13d05","kind":"COMMISSION","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","amount":{"amountMinor":2405,"currency":"EGP"}}],"createdBy":"00000000-0000-0000-0000-0000000002c5","rev… [complete value in reviewed-results.json]`.

- **PASS** Creator cannot review own settlement. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"d1967697-ed90-4ab3-a42c-4a47704254c8","retryable":false}}`.

- **PASS** Independent reviewer freezes batch without implying remittance. Expected: `"REVIEWED; no actual payment"`. Actual: `{"status":200,"data":{"id":"d44c87e3-d3b2-4050-bead-2d4b43a61b8e","kitchenId":"00000000-0000-0000-0000-00000000012d","periodFrom":"2026-10-01","periodTo":"2026-10-12","state":"REVIEWED","total":{"amountMinor":2405,"currency":"EGP"},"direction":"COOK_PAYS_PLATFORM","lines":[{"ledgerGroupId":"c8451550-5e7c-45b6-beba-8858f7c13d05","kind":"COMMISSION","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","amount":{"amountMinor":2405,"currency":"EGP"}}],"createdBy":"00000000-0000-0000-0000-0000000002c5","… [complete value in reviewed-results.json]`.

- **PASS** Cannot reconcile unpaid batch. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"state","code":"REVIEWED"}],"requestId":"a305abc1-dcf8-475d-9661-49cdfe3656f7","retryable":false}}`.

- **PASS** Customer denied finance data. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"166eceea-0321-42e8-9e1d-9ef0e9c712a3","retryable":false}}`.

- **PASS** All posted ledger groups balance in dedicated DB. Expected: `"all balance0"`. Actual: `[{"group_id":"7f03228b-9260-4222-9822-4446d075ae1c","balance":0},{"group_id":"c8451550-5e7c-45b6-beba-8858f7c13d05","balance":0}]`.

**Evidence:** [S-FINANCE-cook-statement.png](evidence/S-FINANCE-cook-statement.png), [S-FINANCE-cook-statement.json](evidence/S-FINANCE-cook-statement.json), [S-FINANCE-finance-summary.png](evidence/S-FINANCE-finance-summary.png), [S-FINANCE-finance-summary.json](evidence/S-FINANCE-finance-summary.json)

**Execution review:** Original business steps actually executed; supplemental FINAL verified remaining assertions after correcting a QA locator.

## C-DELIVERY — PASS

**Actor/surface:** Customer/cook web + API/database. **Requirement:** BR-019/020/030/031/033; MB-017/019.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** UI requires own address; empty validation422; save generated synthetic address; configured3000 fee +12025 subtotal =15025, tax0. UIplace→cook accept/prepare/ready; handover-before-dispatch409; FINAL same order dispatch/correct-code handover→Delivered; storedcommission1203 excludesfee.

**Expected and actual:**

- **PASS** Delivery requires selected own address. Expected: `"disabled"`. Actual: `"Calculate disabled"`.

- **PASS** Empty address is rejected. Expected: `422`. Actual: `{"status":422,"data":{"code":"VALIDATION_FAILED","messageKey":"error.validation_failed","fieldErrors":[{"field":"building","code":"Size"},{"field":"city","code":"Size"},{"field":"completeForCreate","code":"AssertTrue"},{"field":"governorate","code":"Size"},{"field":"label","code":"Size"},{"field":"recipientName","code":"Size"},{"field":"street","code":"Size"}],"requestId":"4c1157d8-00fe-4a19-94f9-e5f87e97c7c9","retryable":false}}`.

- **PASS** Valid synthetic address saved through UI. Expected: `201`. Actual: `{"status":201,"data":{"id":"e090f730-ca2b-44f5-9f39-9a147280ec9d","label":"[REDACTED LOCATION]","recipientName":"[REDACTED]","phoneE164":"[REDACTED]","governorate":"[REDACTED LOCATION]","city":"[REDACTED LOCATION]","zoneId":"00000000-0000-4000-8000-00000000a001","street":"[REDACTED]","building":"[REDACTED]","floor":null,"unit":null,"landmark":null,"latitude":null,"longitude":null,"version":0}}`.

- **PASS** Actual configured delivery fee and integer total. Expected: `{"subtotal":12025,"fee":3000,"tax":0,"total":15025}`. Actual: `{"status":201,"data":{"quoteId":"1c59ca71-e5e1-4e58-ae0e-465cba293499","expiresAt":"2026-10-12T17:03:00.302322821Z","kitchenId":"00000000-0000-0000-0000-00000000012d","items":[{"mealId":"00000000-0000-0000-0000-000000000263","mealRevision":1,"title":"QA Meal A1","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amountMino… [complete value in reviewed-results.json]`.

- **PASS** Delivery UI creates exact priced order. Expected: `201`. Actual: `{"status":201,"data":{"id":"9a29b97b-ae92-471e-a448-759997fdd767","publicNumber":"MB-100009","version":0,"state":"PENDING_ACCEPTANCE","fulfillmentMode":"DELIVERY","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"a… [complete value in reviewed-results.json]`.

- **PASS** Delivery transition ACCEPTED. Expected: `"ACCEPTED"`. Actual: `{"status":200,"data":{"id":"9a29b97b-ae92-471e-a448-759997fdd767","publicNumber":"MB-100009","version":1,"state":"ACCEPTED","fulfillmentMode":"DELIVERY","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amountMinor… [complete value in reviewed-results.json]`.

- **PASS** Delivery transition PREPARING. Expected: `"PREPARING"`. Actual: `{"status":200,"data":{"id":"9a29b97b-ae92-471e-a448-759997fdd767","publicNumber":"MB-100009","version":2,"state":"PREPARING","fulfillmentMode":"DELIVERY","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amountMino… [complete value in reviewed-results.json]`.

- **PASS** Delivery transition READY. Expected: `"READY"`. Actual: `{"status":200,"data":{"id":"9a29b97b-ae92-471e-a448-759997fdd767","publicNumber":"MB-100009","version":3,"state":"READY","fulfillmentMode":"DELIVERY","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amountMinor":0… [complete value in reviewed-results.json]`.

- **PASS** Delivery cannot hand over before dispatch. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"fulfillmentMode","code":"WRONG_FULFILLMENT_MODE"}],"requestId":"3ee3334d-37af-41af-bac3-4e3447ca7969","retryable":false}}`.

- **PASS** UI dispatches same created delivery order. Expected: `"OUT_FOR_DELIVERY"`. Actual: `{"status":200,"data":{"id":"9a29b97b-ae92-471e-a448-759997fdd767","publicNumber":"MB-100009","version":4,"state":"OUT_FOR_DELIVERY","fulfillmentMode":"DELIVERY","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amo… [complete value in reviewed-results.json]`.

- **PASS** Actual customer code completes dispatched delivery. Expected: `"COMPLETED"`. Actual: `{"status":200,"data":{"id":"9a29b97b-ae92-471e-a448-759997fdd767","publicNumber":"MB-100009","version":5,"state":"COMPLETED","fulfillmentMode":"DELIVERY","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":12025,"currency":"EGP"}}],"subtotal":{"amountMinor":12025,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amountMino… [complete value in reviewed-results.json]`.

- **PASS** Stored exact total/fee/tax and snapshot rounding exclude delivery. Expected: `{"subtotal":12025,"fee":3000,"tax":0,"total":15025,"commissionMinor":1203}`. Actual: `{"id":"9a29b97b-ae92-471e-a448-759997fdd767","subtotal_minor":12025,"delivery_fee_minor":3000,"tax_minor":0,"total_minor":15025,"commission_minor":1203,"commission_bps_snapshot":1000,"state":"COMPLETED"}`.

**Evidence:** [C-DELIVERY-completed.png](evidence/C-DELIVERY-completed.png), [C-DELIVERY-completed.json](evidence/C-DELIVERY-completed.json)

**Execution review:** Original business steps actually executed; supplemental FINAL verified remaining assertions after correcting a QA locator.

## S-CONFIG — PASS

**Actor/surface:** Admin/config and password-only customer API + admin web. **Requirement:** BR-007/043/042; MB-026.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Fresh true OTP admin; inspect synthetic policy; selfescalation403; temporary config.write grant to password-only customer, sensitivecommand STEP_UP_REQUIRED403; revoke and recheckdenial. Change zonefee+125 via actual versionedcommand, verify audit, restorefee.

**Expected and actual:**

- **PASS** Configuration shows actual synthetic policy. Expected: `"synthetic 10% commission"`. Actual: `{"version":1,"label":"SYNTHETIC development policy (not for live use)","synthetic":true,"status":"ACTIVE","taxRateBps":0,"commissionRateBps":1000,"maxItemQuantity":10,"quoteTtlSeconds":300,"acceptanceWindowMinutes":15,"minAcceptanceMinutes":5,"cancellationPolicyVersion":"synthetic-cancel-v1","termsVersion":"synthetic-terms-v1","approvedAt":null}`.

- **PASS** Self-escalation refused. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"43b92ed5-000a-4a07-971c-86820c9ec2f1","retryable":false}}`.

- **PASS** Genuine OTP admin grants bounded synthetic permission. Expected: `201`. Actual: `{"status":201,"data":{"id":"7158cb30-5d7c-4e50-be97-36ee2de3955a","userId":"00000000-0000-0000-0000-0000000002bd","permission":"config.write","grantedAt":"2026-10-12T16:58:16.796701249Z","expiresAt":null,"revokedAt":null}}`.

- **PASS** Permission alone cannot bypass fresh MFA. Expected: `{"status":403,"code":"STEP_UP_REQUIRED"}`. Actual: `{"status":403,"data":{"code":"STEP_UP_REQUIRED","messageKey":"error.step_up_required","fieldErrors":[],"requestId":"f441c14a-5841-4155-86b9-ad8f4bb78e7f","retryable":false}}`.

- **PASS** Grant revoked without product fixture rewrite. Expected: `200`. Actual: `{"status":200,"data":{"id":"7158cb30-5d7c-4e50-be97-36ee2de3955a","userId":"00000000-0000-0000-0000-0000000002bd","permission":"config.write","grantedAt":"2026-10-12T16:58:16.796701Z","expiresAt":null,"revokedAt":"2026-10-12T16:58:16.933731897Z"}}`.

- **PASS** Revoked/customer identity cannot read staff config. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"d79322fd-3988-4f25-a23b-9e17f6c351f4","retryable":false}}`.

- **PASS** Fresh real MFA performs versioned fee change. Expected: `200`. Actual: `{"status":200,"data":{"id":"3f9d32cc-dfb1-4236-92a5-7eb762ab00f8","key":"[REDACTED IDEMPOTENCY KEY]","version":1,"value":{"active":true,"deliveryFeeMinor":3125},"reason":"Bounded QA fee-version probe","approvalReference":null,"actorId":"00000000-0000-0000-0000-0000000002c2","createdAt":"2026-10-12T16:58:16.986777521Z"}}`.

- **PASS** Versioned change is visible and audited. Expected: `"changed version + audit"`. Actual: `{"zone":{"id":"00000000-0000-4000-8000-00000000a001","code":"test-zone-a","nameAr":"منطقة تجريبية أ","nameEn":"Test zone A","active":true,"deliveryFeeMinor":3125,"version":1},"changes":[{"id":"3f9d32cc-dfb1-4236-92a5-7eb762ab00f8","key":"[REDACTED IDEMPOTENCY KEY]","version":1,"value":{"active":true,"deliveryFeeMinor":3125},"reason":"Bounded QA fee-version probe","approvalReference":null,"actorId":"00000000-0000-0000-0000-0000000002c2","createdAt":"2026-10-12T16:58:16.986778Z"}]}`.

**Evidence:** [S-CONFIG-policy.png](evidence/S-CONFIG-policy.png), [S-CONFIG-policy.json](evidence/S-CONFIG-policy.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-ONBOARD — PASS

**Actor/surface:** Applicant API/read-only web → reviewer web. **Requirement:** MB-012/023; BR-008/009; applicant editing UI explicitly future.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Customer-b applicant API draft/submit without cookprivilege. Fresh trueOTP reviewer UI startreview/approve; applicant logs into ops and sees cooktabs. No applicant editing UI claim; mandatory-document policy unresolved.

**Expected and actual:**

- **PASS** Actual applicant API saves draft without role escalation. Expected: `"DRAFT"`. Actual: `{"status":200,"data":{"id":"6f72f1f4-1334-4cce-b16d-48da370407f5","state":"DRAFT","draft":{"publicName":"QA Applicant Kitchen","bio":"Independent synthetic kitchen application.","contactPhone":"[REDACTED]","privateLocation":"[REDACTED]","pickupEnabled":true,"zoneIds":["00000000-0000-4000-8000-00000000a001"],"documentIds":[]},"currentRevision":0,"history":[],"version":0}}`.

- **PASS** Draft applicant has no cook privileges. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"f1df4ae8-923f-410c-a173-0de2a6d7c157","retryable":false}}`.

- **PASS** Valid draft submits immutable revision. Expected: `"SUBMITTED"`. Actual: `{"status":200,"data":{"id":"6f72f1f4-1334-4cce-b16d-48da370407f5","state":"SUBMITTED","draft":{"publicName":"QA Applicant Kitchen","bio":"Independent synthetic kitchen application.","contactPhone":"[REDACTED]","privateLocation":"[REDACTED]","pickupEnabled":true,"zoneIds":["00000000-0000-4000-8000-00000000a001"],"documentIds":[]},"currentRevision":1,"history":[{"revision":1,"submittedAt":"2026-10-12T16:58:25.020657Z","decision":null,"decisionReason":null,"decidedAt":null}],"version":1}}`.

- **PASS** Real MFA reviewer starts review. Expected: `"UNDER_REVIEW"`. Actual: `{"status":200,"data":{"id":"6f72f1f4-1334-4cce-b16d-48da370407f5","applicantId":"00000000-0000-0000-0000-0000000002be","state":"UNDER_REVIEW","currentRevision":1,"revisions":[{"revision":1,"publicName":"QA Applicant Kitchen","bio":"Independent synthetic kitchen application.","contactPhone":"[REDACTED]","privateLocation":"[REDACTED]","pickupEnabled":true,"zoneIds":["00000000-0000-4000-8000-00000000a001"],"documentIds":[],"submittedAt":"2026-10-12T16:58:25.020657Z","decision":null,"decisionReason"… [complete value in reviewed-results.json]`.

- **PASS** Separate reviewer approval creates kitchen. Expected: `"APPROVED + kitchen"`. Actual: `{"status":200,"data":{"id":"6f72f1f4-1334-4cce-b16d-48da370407f5","applicantId":"00000000-0000-0000-0000-0000000002be","state":"APPROVED","currentRevision":1,"revisions":[{"revision":1,"publicName":"QA Applicant Kitchen","bio":"Independent synthetic kitchen application.","contactPhone":"[REDACTED]","privateLocation":"[REDACTED]","pickupEnabled":true,"zoneIds":["00000000-0000-4000-8000-00000000a001"],"documentIds":[],"submittedAt":"2026-10-12T16:58:25.020657Z","decision":"APPROVED","decisionReaso… [complete value in reviewed-results.json]`.

- **PASS** Approved owner gains actual cook workspace. Expected: `"cook tabs"`. Actual: `["Home","Orders","Meals","Slots","Statement","Settings"]`.

**Evidence:** [S-ONBOARD-approved-owner.png](evidence/S-ONBOARD-approved-owner.png), [S-ONBOARD-approved-owner.json](evidence/S-ONBOARD-approved-owner.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## W-A11Y — PASS

**Actor/surface:** Customer/cook/support/admin responsive web. **Requirement:** BR-046/047; MB-029 practical checks, screen reader remains BLOCKED.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Six actual customer/staff screens × widths1440/390/320 × EN/AR. Verify direction/reflow; axe WCAG A/AA; keyboardfocus/outline; statement5column header scopes/cells. Screenshots inspected. Screen-reader and complete modalfocus testing remain gaps.

**Expected and actual:**

- **PASS** No document overflow customer-a///1440/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility customer-a///1440/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a///1440/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility customer-a///1440/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a///390/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility customer-a///390/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a///390/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility customer-a///390/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a///320/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility customer-a///320/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a///320/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility customer-a///320/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** Keyboard reaches a visibly focused control customer-a. Expected: `"focusable control + outline"`. Actual: `{"tag":"BUTTON","outline":"solid"}`.

- **PASS** No document overflow customer-a//meal/00000000-0000-0000-0000-000000000264/1440/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility customer-a//meal/00000000-0000-0000-0000-000000000264/1440/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//meal/00000000-0000-0000-0000-000000000264/1440/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility customer-a//meal/00000000-0000-0000-0000-000000000264/1440/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//meal/00000000-0000-0000-0000-000000000264/390/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility customer-a//meal/00000000-0000-0000-0000-000000000264/390/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//meal/00000000-0000-0000-0000-000000000264/390/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility customer-a//meal/00000000-0000-0000-0000-000000000264/390/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//meal/00000000-0000-0000-0000-000000000264/320/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility customer-a//meal/00000000-0000-0000-0000-000000000264/320/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//meal/00000000-0000-0000-0000-000000000264/320/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility customer-a//meal/00000000-0000-0000-0000-000000000264/320/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** Keyboard reaches a visibly focused control customer-a. Expected: `"focusable control + outline"`. Actual: `{"tag":"A","outline":"auto"}`.

- **PASS** No document overflow customer-a//checkout/1440/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility customer-a//checkout/1440/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//checkout/1440/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility customer-a//checkout/1440/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//checkout/390/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility customer-a//checkout/390/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//checkout/390/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility customer-a//checkout/390/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//checkout/320/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility customer-a//checkout/320/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow customer-a//checkout/320/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility customer-a//checkout/320/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** Keyboard reaches a visibly focused control customer-a. Expected: `"focusable control + outline"`. Actual: `{"tag":"A","outline":"solid"}`.

- **PASS** No document overflow cook-a/Statement/1440/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility cook-a/Statement/1440/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow cook-a/Statement/1440/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility cook-a/Statement/1440/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow cook-a/Statement/390/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility cook-a/Statement/390/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow cook-a/Statement/390/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility cook-a/Statement/390/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow cook-a/Statement/320/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility cook-a/Statement/320/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow cook-a/Statement/320/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility cook-a/Statement/320/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** Keyboard reaches a visibly focused control cook-a. Expected: `"focusable control + outline"`. Actual: `{"tag":"BUTTON","outline":"solid"}`.

- **PASS** Statement table has matching semantic header/cell counts. Expected: `"5 scoped headers / 5 cells; table-wrap controlled overflow"`. Actual: `{"headers":["col","col","col","col","col"],"rows":[5],"wrap":"table-wrap"}`.

- **PASS** No document overflow support-a/Support/1440/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility support-a/Support/1440/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow support-a/Support/1440/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility support-a/Support/1440/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow support-a/Support/390/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility support-a/Support/390/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow support-a/Support/390/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility support-a/Support/390/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow support-a/Support/320/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility support-a/Support/320/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow support-a/Support/320/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility support-a/Support/320/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** Keyboard reaches a visibly focused control support-a. Expected: `"focusable control + outline"`. Actual: `{"tag":"BUTTON","outline":"solid"}`.

- **PASS** No document overflow ops-a/Operating configuration/1440/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility ops-a/Operating configuration/1440/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow ops-a/Operating configuration/1440/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":1440,"scroll":1440}`.

- **PASS** Automated accessibility ops-a/Operating configuration/1440/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow ops-a/Operating configuration/390/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility ops-a/Operating configuration/390/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow ops-a/Operating configuration/390/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":390,"scroll":390}`.

- **PASS** Automated accessibility ops-a/Operating configuration/390/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow ops-a/Operating configuration/320/en. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility ops-a/Operating configuration/320/en. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** No document overflow ops-a/Operating configuration/320/ar. Expected: `"scrollWidth <= viewport"`. Actual: `{"inner":320,"scroll":320}`.

- **PASS** Automated accessibility ops-a/Operating configuration/320/ar. Expected: `"no critical/serious axe violations; not screen-reader acceptance"`. Actual: `[]`.

- **PASS** Keyboard reaches a visibly focused control ops-a. Expected: `"focusable control + outline"`. Actual: `{"tag":"BUTTON","outline":"solid"}`.

**Evidence:** [W-A11Y-customer-a-home-en.png](evidence/W-A11Y-customer-a-home-en.png), [W-A11Y-customer-a-home-en.json](evidence/W-A11Y-customer-a-home-en.json), [W-A11Y-customer-a-home-ar.png](evidence/W-A11Y-customer-a-home-ar.png), [W-A11Y-customer-a-home-ar.json](evidence/W-A11Y-customer-a-home-ar.json), [W-A11Y-customer-a-meal-en.png](evidence/W-A11Y-customer-a-meal-en.png), [W-A11Y-customer-a-meal-en.json](evidence/W-A11Y-customer-a-meal-en.json), [W-A11Y-customer-a-meal-ar.png](evidence/W-A11Y-customer-a-meal-ar.png), [W-A11Y-customer-a-meal-ar.json](evidence/W-A11Y-customer-a-meal-ar.json), [W-A11Y-customer-a-basket-en.png](evidence/W-A11Y-customer-a-basket-en.png), [W-A11Y-customer-a-basket-en.json](evidence/W-A11Y-customer-a-basket-en.json), [W-A11Y-customer-a-basket-ar.png](evidence/W-A11Y-customer-a-basket-ar.png), [W-A11Y-customer-a-basket-ar.json](evidence/W-A11Y-customer-a-basket-ar.json), [W-A11Y-cook-a-Statement-en.png](evidence/W-A11Y-cook-a-Statement-en.png), [W-A11Y-cook-a-Statement-en.json](evidence/W-A11Y-cook-a-Statement-en.json), [W-A11Y-cook-a-Statement-ar.png](evidence/W-A11Y-cook-a-Statement-ar.png), [W-A11Y-cook-a-Statement-ar.json](evidence/W-A11Y-cook-a-Statement-ar.json), [W-A11Y-support-a-Support-en.png](evidence/W-A11Y-support-a-Support-en.png), [W-A11Y-support-a-Support-en.json](evidence/W-A11Y-support-a-Support-en.json), [W-A11Y-support-a-Support-ar.png](evidence/W-A11Y-support-a-Support-ar.png), [W-A11Y-support-a-Support-ar.json](evidence/W-A11Y-support-a-Support-ar.json), [W-A11Y-ops-a-Operating configuration-en.png](evidence/W-A11Y-ops-a-Operating configuration-en.png), [W-A11Y-ops-a-Operating configuration-en.json](evidence/W-A11Y-ops-a-Operating configuration-en.json), [W-A11Y-ops-a-Operating configuration-ar.png](evidence/W-A11Y-ops-a-Operating configuration-ar.png), [W-A11Y-ops-a-Operating configuration-ar.json](evidence/W-A11Y-ops-a-Operating configuration-ar.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## C-CAPACITY-STALE — PASS

**Actor/surface:** Customer web + cook API/database. **Requirement:** BR-013/021/022; stale capacity after quote.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Customer A2×3/A5quote; cook reduces A2 capacity to1 through authorized API (no existing obligations). Placement409 STOCK_CHANGED/INSUFFICIENT_PORTIONS; ordercount/reserved unchanged; restorecapacity25.

**Expected and actual:**

- **PASS** Actual staff reduces offered capacity after valid quote. Expected: `200`. Actual: `{"status":200,"data":{"id":"00000000-0000-0000-0000-00000000019f","kitchenId":"00000000-0000-0000-0000-00000000012d","localDate":"2026-10-18","startsAt":"2026-10-18T15:59:42.580575Z","endsAt":"2026-10-18T17:59:42.580575Z","timezone":"Africa/Cairo","leadMinutes":120,"cutoffAt":"2026-10-18T13:59:42.580575Z","maxPortions":50,"reserved":5,"allocated":0,"consumed":1,"availablePortions":44,"state":"OPEN","orderable":true,"meals":[{"mealId":"00000000-0000-0000-0000-000000000263","capacity":25,"reserved… [complete value in reviewed-results.json]`.

- **PASS** Placement revalidates capacity atomically. Expected: `{"status":409,"code":"STOCK_CHANGED"}`. Actual: `{"status":409,"data":{"code":"STOCK_CHANGED","messageKey":"error.stock_changed","fieldErrors":[{"field":"items.quantity","code":"INSUFFICIENT_PORTIONS"}],"requestId":"6275a590-b061-43a3-8e4e-5eabcd2bd74b","retryable":false}}`.

- **PASS** No partial business effects on conflict. Expected: `{"orders":"13","reserved":0}`. Actual: `{"orders":"13","reserved":"0"}`.

**Evidence:** [C-CAPACITY-STALE-rejected.png](evidence/C-CAPACITY-STALE-rejected.png), [C-CAPACITY-STALE-rejected.json](evidence/C-CAPACITY-STALE-rejected.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.

## S-MODERATE — PASS

**Actor/surface:** Reviewer/customer web + API. **Requirement:** MB-023/028; BR-017/039.

**Preconditions:** Fresh own session with listed role; synthetic A/B catalogue. Case-specific slot, policy, publication and draft state as described below; fixture setup is not a tested behavior.

**Steps:** Reviewer UI hides A2, customerlookup404; UI Show restorespublic200. Ordermonitor sees actual completed customer public number. No ops override/failure transition PASS inferred.

**Expected and actual:**

- **PASS** Granted reviewer hides synthetic meal through UI. Expected: `"HIDDEN"`. Actual: `{"status":200,"data":{"id":"00000000-0000-0000-0000-000000000264","kitchenId":"00000000-0000-0000-0000-00000000012d","status":"PUBLISHED","content":{"titleAr":"وجبة تجريبية A2 عنوان عربي طويل لاختبار العرض في الهاتف والأرقام المختلطة","titleEn":"QA Meal A2","descriptionAr":"وصف مستقل للوجبة A2 بمكونات تجريبية فقط. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاخ… [complete value in reviewed-results.json]`.

- **PASS** Moderated meal absent to customer. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"72402486-99da-43ce-8375-798702cc892e","retryable":false}}`.

- **PASS** Actual restore makes meal visible. Expected: `200`. Actual: `{"status":200,"data":{"id":"00000000-0000-0000-0000-000000000264","kitchenId":"00000000-0000-0000-0000-00000000012d","status":"PUBLISHED","content":{"titleAr":"وجبة تجريبية A2 عنوان عربي طويل لاختبار العرض في الهاتف والأرقام المختلطة","titleEn":"QA Meal A2","descriptionAr":"وصف مستقل للوجبة A2 بمكونات تجريبية فقط. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاختبار. وصف طويل للاخ… [complete value in reviewed-results.json]`.

- **PASS** Staff monitor uses actual completed order identity. Expected: `"same customer order"`. Actual: `"MB-100009"`.

**Evidence:** [S-MODERATE-order-monitor.png](evidence/S-MODERATE-order-monitor.png), [S-MODERATE-order-monitor.json](evidence/S-MODERATE-order-monitor.json)

**Execution review:** Latest execution; earlier attempts remain in results.json.


## Additional executed cases during native runner preparation

All use the identical productSHA and real owned sessions/services. No provider/real payment was called.

## C-AFTER-ACCEPT — PASS

**Actor/surface:** Customer/cook/ops web + API/database. **Requirement:** BR-028/029/031; MB-019/024.

**Preconditions and steps:** Prerequisite: new B1×1/B2 slot order via real customer quote/place API, correct cook-b, fresh OTP ops-review. Accept; customer UI shows request only. Direct customer cancel409; UI cancellation request201 and same-key repeat same case; cookprepare; unauthorizedcookops403; fresh-MFA ops cancels with case/reason. Customer seesCancelled. Meal/slotconsumed1 remain, reserved/allocated0.

**Expected/actual subchecks:**

- **PASS** Separate correct cook accepts exact request. Expected: `"ACCEPTED"`. Actual: `{"status":200,"data":{"id":"b014ed5f-1447-417f-abde-f1eee5f304ca","publicNumber":"MB-100014","version":1,"state":"ACCEPTED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-00000000026d","title":"QA Meal B1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":8025,"currency":"EGP"},"lineTotal":{"amountMinor":8025,"currency":"EGP"}}],"subtotal":{"amountMinor":8025,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"cur… [full in reviewed-results.json]`.

- **PASS** Accepted customer has request action and no direct cancel. Expected: `"request only"`. Actual: `["العربية","Request cancellation (reviewed by support)"]`.

- **PASS** Backend agrees accepted direct cancellation denied. Expected: `409`. Actual: `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[{"field":"state","code":"CANCELLATION_REQUEST_REQUIRED"}],"requestId":"ba09ef62-25e9-40e1-aed5-c58fc3ca4314","retryable":false}}`.

- **PASS** UI creates support request without cancelling order. Expected: `"case201; orderACCEPTED"`. Actual: `{"status":201,"data":{"id":"a1e44563-1634-4940-a104-592968c2caa0","reference":"CS-4F6BC132","orderId":"b014ed5f-1447-417f-abde-f1eee5f304ca","orderNumber":"MB-100014","category":"CANCELLATION_REQUEST","state":"OPEN","description":"Cancellation requested: CHANGED_MIND","resolutionCode":null,"createdAt":"2026-10-12T17:26:13.253329Z","updatedAt":"2026-10-12T17:26:13.253329Z","messages":[],"version":0},"body":{"expectedVersion":1,"reasonCode":"CHANGED_MIND"},"key":"[REDACTED IDEMPOTENCY KEY]"}`.

- **PASS** Same-key cancellation request has one exact case. Expected: `"a1e44563-1634-4940-a104-592968c2caa0"`. Actual: `{"status":201,"data":{"id":"a1e44563-1634-4940-a104-592968c2caa0","reference":"CS-4F6BC132","orderId":"b014ed5f-1447-417f-abde-f1eee5f304ca","orderNumber":"MB-100014","category":"CANCELLATION_REQUEST","state":"OPEN","description":"Cancellation requested: CHANGED_MIND","resolutionCode":null,"createdAt":"2026-10-12T17:26:13.253329Z","updatedAt":"2026-10-12T17:26:13.253329Z","messages":[],"version":0}}`.

- **PASS** Preparing consumes capacity. Expected: `"PREPARING"`. Actual: `{"status":200,"data":{"id":"b014ed5f-1447-417f-abde-f1eee5f304ca","publicNumber":"MB-100014","version":2,"state":"PREPARING","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-00000000026d","title":"QA Meal B1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":8025,"currency":"EGP"},"lineTotal":{"amountMinor":8025,"currency":"EGP"}}],"subtotal":{"amountMinor":8025,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,"cu… [full in reviewed-results.json]`.

- **PASS** Cook cannot perform operational exception. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"17531a74-4296-426d-8e47-b7487386acb8","retryable":false}}`.

- **PASS** Fresh-MFA operational actor cancels with case/reason. Expected: `"CANCELLED"`. Actual: `{"status":200,"data":{"id":"b014ed5f-1447-417f-abde-f1eee5f304ca","publicNumber":"MB-100014","version":3,"state":"CANCELLED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-00000000026d","title":"وجبة تجريبية B1","allergenStatement":"UNKNOWN","quantity":1,"unitPrice":{"amountMinor":8025,"currency":"EGP"},"lineTotal":{"amountMinor":8025,"currency":"EGP"}}],"subtotal":{"amountMinor":8025,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":… [full in reviewed-results.json]`.

- **PASS** After preparation cancellation retains consumed portion. Expected: `"both consumed1/reserved0/allocated0"`. Actual: `{"meal":{"reserved":0,"allocated":0,"consumed":1},"slot":{"reserved":0,"allocated":0,"consumed":1}}`.

**Review:** Latest execution; earlier attempts remain in results.json.

**Evidence:** [C-AFTER-ACCEPT-cancelled.png](evidence/C-AFTER-ACCEPT-cancelled.png), [C-AFTER-ACCEPT-cancelled.json](evidence/C-AFTER-ACCEPT-cancelled.json).

## S-SETTINGS — PASS

**Actor/surface:** Cook/customer web + API. **Requirement:** BR-008/017; MB-022.

**Preconditions and steps:** Prerequisite cook-a own ACTIVE kitchen and historical completedMB-100005. Actual UI pause; publicmeal404, completedorder remains immutable24050. UIresume returnsACTIVE/public200. Edit publicdescription through Kitchen settings form; authorized restore of original synthetic description. Existing labels wrapped textarea include content; QA selector/form label corrected, not product changes.

**Expected/actual subchecks:**

- **PASS** Cook can pause own sales through delivered UI. Expected: `"PAUSED"`. Actual: `{"status":200,"data":{"kitchenId":"00000000-0000-0000-0000-00000000012d","publicSlug":"qa-kitchen-a","displayName":"مطبخ تجريبي A","description":"Synthetic independent QA kitchen","operatingState":"PAUSED","pausedBy":"COOK","pickupEnabled":true,"zones":[{"zoneId":"00000000-0000-4000-8000-00000000a003","nameAr":"منطقة تجريبية متوقفة","nameEn":"Test zone (inactive)","deliveryEnabled":true},{"zoneId":"00000000-0000-4000-8000-00000000a001","nameAr":"منطقة تجريبية أ","nameEn":"Test zone A","deliveryE… [full in reviewed-results.json]`.

- **PASS** Paused kitchen cannot offer customer meal. Expected: `404`. Actual: `{"status":404,"data":{"code":"NOT_FOUND","messageKey":"error.not_found","fieldErrors":[],"requestId":"1956247d-4326-4ba4-9819-4dd275ef5574","retryable":false}}`.

- **PASS** Historical completed order remains readable/immutable. Expected: `"COMPLETED24050"`. Actual: `{"status":200,"data":{"id":"620ab03d-db91-443f-8e32-d36cbd939e36","publicNumber":"MB-100005","version":4,"state":"COMPLETED","fulfillmentMode":"PICKUP","items":[{"mealId":"00000000-0000-0000-0000-000000000263","title":"QA Meal A1","allergenStatement":"UNKNOWN","quantity":2,"unitPrice":{"amountMinor":12025,"currency":"EGP"},"lineTotal":{"amountMinor":24050,"currency":"EGP"}}],"subtotal":{"amountMinor":24050,"currency":"EGP"},"deliveryFee":{"amountMinor":0,"currency":"EGP"},"tax":{"amountMinor":0,… [full in reviewed-results.json]`.

- **PASS** Cook resumes own pause; customer offering returns. Expected: `"ACTIVE/public200"`. Actual: `{"status":200,"data":{"kitchenId":"00000000-0000-0000-0000-00000000012d","publicSlug":"qa-kitchen-a","displayName":"مطبخ تجريبي A","description":"Synthetic independent QA kitchen","operatingState":"ACTIVE","pausedBy":null,"pickupEnabled":true,"zones":[{"zoneId":"00000000-0000-4000-8000-00000000a003","nameAr":"منطقة تجريبية متوقفة","nameEn":"Test zone (inactive)","deliveryEnabled":true},{"zoneId":"00000000-0000-4000-8000-00000000a001","nameAr":"منطقة تجريبية أ","nameEn":"Test zone A","deliveryEna… [full in reviewed-results.json]`.

- **PASS** Settings UI saves own public description. Expected: `"saved content"`. Actual: `{"status":200,"data":{"kitchenId":"00000000-0000-0000-0000-00000000012d","publicSlug":"qa-kitchen-a","displayName":"مطبخ تجريبي A","description":"Independent synthetic QA settings description","operatingState":"ACTIVE","pausedBy":null,"pickupEnabled":true,"zones":[{"zoneId":"00000000-0000-4000-8000-00000000a003","nameAr":"منطقة تجريبية متوقفة","nameEn":"Test zone (inactive)","deliveryEnabled":true},{"zoneId":"00000000-0000-4000-8000-00000000a001","nameAr":"منطقة تجريبية أ","nameEn":"Test zone A"… [full in reviewed-results.json]`.

**Review:** Latest execution; earlier attempts remain in results.json.

**Evidence:** [S-SETTINGS-resumed.png](evidence/S-SETTINGS-resumed.png), [S-SETTINGS-resumed.json](evidence/S-SETTINGS-resumed.json).

## S-CONFIG-QUOTES — BLOCKED

**Actor/surface:** Customer/admin API + customer web. **Requirement:** BR-021/042; immutable old quote vs new zone configuration.

**Preconditions and steps:** Prerequisite UI-created own delivery address, active zone, A2/A5capacity25, freshOTP admin. Create old valid3000fee quote; adminversions fee3125; newquote3125; place old quote beforeexpiry. Actual409PRICE_CHANGED/TOTAL_CHANGED, notorder; restorefee. Initial expected honoroldquotedprice is retained. BR-042/backlog/contract/UI newquotesonly wording conflicts with verification stale-zone/policy expectation, so reviewedBLOCKEDoracle, notPASS or confirmed productdefect. Owner clarification pending.

**Expected/actual subchecks:**

- **PASS** Original quote snapshots active fee. Expected: `3000`. Actual: `{"status":201,"data":{"quoteId":"8670eb9f-d77b-44b3-b378-4695065d0897","expiresAt":"2026-10-12T17:34:35.354855647Z","kitchenId":"00000000-0000-0000-0000-00000000012d","items":[{"mealId":"00000000-0000-0000-0000-000000000264","mealRevision":1,"title":"QA Meal A2","quantity":1,"unitPrice":{"amountMinor":9550,"currency":"EGP"},"lineTotal":{"amountMinor":9550,"currency":"EGP"}}],"subtotal":{"amountMinor":9550,"currency":"EGP"},"deliveryFee":{"amountMinor":3000,"currency":"EGP"},"tax":{"amountMinor":… [full in reviewed-results.json]`.

- **PASS** Actual fresh-MFA admin versions fee. Expected: `200`. Actual: `{"status":200,"data":{"id":"352b4537-ae9e-404b-ad2b-9f09557a9e97","key":"[REDACTED IDEMPOTENCY KEY]","version":5,"value":{"active":true,"deliveryFeeMinor":3125},"reason":"Synthetic old/new quote boundary","approvalReference":null,"actorId":"00000000-0000-0000-0000-0000000002c2","createdAt":"2026-10-12T17:29:35.374968272Z"}}`.

- **PASS** New quote sees changed fee/version. Expected: `{"fee":3125}`. Actual: `{"status":201,"data":{"quoteId":"fa329877-2e85-41f0-a587-ab1a266fe70c","expiresAt":"2026-10-12T17:34:35.389263499Z","kitchenId":"00000000-0000-0000-0000-00000000012d","items":[{"mealId":"00000000-0000-0000-0000-000000000264","mealRevision":1,"title":"QA Meal A2","quantity":1,"unitPrice":{"amountMinor":9550,"currency":"EGP"},"lineTotal":{"amountMinor":9550,"currency":"EGP"}}],"subtotal":{"amountMinor":9550,"currency":"EGP"},"deliveryFee":{"amountMinor":3125,"currency":"EGP"},"tax":{"amountMinor":… [full in reviewed-results.json]`.

- **FAIL** Unexpired old quote keeps agreed snapshot total. Expected: `{"total":12550}`. Actual: `{"status":409,"data":{"code":"PRICE_CHANGED","messageKey":"error.price_changed","fieldErrors":[{"field":"quoteId","code":"TOTAL_CHANGED"}],"requestId":"5a354a9b-5074-4a99-9f76-96e7559e8e93","retryable":false}}`.

**Review:** Oracle conflict: BR-042/backlog/contract/UI new-quotes-only wording vs verification stale-zone/policy scenario and implementation. Initial failed expectation remains visible; not changed into a PASS. Owner clarification requested.

**Evidence:** [S-CONFIG-QUOTES-conflict-context.png](evidence/S-CONFIG-QUOTES-conflict-context.png), [S-CONFIG-QUOTES-conflict-context.json](evidence/S-CONFIG-QUOTES-conflict-context.json).

## S-FINANCE-SYNTHETIC-FINAL — PASS

**Actor/surface:** Finance web/API/database with synthetic external-reference fixture. **Requirement:** BR-033/035/036; no actual bank/refund/payment integration.

**Preconditions and steps:** Prerequisite real isolated verified fictionalcollection and reviewedunpaid settlement from S-FINANCE, separatefinance-a/b. Webrecord remittance using QA-SYNTHETIC-NO-TRANSFER; selfreconcile403, separate reconcileterminal; retryversionunchanged. Fullrefundrequest→separateapprove→webrecord QA-SYNTHETIC-NO-REFUND-TRANSFER→selfverify403→separateverify; retry yields1commissionreversal2405; ledgerbalanced; originalCOMPLETED24050immutable. All cash/externalreference fixtures fictional; no bank/payment integration tested.

**Expected/actual subchecks:**

- **PASS** Prerequisite reviewed settlement is unpaid. Expected: `"REVIEWED/no payment"`. Actual: `{"id":"d44c87e3-d3b2-4050-bead-2d4b43a61b8e","kitchenId":"00000000-0000-0000-0000-00000000012d","periodFrom":"2026-10-01","periodTo":"2026-10-12","state":"REVIEWED","total":{"amountMinor":2405,"currency":"EGP"},"direction":"COOK_PAYS_PLATFORM","lines":[{"ledgerGroupId":"c8451550-5e7c-45b6-beba-8858f7c13d05","kind":"COMMISSION","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","amount":{"amountMinor":2405,"currency":"EGP"}}],"createdBy":"00000000-0000-0000-0000-0000000002c5","reviewedBy":"00000000… [full in reviewed-results.json]`.

- **PASS** UI records clearly synthetic external-reference state. Expected: `"PAYMENT_RECORDED fixture only"`. Actual: `{"status":200,"data":{"id":"d44c87e3-d3b2-4050-bead-2d4b43a61b8e","kitchenId":"00000000-0000-0000-0000-00000000012d","periodFrom":"2026-10-01","periodTo":"2026-10-12","state":"PAYMENT_RECORDED","total":{"amountMinor":2405,"currency":"EGP"},"direction":"COOK_PAYS_PLATFORM","lines":[{"ledgerGroupId":"c8451550-5e7c-45b6-beba-8858f7c13d05","kind":"COMMISSION","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","amount":{"amountMinor":2405,"currency":"EGP"}}],"createdBy":"00000000-0000-0000-0000-0000000… [full in reviewed-results.json]`.

- **PASS** Payment recorder cannot reconcile own batch. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"f4b68fa3-4bd5-4d35-adb8-32c84b9d9d0a","retryable":false}}`.

- **PASS** Separate reconciliation reaches terminal accounting state. Expected: `"RECONCILED"`. Actual: `{"status":200,"data":{"id":"d44c87e3-d3b2-4050-bead-2d4b43a61b8e","kitchenId":"00000000-0000-0000-0000-00000000012d","periodFrom":"2026-10-01","periodTo":"2026-10-12","state":"RECONCILED","total":{"amountMinor":2405,"currency":"EGP"},"direction":"COOK_PAYS_PLATFORM","lines":[{"ledgerGroupId":"c8451550-5e7c-45b6-beba-8858f7c13d05","kind":"COMMISSION","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","amount":{"amountMinor":2405,"currency":"EGP"}}],"createdBy":"00000000-0000-0000-0000-0000000002c5"… [full in reviewed-results.json]`.

- **PASS** Repeated reconciliation returns same frozen batch. Expected: `"d44c87e3-d3b2-4050-bead-2d4b43a61b8e"`. Actual: `{"status":200,"data":{"id":"d44c87e3-d3b2-4050-bead-2d4b43a61b8e","kitchenId":"00000000-0000-0000-0000-00000000012d","periodFrom":"2026-10-01","periodTo":"2026-10-12","state":"RECONCILED","total":{"amountMinor":2405,"currency":"EGP"},"direction":"COOK_PAYS_PLATFORM","lines":[{"ledgerGroupId":"c8451550-5e7c-45b6-beba-8858f7c13d05","kind":"COMMISSION","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","amount":{"amountMinor":2405,"currency":"EGP"}}],"createdBy":"00000000-0000-0000-0000-0000000002c5"… [full in reviewed-results.json]`.

- **PASS** Full synthetic verified collection request succeeds. Expected: `"REQUESTED"`. Actual: `{"status":200,"data":{"id":"a4d38205-48e1-467d-a7e6-fbef071493c6","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"REQUESTED","reason":"Synthetic refund accounting test; no money transfer","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":null,"paidBy":null,"paymentReference":null,"verifiedBy":null,"decisionReason":null,"createdAt":"2026-10-12T… [full in reviewed-results.json]`.

- **PASS** Separate approval stays unpaid. Expected: `"APPROVED"`. Actual: `{"status":200,"data":{"id":"a4d38205-48e1-467d-a7e6-fbef071493c6","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"APPROVED","reason":"Synthetic refund accounting test; no money transfer","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":"00000000-0000-0000-0000-0000000002c6","paidBy":null,"paymentReference":null,"verifiedBy":null,"decisionReas… [full in reviewed-results.json]`.

- **PASS** UI records explicit synthetic refund reference. Expected: `"PAID fixture only"`. Actual: `{"status":200,"data":{"id":"a4d38205-48e1-467d-a7e6-fbef071493c6","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"PAID","reason":"Synthetic refund accounting test; no money transfer","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":"00000000-0000-0000-0000-0000000002c6","paidBy":"00000000-0000-0000-0000-0000000002c5","paymentReference":"QA-SY… [full in reviewed-results.json]`.

- **PASS** Payment recorder cannot verify own refund. Expected: `403`. Actual: `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"3a58ea7f-c1fa-49c5-8f98-6a0df79d8667","retryable":false}}`.

- **PASS** Separate verification completes accounting refund. Expected: `"VERIFIED"`. Actual: `{"status":200,"data":{"id":"a4d38205-48e1-467d-a7e6-fbef071493c6","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"VERIFIED","reason":"Synthetic refund accounting test; no money transfer","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":"00000000-0000-0000-0000-0000000002c6","paidBy":"00000000-0000-0000-0000-0000000002c5","paymentReference":"Q… [full in reviewed-results.json]`.

- **PASS** Verification retry does not append second reversal. Expected: `3`. Actual: `{"status":200,"data":{"id":"a4d38205-48e1-467d-a7e6-fbef071493c6","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","kitchenId":"00000000-0000-0000-0000-00000000012d","caseReference":"CS-A12B4EF8","amount":{"amountMinor":24050,"currency":"EGP"},"state":"VERIFIED","reason":"Synthetic refund accounting test; no money transfer","requestedBy":"00000000-0000-0000-0000-0000000002c5","approvedBy":"00000000-0000-0000-0000-0000000002c6","paidBy":"00000000-0000-0000-0000-0000000002c5","paymentReference":"Q… [full in reviewed-results.json]`.

- **PASS** Exactly one full snapshot commission reversal. Expected: `{"count":1,"amount":2405}`. Actual: `{"count":1,"amount":2405}`.

- **PASS** Ledger still balances after refund and reconciliation. Expected: `0`. Actual: `0`.

- **PASS** Refund never rewrites completed order. Expected: `"COMPLETED|24050"`. Actual: `"COMPLETED|24050"`.

**Review:** Latest execution; earlier attempts remain in results.json.

**Evidence:** [S-FINANCE-synthetic-final.png](evidence/S-FINANCE-synthetic-final.png), [S-FINANCE-synthetic-final.json](evidence/S-FINANCE-synthetic-final.json).

## S-SUPPORT-RESOLVE — PASS

**Actor:** Support web + customer API. **Requirement:** OP05/BR-041; MB-024 delivered staff case segment.

**Preconditions/steps:** Existing real synthetic support case CS-A12B4EF8/public+private replies; support-a assigned/resolve/close in UI with reason/resolution; owncustomer API public-only; stale updates and closed-customer replies refused. Assignment actual assigneeId matches support-a, independently checked from captured response.

**Expected/actual:**

- **PASS** Delivered staff UI assigns actual case to current staff. Expected `200`, actual `{"status":200,"data":{"id":"385c1e8f-c6c2-4dba-8733-fd3f306a27e4","reference":"CS-A12B4EF8","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","orderNumber":"MB-100005","customerId":"00000000-0000-0000-0000-0000000002bd","category":"QUALITY","severity":"NORMAL","state":"IN_PROGRESS","description":"Independent synthetic QA support thread check.","cancellationReason":null,"assigneeId":"00000000-0000-0000-0000-0000000002c4","slaDueAt":"2026-10-13T16:56:14.267984Z","overdue":false,"firstResponseAt":"2… [full reviewed-results.json]`.

- **PASS** Staff resolves case with explicit resolution. Expected `"RESOLVED/INFORMATION_PROVIDED"`, actual `{"status":200,"data":{"id":"385c1e8f-c6c2-4dba-8733-fd3f306a27e4","reference":"CS-A12B4EF8","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","orderNumber":"MB-100005","customerId":"00000000-0000-0000-0000-0000000002bd","category":"QUALITY","severity":"NORMAL","state":"RESOLVED","description":"Independent synthetic QA support thread check.","cancellationReason":null,"assigneeId":"00000000-0000-0000-0000-0000000002c4","slaDueAt":"2026-10-13T16:56:14.267984Z","overdue":false,"firstResponseAt":"2026… [full reviewed-results.json]`.

- **PASS** Customer API keeps public reply and resolved status, never private note. Expected `"RESOLVED/public only"`, actual `{"id":"385c1e8f-c6c2-4dba-8733-fd3f306a27e4","reference":"CS-A12B4EF8","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","orderNumber":"MB-100005","category":"QUALITY","state":"RESOLVED","description":"Independent synthetic QA support thread check.","resolutionCode":"INFORMATION_PROVIDED","createdAt":"2026-10-12T16:56:14.267984Z","updatedAt":"2026-10-12T17:40:48.937419Z","messages":[{"id":"23d28554-1d79-4edc-bb16-d1cb2704c766","authorRole":"STAFF","visibility":"PUBLIC","body":"QA PUBLIC REPLY FOR… [full reviewed-results.json]`.

- **PASS** Stale case update refused without state mutation. Expected `409`, actual `{"status":409,"data":{"code":"VERSION_CONFLICT","messageKey":"error.version_conflict","fieldErrors":[],"requestId":"e35d5a95-084b-45c0-a25a-58e71b2515c6","retryable":false}}`.

- **PASS** Staff UI closes resolved case. Expected `"CLOSED"`, actual `{"status":200,"data":{"id":"385c1e8f-c6c2-4dba-8733-fd3f306a27e4","reference":"CS-A12B4EF8","orderId":"620ab03d-db91-443f-8e32-d36cbd939e36","orderNumber":"MB-100005","customerId":"00000000-0000-0000-0000-0000000002bd","category":"QUALITY","severity":"NORMAL","state":"CLOSED","description":"Independent synthetic QA support thread check.","cancellationReason":null,"assigneeId":"00000000-0000-0000-0000-0000000002c4","slaDueAt":"2026-10-13T16:56:14.267984Z","overdue":false,"firstResponseAt":"2026-1… [full reviewed-results.json]`.

- **PASS** Repeated stale close refused. Expected `409`, actual `{"status":409,"data":{"code":"VERSION_CONFLICT","messageKey":"error.version_conflict","fieldErrors":[],"requestId":"6398d06d-2e65-42ba-b6a6-7762821c4fc6","retryable":false}}`.

- **PASS** Closed-case reply rejected server-side. Expected `409`, actual `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[],"requestId":"bfd1269b-f05e-442c-af49-87f66d86e3db","retryable":false}}`.

- **PASS** Independent review: assigned staff identity equals actual support actor. Expected `"00000000-0000-0000-0000-0000000002c4"`, actual `"00000000-0000-0000-0000-0000000002c4"`.

**Evidence:** [S-SUPPORT-RESOLVE-closed.png](evidence/S-SUPPORT-RESOLVE-closed.png), [S-SUPPORT-RESOLVE-closed.json](evidence/S-SUPPORT-RESOLVE-closed.json).

## S-SECURITY-REVOKED — PASS

**Actor:** Password-only customer/admin API; genuine admin OTP. **Requirement:** BR-007/043; supplements grant/revoke sensitive-command meaning.

**Preconditions/steps:** Customer-a temporary config.write already revoked by prior genuine-MFA admin action; actual freshOTP ops-a. Invoke sensitive configwrite with correct currentversion,403FORBIDDEN; only recoverable admin staffgrant has1activeholder; attempt own protected revoke409 and verify grant and flag/version unchanged.

**Expected/actual:**

- **PASS** Temporary sensitive grant is actually revoked. Expected `0`, actual `0`.

- **PASS** Revoked identity cannot perform sensitive configuration write. Expected `{"status":403,"code":"FORBIDDEN"}`, actual `{"status":403,"data":{"code":"FORBIDDEN","messageKey":"error.forbidden","fieldErrors":[],"requestId":"b830383a-1ea0-48fa-9673-1e2404213c4c","retryable":false}}`.

- **PASS** Fresh-MFA last-admin removal is safely refused. Expected `{"status":409,"code":"STATE_CONFLICT"}`, actual `{"status":409,"data":{"code":"STATE_CONFLICT","messageKey":"error.state_conflict","fieldErrors":[],"requestId":"d08f52c8-d8e6-4dc9-a819-67c2c346c8ff","retryable":false}}`.

- **PASS** Denied operation preserves recoverable administrator. Expected `"same active staff.grant"`, actual `[{"permission":"audit.read","grantId":"9a8a3a9c-1f32-4c54-b7e0-e8a536b3db28"},{"permission":"config.read","grantId":"bd8f50c2-21d5-486a-81b3-9af1c483e7c7"},{"permission":"config.write","grantId":"dc8c3bc8-adf4-4513-9063-268e8c07f510"},{"permission":"staff.grant","grantId":"219b657b-f612-4259-8054-587780702416"}]`.

- **PASS** Denied write leaves flag/version unchanged. Expected `{"key":"ordering-enabled","enabled":true,"version":0}`, actual `{"key":"ordering-enabled","enabled":true,"version":0}`.

**Evidence:** `security-revoked.json`, `network.json`, `reviewed-results.json`.
