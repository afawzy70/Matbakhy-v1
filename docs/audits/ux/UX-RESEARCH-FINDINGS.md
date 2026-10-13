# Matbakhy independent UX research findings

Audit date: 2026-10-13. Role: **UX Researcher**, using the complete preserved upstream Agency Agents developer instructions and Matbakhy appendix through an explicit-instructions hosted-agent fallback. This report does not establish native TOML role discovery. Application source inspected: `cc52fa4224e3163677a1215dc8f63ede2a9dbe4c` on `origin/implementation/mvp`; owner-policy comparison: `origin/main` at `047f42e` and accepted implementation decisions D11–D14. Independent coordinator assigns final stable UX IDs; `R-xx` identifiers below are provisional source IDs.

## Scope, method and confidence

Questions: Can a customer choose a serviceable meal and understand quantities, current cost, commitment and recovery? Can a kitchen join, publish accurate food information, manage capacity and act in time? Are mobile and Arabic experiences sufficient for those jobs? Which recommendations preserve approved behavior and avoid speculative redesign?

Method: source and contract inspection; documentary comparison; examination of product, business-rule, lifecycle, delivery, design-approval, mobile, verification and prior-QA records. No participant sessions, interviews, surveys, analytics analysis or conversion experiment occurred. Persona labels describe task contexts, not validated demographic segments. No task completion rate, time, emotional response, conversion uplift or usability score is asserted. No runtime interaction was exercised by this researcher; coordinator runtime evidence is recorded separately.

Evidence labels:

- **VERIFIED**: directly reproduced by this audit. Documentary verification would be explicitly limited to the documents. No UI finding below has this label in the researcher-only version.
- **CODE-SUPPORTED**: concrete implementation behavior/gap supported by inspected code. Runtime appearance and extent remain unmeasured.
- **HEURISTIC**: specific concern requiring participant or runtime validation.
- **UNVERIFIED**: runtime accessibility, interaction, device behavior or user response not tested.

Initial `main` inspection showed only the planning pack. Discovery of the active implementation branch corrected the audit target before any report was finalized. The product contains substantial web/backend/native implementation; a stale README or checkpoint is not evidence that screens are absent. Existing QA repairs MBQA-001–005, FG-001 and OBS-001 are historical context, not newly reproduced defects. Their independent retest status is not changed by this report.

## What exists and what remains incomplete

| Surface | Source-inspected functionality | Incomplete/planned/unavailable distinction |
|---|---|---|
| Customer web | Real API-backed public discovery, meal and kitchen details, single-kitchen/slot basket, contact verification, address creation, quote and order submission/recovery, orders/status, reviews, account/favorites/inbox/support | No complete repeat basket reconstruction; no public policy screens in route dispatcher; lists often consume one page; filters lose context across route changes |
| Customer Android/iOS | Native navigation, API-backed discovery/meal/basket/quote/orders, account/favorites/support/notifications, auth and recovery code | Discovery/filter/kitchen-profile and meal-presentation parity incomplete; native screen readers, large text and physical-device validity not independently tested here |
| Cook workspace | Dashboard, meals/create/publication/price history, slots/create/state actions, order actions/cash reporting, statements/settings | Onboarding read-only; general meal content editing and existing slot-capacity editing absent from inspected UI; queue freshness depends on user actions/remount |
| Backoffice | Permission-filtered cook review, catalogue/reviews, support, order exceptions, finance, configuration, staff | Included for journey dependencies; full route history/deep-link navigation and runtime operations/accessibility not independently verified here |
| Price behavior | Web/native old/new quote disclosure and explicit acceptance; immutable committed order snapshots; active/historical kitchen price view | Preserve D13/D14; do not replace these with price locking before submission or editable history |
| 2026-10-13 commercial direction | Main documents approve launch with zero platform commission/fees/ads and manual direct-to-kitchen deposits alongside eligible COD | Active UI/contract remains cash-first with commission-oriented statements; reconciliation is required before affected release; this is not a verified live fee/payment defect |
| Future growth/catering | Growth insight and opt-in monetization research described in main strategy | Paid growth, AI Concierge and bespoke catering workflow are deferred/unapproved; no recommendation authorizes them |

The selected terracotta/Cairo direction is established in `docs/design/approved/DESIGN.md:5`. Whole-product approval remains bounded separately in `docs/design/approval.md:3`. D11 explicitly defers structured serving-person filters (`planning/decisions.json:215`); their absence is intentional and is not filed as a defect.

## Findings

### R-01 — A prospective kitchen cannot complete onboarding in the UI

**Priority P1; CODE-SUPPORTED; suggested effort M (3–5 focused engineer-days after existing contract review).** Persona: first-time home cook. Screen: operations app's no-kitchen view (planned CK01 `/cook/onboarding`; actual UI is tab/state driven).

`apps/operations-web/src/cook/Onboarding.tsx:4` explicitly postpones editing; lines 9–25 only fetch and print state/no-application. `apps/operations-web/src/App.tsx:201` renders this component when no section is available. Customer and native route inventories expose no application editor. API-backed application persistence is present, so this is a client journey gap, not evidence that the backend approval workflow is absent.

Expected: a cook starts/saves/submits an application and revises named rejected fields. Actual source behavior: read-only status with no create/edit/submit route. Impact: blocks self-service supply activation and gives no next action; raw application enum is also not clear Arabic.

Recommendation: add the bounded draft/revision editor using existing application/upload contracts; clearly separate public profile and private documents, show required documents from approved policy, draft-save feedback and reviewer reasons; preserve input after errors. Do not invent mandatory documents or legal clearance. Suggested files: `Onboarding.tsx`, operations `App.tsx`/`i18n.ts`, existing cook application contracts.

Acceptance/validation: synthetic applicant completes draft→submit→changes-requested→resubmit; missing fields focus the associated error; private document never enters public response; rejection reasons are localized; screen works at 360/390 px and keyboard-only. Proposed regression R-T01.

### R-02 — Kitchen meal form cannot truthfully declare known allergens

**P1; CODE-SUPPORTED; S–M (1–3 days).** Personas: cook and customers with allergies. Screen: Meals creation/publication.

`apps/operations-web/src/cook/Meals.tsx:164` defaults to `NONE_DECLARED`; lines 247–252 offer only `NONE_DECLARED` and `UNKNOWN`. The body at lines 203–213 contains no allergen codes/note. Backend already supports `CONTAINS`, allergen codes and note (`backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java:50`; `.../internal/meal/MealService.java:479`).

Expected: a cook can declare actual known allergens without claiming none. Actual: the UI cannot express `CONTAINS`; defaulting to none creates an error-prevention concern. No claim that a cook has misdeclared real food or that an injury occurred.

Recommendation: neutral required declaration choice; enable localized known-allergen multiselect and optional kitchen note when `CONTAINS`; retain backend publication guards and uncertainty wording. Include the same fields in meal editing. Files: `Meals.tsx`, operations `i18n.ts`; reuse existing DTO, avoid a schema rewrite.

Acceptance: milk/wheat meal saves and publishes with true codes; unset declaration cannot publish; uncertainty and none remain distinguishable; screen reader announces group/selection/errors; Arabic labels are intelligible. R-T02.

### R-03 — Native meal details lose Arabic allergen meaning and kitchen notes

**P1; CODE-SUPPORTED; S (0.5–1.5 days).** Persona: Arabic mobile customer with dietary concerns. Screen: native Meal.

`apps/customer-mobile/src/screens/Meal.tsx:81` displays ingredients, but line 87 joins raw `allergenCodes`; it does not render `allergenStatement`. Web localizes codes and displays note at `apps/customer-web/src/Meal.tsx:56`–63.

Expected: both clients expose the same declared allergens and qualification, in understandable Arabic. Actual native rendering would show internal English codes for known allergens and omit the cook's statement. Recommendation: share translation keys, map enum codes, render note and unknown/none distinction; do not imply dietary suitability guarantees. Files: mobile Meal, mobile translations, shared i18n where already appropriate.

Acceptance: Arabic/English synthetic `CONTAINS` + note is semantically equivalent on web/Android/iOS; long note wraps at large text; unsupported code has safe localized fallback. R-T03.

### R-04 — An idle kitchen queue does not refresh incoming orders or deadlines

**P1; CODE-SUPPORTED; M (1–2 days).** Persona: cook accepting/preparing orders. Screens: dashboard/Orders.

`apps/operations-web/src/cook/Orders.tsx:23`–37 loads on filter changes; `Dashboard.tsx:5` loads on locale; `cook/shared.tsx:18`–53 only reloads on key/nonce. Neither screen schedules polling/foreground refresh. Dashboard order-number action (`Dashboard.tsx:41`) opens the generic orders tab rather than that order.

Expected: a cook keeping the workspace open sees new commitments and can open the named order before `acceptBy`. Actual: source has no background freshness trigger; displayed minutes are calculated at render. Impact: plausible missed acceptance or stale decisions; frequency/loss unmeasured.

Recommendation: bounded visible-page polling plus foreground refetch, stale-data indicator/last-updated time, current deadline calculation from server offset, and direct order focus/selection. Keep race/authorization guards and no false deadline extensions. Files: Dashboard, Orders, operations App/shared hook.

Acceptance: order created from another synthetic client appears within an explicit freshness budget; hidden tab pauses/reduces polling; returning refetches; expired action cannot be submitted successfully; clicking order number focuses that order. R-T04.

### R-05 — First-page limits hide additional meals, kitchens and order history

**P1; CODE-SUPPORTED; M (2–4 days for shared bounded pagination).** Personas: browsing customer, returning customer, busy cook. Screens: discovery/kitchen/orders and analogous operations lists.

Web `Browse.tsx:37` requests 50 and lines 189–210 render only `page.items`; kitchen profile `Kitchen.tsx:34` requests 50 without more; customer `Orders.tsx:7` requests 50. Mobile Discover/Orders likewise request 50. Cook `Orders.tsx:28` requests 50 per state and discards page cursors. Kitchen mode derives kitchens from the currently loaded meal subset (`Browse.tsx:255`). No inspected continuation control uses `nextCursor`/`hasMore`.

Expected: every eligible result remains reachable. Actual: only first pages are reachable through these views. Impact depends on catalogue/order volume; not reproduced with 51 fixtures here.

Recommendation: incremental pagination/load-more with explicit loaded-count semantics, safe cursor reset on filter changes, stable ordering and scroll/focus continuity; scoped kitchen results must not imply exhaustive coverage. No search-engine rewrite. Files: Browse, Kitchen, customer/mobile Orders/Discover, cook Orders; existing generated pagination contracts.

Acceptance: fixture of 51+ matching meals/orders reveals all exactly once; changed query resets cursor; kitchen count describes loaded scope; stale fetch cannot mix results; screen reader receives newly loaded count. R-T05.

### R-06 — Approved direct-deposit journey is absent from current checkout/status UI

**P1; CODE-SUPPORTED against latest owner direction; L (4–8 days across existing transactional/API/client stories, then external release gates).** Personas: gathering/family customer and kitchen.

Main authority: `docs/strategy/mvp-direct-deposit-payment.md:14`–22 and `docs/delivery/07-owner-decisions-implementation-sequence.md:15`–23. Active UI: web Checkout at line 384 and native Cart at line 414 show cash only; inspected request/quote shape contains no payment selection, deposit due/remainder/recipient or transfer/reconciliation commands.

Expected: eligible COD and approved manual deposit paths with honest kitchen-reported acknowledgement, separately accepted order and clear dispute/refund recovery. Actual: new owner scope is not integrated into current UI/contract. This is a scope integration gap, not a failed tested live transfer.

Recommendation: first reconcile source/registries with owner direction; extend existing quote/payment state contracts and workflows in dependency order. Separate order, payment and fulfillment states. Show exact EGP total/deposit/remainder, permitted recipient/method, expiry and next action; never demand a second transfer while uncertain; receipt report is not bank verification. No custody/wallet, payment gateway, guarantee or live money activation.

Acceptance: synthetic COD/deposit tests include mismatch, false receipt, delayed receipt, customer cancel, kitchen reject after acknowledgement/refund; no prep before acknowledgement AND acceptance; snapshots immutable; web/iOS/Android show same state. Suggested files: quote/order/payment contracts/services, Checkout/Cart/Orders, cook Orders/Statement, operations finance/cases, mapped planning stories/scenarios. R-T06. Real activation remains blocked by legal/provider/kitchen gates.

### R-07 — Free-launch owner direction conflicts with commission-oriented implementation surfaces

**P1; CODE-SUPPORTED/documents; M (2–4 days to reconcile and test existing policy/ledger/UI).** Personas: cook and customer. Screens: earnings/configuration/checkout.

Main authority `docs/delivery/07-owner-decisions-implementation-sequence.md:10`–11 requires actual zero/inactive commission, not hiding fees. Active `planning/decisions.json:98` still says configurable commission; cook Statement at lines 50–60 exposes commission posted/pending/reversed/settled/outstanding. Prior test setup explicitly uses synthetic 10% (`docs/quality/08-codex-qa-repair-cross-role.md:73`). This audit has not observed a production charge; a clearly synthetic policy must not be mistaken for a live fee.

Recommendation: reconcile confirmed launch policy in machine registries, fixtures, server calculation, ledger and wording; essentials free, real external delivery/transfer cost clearly distinguished. Keep inactive future policy flexibility gated by explicit owner authorization. Earnings should explain gross sales, reported/verified receipts, refunds, pending balance and period; never call them profit without supplied costs.

Acceptance: zero platform fees/commission in quote/order/ledger/statements for launch configuration across clients; no paid rank/campaign/billing enabled; future flag cannot activate without approval; no altered placed-order snapshot. Suggested files: commerce configuration, quote/finance modules, Statement/i18n, fixture policies/registries. R-T07.

### R-08 — “Order again” opens only the first meal instead of reviewing the previous basket

**P2; CODE-SUPPORTED; M (1–3 days).** Persona: returning customer/family.

Web `Orders.tsx:124`, mobile `Orders.tsx:196` and `OrderAgain:238` pass only `o.items[0].mealId`. Expected: review a new basket preserving intended lines/quantities where currently available. Actual: first-meal link discards other lines and quantities. New prices/stock are correctly needed; do not replay old totals.

Recommendation: rebuild proposed basket from full order into a review step, fetch current availability/prices, label unavailable lines, require a current common kitchen/slot and normal basket-replacement confirmation; alternatively rename the current shortcut accurately until bounded reorder is delivered.

Acceptance: completed 3-line/2-quantity order yields current review with each available line; archived food is named unavailable; no invisible deletion/substitution; current quote and D13 apply. Files: web/native Orders and basket/cart helpers; existing public catalogue/quote APIs. R-T08.

### R-09 — Discovery and operations navigation lose working context

**P2; CODE-SUPPORTED; M (2–3 days).** Personas: comparing customer and cook/operator.

`Browse.tsx:20`–29 stores all filters locally; Meal's back link (`Meal.tsx:35`) returns `#/`; Browse unmounts in App's route switch. Operations `App.tsx:89` keeps tab in memory; lines 181–214 do not encode it in URL. Expected: back/reload returns to filtered results or the selected work queue. Actual source loses those choices on remount/reload; no measured navigation time.

Recommendation: serialize supported filters/sort/cursor in safe URL query/hash state; retain scroll where appropriate; operations route/tab/object IDs in allowlisted navigation, with permissions rechecked. Preserve approved homepage structure. Acceptance: search→detail→back/reload restores same query; copying a permitted queue/order link works; unauthorized IDs remain denied. Files: customer App/Browse/Meal, operations App. R-T09.

### R-10 — Native discovery and comparison omit supported decision information

**P2; CODE-SUPPORTED; M (3–5 days, reuse backend capability).** Personas: mobile first-time, couple/family and kitchen-comparison customer.

Native `Discover.tsx:18` requests search/newest only; cards at lines 57–70 omit photograph, portion and next slot. Root navigation at `screens/navigation.ts:12`–19 has no kitchen or filter stack. Native Meal lines 69–89 has no photo/kitchen-link/reviews; web exposes them. Serving-person filters remain D11-deferred; this finding covers already supported zone/category/price/sort and existing decision information.

Recommendation: add accessible filters using current endpoint, service zone visible before ordering, portion/next-date/photo where genuine, and bounded public kitchen profile/review navigation. Do not add person-count filters or catering booking. Acceptance: web/native same supported query yields equivalent eligible meals; manual zone selection works without permissions; card data real; kitchen has no private fields; large text and screen-reader flow tested separately. R-T10.

### R-11 — Kitchens can create food/slots but cannot maintain their content/capacity in the UI

**P2; CODE-SUPPORTED; M (2–4 days).** Personas: cook managing menu/availability.

`Meals.tsx:36`–71 exposes publication and price history only; NewMeal creates content but no editor updates portion/ingredients/photo/translations. `Slots.tsx:45`–77 only changes state; existing total and meal capacities cannot be edited there, although PATCH contract supports capacity and preserves obligations. Expected: correct a draft/publication error or future capacity without duplicate meal/slot creation.

Recommendation: bounded existing-content editor and capacity edit using `expectedVersion`; retain valid edits on conflicts; show reserved/allocated/consumed obligations and server rejection when capacity falls below them. No scheduled/bulk pricing/history rewrite. Acceptance: correct draft lacking declaration/photo without recreation; edit future capacity with zero obligations; reject below-obligations change while preserving input; no historical snapshot change. R-T11.

### R-12 — Checkout asks for policy acceptance without a readable policy route

**P1; CODE-SUPPORTED; S–M (1–2 days plus approved copy dependency).** Persona: customer committing an order.

Web Checkout lines 386–388 is a checkbox plus plain terms label; native Cart line 417 is a choice label. Web route dispatcher `App.tsx:147`–218 and native root type have no terms/privacy/help policy screen. Main product screen contract expects `/terms`, `/privacy`, `/help`. No claim that approved legal copy already exists.

Recommendation: link exact server policy versions adjacent to acceptance, preserving basket when opening/returning; local draft visibly draft, release blocks absent approved policy. Show cancellation deadline and seller/support responsibilities in understandable Arabic, not backend enum language. Acceptance: keyboard/screen reader can read version accepted; changed policy invalidates prior acceptance; return retains form; no manufactured legal approval. Files: Checkout/Cart/App/navigation, existing policy API/translations. R-T12.

### R-13 — Order tracking lacks the deadline and exception explanation customers need

**P2; CODE-SUPPORTED; S–M (1–2 days).** Personas: customer awaiting acceptance/rejected order.

Web Orders lines 45–75/native Orders lines 114–158 show state/slot/timeline, but not `acceptBy` or event reason; web only exposes support links when a case already exists (lines 76–85). A new web case requires Account's Support section and choosing among first 20 orders (`Account.tsx:125`–159). Expected: know whether kitchen has accepted, by when, why it declined/expired, and how to ask for help on this order.

Recommendation: localized current-state explanation with real acceptance deadline and next action, permitted reason mapping, order-linked support entry retaining order ID; no live GPS or invented ETA. Include truthful payment/reconciliation labels when R-06 lands. Acceptance: pending/rejected/expired/delivery-failed fixtures have distinct explanation and support; old notifications cannot regress status; order ownership checked; no private note leakage. R-T13.

## Secondary concerns requiring validation

**HEURISTIC (no duplicate implementation issue filed):** Three intent tiles currently reveal the same maximum unit-price field (`Browse.tsx:72`–103), while copy speaks about a meal/table/gathering. The existing copy explicitly says per dish, which mitigates confusion; participant comprehension of unit vs household total is still unmeasured; `i18n.ts:47`/English counterpart should be checked with the actual UI. Validate whether Arabic clearly describes per-serving price and the single-kitchen/slot boundary. Tune labels/helper copy within approved layout; do not restore D11 person filters.

A long Account combines profile/favorites/inbox/support/addresses/deletion (`Account.tsx:10`–16). Validate discoverability at 390 px before proposing an IA change; a short section index may be enough. Native favorite toggle starts `favorite=null` without reading the user's saved state and silently catches failures (`Meal.tsx:28`, 147–153); confirm existing favorite and failed request behavior before prioritizing. These may be coordinator backlog candidates if reproduced.

No paid growth screen is needed at launch. An essential free statement and plain-language performance definitions should precede any sales advice. Once valid organic measures exist, test voluntary kitchen interest in free suggestions. Future paid offers require owner approval, opt-in, budget/risks and credible evidence; never fabricate ROI, thresholds or scarcity. Full catering needs an approved separate contract/operational scope; currently explain supported standard preorders honestly.

## Proposed research protocol, not completed research

Start with two rounds of 6–8 Egyptian Arabic-speaking customer sessions, purposefully varying one-person/couple/family/gathering tasks, repeat-order experience, device familiarity and accessibility needs. Separately recruit 5–6 prospective/active home cooks with varied catalogue and order volumes. These proposed counts support formative issue discovery, not representative conversion estimates or statistical A/B inference. Recruit additional screen-reader and large-text participants to cover relevant access needs; obtain consent, avoid recording private payment/identity data and compensate only under separately approved research budget.

Use synthetic menus/identities in a local/private test environment, and the current approved prototype when implementation is unavailable. Forty-five-to-sixty-minute moderated sessions: brief context interview, think-aloud tasks, neutral comprehension questions and short task-ease ratings. Rotate task order to reduce learning bias. Do not lead with “find our problem.” Ask participants to explain portion, per-unit cost, total, deposit vs kitchen acknowledgement, acceptance vs preparation, deadline, and seller refund responsibility.

Capture task completion independently, wrong turns, abandonment, clarification requests, error recovery and time with a defined start/end. First establish baselines; no uplift target is asserted. Do not infer emotions from silence. Keep consented quotes verbatim and separated from interpretations. Prioritize repeated blockers, money/safety misunderstandings and accessibility failures; triangulate participant observations with code and traces. Qualitative formative studies do not substantiate market fit or conversion percentages.

Minimum validation tasks: complete a solo and family basket; return from comparison with filters intact; reorder three lines with one unavailable; change quantity within policy; changed quote and lost placement response; deposit uncertainty without second payment; rejected order support; kitchen draft/revision/publication with real allergens; capacity conflict; new order appearing in an idle queue; interpret earnings without confusing gross with profit; explain that free launch has no paid growth activation. Keyboard, Arabic RTL, Android TalkBack and iOS VoiceOver need their own evidence; emulators and screenshots alone do not prove physical-device usability.

## Researcher's limits and handoff

Thirteen source-supported provisional findings; no runtime-VERIFIED UI defect from this agent. No P0 recommendation without a reproduced immediate critical failure. Suggested effort is incremental engineering effort, not elapsed external-review time. Follow existing dependencies; R-06/R-07 require decision/contract reconciliation first, R-02/R-03 share data semantics, and R-05/R-09 can share navigation/pagination work. The coordinator owns stable IDs, deduplication, runtime evidence, final backlog, limitations and Claude batches. These reports do not mark existing backlog stories FIXED/DONE or authorize implementation, release, billing or live money.
