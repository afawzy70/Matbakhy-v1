# UX improvement backlog

Baseline: `cc52fa4224e3163677a1215dc8f63ede2a9dbe4c` (implementation/mvp), 2026-10-13. Owner-policy comparison: main `047f42eebfcfc904622a123bbb8bdf9b2a16cda8`. Audit only; all findings OPEN, no application fixes. Stable IDs are allocated once here; later audits retain them, record disposition and cross-reference prior IDs rather than renumber. UX-021 preserves historical MBQA-006 as its external alias.

Priority: P0 immediate catastrophic demonstrated risk; P1 high-impact primary journey/trust/release gap; P2 material recoverable usability/accessibility; P3 bounded recovery/polish. No P0 is evidenced. Ranking considers severity, likely task frequency (unmeasured), source/reproducer confidence, effort and business value. No frequency or conversion estimate is claimed. S ≈0.5–1 day, M ≈2–5 focused engineer-days, L ≈6–12 days split into existing dependency stages; include affected tests, exclude external approvals/recruitment. Re-estimate after contract review.

| ID | Priority | Complexity | Confidence | Finding |
|---|---|---|---|---|
| UX-001 | P1 | S | VERIFIED | [Recalculation clears changed-price acknowledgement](#ux-001) |
| UX-002 | P1 | M | CODE-SUPPORTED | [Known allergens cannot be declared in meal creation](#ux-002) |
| UX-003 | P1 | S | CODE-SUPPORTED | [Native meal omits understandable allergen information](#ux-003) |
| UX-004 | P1 | M | VERIFIED | [Kitchen onboarding ends without an action](#ux-004) |
| UX-005 | P1 | M | CODE-SUPPORTED | [Idle cook queues do not refresh incoming orders](#ux-005) |
| UX-006 | P1 | M | VERIFIED | [Later result pages are unreachable](#ux-006) |
| UX-007 | P1 | M | CODE-SUPPORTED | [Active commerce configuration has not reconciled free-launch direction](#ux-007) |
| UX-008 | P1 | L | CODE-SUPPORTED | [Approved direct-deposit journey is not integrated](#ux-008) |
| UX-009 | P1 | M | CODE-SUPPORTED | [Policy acceptance has no readable policy destination](#ux-009) |
| UX-010 | P2 | M | CODE-SUPPORTED | [Order again only opens the first dish](#ux-010) |
| UX-011 | P2 | M | VERIFIED | [Navigation loses search and working context](#ux-011) |
| UX-012 | P2 | M | CODE-SUPPORTED | [Native discovery lacks supported decision information](#ux-012) |
| UX-013 | P2 | M | CODE-SUPPORTED | [Kitchen cannot maintain general meal or slot content](#ux-013) |
| UX-014 | P2 | M | CODE-SUPPORTED | [Field-level server validation is discarded](#ux-014) |
| UX-015 | P2 | S | VERIFIED | [Arabic numeric input is rejected or erased](#ux-015) |
| UX-016 | P2 | M | CODE-SUPPORTED | [Finance requires a kitchen UUID](#ux-016) |
| UX-017 | P2 | S | CODE-SUPPORTED | [Native controls use low-contrast decorative borders](#ux-017) |
| UX-018 | P2 | M | CODE-SUPPORTED | [Order status omits useful deadline/reason/support context](#ux-018) |
| UX-019 | P2 | S | CODE-SUPPORTED | [Pending sign-out dialog does not manage focus](#ux-019) |
| UX-020 | P3 | S | VERIFIED | [Unknown customer routes render an empty main area](#ux-020) |
| UX-021 | P2 | S | VERIFIED | [Narrow financial summaries break monetary tokens](#ux-021) |

## UX-001

**Recalculation clears changed-price acknowledgement**

- Affected screen and persona: Customer checkout; web and native.
- Problem: A customer can bypass the dedicated changed-price acceptance after a stale quote rejection.
- Expected user behavior: The old/new affected prices stay disclosed and the exact fresh quote needs explicit acknowledgement before placement.
- Actual/observed behavior: React/jsdom probe: reject old quote, auto-requote, click Calculate total, tick terms, submit; no Accept new total click occurred. Native equivalent is code-supported.
- Evidence/confidence: **VERIFIED**; [evidence/CONSENT-PROBE-README.md](evidence/CONSENT-PROBE-README.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: A customer can bypass the dedicated changed-price acceptance after a stale quote rejection. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Bind price-change consent to quote ID/version and retain the old/new comparison across recalculation; invalidate all prior consent when terms or amounts change. Do not silently retry placement.
- Suggested implementation files: [apps/customer-web/src/Checkout.tsx](../../../apps/customer-web/src/Checkout.tsx), [apps/customer-mobile/src/screens/Cart.tsx](../../../apps/customer-mobile/src/screens/Cart.tsx).
- Acceptance criteria: No order POST until explicit consent to the current changed quote; committed order snapshots and same-key recovery remain unchanged.
- Regression test: **UX-T001** — Run the committed diagnostic; preserve the probe; copy inverse expectations into application tests after repair and add repeated change, mode/address/quantity edits, refresh failure and accepted-order recovery cases.

## UX-002

**Known allergens cannot be declared in meal creation**

- Affected screen and persona: Cook meal creation; customers with allergies.
- Problem: The form defaults to NONE_DECLARED and offers only none/unknown.
- Expected user behavior: Cook deliberately selects unknown/none/contains and can name known allergens and explanatory text.
- Actual/observed behavior: Meals.tsx:164,203–213,247–252 cannot submit CONTAINS/codes/note although MealController supports them. No real misdeclaration is claimed.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: The form defaults to NONE_DECLARED and offers only none/unknown. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Use a neutral required declaration, localized supported-code choices and statement; retain publication guards.
- Suggested implementation files: [apps/operations-web/src/cook/Meals.tsx](../../../apps/operations-web/src/cook/Meals.tsx), [apps/operations-web/src/i18n.ts](../../../apps/operations-web/src/i18n.ts), [backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java](../../../backend/modules/catalog/src/main/java/com/matbakhy/catalog/web/MealController.java).
- Acceptance criteria: All three truthful declaration states can be authored; no default claim of absence; kitchen statements reach both clients.
- Regression test: **UX-T002** — Create unknown/none/contains fixtures, reject missing contains codes; reopen/edit and inspect customer web/native response.

## UX-003

**Native meal omits understandable allergen information**

- Affected screen and persona: Native Meal; Arabic customers with allergies.
- Problem: Raw enum values replace localized allergen names and the kitchen statement is omitted.
- Expected user behavior: Read the same meaningful allergy information available on web before adding food.
- Actual/observed behavior: Native Meal.tsx:83–87 joins allergenCodes and omits allergenStatement; web Meal.tsx maps names and renders the note.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-ARCHITECTURE-REVIEW.md](UX-ARCHITECTURE-REVIEW.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Raw enum values replace localized allergen names and the kitchen statement is omitted. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Reuse localized allergen keys and include statement with unknown/none/contains distinctions; no dietary safety guarantees.
- Suggested implementation files: [apps/customer-mobile/src/screens/Meal.tsx](../../../apps/customer-mobile/src/screens/Meal.tsx), [apps/customer-mobile/src/i18n.ts](../../../apps/customer-mobile/src/i18n.ts).
- Acceptance criteria: No raw known enum appears; note retained; accessible reading order precedes purchase controls.
- Regression test: **UX-T003** — Render each declaration plus a long Arabic statement; exercise TalkBack/VoiceOver and large text on actual native targets.

## UX-004

**Kitchen onboarding ends without an action**

- Affected screen and persona: Operations no-kitchen view; prospective kitchen.
- Problem: The existing onboarding screen has no application editor or start/submit action.
- Expected user behavior: Start/save/submit a draft and respond to requested changes with clear public/private fields.
- Actual/observed behavior: Fixture-backed browser rendered no-application message with zero interactive controls in main; source Onboarding.tsx only GETs and prints state. Backend workflow exists.
- Evidence/confidence: **VERIFIED**; [evidence/browser-probes.json](evidence/browser-probes.json). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: The existing onboarding screen has no application editor or start/submit action. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Build the bounded application/revision form on existing contracts and private upload flow; document policy-dependent required documents.
- Suggested implementation files: [apps/operations-web/src/cook/Onboarding.tsx](../../../apps/operations-web/src/cook/Onboarding.tsx), [apps/operations-web/src/App.tsx](../../../apps/operations-web/src/App.tsx), [apps/operations-web/src/i18n.ts](../../../apps/operations-web/src/i18n.ts), [contracts/openapi.yaml](../../../contracts/openapi.yaml).
- Acceptance criteria: Application can progress without SQL/operator impersonation; errors preserve draft; review reasons localized; private documents never public.
- Regression test: **UX-T004** — Applicant draft→submit→changes requested→resubmit; keyboard and narrow-screen upload/error recovery; privacy checks.

## UX-005

**Idle cook queues do not refresh incoming orders**

- Affected screen and persona: Cook dashboard/orders; active kitchen.
- Problem: Orders arriving after mount are not fetched until a user action/remount.
- Expected user behavior: See new pending orders and accurate deadlines while the workspace remains open.
- Actual/observed behavior: Orders.tsx and Dashboard.tsx load via useServer keyed to filters/mutations, with no periodic/foreground refresh; dashboard link opens generic queue.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Orders arriving after mount are not fetched until a user action/remount. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Add bounded visible-page polling plus foreground refresh, last-updated/error status and direct focus on the selected order. Preserve server clock and state guards.
- Suggested implementation files: [apps/operations-web/src/cook/Orders.tsx](../../../apps/operations-web/src/cook/Orders.tsx), [apps/operations-web/src/cook/Dashboard.tsx](../../../apps/operations-web/src/cook/Dashboard.tsx), [apps/operations-web/src/App.tsx](../../../apps/operations-web/src/App.tsx).
- Acceptance criteria: Incoming order appears within declared bound without manual navigation; focus not stolen; stale/failed refresh visible; no timer extension.
- Regression test: **UX-T005** — Open empty queue, create a synthetic order elsewhere, wait configured refresh bound, foreground after pause, race acceptance/expiry.

## UX-006

**Later result pages are unreachable**

- Affected screen and persona: Discovery/kitchen comparison and affected lists; customers/cooks.
- Problem: Clients request fixed first pages without next-cursor controls, hiding older or additional data.
- Expected user behavior: Reach all eligible results and understand whether more exist.
- Actual/observed behavior: Browser synthetic response has hasMore=true and nextCursor, but no next/load-more action or cursor request. Source extends concern to native discovery and multiple queues; those extensions remain code-supported.
- Evidence/confidence: **VERIFIED**; [evidence/browser-probes.json](evidence/browser-probes.json). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Clients request fixed first pages without next-cursor controls, hiding older or additional data. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Consume bounded cursor pages with explicit loading/retry/end status; reset on filter change. Do not present kitchens derived from one meal page as complete discovery.
- Suggested implementation files: [apps/customer-web/src/Browse.tsx](../../../apps/customer-web/src/Browse.tsx), [apps/customer-web/src/Orders.tsx](../../../apps/customer-web/src/Orders.tsx), [apps/customer-web/src/Account.tsx](../../../apps/customer-web/src/Account.tsx), [apps/operations-web/src/cook/Orders.tsx](../../../apps/operations-web/src/cook/Orders.tsx), [apps/operations-web/src/cook/Statement.tsx](../../../apps/operations-web/src/cook/Statement.tsx), [apps/customer-mobile/src/screens/Discover.tsx](../../../apps/customer-mobile/src/screens/Discover.tsx).
- Acceptance criteria: Every hasMore page reachable; no duplicates/omissions; stale cursor discarded; counts labelled as loaded vs total.
- Regression test: **UX-T006** — Seed more than each endpoint page limit; retrieve last item; change filters during slow pagination; handle empty/failed next page.

## UX-007

**Active commerce configuration has not reconciled free-launch direction**

- Affected screen and persona: Statements/configuration/quote/ledger; kitchens/customers/finance.
- Problem: Latest owner zero-commission launch direction diverges from commission-capable active policy, fixtures and wording.
- Expected user behavior: All Matbakhy platform charges remain zero/inactive at launch; external costs distinct.
- Actual/observed behavior: Active implementation retains commission calculations/statement fields and synthetic 10% examples; latest main explicitly requires zero across server/ledger/UI. No production fee or charge was observed.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Latest owner zero-commission launch direction diverges from commission-capable active policy, fixtures and wording. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Reconcile authority into existing stories and server/ledger launch configuration, fixtures and labels; essential earnings free; keep future paid growth gated off.
- Suggested implementation files: [backend/app/src/main/java/com/matbakhy/app/config/CommerceConfiguration.java](../../../backend/app/src/main/java/com/matbakhy/app/config/CommerceConfiguration.java), [backend/modules/orders/src/main/java/com/matbakhy/orders/internal/CommercePolicies.java](../../../backend/modules/orders/src/main/java/com/matbakhy/orders/internal/CommercePolicies.java), [apps/operations-web/src/cook/Statement.tsx](../../../apps/operations-web/src/cook/Statement.tsx), [apps/operations-web/src/ops/Configuration.tsx](../../../apps/operations-web/src/ops/Configuration.tsx), [planning/decisions.json](../../../planning/decisions.json), [planning/scenarios.json](../../../planning/scenarios.json).
- Acceptance criteria: No hidden fees/paid rank/billing; no misleading profit label; placed snapshots untouched; future activation requires owner authorization.
- Regression test: **UX-T007** — Launch quote→fulfilled order→statement/refund: platform charge/commission zero in each layer; try unauthorized paid activation.

## UX-008

**Approved direct-deposit journey is not integrated**

- Affected screen and persona: Checkout/status/cook/finance; deposit-eligible order.
- Problem: Cash-first clients/contracts do not express latest approved manual direct-to-kitchen deposit flow.
- Expected user behavior: See total/deposit/remainder/recipient/expiry and honest receipt-report status with recovery.
- Actual/observed behavior: Latest main strategy and sequence approve manual deposit product scope; active Checkout/Cart/order state contract remains cash-first. This is incomplete approved scope, not a failed live payment.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Cash-first clients/contracts do not express latest approved manual direct-to-kitchen deposit flow. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **L**. Status: **OPEN**.
- Recommended solution: First reconcile state/contract stories; deliver synthetic vertical slices. Separate customer transfer report, kitchen receipt report and acceptance; never claim bank verification or request automatic duplicate transfer.
- Suggested implementation files: [contracts/openapi.yaml](../../../contracts/openapi.yaml), [backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java](../../../backend/modules/orders/src/main/java/com/matbakhy/orders/internal/QuoteService.java), [apps/customer-web/src/Checkout.tsx](../../../apps/customer-web/src/Checkout.tsx), [apps/customer-mobile/src/screens/Cart.tsx](../../../apps/customer-mobile/src/screens/Cart.tsx), [apps/operations-web/src/cook/Orders.tsx](../../../apps/operations-web/src/cook/Orders.tsx), [apps/operations-web/src/ops/Finance.tsx](../../../apps/operations-web/src/ops/Finance.tsx), [planning/backlog.json](../../../planning/backlog.json).
- Acceptance criteria: Preparation blocked until receipt acknowledged AND order accepted; price immutable after placement; remaining amount exact; uncertainty has reconciliation path; live-money gates remain blocked.
- Regression test: **UX-T008** — COD plus deposit fixtures: mismatch/false proof/expiry/delayed transfer/cancellation/kitchen rejection/manual refund; prep gate and concurrency.

## UX-009

**Policy acceptance has no readable policy destination**

- Affected screen and persona: Checkout; customer.
- Problem: Terms checkbox has plain text while web/native routers provide no terms/privacy/help document screen.
- Expected user behavior: Read the exact policies before accepting and return without losing basket.
- Actual/observed behavior: Checkout.tsx:386–388; native Cart choice; App route dispatcher and native navigation have no policy route. Approved legal text availability is unverified.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Terms checkbox has plain text while web/native routers provide no terms/privacy/help document screen. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P1**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Expose versioned policy content adjacent to consent with readable Arabic; clearly draft in local environments and gate release on approved copy.
- Suggested implementation files: [apps/customer-web/src/Checkout.tsx](../../../apps/customer-web/src/Checkout.tsx), [apps/customer-web/src/App.tsx](../../../apps/customer-web/src/App.tsx), [apps/customer-mobile/src/screens/Cart.tsx](../../../apps/customer-mobile/src/screens/Cart.tsx), [apps/customer-mobile/src/screens/navigation.ts](../../../apps/customer-mobile/src/screens/navigation.ts), [contracts/openapi.yaml](../../../contracts/openapi.yaml).
- Acceptance criteria: Accepted version matches readable content; changed version resets consent; draft never presented as approved legal policy.
- Regression test: **UX-T009** — Open/read/return with keyboard and native screen reader; change policy version after acceptance and retry.

## UX-010

**Order again only opens the first dish**

- Affected screen and persona: Order detail; returning customer/family.
- Problem: Reorder shortcut drops all other prior lines and quantities.
- Expected user behavior: Review the whole previous basket using current availability/prices.
- Actual/observed behavior: Web Orders.tsx:124 and native Orders.tsx:196/238 navigate to items[0].mealId.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Reorder shortcut drops all other prior lines and quantities. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Build a current multi-line reorder review with unavailable-line explanation and common slot; until then name first-dish shortcut accurately.
- Suggested implementation files: [apps/customer-web/src/Orders.tsx](../../../apps/customer-web/src/Orders.tsx), [apps/customer-web/src/cart.ts](../../../apps/customer-web/src/cart.ts), [apps/customer-mobile/src/screens/Orders.tsx](../../../apps/customer-mobile/src/screens/Orders.tsx), [apps/customer-mobile/src/basket.ts](../../../apps/customer-mobile/src/basket.ts).
- Acceptance criteria: Available quantities retained for review; unavailable items named, no silent substitutions; replacement consent and fresh quote mandatory.
- Regression test: **UX-T010** — Three-line historic order with changed price, archived line and existing conflicting basket.

## UX-011

**Navigation loses search and working context**

- Affected screen and persona: Browse/detail and operations tabs; customer/cook/staff.
- Problem: Returning from meal detail resets discovery query; operations selection also lives only in memory.
- Expected user behavior: Back/reload/share should recover meaningful permitted working context.
- Actual/observed behavior: Browser search→meal→in-page Back resets text and sends default query. Operations tab/reload behavior is code-supported.
- Evidence/confidence: **VERIFIED**; [evidence/browser-probes.json](evidence/browser-probes.json). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Returning from meal detail resets discovery query; operations selection also lives only in memory. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Serialize allowlisted filter/sort/cursor/tab/object state in existing navigation and restore scroll where useful; recheck access.
- Suggested implementation files: [apps/customer-web/src/App.tsx](../../../apps/customer-web/src/App.tsx), [apps/customer-web/src/Browse.tsx](../../../apps/customer-web/src/Browse.tsx), [apps/customer-web/src/Meal.tsx](../../../apps/customer-web/src/Meal.tsx), [apps/operations-web/src/App.tsx](../../../apps/operations-web/src/App.tsx).
- Acceptance criteria: Supported filters/locale persist; permitted object opens; no cross-role leakage; approved layout unchanged.
- Regression test: **UX-T011** — Filter→detail→Back/browser back/reload; ops select order→copy link/reload; invalid and forbidden state.

## UX-012

**Native discovery lacks supported decision information**

- Affected screen and persona: Native Discover/Meal; first-time and comparing customers.
- Problem: Only text search/newest available; missing supported zone/category/price/sort and kitchen/photo/portion/slot detail.
- Expected user behavior: Find serviceable meals and compare genuine information on native as on web.
- Actual/observed behavior: Discover.tsx:18,57–70 and navigation lack filter/kitchen screen; Meal presentation omits photo/reviews.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Only text search/newest available; missing supported zone/category/price/sort and kitchen/photo/portion/slot detail. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Reuse current API capability and approved hierarchy, with native filter/kitchen navigation; keep person-count filters D11-deferred.
- Suggested implementation files: [apps/customer-mobile/src/screens/Discover.tsx](../../../apps/customer-mobile/src/screens/Discover.tsx), [apps/customer-mobile/src/screens/Meal.tsx](../../../apps/customer-mobile/src/screens/Meal.tsx), [apps/customer-mobile/src/screens/navigation.ts](../../../apps/customer-mobile/src/screens/navigation.ts), [apps/customer-mobile/src/App.tsx](../../../apps/customer-mobile/src/App.tsx).
- Acceptance criteria: Native displays real portion/photo/next-slot where available, reachable kitchen context and supported filters; no invented catering or serving-person feature.
- Regression test: **UX-T012** — Same supported query web/native yields equivalent eligible results; large-text/manual-zone/permission-denied flows.

## UX-013

**Kitchen cannot maintain general meal or slot content**

- Affected screen and persona: Cook Meals/Slots; managing kitchen.
- Problem: Creation/state/price actions exist but general content and existing capacity editing are absent.
- Expected user behavior: Correct meals and future capacity without recreating objects.
- Actual/observed behavior: Meals.tsx:36–71 and Slots.tsx:45–77 have no general editor; existing PATCH contracts support bounded updates.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Creation/state/price actions exist but general content and existing capacity editing are absent. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Expose existing edit contracts with expectedVersion, dirty-state protection and obligation explanation; preserve price history and order snapshots.
- Suggested implementation files: [apps/operations-web/src/cook/Meals.tsx](../../../apps/operations-web/src/cook/Meals.tsx), [apps/operations-web/src/cook/Slots.tsx](../../../apps/operations-web/src/cook/Slots.tsx), [apps/operations-web/src/i18n.ts](../../../apps/operations-web/src/i18n.ts).
- Acceptance criteria: Valid edit persists; conflict preserves input; below-obligation edit rejected with useful reason; history immutable.
- Regression test: **UX-T013** — Edit ingredient/portion/photo, change capacity above and below obligations, simulate concurrent editor.

## UX-014

**Field-level server validation is discarded**

- Affected screen and persona: Address/meal/operations forms; customer/cook.
- Problem: Shared mutation handlers reduce typed fieldErrors to a generic message.
- Expected user behavior: Identify and correct the exact failed field with preserved input.
- Actual/observed behavior: Customer shared.tsx:92–94 and operations equivalent ignore fieldErrors; forms largely lack error association.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-ARCHITECTURE-REVIEW.md](UX-ARCHITECTURE-REVIEW.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Shared mutation handlers reduce typed fieldErrors to a generic message. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Retain typed errors, map localized messages, associate aria-describedby/invalid, focus summary or first invalid field; avoid universal form-framework rewrite.
- Suggested implementation files: [apps/customer-web/src/shared.tsx](../../../apps/customer-web/src/shared.tsx), [apps/customer-web/src/Checkout.tsx](../../../apps/customer-web/src/Checkout.tsx), [apps/operations-web/src/cook/shared.tsx](../../../apps/operations-web/src/cook/shared.tsx), [apps/operations-web/src/cook/Meals.tsx](../../../apps/operations-web/src/cook/Meals.tsx).
- Acceptance criteria: Each invalid field named and associated; no valid input lost; generic fallback remains for unknown errors.
- Regression test: **UX-T014** — 422 with two fields, server conflict and network retry; keyboard/screen-reader focus and preserved values.

## UX-015

**Arabic numeric input is rejected or erased**

- Affected screen and persona: Budget/price and OTP; Arabic customers/cooks.
- Problem: Arabic and Persian digits are not normalized before ASCII-only parsing/sanitization.
- Expected user behavior: Enter familiar digits without losing intended number or verification code.
- Actual/observed behavior: Browser maximum price ١٢٠ disables submit; source egpToMinor rejects localized digits and OTP replace(/\D/g,"") removes them. OTP extent is code-supported.
- Evidence/confidence: **VERIFIED**; [evidence/browser-probes.json](evidence/browser-probes.json). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Arabic and Persian digits are not normalized before ASCII-only parsing/sanitization. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Normalize Arabic/Persian decimal digits and supported decimal separator at boundary, then apply existing integer-money bounds; keep opaque IDs unchanged.
- Suggested implementation files: [apps/customer-web/src/shared.tsx](../../../apps/customer-web/src/shared.tsx), [apps/customer-web/src/Checkout.tsx](../../../apps/customer-web/src/Checkout.tsx), [apps/operations-web/src/cook/shared.tsx](../../../apps/operations-web/src/cook/shared.tsx), [apps/customer-mobile/src/screens/Cart.tsx](../../../apps/customer-mobile/src/screens/Cart.tsx).
- Acceptance criteria: Equivalent inputs produce same minor units/code; invalid values explained; no float-based money or weakened OTP validation.
- Regression test: **UX-T015** — ASCII/Arabic/Persian budget and pasted OTP; invalid punctuation, huge number, negative and mixed input cases.

## UX-016

**Finance requires a kitchen UUID**

- Affected screen and persona: Operations Finance; finance operator.
- Problem: User must know an internal identifier before viewing a statement.
- Expected user behavior: Select a permitted named kitchen or follow an order/kitchen context link.
- Actual/observed behavior: Finance.tsx:6–25 validates raw UUID entry with no kitchen search/lookup.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-ARCHITECTURE-REVIEW.md](UX-ARCHITECTURE-REVIEW.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: User must know an internal identifier before viewing a statement. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Add permission-scoped named kitchen selector and context links; retain opaque ID internally and server authorization.
- Suggested implementation files: [apps/operations-web/src/ops/Finance.tsx](../../../apps/operations-web/src/ops/Finance.tsx), [apps/operations-web/src/App.tsx](../../../apps/operations-web/src/App.tsx), [apps/operations-web/src/ops/Reviews.tsx](../../../apps/operations-web/src/ops/Reviews.tsx).
- Acceptance criteria: Operator can open intended statement without copying UUID; labels disambiguate; unauthorized data stays unavailable.
- Regression test: **UX-T016** — Select similarly named kitchens with distinct stable labels; forbidden kitchen and network failure; keyboard selection.

## UX-017

**Native controls use low-contrast decorative borders**

- Affected screen and persona: Native forms/options; low-vision customer.
- Problem: Essential controls use decorative border token against their surfaces.
- Expected user behavior: Control boundaries remain distinguishable at WCAG non-text contrast target.
- Actual/observed behavior: ui.tsx:173/179/183; computed token border/surface ratio 1.702:1 (<3:1). Pixel/device result remains unverified.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-ARCHITECTURE-REVIEW.md](UX-ARCHITECTURE-REVIEW.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Essential controls use decorative border token against their surfaces. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Use existing borderStrong for essential boundaries; preserve selected/focus text and state, verify actual composited native rendering.
- Suggested implementation files: [apps/customer-mobile/src/ui.tsx](../../../apps/customer-mobile/src/ui.tsx), [packages/design-tokens/tokens.json](../../../packages/design-tokens/tokens.json).
- Acceptance criteria: Essential boundaries reach 3:1 or an adequate alternative visible boundary; text contrast and touch targets retained.
- Regression test: **UX-T017** — Measure actual native normal/focus/selected input and choices against adjacent colors with large text.

## UX-018

**Order status omits useful deadline/reason/support context**

- Affected screen and persona: Order detail; waiting or rejected customer.
- Problem: State timeline lacks acceptBy and event reason; new web support requires a detour through Account.
- Expected user behavior: Know what happens next, by when, why an exception occurred and how to get order-linked help.
- Actual/observed behavior: Web Orders.tsx:45–85 and native Orders.tsx:114–158; support links only existing cases; Account selects first-page orders.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-RESEARCH-FINDINGS.md](UX-RESEARCH-FINDINGS.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: State timeline lacks acceptBy and event reason; new web support requires a detour through Account. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **M**. Status: **OPEN**.
- Recommended solution: Show localized real deadline/reason/next action and create-case entry prefilled with current order. Keep payment/fulfillment separate and private notes excluded.
- Suggested implementation files: [apps/customer-web/src/Orders.tsx](../../../apps/customer-web/src/Orders.tsx), [apps/customer-web/src/Account.tsx](../../../apps/customer-web/src/Account.tsx), [apps/customer-mobile/src/screens/Orders.tsx](../../../apps/customer-mobile/src/screens/Orders.tsx), [apps/customer-mobile/src/screens/Support.tsx](../../../apps/customer-mobile/src/screens/Support.tsx).
- Acceptance criteria: No invented ETA/GPS; pending distinguished from accepted; explanation and help reachable on relevant order.
- Regression test: **UX-T018** — Pending/expired/rejected/delivery-failed fixtures, stale notification and foreign order; accessible help path.

## UX-019

**Pending sign-out dialog does not manage focus**

- Affected screen and persona: Customer pending-order sign-out; keyboard/screen-reader user.
- Problem: Alertdialog renders after the active header control without intentional focus/return handling.
- Expected user behavior: Discover the warning and safely choose recovery, stay or leave using keyboard.
- Actual/observed behavior: App.tsx:157–177 alertdialog has no focus ref/effect; contrast with meal replacement managed focus.
- Evidence/confidence: **CODE-SUPPORTED**; [UX-ARCHITECTURE-REVIEW.md](UX-ARCHITECTURE-REVIEW.md). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Alertdialog renders after the active header control without intentional focus/return handling. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Apply existing dialog focus pattern; safest action first, sensible Escape and restoration; preserve pending keys when staying or sign-out fails.
- Suggested implementation files: [apps/customer-web/src/App.tsx](../../../apps/customer-web/src/App.tsx), [apps/customer-web/src/App.test.tsx](../../../apps/customer-web/src/App.test.tsx), [apps/operations-web/e2e/keyboard.e2e.ts](../../../apps/operations-web/e2e/keyboard.e2e.ts).
- Acceptance criteria: Warning immediately reachable/announced; no accidental placement/sign-out; focus returns predictably; pending recovery unchanged.
- Regression test: **UX-T019** — With pending request keyboard-activate sign-out, inspect announced focus, cancel/escape/leave and failed logout.

## UX-020

**Unknown customer routes render an empty main area**

- Affected screen and persona: Unknown hash route; customer following old link.
- Problem: Unmatched route has no explanation or recovery link in main.
- Expected user behavior: Understand link is unavailable and navigate safely to browsing/orders.
- Actual/observed behavior: Browser #/missing-audit-route produces empty main; App only enumerates known matches.
- Evidence/confidence: **VERIFIED**; [evidence/browser-probes.json](evidence/browser-probes.json). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Unmatched route has no explanation or recovery link in main. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P3**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Add localized not-found state with safe Browse/Orders destinations and focusable heading; do not expose object existence.
- Suggested implementation files: [apps/customer-web/src/App.tsx](../../../apps/customer-web/src/App.tsx), [apps/customer-web/src/i18n.ts](../../../apps/customer-web/src/i18n.ts).
- Acceptance criteria: Unknown route explains recovery; known routes unaffected; object authorization behavior retained.
- Regression test: **UX-T020** — Malformed route, retired link, valid route, forbidden order and refresh.

## UX-021

**Narrow financial summaries break monetary tokens**

- Affected screen and persona: Cook Statement/ops Finance; phone user.
- Problem: Long labels and summary grid can squeeze currency amounts into vertical fragments.
- Expected user behavior: Read an intact amount/currency and map each value to the right label.
- Actual/observed behavior: Current Chromium synthetic-statement probe reproduced split monetary digits in cook AR320, EN320 and EN390; desktop1440 and AR390 intact for this fixture. Existing MBQA-006 documents additional historical finance variants; those were not rerun here.
- Evidence/confidence: **VERIFIED**; [money-reflow-current.json](evidence/money-reflow-current.json). Inspection is at the pinned baseline; fixture/component evidence does not prove server/native behavior.
- User impact: Long labels and summary grid can squeeze currency amounts into vertical fragments. This can prevent or complicate the task stated above; prevalence and conversion effect are unmeasured.
- Priority: **P2**. Complexity: **S**. Status: **OPEN**.
- Recommended solution: Reuse historical MBQA-006; stack summary rows at narrow width or reserve min-content monetary width; preserve semantic table/scroll region and bidi isolation.
- Suggested implementation files: [apps/operations-web/src/app.css](../../../apps/operations-web/src/app.css), [apps/operations-web/src/cook/Statement.tsx](../../../apps/operations-web/src/cook/Statement.tsx), [apps/operations-web/src/ops/Finance.tsx](../../../apps/operations-web/src/ops/Finance.tsx).
- Acceptance criteria: Digits/decimal token stay intact; no label-value ambiguity; no horizontal page overflow; exact API values preserved.
- Regression test: **UX-T021** — Repeat cook/finance ×320/390/1440×AR/EN with decimals, long labels and keyboard scroll.
