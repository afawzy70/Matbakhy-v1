# Independent defect register — pinned application

Application: **d877a1576cd942200fe0a53b5a19fee76b996025**. Product code untouched. Evidence from fresh FULL REGRESSION attempt6 and separately labelled TARGETED follow-ups. Historical findings are deduplicated; unfinished D13 client functionality is an implementation gap, not a regression.

## MBQA-006 — financial amounts and table values break inside numeric tokens on narrow staff web

**Severity:** Moderate (business information becomes difficult to read reliably; desktop workaround; no backend corruption observed). **Status:** NEWLY_REPORTED, reproducible on the pinned candidate; no regression claim against an earlier SHA. **Roles/surfaces:** owning cook Statement and permission-scoped finance web; Chromium154.0.8037.57,320/390px, English/Arabic. Native staff apps do not exist.

**Requirements:** BR-046/TC-046 require usable phone layouts, long text and mixed numbers; MB-022/025 deliver statement/finance views. Axe/semantic tables and a document scrollWidth check alone do not meet functional monetary readability. Definition: W-MONEY-REFLOW, SELF_REVIEWED, not owner-approved.

**Preconditions:** isolated known seed; kitchenA00000000-0000-0000-0000-00000000012d, cook-a and finance-a generated local identities; two completed synthetic orders from the actual connected pickup/delivery flows. No real cash/payment. At this run summary order total39075minor (390.75EGP), pickup24050minor and delivery15025minor. Current settled/refund states are synthetic accounting only; original amounts unchanged.

**Reproduction:**
1. Sign in as cook-a on operations web; open Statement with the default month-to-date range.
2. Set viewport320×1000, English; inspect Order value/cash/commission amounts and the order table.
3. Repeat at390px and Arabic. As finance-a, open Finance, enter kitchenAID, Show, repeat both widths/locales.
4. Compare1440px baseline and actual API amounts; inspect DOM monetary-token ranges and screenshots.

**Expected:** grouped decimal amounts remain readable as intact numeric tokens and intelligible currency groups; tables maintain readable header/data relationships or provide an accessible scroll/reflow layout. Whole words may wrap; amount digits/decimal fractions must not form a vertical glyph column.

**Actual:** at320px English, cook summary's money column is9.875px wide and `390.75` occupies6lines (cell255.94px tall); finance summary is9.859px wide and `240.50` occupies6lines. At390px English both roles split numeric tokens across2lines. Cook Arabic splits summary/table numeric tokens at320/390. Desktop both locales pass this targeted readability check. Finance Arabic320/390 passes the measured numeric-token criterion; do not inflate the defect across that variation.

**Reproducibility:**6/12 targeted role×width×locale variations FAIL,6PASS,0BLOCKED; matching original fresh-run screenshots independently inspected. Raw W-A11Y remains PASS for its actual axe/overflow/semantic assertions; this new finding corrects any broader usability inference.

**Evidence:** [per-variation geometry](evidence/money-reflow.json), [targeted results](targeted-layout/results.json), [cook320EN](evidence/W-MONEY-cook-a-320-en.png), [cook320AR](evidence/W-MONEY-cook-a-320-ar.png), [finance320EN](evidence/W-MONEY-finance-a-320-en.png), [desktop](evidence/W-MONEY-cook-a-1440-en.png). Layout test suite SHA is recorded per variation.

**Impact/workaround:** staff can misread totals, cash and commission on narrow phones or struggle to match an order row. Use a wider desktop viewport while awaiting repair. No order-price mutation or financial calculation defect demonstrated.

**Confirmed evidence/investigation area:** operations `app.css` uses summary `grid-template-columns:max-content 1fr`,100%-width tables without a minimum readable table width, and broad `overflow-wrap:anywhere`. Computed widths/line breaks show the long label leaves only9.9px for money. Investigate responsive summary/table rules; avoid changing money, auth or backend policies as a UI repair.

**Repair acceptance:** all12 W-MONEY-REFLOW variations show intact monetary tokens and usable table mapping; recheck long labels/order numbers, table keyboard scrolling/focus, ARRTL/ENLTR, zoom/text scaling and desktop; retain authoritative amounts. Then pin the pushed repair and run the full suite from a new seed, not only this targeted test.

## Historical findings — current independent disposition

| ID | Fresh web result | Native result / limitation |
|---|---|---|
| MBQA-001 language switch | PASS: actual request language and rendered home/meal/kitchen; en/ar direction and preserved basket | Native language/layout supplementary execution pending; no inherited webPASS |
| MBQA-002 unavailable selected slot | PASS: real competing reservation exhausts chosen slot while another positive; disabledAdd/no cart; legal release | Mixed-stock native variant not executed by one-slot fixtures; gap explicit |
| FG-001 basket edit/remove/estimate | PASS:33600→43150→31125→19100→empty, exactkitchen/slot, persistence/replacements | Native core basket/replacement and quantity correction executed; extra remove/lifecycle states pending validated controls |
| MBQA-003 web sign-out | PASS in fresh attempt6: reachable action, failed request retains session, provider logout completed, same-browserB requires credentials; pending warning cancel preserves originalrequest | Native original auth/logout/account switch independently executed; populated-cache variants remain a separate gap |
| MBQA-004 public support reply | PASS in fresh web: true support PUBLIC/STAFF_ONLY writes, own visible conversation/customer reply, foreign404/private filtering; resolved/closed states | Native public conversation test pending; Support.tsx lists case references without a detail route. Code inspection is not runtimeFAIL; do not close the native gap yet |
| MBQA-005 DRAFT Close | PASS: noClose inDRAFT; direct invalidClose409 unchanged; OPEN exposes legalPause/Close | Staff-native not implemented |
| OBS-001 recovery lookup network failure | PASS: actual lookup aborted, no uncaught rejection, originalkeys/pendingstate remain, same-order recovery | Separate native recovery independently executed; no cross-platform inference |
| Q1 | PASS: actualsynthetic10, UI clamps/API422 for11; same-runningbrowser reads lowered5, blocks invalidquote, correctsto5 | Android same-process PID proofPASS; iOS restored-lower-limitcorePASS, same-process controls pending rebuilt tests |

## Not product defects

- D13 informed old/new component comparison/attribution and repeated repricing/refresh-failure client UX (MB-049/TC-154) are newly approved and unfinished at this candidate: NOT_RUN implementation gap. Delivered stale refusal, transaction races/history/snapshot recovery independently tested. RQ-004 has been decided by the owner.
- Cold-stack first OAuth callback sometimes reached backend `/login`401; preflight recovered,3 subsequent targeted real sign-ins200. Cause not confirmed: environment/startup observation, no product regression verdict.
- QA language timing, OTP counter reuse/expiry, early navigation interrupting logout, same-session diagnostic overlap, asynchronous commission assumption, filter locator/control assumptions, Android generated-shell quoting and iOS selectors using an outdated test bundle: harness failures with preserved corrections. No expectation weakened to obtain green results.
- Screen reader, provider SMS/push, physical devices and deployed controls remain unavailable/unexecuted; no release gate approved.
