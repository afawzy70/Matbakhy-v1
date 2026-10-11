# API contract and wire semantics

The [endpoint registry](../../contracts/endpoints.json) is the planned route inventory. [`contracts/openapi.yaml`](../../contracts/openapi.yaml) (OpenAPI 3.1, MB-007) is the typed contract: linted with pinned Redocly CLI, and `packages/api-client` generates TypeScript types from it with a drift check (`pnpm contract:check`). Schemas marked `x-schema-status: PENDING` are explicit placeholders; the owning story must type them before any client uses them. The backend rejects unknown JSON fields, decimal-to-integer truncation and string-to-number coercion. UI work cannot invent incompatible DTOs. Add schema/contract tests and regenerate clients in the same change.

## Conventions

- Base `/api/v1`; UTF-8 JSON; camelCase fields; opaque UUID IDs; ISO-8601 UTC instants, separate IANA timezone where needed.
- `Money = {"amountMinor": 27000, "currency": "EGP"}`. No formatted money accepted as calculation input.
- Pagination `{items, nextCursor, hasMore}` with bounded `limit` default 20/max 100, allowlisted sorting and stable ID tie-breaker. Never silently truncate an order and label it complete.
- Error `{code,messageKey,fieldErrors:[{field,code}],requestId,retryable}`; use 400 malformed, 401 unauthenticated, 403 denied, 404 hidden/not found, 409 state/stock/version/idempotency conflict, 422 validation, 429 limits, 503 transient dependency unavailable. No stack traces or secrets.
- Mutations validate content type, body size, authenticated actor/scope, state, version and input allowlists. Ignore no unexpected privileged fields: reject them.
- Correlation request ID is generated/validated at ingress and echoed; trace IDs must not expose customer identity. Logs redact authorization/cookies, codes, addresses and provider payloads.
- Order creation and all financial commands require `Idempotency-Key` and a stable client request identifier. Store canonical body hash; same key+same body returns original outcome; different body returns `IDEMPOTENCY_CONFLICT`. Scope by actor+command.
- Versioned updates carry `expectedVersion` or `If-Match`; choose one per endpoint in OpenAPI. Stale update is 409, UI reloads and requests deliberate action.
- Customer web cookie sessions require CSRF tokens. Native bearer tokens require issuer/audience/expiry verification; CORS is an allowlist, never `*` with credentials.

## Core DTOs

| DTO | Required shape / key restrictions |
|---|---|
| MealSummary | id, kitchen{id,slug,name,ratingSummary}, title, photo{url,alt}, portionText, price, nextSlots[], availability; no kitchen private address |
| MealDetail | summary + description, ingredients, allergenCodes, allergenStatement, photos, reviewSummary, publishedRevision |
| SlotAvailability | id, startsAt, endsAt, timezone, cutoffAt, availablePortions, fulfillmentModes; availability advisory until submission |
| QuoteRequest | items[{mealId,quantity}], slotId, fulfillmentMode, addressId for DELIVERY; no client price/tax/commission fields |
| QuoteResponse | quoteId, expiresAt, kitchenId, items with snapshots, subtotal, deliveryFee, tax, total, cancellationPolicyVersion, acceptanceDeadlinePreview, warnings[] |
| PlaceOrderRequest | quoteId, clientRequestId, acceptedPolicyVersions, optional bounded fulfillmentNote; no status/role/customerId/price override |
| OrderView | id, publicNumber, state, fulfillmentMode, items, monetary snapshot, slot, acceptBy, events, allowedActions, cashState, supportCaseIds; actor-specific contact and handover fields |
| TransitionCommand | expectedVersion, reasonCode when required, optional bounded note; concrete route selects action |
| CashReport | amountMinor, currency, collectedAt, method=CASH, externalReference optional, expectedVersion; finance verifies separately |
| SupportCaseCreate | orderId, category, description, approved attachmentIds; ownership checked |

## Example: order creation and unknown response

```json
{
  "quoteId": "00000000-0000-4000-8000-000000000101",
  "clientRequestId": "00000000-0000-4000-8000-000000000202",
  "acceptedPolicyVersions": {"terms": "draft-test-1", "cancellation": "draft-test-1"},
  "fulfillmentNote": "Synthetic test order"
}
```

Use a persisted random UUID as the header key and client request ID before the first network attempt. On timeout query `GET /orders/by-request/{clientRequestId}` for the current customer. A found result wins; a 404 after uncertain commit is not proof the first request cannot still complete, so retry the original request with the original identifiers. Never create a new key to recover a timed-out order.

## Full command transaction

1. Authenticate and resolve actor; reserve/lock idempotency record by unique scope.
2. Load quote, verify ownership/expiry and versions using server time.
3. Revalidate kitchen/meal/slot/serviceability/phone and policy acceptance.
4. Lock meal-slot rows and cook-slot aggregate in deterministic order; enforce available quantities.
5. Insert immutable order/item/address/financial snapshots and allocations; increment stock counters.
6. Write state event, cash DUE record, audit and outbox events; persist idempotency result.
7. Commit. Return 201 with Location + OrderView. Replay returns same resource/outcome with an explicit replay header.

Use 409 `STOCK_CHANGED`, `PRICE_CHANGED`, `QUOTE_EXPIRED`, `SLOT_CLOSED`, `STATE_CONFLICT`, `VERSION_CONFLICT`, `KITCHEN_UNAVAILABLE` as distinguishable localized recoverable failures. Never partially create an order.

## Notifications and optional webhooks

Order history endpoint is authoritative. Poll visible active orders with bounded backoff; background/native refresh refetches history. Push is a hint with order ID/event ID and no sensitive address. A stale push cannot regress state.

Online payment/courier routes are conditional and not part of cash-first API acceptance. When enabled, webhook signature verification uses raw request bytes, replay window and event deduplication before side effects. Provider/browser redirect never marks payment captured. See [integration design](07-integrations.md).
