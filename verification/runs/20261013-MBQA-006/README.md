# QA repair MBQA-006: money amounts on narrow staff screens

Implementation-agent record (self-review, not an independent retest). Independent report: `verification/runs/20261013-IQA-d877a15/defects.md` on `qa/full-regression-d877a15-20261013` (not merged).

## Cause

`apps/operations-web/src/app.css`: `body { overflow-wrap: anywhere }` lets the browser break inside any word, which shrinks the minimum width of a value to one glyph. With `.summary { grid-template-columns: max-content 1fr }` a long label took the row and left about 10 px for the amount, so `EGP 390.75` became a column of digits. The statement table cells shrank the same way.

## Repair (`f8a0087`)

- `.summary` label column `minmax(0, max-content)`: labels shrink and wrap their words.
- `.summary dd, td { overflow-wrap: normal }`: an amount is one token (both locales join currency and digits with a no-break space), so it stays on one line; a table that no longer fits scrolls inside its existing focusable region (`.table-wrap`, `tabindex=0`), keeping values under their headers.
- No change to amounts, API, authorization or accounting.

## Regression test

`apps/operations-web/e2e/money-reflow.e2e.ts` (real local stack, part of `scripts/dev.sh e2e` / BROWSER-E2E): cook-a Statement and finance (ops-a with a test-only `finance.read` grant) for kitchen d1 after the cook workflow's completed orders. For each of 2 roles × 320/390/1440 px × Arabic/English (12 variations), plus 390 px at 200% root text size per role and locale: at least three amounts present; every amount spans exactly one line (Range client rects); no page-level sideways scroll; if the statement table scrolls, the region takes keyboard focus and arrow keys reach its end (RTL: left arrow).

| Run | Source | Result |
|---|---|---|
| Red | `cd6494b` test + unfixed CSS | FAIL: `cook ar 320: amounts split across lines` ([log](RED-browser-e2e.log)) |
| Green | `f8a0087` working tree | 6/6 browser workflows PASS incl. 12 variations ([log](GREEN-browser-e2e.log), [screens](screens/)) |

The red run also failed four other workflows. These were the implementation's own regressions from today's commits, fixed in `f8a0087`: the statement test still expected the commission note (the launch policy is now 0%, MB-050), and checkout chose the first radio on the page, which became the MB-053 payment choice before the address list loaded. Operations-layout and keyboard failed as a consequence.

## Observed while reviewing the screenshots

- Read-only finance staff saw two "Not allowed / Retry" cards in the MB-052 payment panels: fixed in `ec68cb9` (test red first).
- The finance Settlements list shows zero-amount `COMMISSION` entries under the 0% launch policy, with a "Create settlement batch" action. Not a money error (they are 0), but misleading for staff. Recorded as an MB-050 follow-up; not changed today.

Independent retest required: MBQA-006 stays FIXED_PENDING_INDEPENDENT_RETEST.
