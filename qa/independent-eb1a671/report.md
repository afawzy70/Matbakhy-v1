# Independent QA handoff — browse and basket

**Application tested:** `eb1a6717b3304d3a02e6a46d06c77ed5d43ac144` from remote `implementation/mvp`, resolved before cloning. **QA branch:** `qa/independent-browse-basket-eb1a671-20261012`. Execution: 2026-10-12 UTC. Report-only; no product code, release gates, implementation checkpoints or approved decisions were changed. No merge, deployment, paid testing service, credit use, or messages to other people.

**Reviewed outcomes: 2 PASS, 3 FAIL, 5 BLOCKED, 0 NOT_RUN cases.** This includes partially executed cases: all four replacement identity/state paths passed, but their total subchecks are blocked by the absent basket subtotal. It does not mean five cases were never attempted. The raw runner reported 7 PASS / 3 FAIL; independent assertion review and supplementary execution lower whole-case results where evidence is incomplete. See [reviewed results](evidence/reviewed-results.json), [raw results](evidence/results.json), and [supplementary reproductions](evidence/supplementary.json).

Two confirmed runtime defects, one unfinished basket feature group, and one unresolved accessibility expectation are recorded below. This is not release approval or a claim that web/native applications are bug free.

## Baseline and independent method

- Read `AGENTS.md`, `CLAUDE.md`, delivery protocol/publication/daily policies, confirmed context, scope, decision register, business rules, customer screen acceptance and design approval. Latest user instruction limits work to QA and permits a separate QA branch, overriding implementation-agent publication/checkpoint directions.
- Initial ten cases were frozen in commit `2322b64` before inspecting product code, developer test bodies, or Kane findings. [Initial cases](cases.md) preserve requirements, common preconditions, exact steps and expected subchecks. Scenario acceptance text was read; its pre-existing PASS labels were not trusted.
- All application builds and browser runs use the pinned revision. Checkout includes only QA additions after it. No later candidate HEAD was followed. The full product diff must remain empty outside `qa/independent-eb1a671/`.
- Applied [react-ui-bug-checker](skill://user/6a7db41bdb8c8191aa21d9a4a12035dc/react-ui-bug-checker/SKILL.md) in REPORT-ONLY mode: runtime reproduction, API/state/DOM/style inspection, screenshots, console/network examination, adjacent availability/error/layout checks. Its linked `references/ui-audit-checklist.md` was unavailable on two tool reads; the main skill loaded. No table screen was in this journey. This is a bounded affected-screen audit, not the complete inaccessible reference checklist or an application-wide audit.
- Used the cloud runtime skill for network/Docker setup and CA trust. Used repository-pinned Playwright 1.56.1; no third-party skill installed, no paid service used.

## Environment and setup evidence

Separate clone `/workspace/matbakhy-qa`, dedicated Compose project `matbakhy-iqa-eb1a671`, dedicated PostgreSQL volume/network and local synthetic catalogue. Existing containers were absent before setup. Web **25173**, API **28080**, PostgreSQL **25432**, identity **28180**, storage **29090**, mail UI **28025**, mail SMTP **21025**. Generated local credentials and identity configuration stay in `/workspace/qa-tools/stack`, outside Git. The web allowed origin/return URL is changed only by a QA Compose override.

Node **24.21.0**, pnpm **10.34.6**, checksum-verified Temurin JDK **21.0.12.1**, Maven wrapper **3.9.11**, Docker **28.4.0**, Chromium **154.0.8037.57**, Linux. Vite and real Spring Boot API started; API health UP, Flyway applied 24 migrations through V22. Browser loaded title `مطبخي` with Arabic RTL. No login was needed for the public basket journey; cook identities are local synthetic fixture rows, not real accounts. Identity/mail/provider end-to-end behaviour was not tested.

Setup failures were diagnosed as environment/QA failures, not product defects:

1. Default sandbox blocked proxy/socket access; permitted network access enabled Git/Docker.
2. npm/Maven default caches were outside writable paths; redirected caches into the QA tools directory.
3. The system had a Java 21 runtime but no matching compiler; downloaded a checksum-verified full JDK. Its Maven HTTPS trust needed the system CA trust store; TLS verification remained enabled.
4. Buildx wanted a read-only home directory; redirected only `BUILDX_CONFIG`, preserving Docker credential configuration.
5. Restrictive QA umask produced a mode-600 jar, unreadable to the non-root container user. Changed the generated jar to 644 and rebuilt the unchanged Dockerfile. No tracked product file changed.

Executed `./mvnw ... -DskipTests package`: BUILD SUCCESS, used only for startup; developer tests were intentionally not used as correctness evidence. Workspace install used the frozen lockfile. The initial fixture `A3` capacity=0 was **unoffered/UNAVAILABLE**, not true SOLD_OUT. Supplementary execution explicitly exhausted positive capacity to verify SOLD_OUT; no false stock-test PASS is inferred from capacity zero.

The first harness attempt used an incorrect search button name and trailing-space-sensitive description comparisons, and unroute handling failed. Attempt two exposed missing preconditions in paused/basket scenarios. Preserved attempt records identify these failures. The final run corrected locators, whitespace normalization and fixture preconditions; it did not change approved expectations to match the product. English catalogue mismatch persisted after network idle and was reproduced in three fresh contexts.

## Case execution record

Preconditions and steps in [cases.md](cases.md) apply to every row. The exact synthetic IDs, descriptions, prices and capacities are in [fixtures.json](evidence/fixtures.json). Each evidence JSON records actual viewport, language, DOM text/controls and basket; raw and reviewed results record expected/actual assertions and outcomes. `A/S1` means kitchen A / `slots.A1` in that manifest; `A/S2` means `slots.A2`.

| Case / requirement | Specific precondition and executed steps | Expected result | Actual result and reviewed status | Evidence |
|---|---|---|---|---|
| IQA-01 — CW01/02, BR-016/017/019, D11 | Fresh anonymous context; load AR browse, select English, open filters/select test zone, search B1, reset | Localized eligible cards; correct kitchen/EGP; fresh server requests | **FAIL MBQA-001:** English chrome/LTR, Arabic API content after switch. New route request recovers; zone/search/reset, A/B labels and 120.25/95.50/80.25 EGP prices pass. Paused kitchen excluded. | [language screenshot](evidence/IQA-01-language-switch.png), [search](evidence/IQA-01-search.json), network.json, MBQA-001-repro-1/2/3.png, supplementary.json |
| IQA-02 — CW03/04, BR-011/014/017 | Open A1 detail, then its kitchen link | Own description/portion/ingredients, explicit unknown allergies, 120.25 EGP, open dated slots; public operational kitchen identity | **PASS:** matching real API/fixture content; only S1/S2 offered, closed/past absent; 3 portions each; no private contact/location in public kitchen response. Approval says operational verification, not food-safety certification. | [meal](evidence/IQA-02-meal.png), [kitchen](evidence/IQA-02-kitchen.png), results.json/network.json |
| IQA-03 — D11/D12 | Browse own short/long descriptions; open intent budget; inject `description:null` only at discovery response boundary | Own published text, clean omission; no structured one/two-person filter or substitute sorting | **PASS within stated boundary:** real descriptions match; injected null cards omit paragraph cleanly; budget-only intent choices and normal price/newest sort. Null publication not tested/allowed by this check. | [null contract screenshot](evidence/IQA-03-null-contract.png), results.json, RQ-001 |
| IQA-04 — CW03/05, BR-013/018/019 | Add A1/S1 ×2; open cart, reload, inspect change/remove/total controls, empty it | 240.50 EGP preview; exact draft; quantity and per-line remove controls plus estimated subtotal | **FAIL FG-001:** 240.50 EGP meal preview, persistence, exact IDs and clearing pass; basket only displays `2 × QA Meal A1`, slot and Empty cart. Quantity editing, line removal and estimated subtotal absent. | [basket screenshot](evidence/IQA-04-cart.png), [DOM/storage](evidence/IQA-04-cart.json), results.json |
| IQA-05 — BR-018/TC-147 | A1/S1 ×2; attempt B1/B-S1 ×1; cancel | No pre-confirmation mutation; retain A/A1/S1 ×2, 240.50 estimate | **BLOCKED total subcheck; core PASS:** entire saved draft unchanged before/after cancel; explicit IDs and visible meal/quantity correct. Basket total unavailable (FG-001). | IQA-05-prompt.png/json, [result](evidence/IQA-05-result.json) |
| IQA-06 — BR-018/TC-147 | Repeat B1/B-S1 attempt; confirm | Only B/B1/B-S1 ×1; 80.25 EGP estimate | **BLOCKED total subcheck; core PASS:** exact new kitchen/meal/slot/quantity; A1 absent; visible result correct. No basket subtotal. | IQA-06-prompt.png/json, [result](evidence/IQA-06-result.json) |
| IQA-07 — BR-018/TC-147 | A1/S1 ×2; attempt A2/S2 ×1; cancel | Keep A/A1/S1 ×2 unchanged; 240.50 estimate | **BLOCKED total subcheck; core PASS:** exact original draft preserved before/after cancel. No basket subtotal. | IQA-07-prompt.png/json, [result](evidence/IQA-07-result.json) |
| IQA-08 — BR-018/TC-147 | Repeat A2/S2 attempt; confirm | Only A/A2/S2 ×1; 95.50 estimate | **BLOCKED total subcheck; core PASS:** exact A/A2/S2 ×1, old meal and slot absent, visible line correct. No basket subtotal. | IQA-08-prompt.png/json, [result](evidence/IQA-08-result.json) |
| IQA-09 — CW03, BR-013/015/017/045 | Unoffered/all-exhausted/partly-exhausted selections; quantity 99; paused kitchen; empty search; controlled loading; injected 503 then real retry | Cannot add unavailable selection; cap 3; closed/past unavailable; actionable states | **FAIL MBQA-002:** selected slot with 0 remaining added when second slot has 3. Fully SOLD_OUT Add disabled; normal cap clamps 99→3 with increase disabled; paused meal API 404, kitchen explains unavailable; closed/past absent; empty/loading/error/retry pass. | [zero-stock addition](evidence/MBQA-002-exhausted-selection.png), supplementary.json, IQA-09-true-soldout.png, IQA-09-controlled-loading.png, IQA-09-error.png |
| IQA-10 — BR-046/047, CW01–05 | Browse/detail/kitchen/basket in AR/EN at 1440/390/320; long/mixed text; prompt Tab/Escape; 200% computed-text injection | Direction/layout usable; visible focus; prompt focus/restoration; no hidden actions | **BLOCKED accessibility interpretation (RQ-002):** all 24 route/locale/width scroll metrics pass; representative screenshots inspected, no clipping of primary actions/prices found. Cancel autofocus and Escape return pass. Tab leaves inline alertdialog for Copy link while open. Computed title 20→40px, 320px page still fits; actual browser zoom/full keyboard/screen reader not run. | IQA-10-*.png/json, [focus evidence](evidence/IQA-10-prompt-focus.png), [text enlargement](evidence/IQA-10-text-enlargement.png), supplementary.json |

No independent authoritative checkout quote/order test was performed. Basket prices are not stored; exact resulting kitchen/meal/slot IDs were inspected in saved draft state, while visible cart only exposes meal, quantity and date. Arithmetic previews do not substitute for a visible basket subtotal or a server quote.

## Confirmed runtime defects

### MBQA-001 — language switch sends previous language to discovery

Severity **Moderate** (medium); requirement **BR-046**, CW01/02. SHA `eb1a6717b3304d3a02e6a46d06c77ed5d43ac144`. Reproduced **3/3 fresh anonymous contexts**, Chromium 154, 390px; also observed at 1440px.

1. Open customer browse in a fresh context with bilingual published A1/A2/B1.
2. Wait for Arabic cards; click **English**.
3. Wait for requests to finish; inspect cards and outgoing meal request.

Expected: English titles, descriptions and portions from the bilingual API alongside English UI/LTR. Actual: document `lang=en`, `dir=ltr`, English controls/prices, but meal request carries `Accept-Language: ar`; API and cards retain Arabic titles/descriptions/portions. A subsequent new route/query fetch recovers English. The fixtures contain English translations and direct English API requests return them, so missing translations are ruled out.

Evidence: `MBQA-001-repro-1/2/3.png`, request headers/bodies in `supplementary.json`, `IQA-01-language-switch.json/png`, `network.json`. Source diagnosis: `apps/customer-web/src/api.ts` derives request language from document HTML; `App.tsx` updates that DOM language in an effect, while the child `useServer` locale-triggered request can run before that update. Fix the locale/request relationship, then verify API content, not only HTML direction/UI labels. No fix applied.

### MBQA-002 — exhausted selected slot is added while another slot has stock

Severity **Major** (high); requirements **CW03 cannot add unavailable selection**, **BR-013/015**. Same tested SHA; reproduced **1/1 controlled real-API fixture**, not a mocked availability response.

1. A1 has two future OPEN slots, capacity 3 each. Exhaust A1/S1 (consumed=3) in the dedicated fixture DB; S2 remains at 3. Keep aggregate counters consistent.
2. Open A1 and inspect response: overall `availability=AVAILABLE`, S1 `availablePortions=0`, S2 `availablePortions=3`.
3. Select the displayed **0 left** S1. Click **Add to cart**.

Expected: zero-stock selection cannot be added. Actual: quantity forced to 1, Add enabled, “Added to cart”; saved draft contains A1 and S1 ×1. `maxQuantity` floors capacity at 1; Add guards only overall meal availability and whether a slot is selected (`Meal.tsx`), ignoring selected-slot capacity. The first broken layer is frontend selected-slot validation, not the real API. The fixture counters were restored in `finally`.

Evidence: `MBQA-002-exhausted-selection.png`, exact public response and resulting draft in `supplementary.json`. Fully exhausted A3 returns SOLD_OUT and disables Add, establishing why a global availability check misses the mixed-stock case. No order placement/overselling was demonstrated or claimed. No fix applied.

## Unfinished feature and requirement questions

**FG-001 — CW05 basket editing and estimated subtotal absent (Major acceptance gap).** This is missing functionality, separated from confirmed runtime regressions. Cart has only text lines, dated slot and whole-cart clearing; no quantity editor, individual removal, estimated subtotal, or displayed kitchen identity. Required subtotal/line controls are not satisfied by a meal-page preview or a later signed-in server quote. Repair handoff: implement the approved CW05 behaviour, distinguish estimate from authoritative quote, and verify multi-line removal/quantity arithmetic/caps. Evidence `IQA-04-cart.png/json`. Whether this was deliberately deferred needs an explicit scope decision; none found in approved D11/D12.

**RQ-001 — D12 optional discovery descriptions vs BR-011 required publication description.** Both apply at different boundaries if that is intended. Valid published fixtures cannot omit Arabic description, and missing English can fall back to Arabic. This assessment checks omission with a nullable discovery-contract injection only; it does not bypass publication constraints or claim production data exercises null. Clarify if owner expects valid published meals with no description anywhere; that would conflict with BR-011.

**RQ-002 — replacement prompt modal or inline/non-modal?** BR-047 requires keyboard support and focus restoration, but approved screen acceptance does not explicitly require trapping focus. The runner's supplementary containment assertion fails: Tab from Keep leaves the alertdialog for Copy link; prompt is still open. Core focus entry/Escape/restoration pass. Do not call this confirmed solely from an unsupported modal assumption, or turn it into a PASS for full accessibility. Decide semantics, then verify keyboard/screen-reader behaviour accordingly. This question blocks IQA-10's accessibility conclusion, not its layout results.

**RQ-003 — unavailable browse cards.** Product journey text says unavailable items are shown only in favorites/history, whereas the discovery contract includes SOLD_OUT/UNAVAILABLE and the existing browse renders published active-but-unoffered A3 with an unavailable label and disabled purchase. Clarify whether unavailable catalogue cards should be excluded or shown honestly. No defect classification silently selects one reading.

Whole-product visual approval **O05 remains pending**; this assessment reports observable behaviour/layout, not aesthetic acceptance or owner approval.

## Console, network and verification limits

Recorded [console](evidence/console.json) and [public network evidence](evidence/network.json) throughout the main run. No JavaScript `pageerror` was observed. Anonymous `/api/v1/me` 401 responses are expected; injected meal 503 responses belong to the intentional recovery test. One uncorrelated initial 404 resource message was observed; it is not classified as a journey failure without resource evidence. No real public catalogue 5xx was observed. Debug records omit cookies, authorization headers, CSRF tokens, credentials and private payloads. No authentication trace was stored.

Not independently tested: signed-in quote/checkout/order totals and placement, stock races/overselling, stale stock after a long-open tab, multiple basket lines/aggregate limits, filter history/back/forward, date/slot filter UI, >50-result pagination, favorites/history, cook/backoffice, full keyboard-only ordering, screen readers, native browser zoom, other browser engines, tablet 768px, Android/iOS native apps, performance/load/security/recovery and real providers. No results are inferred from developer tests. Layout at narrow width is customer web evidence, not native mobile evidence.

## Comparison after independent execution

Read Kane findings only after initial execution and supplementary reproduction. Compared archived branch head `44db32eea83c5a1907620f87af10ff09da737396` (`claude/kane-cli-qa-pilot-resume-y7scfp`), report `verification/tool-pilots/kane-cli/2026-10-12-pilot.md`; inspected the related j9svbp branch as well. Kane's application setup revisions were `64161d5...` and later `fd689aa901e5fef2d3188bc583d2de8fc104e85d`, different from this assessment.

Kane explicitly reports **all three cases BLOCKED**, credentials rejected, no test execution/screenshots/product findings and no credits consumed. There is no Kane PASS to corroborate or disagree with. This run supplies independent Playwright evidence for its intended browse/add/replacement areas and additional availability/layout checks. Kane's D12 fixture limitation agrees with RQ-001. Its earlier “integer EGP/no fractions” wording is too restrictive if read literally: BR-019 requires integer **minor units**; 12025 piastres correctly displays **EGP 120.25**. No rounded expected values were adopted. Kane's same-kitchen variant covers cancel but not explicit same-kitchen confirm; this assessment checks both with exact IDs.

After independent execution, inspected developer browser assertion meaning at this fixed checkout: the existing TC-147 browser section uses one meal in two slots, asserts cancelled slot unchanged, confirmed slot different and truthy; it does not establish different-kitchen replacement or exact new meal/kitchen/slot. The layout matrix checks direction, control sizes/overflow/axe, not translation request/content correctness. Keyboard section checks prompt entry/Escape/restoration, not containment. Therefore its self-review PASS claims do not disprove MBQA-001/002 or resolve RQ-002. Developer evidence also references earlier source SHAs; it was not rerun or promoted to independent evidence.

## Concise Claude repair handoff

1. **MBQA-002 first:** guard selected slot stock in Meal; keep zero-stock choices explanatory and unpurchasable. TDD: one exhausted slot plus another available, asserting no cart write for the exhausted selection; verify positive cap and entirely SOLD_OUT separately.
2. **MBQA-001:** pass current locale deterministically into requests. TDD/browser: both-direction toggle on browse/detail/kitchen, asserting request header and translated fixture content after settled responses.
3. **FG-001:** complete CW05 quantity/line-remove/estimate behaviour or obtain an explicit recorded deferral. Verify totals 2×120.25=240.50; replacement estimates B1=80.25 and A2=95.50; exact draft IDs remain single-kitchen/single-slot.
4. Resolve RQ-001/002/003, then retest only changed behaviour and its adjacent flow at a **new explicitly pinned SHA**. Do not move this assessment's tested source or overwrite its failures. Native/mobile/all-surface confidence requires a separate evidence-backed round; no release gate approved here.

Reusable setup/execution and evidence review instructions: [README](README.md). Publication SHA is supplied by the QA handoff; this report always refers to the application SHA above, never its own evidence commit as tested product source.
