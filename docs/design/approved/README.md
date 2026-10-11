# Approved-direction handoff for Claude

Start with [DESIGN.md](DESIGN.md). Open [index.html](index.html) directly, or serve the repository and visit `/docs/design/approved/`. Add `?customer=returning` to inspect the separate synthetic returning-user state. The default is a new visitor.

## Corrected in this reference

- Contextual filters actually appear, selection can be cleared and results reflect search/category/portion/price selections. Kitchen mode shows kitchens.
- Home → kitchen → dish navigation works. Browser back retains current discovery selections in this session.
- Quantity starts with decrease disabled, updates integer-minor-unit totals and is bounded by fixture capacity. Demo basket rejects excess quantities and confirms replacement across kitchens; cancelling preserves the previous basket.
- Cairo, warm palette, 44 px targets, minimum 13 px supporting text, safe-area spacing and enabled zoom are shared across screens.
- No external icon font dependency: inline SVG prevents corrupted ligature artifacts. Original supplied food-image URLs are retained; no invented kitchen portraits or trust claims.
- The brand strip wraps naturally instead of clipping its message. History is shown only in the explicit returning-user fixture.

## Integration instructions

1. Fetch `design/approved-terracotta` and inspect its diff against `implementation/mvp`. Integrate the scoped reference/docs changes into the active implementation workflow without discarding newer work.
2. Port the values to `packages/design-tokens/tokens.json`, regenerate its CSS using the existing generator and update web/native font loading to Cairo. Preserve token APIs and test contrast pairs; do not blindly replace production files with this static prototype. Existing package tokens still contain the superseded draft palette/font until that integration is performed.
3. Implement React/React Native components from this hierarchy: header, brand strip, intent cards, contextual filters, mode switch, food card, kitchen card and quantity bar. Connect existing API contracts; replace client fixture filtering/prices with server behavior.
4. Use the older `docs/design/prototype` only for transactional behavior: checkout, price/stock changes, pending acceptance, cook queues and operations. Restyle those journeys using this baseline; retain existing business rules and localization.
5. Do not mark design approval globally complete. Retain the repository approval/evidence gates and current implementation checkpoint. This handoff is not a competing execution-state file.

## Remaining screen work — no further Stitch dependency

| Surface | Next work |
|---|---|
| Home, kitchen, detail | Integrate these references; verify real data, unknown allergens, responsive and locale states |
| Cart, contact/OTP, checkout | Derive from the same components; show single kitchen/slot, fees, cash-first policy, unavailable lines and price-change review |
| Order list/status | Pending cook acceptance, permitted cancellation/request paths and handover semantics from existing contracts |
| Account/address/favorites/support | Complete existing screen specification; no new product scope |
| Cook and operations | Preserve task/queue/table layouts; apply approved typography/palette without forcing consumer food-card layouts |
| Desktop, English, native | Derive and verify separately; phone screenshots do not establish approval or native evidence |

The remaining transactional screens need a coherent implementation preview for owner review, not another visual-direction exercise. Owner approval of this homepage is not permission to silently change checkout policy.

## Prototype limits

This is an isolated reference, not production code or a complete app. Basket is memory-only; no checkout, account, order, address or schedule mutation is performed. Those actions identify their limited preview state. Fixtures are not real stock, prices, ingredients, schedules or customers. The client basket does not reserve stock. Cairo and photos load remotely; image fallback does not establish external asset availability or licensing. Self-host approved licensed assets during implementation.

## Verification

Run `node --check docs/design/approved/app.js`, `node docs/design/approved/check-dom.cjs` with jsdom available, and `node docs/design/approved/check.cjs` with Playwright/Chromium available. These check the scoped reference only. See [verification.md](verification.md) for actual results and environment instructions; no backend, CI, mobile or full UX gate success is implied.
