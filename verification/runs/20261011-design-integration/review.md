# Design integration checkpoint (self-review, not independent)

Scope: owner design PR #2 merged into `implementation/mvp`; approved light palette and self-hosted Cairo in tokens and both web apps; customer web home, kitchen, meal, checkout and order status in the approved hierarchy; MB-029 automated checks started.

Observed in the browser workflow on the real local stack:
- Home, kitchen and meal pages at 320, 360, 390, 420, 768 and 1440 px in Arabic RTL and English LTR: no horizontal overflow, every visible control at least 44 px, correct direction.
- Checkout quote and order status at 320 and 390 px: same layout checks.
- axe-core WCAG 2.2 A/AA rules: no serious or critical findings on the customer pages above (390 and 1440 px) and on five operations screens (cook dashboard, orders, statement; staff catalogue/review queue; support cases). axe was sanity-checked separately: it flags a missing label (critical) and a missing document title (serious) on a test page.

Defects found by these checks and fixed today: inline title link under 44 px; radio, checkbox and number inputs under 44 px; Arabic label collision on the meal page; add-to-cart wrapping at 320 px; Latin digits in the remaining-portions text; raw allergen codes shown to customers.

Not covered (NOT_RUN): keyboard-only journeys, dialog focus restoration, screen readers, operations web at multiple widths and in English, tables with long values, native apps. Decisions needed: structured portion size and descriptions in discovery cards (data/contract changes).
