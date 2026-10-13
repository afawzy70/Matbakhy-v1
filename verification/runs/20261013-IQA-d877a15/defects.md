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

## MBQA-007 — existing native favorite opens with an incorrect Add action

**Severity:** Moderate: the customer cannot remove an existing favorite through its meal-detail action without first performing a misleading Add; removal from the Favorites list is an alternative UI path (independently confirmed on Android; iOS list-removal path not exercised). No pricing/inventory/data-loss effect demonstrated. **Status:** NEWLY_REPORTED on the pinned application; no regression claim. **Role/platform:** customer iOS, iPhone16e simulator iOS26.2. Shared Android code is an investigation lead; the same detail-reopening variation is NOT_RUN on Android and is not inflated into another confirmed failure.

**Application:** d877a1576cd942200fe0a53b5a19fee76b996025. **Suite:** bc1018ef7bce660c312be3c984207ebfa39a65df; independent run38385888133/job115257457104. **Requirement:** delivered MB-028/032/TC-132 customer favorites; N-FAVORITES defines durable own add/list/remove. The action must reflect the actual saved relationship, not only the last mutation on one component instance.

**Preconditions:** real fresh customer-a system-browser authentication, native seed One12000/meal00000000-0000-4000-8000-00000000a0b1/kitchenD1/slot51. No simulated authentication, changed token lifetime or mocked favorites backend.

**Reproduction:**
1. Open Native Dish One from Discover and press Add to favorites. Observe Remove from favorites after success.
2. Back to Discover, open Favorites and verify Native Dish One appears from the actual own favorites read.
3. Press that card's actual **Discover** button, opening the Meal screen.
4. Inspect the favorite action. Expected Remove from favorites for the saved meal; actual **Add to favorites**.

**Evidence/actual:** [sanitized native UI hierarchy and executed markers](native-reviewed-latest.json), public meal screen identifier `meal`, titleNativeDishOne/EGP120.00, and visible Add to favorites button at y664; required Remove action absent. Actual favorite add/list/route preceded this assertion in the compiled test. Fresh G authentication, same-process Q1/edit/remove/lifecycle all executed; this observation has a loaded public Meal screen, unlike the earlier generic load-error/OBS-002. Do not attribute it to token expiry without evidence. No safe screenshot was exported from the failing bundle; the actual public hierarchy is preserved.

**Reproducibility:** one verified independent fresh-run reproduction; the next unchanged-application run retains the original assertion for confirmation. Android list removal is a different path: its failure snapshot showed No favorite dishes yet, revealing a wrong harness assumption about subsequently seeing a Meal-only Add control. It is not evidence of failed Android persistence or an independently reproduced Android detail bug.

**Investigation area (separate from runtime proof):** shared native Meal initializes local favorite state false when opened; the Favorites→Meal navigation does not initialize it from the saved relationship. Inspect hydration/refetch on focus and account changes. Do not alter backend favorite ownership or weaken the Remove assertion.

**Repair acceptance:** after real add→list→actual meal navigation, show Remove, perform removal and verify the own list/API relationship disappears after refresh/cold reopening; also test direct reopening of an existing favorite, AR/EN and account switching on Android and iOS separately. Keep the list-removal workaround working. Pin a published repair and run a new clean full campaign.

## MBQA-008 — Arabic native labels change but the root keeps LTR placement

**Severity:** Moderate, inconsistent Arabic reading/navigation orientation; no financial/state corruption shown. **Status:** NEWLY_REPORTED, no regression claim. **Role/platform:** customer Android API35 emulator, actual screenshot320×640. iOS pixel-layout variation is BLOCKED, not independently failing.

**Application:** d877a1576cd942200fe0a53b5a19fee76b996025. **Suite:** ad752f8a42aa2dd82bb9e80ad96473e460da3991. **Run:**38389933951/job115270939945, screenshot captured11:48UTC. **Requirements:** BR-046 Arabic RTL/English LTR; MB-031/TC-131 delivered shell language/direction; N-EDIT-LAYOUT's independent pixel inspection. This is delivered default-direction behavior, not unfinished MB-033 scaling/push.

**Reproduction:** launch the actual Android app, open Account in English, switch using العربية. Verify Arabic labels, then inspect account header/body alignment and bottom navigation. Expected Arabic root placement/mirrored navigation; actual account header/body remain left-placed, with Discover at far left and Account far right in the English LTR order. The driver's label-switch PASS is retained for that narrower assertion; it does not close the visual failure.

**Evidence:** [actual unedited native screenshot](native/android-38389933951/android-arabic-account.png), [explicit independent visual review](native-rtl-review.json), executed locale marker in the native log extract. Only one Android visual reproduction verified. No iOS failure inferred from shared code.

**Impact/workaround:** Arabic navigation/body positioning remains inconsistent despite translated labels; use English for the existing LTR layout while awaiting repair. **Investigation area:** native navigation direction and content alignment/stretch under the locale; inspect React Navigation and body/container direction handling, rather than translating strings again. The actual header is translated; this is a placement finding, not an untranslated-header claim.

**Repair acceptance:** independently inspect EN/AR screenshots for mirrored root/header/tab placement and readable mixed Latin/Arabic text, quantities/prices, keyboard/scroll/safe areas. Preserve basket, session and pending-request state when switching. Execute Android and iOS separately on a newly pinned repair; complete fresh full regression before any overall pass claim.

## Historical findings — current independent disposition

| ID | Fresh web result | Native result / limitation |
|---|---|---|
| MBQA-001 language switch | PASS: actual request language and rendered home/meal/kitchen; en/ar direction and preserved basket | Android actual labels switched but root RTL fails MBQA-008; iOS later layout blocked; no inherited webPASS |
| MBQA-002 unavailable selected slot | PASS: real competing reservation exhausts chosen slot while another positive; disabledAdd/no cart; legal release | Mixed-stock native variant not executed by one-slot fixtures; gap explicit |
| FG-001 basket edit/remove/estimate | PASS:33600→43150→31125→19100→empty, exactkitchen/slot, persistence/replacements | Native actual4/480/remove/empty and before/after background/cold state verified on both; exact Keep-slot/native language-state gaps explicit |
| MBQA-003 web sign-out | PASS in fresh attempt6: reachable action, failed request retains session, provider logout completed, same-browserB requires credentials; pending warning cancel preserves originalrequest | Native original auth/logout/account switch independently executed; populated-cache variants remain a separate gap |
| MBQA-004 public support reply | PASS in fresh web: true support PUBLIC/STAFF_ONLY writes, own visible conversation/customer reply, foreign404/private filtering; resolved/closed states | Android own case listed/tapped, public thread unreachable (runtimeFAIL); iOS public-thread BLOCKED despite real creation/cancellation PASS, not inferred from shared code |
| MBQA-005 DRAFT Close | PASS: noClose inDRAFT; direct invalidClose409 unchanged; OPEN exposes legalPause/Close | Staff-native not implemented |
| OBS-001 recovery lookup network failure | PASS: actual lookup aborted, no uncaught rejection, originalkeys/pendingstate remain, same-order recovery | Separate native recovery independently executed; no cross-platform inference |
| Q1 | PASS: actualsynthetic10, UI clamps/API422 for11; same-runningbrowser reads lowered5, blocks invalidquote, correctsto5 | Android unchangedPID and iOS continuous-runningForeground lower-limit refresh independently verified; availability-below-policy fixture remains blocked |

## Not product defects

- D13 informed old/new component comparison/attribution and repeated repricing/refresh-failure client UX (MB-049/TC-154) are newly approved and unfinished at this candidate: NOT_RUN implementation gap. Delivered stale refusal, transaction races/history/snapshot recovery independently tested. RQ-004 has been decided by the owner.
- Cold-stack first OAuth callback sometimes reached backend `/login`401; preflight recovered,3 subsequent targeted real sign-ins200. Cause not confirmed: environment/startup observation, no product regression verdict.
- QA language timing, OTP counter reuse/expiry, early navigation interrupting logout, same-session diagnostic overlap, asynchronous commission assumption, filter locator/control assumptions, Android generated-shell quoting and iOS selectors using an outdated test bundle: harness failures with preserved corrections. No expectation weakened to obtain green results.
- Screen reader, provider SMS/push, physical devices and deployed controls remain unavailable/unexecuted; no release gate approved.
