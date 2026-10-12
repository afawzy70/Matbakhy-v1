# Q1: maximum quantity per order line — decision note (proposal, not approved)

Status: **OPEN, owner decision needed.** This note recommends an approach. It does not change the commerce policy, the contract or any behaviour. Nothing here is owner approval.

## The conflict

| Source | What it says |
|---|---|
| `docs/product/05-screen-specification.md` (proposed defaults) | "quantity 1–20 per line; max 20 lines/cart. Configure and match client/server limits." |
| `docs/product/03-business-rules.md` BR-019 | The server calculates and validates; client values are not trusted. |
| Commerce policy (`commerce_policy.max_item_quantity`, versioned and MFA-gated, O04) | The server refuses a line above the **active** policy's limit. The synthetic local/CI policies use **10**. The real value is pending O04. |
| Contract `QuoteItemRequest.quantity` | `maximum: 99`, plus the note "Upper bound is configured server-side and may be lower". No endpoint tells clients the active limit. |
| Customer web and native app | The quantity stepper allows up to **20**, or the slot's remaining portions if fewer. |

**Effect today.** With the synthetic policy, a customer can select 11–20 portions on a meal page. They are then refused with a validation error when the quote is calculated. No order is wrong, because the server is authoritative, but the screens do not match the limit ("quantity cap clear", CW03).

## Options

| Option | What changes | Pros | Cons |
|---|---|---|---|
| A. **Expose the active limit to clients (recommended)** | The server publishes the active policy's per-line limit and the cart line limit; web and native cap the stepper at min(limit, remaining portions) | One source of truth; follows policy changes without an app release; keeps O04 free to choose any value | Small contract and API addition; implements the planned public configuration endpoint |
| B. Fix the policy limit at 20 | The owner approves a policy with `max_item_quantity = 20` | No code change | Clients would silently drift again if the policy ever changes; native apps cannot be updated instantly |
| C. Lower the client cap to a constant | Hard-code 10 (or another value) in the apps | Quick | Duplicates a commercial setting in two apps; breaks when the policy changes |

## Recommendation: option A, through the already-planned API-001

The contract already lists `GET /api/v1/configuration/public` (API-001, public, not yet implemented). It is meant for "locale, zones summary, enabled fulfillment modes, support/public policy versions". Its `PublicConfiguration` schema is still marked `PENDING`.

**Proposed addition to the schema:**

```yaml
orderLimits:
  type: object
  required: [maxQuantityPerLine, maxLinesPerOrder, policyVersion]
  properties:
    maxQuantityPerLine: { type: integer, minimum: 1, maximum: 99 }  # active commerce_policy.max_item_quantity
    maxLinesPerOrder:   { type: integer, minimum: 1 }               # server constant (20)
    policyVersion:      { type: integer, minimum: 1 }
```

**Behaviour:**
- Clients cap the quantity stepper at `min(maxQuantityPerLine, slot.availablePortions)`.
- When the configuration cannot be loaded, clients fall back to the slot's portions and the server's refusal. The quote stays authoritative (BR-019).
- A policy change affects only new quotes, as today.

## Affected artefacts (if approved)

| Area | Change |
|---|---|
| `contracts/openapi.yaml` | Type `PublicConfiguration` (at least `orderLimits`); `x-implemented: true` for API-001 |
| `contracts/endpoints.json` | Mark API-001 implemented; route-parity test (`ContractRoutesTest`) |
| `packages/api-client` | Regenerate; `pnpm contract:check` |
| Backend `orders` module | Public read of the active policy limit, plus a controller for API-001 |
| `apps/customer-web/src/Meal.tsx` | `maxQuantity(slot)` uses the published limit |
| `apps/customer-mobile/src/screens/Meal.tsx` | The same |
| Docs | `docs/engineering/03-api-contract.md`; screen-specification note on the cap |

## Acceptance tests to write first (red, then green)

1. **Backend integration.** `GET /api/v1/configuration/public` without authentication returns the active policy's `maxQuantityPerLine` and `policyVersion`. After a new approved policy version is activated, it returns the new values. It never exposes commission, tax internals or staff-only fields.
2. **Backend regression (exists).** A quote above the active limit is refused with `VALIDATION_FAILED` (`QuoteIntegrationTest`, "above policy maximum").
3. **Web unit.** With `maxQuantityPerLine = 10` and 15 remaining portions, the meal stepper stops at 10 and "Add" sends at most 10. With 3 remaining portions it stops at 3.
4. **Native unit and UI.** The same as test 3 on the native meal screen.
5. **Contract.** The registry and the served routes match; the generated client has no drift.
6. **Browser and native journeys.** Selecting the maximum allowed quantity produces a quote without a validation error.

## Decision needed from the owner

- Approve option A, B or C. If A, it would be implemented as a small story under the configuration area; it touches the public contract.
- Independently, set the real `max_item_quantity` under O04. Synthetic policies keep 10 until then.
