# Claude UX implementation handoff

Audited implementation: `cc52fa4224e3163677a1215dc8f63ede2a9dbe4c`, `implementation/mvp`, existing [implementation PR #1](https://github.com/afawzy70/Matbakhy-v1/pull/1). Owner commercial authority also includes main `047f42eebfcfc904622a123bbb8bdf9b2a16cda8`. Reconcile newer work before editing. This audit changes no application source and approves no major redesign, payment activation, paid growth or release. Preserve approved terracotta/Cairo identity and D11–D14. The [backlog](UX-IMPROVEMENT-BACKLOG.md) is the acceptance source and [regression catalogue](UX-REGRESSION-TEST-CASES.md) names cases; all issues are OPEN.

Start by reading repository instructions, latest active branch/checkpoint, main's [owner sequence at pinned SHA](https://github.com/afawzy70/Matbakhy-v1/blob/047f42eebfcfc904622a123bbb8bdf9b2a16cda8/docs/delivery/07-owner-decisions-implementation-sequence.md), prior QA repairs and this audit. Reproduce on a synthetic local stack. Existing audited UI is real, not the planning-only main branch. Never replace the implementation branch with the audit branch or merge an unrelated main/implementation history blindly.

Efforts are focused engineer-day ranges including scoped tests, not delivery promises. Each issue can be its own commit/PR under its batch; do not couple unrelated repairs. Tests below supplement existing story gates. Publish actual tested source SHA, before/after evidence and failures; a passing diagnostic for unwanted behavior does not close a bug. Close FIXED only after implementation and VERIFIED after independent acceptance evidence. Do not mark source-inspected findings as runtime-reproduced.

## Batch 1 — Preserve informed quote consent (UX-001)

- Objective: eliminate the recalculation bypass without changing server pricing or committed snapshots.
- Scope/files: `apps/customer-web/src/Checkout.tsx`, `apps/customer-mobile/src/screens/Cart.tsx`, their existing tests; read the archived audit diagnostic. S, 0.5–1 day.
- Required behavior: consent belongs to the currently displayed changed quote; recalculation, address/mode/quantity/policy changes cannot silently discard the comparison or carry consent to a different quote. Recover a committed unknown response at its original accepted price.
- Acceptance: UX-T001 and UX-G01–02. No placement POST before explicit acknowledgement of the fresh changed quote; no misleading attribution of delivery/tax changes to the cook.
- Tests: copy the scenario from `evidence/consent.probe.test.tsx` with inverse expectations into the normal application regression suite; preserve the original audit probe and log unchanged; existing customer web and native ordering tests; real-API stale-quote/immutable snapshot/backend race tests; browser and native targeted acceptance.
- Risks: resetting pending request keys, double placement, infinite re-quote loops, losing fulfillment details, stale accepted flags. Keep authoritative quote service and idempotency unchanged unless a concrete bug requires it.
- Dependencies: existing D13/MB-049 contracts; no new owner decision.
- Verification: capture old/new disclosure, failed bypass attempts and deliberate acceptance; repeat two consecutive repricings and refresh failure in AR/EN. The present component reproducer is fixture-based, not a backend payment test.

## Batch 2 — Truthful food/allergen information (UX-002, UX-003)

- Objective: let kitchens declare known allergens and expose equivalent understandable information on native.
- Scope/files: operations `cook/Meals.tsx`, operations/native `i18n.ts`, native `screens/Meal.tsx`; existing MealController/contract tests only where needed. M, 2–3 days.
- Required behavior: neutral required declaration; unknown/none/contains are distinct; contains permits existing supported codes and note; customer sees localized names and the kitchen note before purchase.
- Acceptance: UX-T002/003; truthful declaration survives save/reload and reaches both clients. Never infer safety from an ingredient list or fabricate an allergen-free claim.
- Tests: existing catalogue publication/ownership tests, operations component tests, native component tests, real-API create/edit/render and screen-reader/large-text checks.
- Risks: silently changing historical order allergen snapshots, dropping a previous declaration during price-only edits, falsely defaulting to none.
- Dependencies: existing backend declaration shape; approved allergen content/process for live release. Synthetic fixtures can proceed.
- Verification: capture each declaration state in Arabic and English, keyboard selection, missing-code validation and long-note rendering; verify historical accepted order data unchanged.

## Batch 3 — Kitchen self-service completion (UX-004, UX-013)

- Objective: connect the existing onboarding and edit APIs to bounded usable screens.
- Scope/files: operations `cook/Onboarding.tsx`, `Meals.tsx`, `Slots.tsx`, `App.tsx`, `i18n.ts`, existing application/upload/meal/slot contracts. M per independent sub-slice, 3–5 days onboarding and 2–4 days maintenance.
- Required behavior: application draft/save/submit/revise with private upload progress and localized reasons; general meal content and future capacity edit with expectedVersion and obligation explanation.
- Acceptance: UX-T004/013; no SQL required to start an application; no new mandatory document invented; conflict preserves input; capacity cannot fall below obligations.
- Tests: existing CookApplication/Meal/Slot integration suites; operations component/E2E form flow; object-scope/private-media checks, keyboard/mobile/error recovery.
- Risks: leaking private documents, bypassing review/publication, duplicate meals/slots, dropping active/history prices or changing order snapshots.
- Dependencies: UX-002 for allergen editor, UX-014 error mapping can land independently first. Open legal document policy blocks live requirements, not a synthetic draft flow.
- Verification: applicant→review changes→resubmit using real local API; draft correction and capacity conflict at 390/1440 AR/EN; screenshot actual success/error state.

## Batch 4 — Complete and timely operational/discovery lists (UX-005, UX-006)

- Objective: expose later cursor pages and incoming orders without accidental stale actions.
- Scope/files: customer `Browse.tsx`, `Orders.tsx`, `Account.tsx`; operations `cook/Orders.tsx`, `Dashboard.tsx`, `Statement.tsx`; native `Discover.tsx` and other affected lists identified by `hasMore` inspection. M, 3–5 days in list-sized commits.
- Required behavior: explicit next/load-more with loading/retry/end; reset cursor on filters; preserve identity scope and stale-response rejection. Visible cook queue polls on a bounded schedule and refreshes on foreground; direct pending-order context is retained.
- Acceptance: UX-T005/006; item after first page is reachable, no duplicate rows; no loaded-page count called total; new order appears within stated bound and failure/stale state is visible.
- Tests: >page-limit fixtures, filter/pagination races, empty/failed next page, incoming order from second session, accept/expiry race; existing E2E and relevant contract tests.
- Risks: duplicate requests/actions, abandoned timers, cache leakage across roles, inappropriate server load, deriving complete kitchens from one meal page.
- Dependencies: existing cursor contracts; UX-011 may add persistent cursor/filter navigation, but basic paging can ship first.
- Verification: record network cursor calls and last-item visibility; idle/foreground queue with server deadline; keyboard next-page feedback and return focus.

## Batch 5 — Navigation, dialog and recovery (UX-011, UX-019, UX-020)

- Objective: preserve work context and provide safe focus/recovery behavior.
- Scope/files: customer `App.tsx`, `Browse.tsx`, `Meal.tsx`, `i18n.ts`; operations `App.tsx`; existing keyboard tests. M navigation, S for each dialog/not-found repair.
- Required behavior: allowlisted URL state for supported filters/ops tab or object; in-page Back/browser history/reload restore context; pending sign-out warning takes/returns focus; unknown routes explain next action.
- Acceptance: UX-T011/019/020; permitted deep links work without leaking forbidden object existence; no accidental pending-order abandonment.
- Tests: history/reload/copy link, 401/403/404, pending logout success/failure/escape, keyboard-only and axe on changed states.
- Risks: open redirects, breaking OIDC return addresses or current hash routes, stale private state, stealing focus on routine refresh.
- Dependencies: no replacement routing framework required; choose smallest change compatible with existing dispatcher. No homepage redesign.
- Verification: repeat original search-reset and empty-main reproductions and capture restored state; test desktop DOM/visual focus order separately.

## Batch 6 — Readable forms and amounts (UX-014, UX-015, UX-017, UX-021 / MBQA-006)

- Objective: make input errors actionable, localized numbers enterable and monetary/control information readable.
- Scope/files: customer/operations shared form helpers and affected fields, native `ui.tsx`, operations `app.css`, `Statement.tsx`, `Finance.tsx`, existing design tokens. S per localized/border/reflow fix; M field-error integration.
- Required behavior: retain typed fieldErrors and associated localized messages; normalize Arabic/Persian digits before strict parsing; essential native boundaries use adequate contrast; narrow summaries/tables keep numeric tokens intact.
- Acceptance: UX-T014/015/017/021. Preserve exact monetary values and semantic table relationships, not merely zero page overflow.
- Tests: invalid field pairs, keyboard error focus, pasted OTP, locale numeric equivalence, actual native contrast, cook/finance ×320/390/1440×AR/EN money geometry with long labels; text-spacing and zoom.
- Risks: floating-point money, weakening bounds/OTP validation, broadly applying nowrap causing overflow, hiding columns, changing decorative palette globally. Do not use arbitrary fixed dimensions to mask symptoms.
- Dependencies: existing tokens/form contracts. Native assistive tech and actual device verification remain separate readiness requirements.
- Verification: use current `money-reflow-probe.cjs` and historical MBQA-006 scenario; capture before/after token readability plus API parity. The audit reproduced cook variants only; finance must also be checked before closing.

## Batch 7 — Repeat ordering and useful task context (UX-010, UX-012, UX-016, UX-018)

- Objective: retain legitimate repeat-order intent and explain available food/order/finance choices.
- Scope/files: web/native Orders/cart/basket helpers; native Discover/Meal/navigation; web/native Support; operations Finance and context links. Independent M slices, 1–5 days each.
- Required behavior: review all historical lines at current price/availability; native uses supported zone/category/price/sort and genuine kitchen/portion/photo/slot data; order shows real deadline/reason and direct support; finance selects named permitted kitchen.
- Acceptance: UX-T010/012/016/018; unavailable lines explicitly named, common slot required, no silent substitution; no private kitchen fields or fabricated ETA; no manual UUID requirement.
- Tests: three-line reorder, archived food, conflicting basket, changed price, native query parity, pending/expired/rejected status, role-scoped kitchen selector and support privacy.
- Risks: replaying historical price, multi-kitchen order, extending D11 into an unapproved person-count filter, introducing full catering, exposing private contact or staff notes.
- Dependencies: UX-001 consent, UX-006 complete lists; use existing APIs and keep each capability separately deliverable.
- Verification: capture realistic single/couple/family flows, native AR/EN/large text on both platforms and current state transitions. Participant comprehension is still proposed research, not claimed validation.

## Batch 8 — Reconcile approved commercial direction, then synthetic payment slices (UX-007, UX-008, UX-009)

- Objective: align launch pricing and policy disclosure with already confirmed owner direction and integrate the approved manual deposit scope safely.
- Scope/files: machine decision/rule/backlog/scenario registries, commerce configuration/policy and quote/order/finance modules, OpenAPI/generated client, web/native Checkout/Cart/Orders, cook Orders/Statement and ops finance/support; versioned policy view is a future bounded screen. M zero-launch reconciliation, L6–12 days deposit split by dependency; policy read flow M plus copy dependency.
- Required behavior: zero platform commissions/fees/paid ads at launch in server/ledger/UI, external costs disclosed. Eligible COD coexists with direct customer-to-approved-kitchen deposit, receipt assertion and kitchen acknowledgement distinct; acceptance separate; preparation gated; uncertainty reconciled. Read exact policy versions before consent.
- Acceptance: UX-T007/008/009, UX-G01/05/08. No bank-verified/custody/escrow claim, no automated second transfer, no paid-growth activation, immutable order snapshot and historical price truth retained.
- Tests: zero-fee ledger/quote/statement/refund fixtures; transfer mismatch/false receipt/expiry/late receipt/duplicate report/cancel/kitchen refusal/refund with real PostgreSQL concurrency; client state parity and readable policy consent.
- Risks: highest cross-layer scope in this handoff. Do not treat the audit as authority to activate money, legal terms or new monetization. Do not change delivered financial history when correcting launch policy.
- Dependencies/order: (8a) reconcile authority/stories and approved zero policy; (8b) specify contract/state/operational exceptions and readable policy draft; (8c) implement/test backend synthetic slice; (8d) wire clients and rehearse; (8e) leave live provider/legal/kitchen gates blocked until actual authorization. Current no-app-rewrite scope remains intact.
- Verification: publish contract/state diff and exact current source; run focused finance/order integration and end-to-end synthetic scenarios on web/iOS/Android. Owner/legal approval concerns final live terms and activation, not routine reversible preparation.

## Readiness and stop conditions

Batches 1–7 are concrete bounded engineering recommendations; begin only in an authorized implementation session, preserving any newer fixes. Batch8 is ready for reconciliation/design of the already approved scope, not live activation. Full catering, paid ads/growth billing, AI Concierge and major visual redesign are excluded. Do not claim complete WCAG compliance, native parity or release readiness from this audit's Chromium/fixture/component evidence. No automatic merge.
