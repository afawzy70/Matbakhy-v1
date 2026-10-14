# Implementation preview for owner review — approved light terracotta/Cairo system (2026-10-12)

These are **rendered screenshots of the running product** on a throwaway local stack with **synthetic data**: kitchen "مطبخ تجريبي", meal "كشري تجريبي", a solid-colour synthetic photo, and synthetic addresses and phone numbers. They were captured by the browser workflow (`scripts/dev.sh e2e` with `E2E_SCREENSHOT_DIR`) at source revision `7ad6c15` (application code; later commits only add documentation and evidence). They are review material, not approval. O05 (whole-product visual approval) stays open until the owner reviews them.

What to review:

| Screen | Phone (390 px, Arabic) | Desktop (1440 px, derived layout) |
|---|---|---|
| Home (approved hierarchy; D11: no portion filter; D12: description on cards) | `home-390.png` | `home-1440.png` |
| Kitchen page | `kitchen-390.png` | `kitchen-1440.png` |
| Meal page (quantity bounded by remaining portions, visible total) | `meal-390.png` | `meal-1440.png` |
| Cart and checkout with server quote | `checkout-390.png` | `checkout-1440.png` |
| Order status (handover code, timeline) | `order-status-390.png` | `order-status-1440.png` |
| Order list | `order-list-390.png` | `order-list-1440.png` |
| Account (profile, favorites, inbox, support, addresses, deletion) | `account-390.png` | `account-1440.png` |
| Home and meal in English (LTR) | `home-en-390.png`, `meal-en-390.png` | — |
| Cook workspace and staff screens (existing layouts, approved palette and Cairo only) | `ops-cook-dashboard-390.png`, `ops-staff-catalogue-1440.png` | |

Notes for the reviewer:
- **Desktop is derived, not approved:** the same hierarchy in a readable column, with the Home / Orders / Account navigation under the header instead of the phone bottom bar.
- **Full-page captures and fixed bars:** in full-page screenshots, the sticky header and the phone bottom bar can appear over the middle of a long page. That is a capture artifact; on screen they stay at the top and bottom.
- **Dark theme:** the derived dark palette is not approved and is not the default. These screenshots use the light theme.
- **Synthetic-policy notice:** the "synthetic policy (test environment)" notice in checkout appears only with the local/CI synthetic commerce policy.
- **Cook and operations screens:** they keep their task/queue/table layouts, per the design handoff.
