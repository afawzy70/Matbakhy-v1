# UX review package (MB-004) — DRAFT, approval PENDING

> **Owner direction update, 2026-10-11:** Use [the terracotta/Cairo reference](approved/DESIGN.md) and [Claude handoff](approved/README.md) for visual implementation. The owner selected the original rich-card homepage and rejected green. The older prototype below remains a behavioral reference; its visual-direction questions, font and colors are superseded. Whole-product UX approval remains pending.

A clickable, static prototype of the Matbakhy critical journeys for owner review. Every kitchen, meal, price, address and policy in it is synthetic. It illustrates screen structure, content and states; it is not the product, and server rules (quotes, stock, transitions) are only simulated.

Visual approval status: **PENDING** — see [approval record](approval.md). No approval has been given or inferred.

## How to review

1. `pnpm install` at the repository root (provides the font files).
2. Open [`prototype/index.html`](prototype/index.html) in a browser. Use the language button (العربية / English) and the Customer / Cook / Operations areas. Narrow the window to about 390 px to see the phone layout.
3. Or regenerate every screenshot: `pnpm --filter @matbakhy/design-tokens ux:journeys <output-dir>` (Chromium). The published set from the tested commit is in `verification/runs/20261011-MB-004/ux/`.

## Journeys covered (each executed in Arabic RTL and English LTR at 390 px and 1280 px)

| # | Journey | What to check |
|---|---|---|
| 1 | Discovery in a served zone; empty state in an unserved zone | Meal cards show kitchen, review count, EGP price and availability; empty-state wording |
| 2 | Sold-out meal | "Add" disabled with an explicit sold-out label |
| 3 | Delivery checkout → place order → "checking your order" → pending acceptance → accepted → preparing → ready (handover code) → on the way → received | Server-quote breakdown (subtotal, delivery fee, tax, total), cash note, 15-minute acceptance deadline, policy acceptance required, uncertain-response message, order timeline, handover code shown only to the customer |
| 4 | Pickup checkout | No delivery fee; pickup wording |
| 5 | Stock changed before submission | Warning and disabled submit; the customer must review the basket |
| 6 | Cook order queue: accept one order, reject another within the deadline | Deadline chips, accept/reject outcomes, "portions released" on reject |
| 7 | Operations: approve a kitchen application; open a review case for a cash discrepancy | Private-document count without exposing documents; original amounts never edited |

Native Android and iOS screens follow the same structure at phone width. They need native review in P07; this web prototype does not count as native verification.

## Design decisions (draft, for review)

- Arabic is the default locale; the language toggle mirrors layout direction. Directional layout uses CSS logical properties. Amounts, order numbers, codes and mixed-script text are isolated with `<bdi>`, and EGP amounts are formatted from integer minor units with `Intl` (Arabic-Indic digits in Arabic).
- Brand values, colors, type and spacing come only from the draft tokens (MB-003). Status is never conveyed by color alone (text label plus timeline position).
- Trust copy says "operationally reviewed (not a food-safety guarantee)", and allergen text distinguishes "no allergens declared" from "unknown". There are no ratings for kitchens without reviews and no fabricated testimonials or countdowns.
- Checkout always shows the authoritative-quote breakdown and states that the server sets the final price; there is no client-side price authority.
- The uncertain submission state tells the customer not to place another order (idempotent recovery), matching the API contract.
- Touch targets are at least 44 px; focus outlines are visible; the spinner respects reduced motion.

## Known gaps for the owner to weigh

- Placeholder photo blocks instead of real food photography (O05 brand direction).
- Draft legal and policy text; real terms, privacy and cancellation wording need owner-approved content (O06).
- No map or geolocation; zones are list-based per the MVP data model.
- Ops screens show one representative queue each, not the full backoffice.

## Bounded review questions

1. Is the overall visual direction (warm palette, IBM Plex Sans Arabic, card layout) acceptable as the basis for P06 UI work?
2. Is the checkout breakdown and the cash/acceptance-deadline wording clear in Arabic?
3. Should discovery lead with meals (current) or with kitchens?
4. Any required changes to the brand name or wordmark draft before trademark clearance (O05)?
