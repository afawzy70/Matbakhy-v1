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

## Authentication and sessions (MB-009, implemented)

| Client | Mechanism | Backend behavior |
|---|---|---|
| Customer and operations web | OIDC authorization code (with PKCE) via the backend: browser navigates to `GET /oauth2/authorization/keycloak`; provider redirects to `/login/oauth2/code/keycloak`; backend redirects to `WEB_POST_LOGIN_REDIRECT` | Server-side session stored in PostgreSQL (`spring_session`), cookie `MATBAKHY_SESSION` (HttpOnly, SameSite=Lax, Secure in staging/production), idle timeout `WEB_SESSION_TIMEOUT` (default 30 min) |
| Web mutations | `XSRF-TOKEN` cookie (readable by the SPA) echoed in the `X-XSRF-TOKEN` header | Missing or wrong token: 403, no state change |
| Web logout | `POST /api/v1/auth/logout` with the CSRF header | Deletes only this session; responds `{"idpLogoutUrl": ...}` so the app can also end the provider session |
| Native app | Authorization code + PKCE (S256) in the system browser against the public `matbakhy-mobile` client (redirect `matbakhy-dev://auth/callback` in development); `Authorization: Bearer <access token>` | Stateless; validates signature (provider JWKS), issuer, audience `matbakhy-api` and expiry; no cookies are read, so no CSRF token is needed |

- Every authenticated identity maps to one local `app_user` by (issuer, subject). Permissions come only from server-side state. Provider role claims (for example a realm `admin` role) grant nothing; staff grants arrive with MB-011.
- Errors use the standard body: 401 `UNAUTHENTICATED` (no or invalid credentials, expired session or token), 403 `ACCOUNT_DISABLED` (suspended account, no profile data returned), 403 `FORBIDDEN`, 403 `STEP_UP_REQUIRED` (staff operation without recent MFA: provider `acr` in `OIDC_MFA_ACR_VALUES` or an MFA `amr`, authenticated within `OIDC_STEP_UP_MAX_AGE`). API calls are never redirected to a login page.
- CORS: only `WEB_ALLOWED_ORIGINS` with credentials; any other origin gets 403 on preflight.
- Implemented operations: API-009 `GET /api/v1/me` and API-010 `PATCH /api/v1/me` (optimistic `expectedVersion`; 409 `VERSION_CONFLICT`; unknown or privileged fields give 400).
- Provider endpoints assume Keycloak's path layout under `OIDC_ISSUER`; `OIDC_BACKCHANNEL_ISSUER` sets the server-to-provider address inside container networks. The production identity provider is open decision O07. Staging and production refuse to start without `OIDC_WEB_CLIENT_SECRET`.
- Native token storage, refresh, state/nonce checks and logout on device are client responsibilities delivered and verified in P07 (MB-031); this story covers the provider contract and backend validation.

## Contact verification and addresses (MB-010, implemented)

- `POST /api/v1/me/phone/challenges` (201): accepts Egyptian mobile numbers in common forms (01x…, +20…, 0020…, spaces/dashes, Arabic-Indic digits) and normalizes them to `+201[0125]xxxxxxxx`; landlines and foreign numbers get 422. Requesting a number different from the stored one replaces it and clears verification (BR-003). Limits: one send per 60 s per account, 5 per hour per account and 5 per hour per number (429 `RATE_LIMITED`). The response never contains the code.
- `POST /api/v1/me/phone/challenges/{id}/verify`: 6-digit code, single use, 5-minute expiry, 5 attempts per challenge. Wrong, expired and used codes all return 422 `VERIFICATION_FAILED`; the 6th attempt returns 429; another account's challenge is 404. Codes are stored only as an HMAC with a server pepper (`PHONE_CODE_PEPPER`, required outside LOCAL/CI) and are never logged.
- Delivery uses the `PhoneVerificationSender` port. LOCAL/CI use an in-memory fake; staging/production refuse to start until an approved SMS provider is configured (O07).
- `CheckoutContactPolicy.requireVerifiedContact` (409 `CONTACT_VERIFICATION_REQUIRED`) is the check quotes/orders (MB-017) call before checkout.
- Addresses (`/api/v1/me/addresses`): owner-only list (max 20 active), create (201), `PATCH` with `expectedVersion`, `DELETE` soft-archives (historical snapshots stay intact). Another account's address id returns 404 with no data. The zone must be an active service zone (422 `ZONE_NOT_SERVED`); optional latitude/longitude must come as a pair and never override the zone.
- `POST /api/v1/me/deletion-requests` (`confirm: true`, `policyVersion`): records the request and disables account access immediately; repeating returns the same open request. Purge/anonymization follows the retention policy once approved (O06, open).

## Staff permissions and audit (MB-011, implemented)

- Permissions are granular grants in `user_grant` (`staff.grant`, `audit.read`, `cook.review`, `content.moderate`, `order.*`, `support.case`, `finance.*`, `config.*`, `integration.ops`), optionally expiring and revocable, with history kept. They are re-read on every request, so a revoked or expired grant stops working at once, even for an unexpired token. Provider role claims are never used.
- `GET /api/v1/ops/staff` (staff.grant). `POST /api/v1/ops/staff/{userId}/grants` and `/grant-revocations` need `staff.grant` plus recent MFA (403 `STEP_UP_REQUIRED`). Granting to yourself is 403, unknown permissions 422, a duplicate active grant 409, and revoking the last active administrator 409.
- First administrator: set `STAFF_BOOTSTRAP_SUBJECT` to the provider subject of the intended administrator. On that identity's first request, while nobody holds `staff.grant`, it receives `staff.grant` and `audit.read` (audited). Unset it afterwards.
- Object scope: `AccessPolicy.requireOwnerOrPermitted(ownerId, permission, type, id)`. The owner passes; staff pass only with the named permission, and that access is audited; everyone else gets 404. Kitchen, order and case resources use it as they are built (MB-012 onward).
- `audit_event` is append-only (database triggers reject UPDATE, DELETE and TRUNCATE). Reasons and details are redacted (tokens, codes, secrets, phone numbers, emails). Authenticated denials of protected operations are recorded with outcome DENIED. `GET /api/v1/ops/audit` (audit.read) lists newest first with keyset pagination and filters by actor, object and action. The CSV export (API-095) is not implemented yet.
