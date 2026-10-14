# MB-029 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Observed (browser, real local stack)
- Layout matrices (TC-046, TC-129): customer pages at 320-1440 px in Arabic RTL and English LTR; transactional screens at 320/390/768/1440 px; every cook and staff operations section at 360/390/768/1440 px in both directions. No page overflow, 44 px controls, tables scroll in their own container, long unbroken values wrap.
- axe-core WCAG 2.2 A/AA: no serious or critical findings on those pages at 390 and 1440 px. axe was shown to detect a missing label and title on a test page.
- Keyboard-only (TC-047, partial): customer search, meal, slot, add to cart, replacement prompt (focus moves in, Escape closes, focus returns to the add button), cart, sign-in, pickup, quote, terms and order placement; cook sign-in and section switching. Every Tab stop has a visible focus indicator; a mutation removing focus outlines failed the test.

## Run history (kept visible)
Failures on the way, all fixed in code (none by loosening a check): the long synthetic title exceeded the 120-character rule (test setup); undersized dashboard/order/case buttons and checkboxes; statement table not keyboard-scrollable; date/time inputs and fieldsets forcing overflow; unbroken words; the order-status card overflowing at 320 px; inbox mark-read buttons; a hash-navigation language carry-over and a journey timeout in the test itself (fixed by reloading and by splitting the matrix into its own test).

## Not covered
- **Screen readers: NOT_RUN.** No screen reader (NVDA, JAWS, VoiceOver, TalkBack, Orca) is available in this environment. TC-047 is therefore BLOCKED and MB-029 is not DONE.
- Manual assistive-technology review, native apps (P07), contrast of the unapproved dark palette.
