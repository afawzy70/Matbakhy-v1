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

## Cook applications and kitchens (MB-012, implemented)

- Any active account may keep one cook application (`GET`/`PUT /api/v1/cook/application`, `POST /api/v1/cook/application/submit`). Applying grants nothing. `PUT` replaces the private working draft (public name, bio, Egyptian contact mobile normalized to E.164, private location, pickup flag, up to 20 active service zones) and needs `expectedVersion` once the application exists (409 `VERSION_CONFLICT`). Drafts are editable only in `DRAFT` and `CHANGES_REQUESTED`; client-supplied state or other unknown fields give 400.
- Submit runs full validation (422 with `REQUIRED`, `PICKUP_OR_ZONE_REQUIRED` or `ZONE_NOT_SERVICEABLE` field errors) and stores an immutable revision snapshot. Database triggers reject changes to a submitted snapshot, a second decision on a revision, and deletion.
- Review (`cook.review`): `GET /api/v1/ops/cooks` (state, q, cursor, limit; drafts excluded; no private fields) and `GET /api/v1/ops/cooks/{id}` (submitted revisions with private fields; every read audited). `POST /api/v1/ops/cooks/{id}/decision` also needs recent MFA and an `Idempotency-Key`. It takes `START_REVIEW` (SUBMITTED to UNDER_REVIEW) or `APPROVE`/`REQUEST_CHANGES`/`REJECT` (from UNDER_REVIEW, reason 3–1000 characters), names the current `revision` (stale revision 409 `STATE_CONFLICT`) and `expectedVersion`. A reviewer cannot act on their own application (403, audited). Requested changes return to SUBMITTED on resubmission as a new revision; REJECTED is final until the reapplication policy is decided (O06).
- Approval creates the kitchen from the approved revision (public slug `kitchen-<random>`, zones from the revision). `KitchenDirectory` in `cooks.api` is the eligibility source for later stories: `canSell` only when ACTIVE, `canAcceptOrders` when ACTIVE or PAUSED, `canFulfillAcceptedOrders` unless CLOSED.
- `POST /api/v1/ops/kitchens/{id}/operating-state` (`cook.review` + recent MFA): PAUSE, SUSPEND, RESTORE with a reason and `expectedVersion`; invalid transitions 409; staff cannot change their own kitchen (403, audited). Operating state is separate from the application decision. Cook self-pause from settings (CK09) and affected-order review arrive with the cook workspace and orders (MB-019 onward).
- `GET /api/v1/kitchens/{slug}` (public) returns display name, description, the operational-verification label (not a food-safety certification), availability, pickup flag and delivery zones. It never returns the private location, contact phone, owner id or documents.
- Idempotency: `IdempotentCommands` (shared) with a PostgreSQL implementation stores the request hash and the result per actor, command and key for at least 24 hours. Same key and body replays the stored result; a different body gives 409 `IDEMPOTENCY_CONFLICT`; a failed command stores nothing.
- Documents (since MB-013): the draft takes up to 10 `documentIds` that must be APPROVED `COOK_DOCUMENT` uploads owned by the applicant (422 `DOCUMENT_NOT_APPROVED`); submission re-checks them and freezes them into the revision. Which documents are required is decision O06, so none is mandatory yet. Not yet implemented: the rating summary on the public profile (reviews, MB-024).

## Media uploads (MB-013, implemented)

- `POST /api/v1/uploads` (201) declares purpose, filename, content type, byte size and SHA-256 and returns a signed `PUT` ticket for a private key `uploads/<id>` (10 minutes). `MEAL_PHOTO`: JPEG or PNG up to 8 MiB, 320–8000 px per side, at most 40 MP. `COOK_DOCUMENT`: JPEG, PNG or PDF up to 10 MiB. Other types or sizes give 422. At most 10 open tickets and 60 tickets per hour per account (429). The filename is display-only.
- `POST /api/v1/uploads/{id}/complete` (owner only, otherwise 404) verifies the stored object: exact size, SHA-256, type sniffed from the bytes (must equal the declared type), image dimensions read from the header before decoding, a clean full decode (decoder warnings reject), PDFs without scripting/launch/embedded-file markers, then the malware scan. Failures end in REJECTED with a reason (`SIZE_MISMATCH`, `CHECKSUM_MISMATCH`, `TYPE_MISMATCH`, `UNSUPPORTED_TYPE`, `DIMENSIONS`, `UNREADABLE`, `ACTIVE_CONTENT`, `MALWARE`, `MISSING_OBJECT`) and the uploaded bytes are deleted; security-relevant rejections are audited. A scan that does not return CLEAN leaves the media QUARANTINED and returns 503 `DEPENDENCY_UNAVAILABLE` (fail closed; retry later). Completing before the bytes arrive is 409. Repeating completion returns the decided state.
- Approved meal photos get a public variant `media/<id>.jpg` re-encoded from decoded pixels (longest side at most 2048 px), which removes EXIF/GPS and appended payloads. Documents never get a public variant; the database rejects a public key on anything but an approved meal photo.
- `GET /api/v1/uploads/{id}`: the owner sees the state; an approved document also returns a signed read URL (5 minutes, `attachment`). A reviewer with `cook.review` can read a document only once it is part of a submitted cook application revision (audited as `access.media`); everyone else gets 404.
- Deleting is a service operation scoped to the owner (no public endpoint yet); it removes private and public objects and is refused for documents frozen into a submitted revision.
- Ports: `ObjectStorage` (S3 API adapter; local S3 emulator) and `MalwareScanner` (LOCAL/CI fake that flags the EICAR test signature). Staging and production refuse to start without real storage credentials, a public base URL and a real scanner (O07). Storage layout and required bucket policies: [infra/storage](../../infra/storage/README.md). The emulator enforces neither bucket privacy nor URL expiry, so those are verified against the production provider before live uploads.

## Meals and publication (MB-014, implemented)

- Cook routes (`/api/v1/cook/meals`) require an approved kitchen (otherwise 403) and act only on the caller's own kitchen; another cook's meal is 404. The kitchen is never taken from the request (unknown fields give 400).
- Content: Arabic and optional English title, description, portion and ingredients; an explicit allergen declaration (`CONTAINS` with codes from a fixed list of 14, `NONE_DECLARED` or `UNKNOWN`; codes only with `CONTAINS`); price as integer piastres 1–1,000,000 (fractional values are 400); up to 6 photos that must be the cook's own APPROVED `MEAL_PHOTO` uploads.
- `POST` creates a DRAFT (201). `PATCH` with `expectedVersion` merges fields (omitted unchanged, empty string clears optional text, `photos` replaces). Each real change creates a new immutable revision in `meal_revision` (database-enforced append-only); a no-op edit creates none. A published or paused meal must stay publishable after an edit (422 otherwise).
- `POST …/publication`: `PUBLISH` from DRAFT or PAUSED needs Arabic title, description, portion and ingredients, an allergen declaration, a price and at least one approved photo (422 lists each missing field) and an ACTIVE kitchen (409 `KITCHEN_UNAVAILABLE`); `PAUSE` from PUBLISHED; `ARCHIVE` is final. Archived meals are read-only and never deleted (database trigger); their revisions stay available for order history. A photo used by a non-archived meal cannot be deleted.
- `GET /api/v1/meals/{id}` (public, API-004) returns the core `MealDetail` DTO only for a PUBLISHED meal of a kitchen that can sell; anything else is 404. Arabic by default, English when `Accept-Language` prefers it and English text exists (field-by-field fallback to Arabic); `Vary: Accept-Language`. `allergenCodes` carries the declaration (`NONE_DECLARED`/`UNKNOWN` as explicit values). Until slots (MB-015) and reviews (MB-024) exist, `nextSlots` is empty, `availability` is `UNAVAILABLE` and rating counts are 0. No owner identity, private location, contact, status or version is exposed.
- `MealService.revision(mealId, revision)` returns the immutable snapshot that quotes and order items will copy (MB-017/MB-019). Discovery listing (API-003) is MB-016; meal moderation (API-071/072) is MB-023.

## Slots and capacity (MB-015, implemented)

- Cook routes (`/api/v1/cook/slots`) need an approved kitchen (403 otherwise) and act on the caller's own kitchen (another kitchen's slot is 404). A slot is scheduled as a local Cairo date with start and end times on that date (a slot cannot cross local midnight) and stored as UTC instants. A local time skipped by the spring daylight-saving change is 422 `NONEXISTENT_LOCAL_TIME`; a time repeated in the autumn needs `startOffset`/`endOffset` naming one of its two offsets (422 `AMBIGUOUS_LOCAL_TIME` or `OFFSET_NOT_VALID_FOR_LOCAL_TIME` otherwise). Only `Africa/Cairo` is accepted.
- Cutoff = start − `leadMinutes` (default 120, proposed pilot policy; 30–1440). Creation needs a future cutoff (409 `SLOT_CLOSED`/`CUTOFF_PASSED`), a start at most 60 days ahead and no overlap with another non-cancelled slot of the kitchen (409). Slots are created as DRAFT; OPEN needs at least one meal with capacity and a future cutoff.
- Capacity: `maxPortions` (1–500) bounds the slot aggregate and each meal capacity (0–`maxPortions`) bounds that meal; both levels keep `reserved + allocated + consumed ≤ capacity` (database checks). Lowering either below current obligations, or removing a meal that has obligations, is 409 `BELOW_OBLIGATIONS`. Capacity changes after cutoff are 409 `SLOT_CLOSED`. A meal can only be offered in a slot of its own kitchen and not when archived (422; a database trigger also refuses cross-kitchen rows).
- States: DRAFT→OPEN, OPEN↔PAUSED, OPEN/PAUSED→CLOSED, DRAFT/OPEN/PAUSED→CANCELLED; CLOSED and CANCELLED are final. Cancelling a slot that has obligations is refused for cooks (409) until the ops exception workflow exists (MB-019/MB-023). Orderable means OPEN and before cutoff by server time; the client clock is never used.
- `GET /api/v1/kitchens/{kitchenId}/slots` (public, API-006) lists OPEN slots before cutoff of a kitchen that can sell (otherwise 404), with remaining aggregate portions and the fulfillment modes the kitchen offers (PICKUP if enabled; DELIVERY if it delivers, or delivers to `zoneId` when given); filters `date`, `fulfillmentMode`, `zoneId`; keyset pagination by start time.
- `SlotDirectory` (availability API) gives quotes and discovery the orderability and per-meal remaining portions (bounded by both the meal and the slot aggregate). Reservation, allocation and consumption updates are MB-018/MB-019. The availability module receives kitchens and meals through the `SlotParticipants` port implemented by the catalog, so discovery can depend on availability without a module cycle.
