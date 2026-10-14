# Matbakhy — owner-selected terracotta baseline

## Authority and scope

On 2026-10-11 the owner explicitly selected the original, information-rich homepage supplied in this conversation, rejected the green direction, supplied the warm color specification and Cairo typography, and authorized minor corrections and publication to GitHub for Claude. This records that direction; it does not approve every product screen, trademark, logo, legal policy or release gate.

This directory is the visual reference for the next implementation iteration. Preserve its homepage hierarchy and card content. The adjacent `../prototype` remains useful for behavioral journeys, but its older colors, IBM Plex typography, discovery structure and approval questions do not override this owner direction. No new branding exploration is required.

The HTML reference is a maintainable reconstruction of the supplied three screens with shared CSS/JS, not a byte-for-byte Stitch export. It is isolated from product code. All meal names, prices, capacity, kitchens, availability and prior-order examples are synthetic, including the supplied photographs. Do not seed production with them.

## Locked palette

| Role | Hex |
|---|---|
| Page surface | #FFF8F6 |
| Cards | #FFFFFF |
| Container / high / low | #F9EBE6 / #F3E5E0 / #FFF1EC |
| Primary / primary accent | #982505 / #BA3D1D |
| On primary | #FFFFFF |
| Secondary / secondary container | #6B5148 / #F0E4DA |
| Tertiary | #754600 |
| Main / secondary text | #211A17 / #59413B |
| Outline / outline variant | #8D716A / #E1BFB7 |

No green decorative accents. Status must have text/icons, not color alone. The owner has not approved a dark theme. Keep any old dark-theme tokens explicitly draft until a derived version is reviewed.

## Typography and layout

- Cairo, Arabic RTL. English remains a required product locale; this supplied reference is Arabic only.
- Screen headings 18–20 px, card headings 16 px, descriptions 14–15 px, supporting labels at least 13 px; price and portions at least 14 px bold.
- All interactive targets at least 44 × 44 CSS px. Browser zoom stays enabled. Visible keyboard focus, semantic buttons/links, named dialogs and reduced-motion support.
- 390 px reference width, responsive down to 320 px. Do not constrain the production desktop site to a narrow phone column; derive the desktop layout using the same information hierarchy.
- 16 px page gutters, full-width vertically stacked food cards, approximately 192 px home-card images, subtle borders/shadows and 12–16 px radii.
- Neutral intent selection for a new visitor. Selecting an intent reveals contextual filters; never silently assume two people. Clear and empty-result paths are required.

## Locked homepage order

1. Compact sticky delivery-area/date header, basket and account shortcuts.
2. Warm brand strip: Matbakhy, home cooking, preorder label.
3. Three side-by-side tiles: meal for me/us, complement my table, prepare a gathering.
4. Contextual portion/category filters, including one person.
5. Dishes/kitchens switch, then search and filter action, then category chips.
6. Food cards: photo, dish name, kitchen/area, EGP price, description, portion and details action.
7. Nearby-kitchen preview.
8. Returning-customer section at its original position, only when real history exists.
9. Home / Orders / Account bottom navigation. Other existing functions such as favorites remain in product scope and need a reachable route, not an unsolicited fourth tab.

Kitchen profile and dish-detail screens inherit this system. Keep kitchen biography, preorder date, available/unavailable items, readable portion information, ingredients/allergen states, bounded quantity and visible total.

## Content and business integrity

- No fabricated reviews, approval badges, safety guarantees or artificial scarcity. “Approved kitchens” was removed from the discovery heading.
- `Tomorrow, 2–4` is a prototype fixture, not a production scheduling rule. Production displays real dated slots in Africa/Cairo, cutoff and fulfillment availability.
- Server remains authoritative for money, capacity and availability. One kitchen and one slot per basket/order. Require confirmation before replacing a basket.
- Exact piece counts, extras, certification and allergen declarations must come from the kitchen, not design generation. This reference intentionally shows an unknown allergy state; production publication must satisfy BR-011's declaration requirement.
- No multi-kitchen consolidation, online payment or new commercial policy is approved by these screens.
- Do not insert engineering caveats into the shipped customer experience; prototype caveats are documented here and shown only for actions outside this reference's scope.

## Acceptance boundary

The chosen visual direction/homepage structure is established. Kitchen/detail refinements are reviewable derivatives. Other journeys, English/desktop/native rendering, actual product integration and release checks remain their existing obligations. This handoff does not change story status or mark O05 fully resolved.
