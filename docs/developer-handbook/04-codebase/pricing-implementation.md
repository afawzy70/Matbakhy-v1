# Pricing implementation walkthrough

> **Owner role:** Backend architecture
> **Status:** IMPLEMENTED — server-side pricing, immutable meal revisions, quote validation, stale-quote refusal and client reconfirmation exist; the commercial values (tax, limits) are synthetic pending owner decision O04
> **Language / اللغة:** English · [العربية](pricing-implementation-ar.md)

Who decides a price, where it is stored, how it is checked, and what happens when it changes between "customer saw it" and
"customer pressed order". Business view: [pricing and payments](../01-business/pricing-and-payments.md). Decisions: D10 (no
commission or customer platform fee), D13 (stale quote handling, `docs/product/rq-004-stale-quote.md`), D14 (kitchen price history).

## Principles in code

- **The server owns every amount.** Request bodies for quotes (`QuoteController.Body`, `backend/modules/orders/src/main/java/com/matbakhy/orders/web/QuoteController.java:36`)
  contain meal ids, quantities, slot, fulfillment mode, address and payment method only — no prices, fees, totals or roles; unknown JSON
  properties are rejected (`StrictJsonConfiguration`). Test: `QuoteIntegrationTest` (forged fields refused, TC-019).
- **Integer money.** Piastres in `long`; arithmetic uses `Math.multiplyExact`/`addExact`. Tax rounds half up to whole piastres
  (`QuoteService.java:269`). Client side: `packages/api-client/src/money.ts` parses `{amountMinor, currency}`.
- **Prices are copied, never referenced, into orders.** Quotes store `unit_price_minor` and the `meal_revision`; orders store the quoted
  price plus the meal snapshot (`OrderService.java:206-213`).

## Where a price comes from

| Component | Source of truth | Code |
|---|---|---|
| Meal unit price | `meal.price_minor` (current) and `meal_revision.price_minor` (history); integer 1..1,000,000 piastres | validated in `MealService` (`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/meal/MealService.java:476`) |
| Price version | `meal.revision` — every content change that differs from the current content inserts a new immutable `meal_revision` row (`MealService.update` `:163`, `writeContent` `:420`); no-op edits add nothing (`:173`) | table V7, append-only trigger `meal_revision_append_only` |
| Delivery fee | `coalesce(kitchen_zone.fee_override_minor, service_zone.delivery_fee_minor)` for an enabled, active zone | `JdbcMealCatalog.deliveryFee` (`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/JdbcMealCatalog.java:44`) |
| Tax | `commerce_policy.tax_rate_bps` of the active policy (synthetic 0 until O04) | `CommercePolicies.active` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/CommercePolicies.java`) |
| Commission | `commerce_policy.commission_rate_bps`; **0** at launch; database CHECK `commerce_policy_launch_commission_ck` (V23) allows non-zero only for synthetic policies; it is snapshotted on the order but never shown to customers | `OrderService.java:194` |
| Limits | `max_item_quantity` (per line), 20 lines (`CommercePolicies.MAX_LINES_PER_ORDER`), `quote_ttl_seconds` (dev policy 300 = 5 minutes, `backend/app/src/main/resources/db/devdata/R__dev_commerce_policy.sql`) | `QuoteService.create` `:92` |
| Deposit | per kitchen, `kitchen_payment_profile.deposit_bps` | [payment implementation](payment-implementation.md) |

The active policy is the single row with `status = 'ACTIVE'` (unique partial index `commerce_policy_one_active_uq`, V10). New versions are created through
`ConfigurationService.newVersion` (`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/ConfigurationService.java:85`, `POST /api/v1/ops/configuration/{key}/versions`,
reason + approval reference + recent MFA); it locks the active row `FOR UPDATE` (`:139`) and retires it. Placed orders keep their snapshot.
Ordering can be paused by the feature flag `ordering-enabled`; a missing policy or a synthetic one outside LOCAL/CI/PREVIEW (`DeploymentEnvironment.synthetic()`) blocks ordering (`NO_APPROVED_POLICY`).

## Quote creation — `QuoteService.create` (`QuoteService.java:92`)

1. `contact.requireVerifiedContact` — verified email and phone (`CONTACT_VERIFICATION_REQUIRED`).
2. `policies.active()` — policy and ordering switch.
3. Input checks: 1..20 distinct lines, quantity 1..`maxItemQuantity` (`invalid("items", ...)` -> `VALIDATION_FAILED`).
4. `terms(...)` (`:206`) recomputes everything from the database:
   - slot must exist and be orderable at server time (`SLOT_CLOSED`); kitchen must be able to sell (`KITCHEN_UNAVAILABLE`);
   - `acceptBy = min(now + acceptanceWindowMinutes, slot cutoff)` and at least `minAcceptanceMinutes` must remain (`ACCEPTANCE_WINDOW_TOO_SHORT`);
   - each meal must be published and visible, belong to the slot's kitchen (one cook and one slot per basket, `MIXED_KITCHENS_OR_SLOT`), be offered in that slot,
     and have enough remaining portions (`STOCK_CHANGED`) — a quote reserves no stock;
   - `line = unitPrice x quantity`, `subtotal = sum`; pickup must be enabled and takes no address; delivery needs an owned, serviceable address and takes the zone fee;
   - `tax = round_half_up((subtotal + fee) x taxRateBps / 10000)`, `total = subtotal + fee + tax`.
5. `payments.terms(...)` derives the deposit/COD terms for the chosen method (`COD_NOT_ELIGIBLE`, `DEPOSIT_NOT_OFFERED`).
6. Inserts `quote` and `quote_item` rows (immutable by trigger, V10) and returns `QuoteResponse` with `expiresAt`, policy versions and warnings (`SYNTHETIC_POLICY`).

## Quote validation at placement — `QuoteService.requireValid` (`:146`)

Called by `OrderService.placeOnce` inside the placement transaction ([order implementation](order-implementation.md)).

1. Load the caller's quote (`NOT_FOUND` for others').
2. **Serialize against concurrent edits (D13).** `SELECT version FROM commerce_policy WHERE status='ACTIVE' FOR SHARE` (`:152`) and
   `MealCatalog.lockPricing` (`JdbcMealCatalog.java:35`: `FOR SHARE` on the meal rows, the `kitchen_zone` row and the `service_zone` row). A cook price edit takes the meal row
   `FOR UPDATE` (`MealService.lockOwn`, `:407`) and a policy change takes the active policy `FOR UPDATE`, so: an update that commits first is seen and the stale quote is refused;
   an update that arrives later waits until the placement commits and the order keeps its validated price. Tests: `PriceChangeIntegrationTest.when_the_price_update_takes_effect_first_the_waiting_placement_is_refused` and `..._when_the_placement_commits_first_the_order_keeps_its_validated_price_...`.
3. **Expiry.** `!now.isBefore(expires_at)` -> `QUOTE_EXPIRED` (`:157`).
4. Verified contact again; policy version must be unchanged (`PRICE_CHANGED` reason `POLICY_CHANGED`).
5. Recompute `terms(...)` from current data and compare: meal revision or unit price differs -> `PRICE_CHANGED` / `MEAL_CHANGED` (`:174`); address version or zone differs -> `STATE_CONFLICT` / `ADDRESS_CHANGED`
   (`:178`); delivery fee or total differs -> `PRICE_CHANGED` / `TOTAL_CHANGED` (`:181`); deposit terms or recipient differ -> `PRICE_CHANGED` / `PAYMENT_CHANGED` (`:191`).
6. Return `ValidQuote` (`backend/modules/orders/src/main/java/com/matbakhy/orders/api/QuoteDirectory.java`) which carries the **quoted** amounts into the order.

Order of the two possible failures matters to the client: both `QUOTE_EXPIRED` and `PRICE_CHANGED` are HTTP 409 with a stable `code`; nothing is written when they occur.

## Stale-quote reconfirmation on the client (D13)

The rule: a displayed price or an unexpired quote is **not** a price guarantee; the customer must see what changed and accept it before a new quote can be placed.

| Step | Web | Mobile |
|---|---|---|
| Placement answers `PRICE_CHANGED` | `Checkout.place` clears pending keys and calls `requote` (`apps/customer-web/src/Checkout.tsx:286-289`, `:245`) | `Cart.submit` -> `requote` (`apps/customer-mobile/src/screens/Cart.tsx:341-343`) |
| Difference computation | `priceChanges(previous, next)` in `packages/api-client/src/priceChanges.ts:31` | same function |
| Attribution | meal price changes are blamed on the kitchen; delivery fee and tax are reported separately; `unexplained` triggers a generic notice | same |
| Acceptance | `PriceChangeNotice` (`Checkout.tsx:444`) requires an explicit acceptance before enabling placement | `apps/customer-mobile/src/screens/Cart.tsx:460-463` |
| Resubmit | a fresh quote gets a new `clientRequestId` and `Idempotency-Key`; never an automatic resubmit | same |

Unit tests: `packages/api-client/test/priceChanges.test.ts`; UI tests in `apps/customer-web/src/App.test.tsx` and `apps/customer-mobile/src/App.test.tsx` (TC-154).

## Price history for kitchens (D14)

`MealService.priceHistory` (`MealService.java:186`, route `GET /api/v1/cook/meals/{mealId}/price-history`) derives history from `meal_revision.price_minor`: one entry for the first priced revision and one for each later
revision whose price differs; description-only edits add nothing; owner-scoped (another kitchen's meal is 404). Test: `PriceHistoryIntegrationTest` (TC-153); owner case E2E-COOK-103.

## Review-required areas

Rounding rules, the `FOR SHARE`/`FOR UPDATE` lock pairing between `requireValid` and `MealService.lockOwn`/`ConfigurationService.newVersion`, and the commission CHECK constraint. Changing any of them without updating
`PriceChangeIntegrationTest` and `ZeroCommissionIntegrationTest` reintroduces silent price drift or a live non-zero commission (D10).

## Known limitations and documentation gaps

- Tax treatment is undecided (O04, `planning/decisions.json`); the code supports a tax rate but the synthetic policy uses 0 and no invoice logic exists. PLANNED.
- Quote TTL is a policy value; `BR-020` says 5 minutes, which equals the synthetic dev policy (300 s) but the live value is NOT VERIFIED because no approved live policy is in the repository.

## Changing the quote lifetime

The lifetime is data, not code: `commerce_policy.quote_ttl_seconds` (database check 60..3600 in migration `V10__quotes.sql`). Placement compares `now` with the quote's `expires_at` (`QuoteService.requireValid`), it does not compute an age. To change the documented "5 minutes": update the policy seed `backend/app/src/main/resources/db/devdata/R__dev_commerce_policy.sql` and the test policy, the javadoc in `QuoteService`, the tests that assert expiry, and every page that states the value (search the handbook for "5 minutes", plus `docs/product/03-business-rules.md` BR-020, `docs/product/04-state-machines.md`, `docs/product/rq-004-stale-quote.md`). A live value needs an approved owner decision (O04).
