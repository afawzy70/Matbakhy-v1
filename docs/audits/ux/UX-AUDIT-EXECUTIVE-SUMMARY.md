# Matbakhy independent UX audit — owner and engineering summary

2026-10-13 · Application inspected: `cc52fa4224e3163677a1215dc8f63ede2a9dbe4c` on `implementation/mvp`. Newer commercial owner direction compared at main `047f42eebfcfc904622a123bbb8bdf9b2a16cda8`. Audit branch: `audit/ux-independent-implementation-2026-10-13`. This is a documentation-only audit, not an application repair or release approval.

Matbakhy has substantial implemented customer web/native, kitchen and back-office workflows. The strongest immediate opportunities are reliable price-change consent, accurate allergen communication, kitchen self-service and timely order handling. Retain the approved terracotta/Cairo direction, existing components and server-owned price/capacity/order invariants. Evidence does not justify a wholesale redesign.

**21 OPEN findings: P0 0 · P1 9 · P2 11 · P3 1.** Seven are VERIFIED within stated browser/component fixture boundaries; fourteen are CODE-SUPPORTED. Priorities balance task impact, likely exposure, confidence, effort and business obligations; no analytics-based frequency or conversion uplift is asserted. P1 means address before the affected flow is relied on for launch; absence of P0 is not release certification.

## Ten recommended next actions

1. **UX-001, P1/S:** keep changed-price consent pending when a customer recalculates a quote. The component reproducer enables a new POST without clicking Accept new total. Preserve accepted-order snapshots and uncertain-placement recovery.
2. **UX-002/003, P1/M+S:** let kitchens declare actual allergens, and translate/display all allergen information in the native meal screen. Do not default uncertainty into a reassuring claim.
3. **UX-004, P1/M:** connect the existing onboarding API to an actionable applicant workflow; the no-application UI currently has no next action.
4. **UX-005, P1/M:** refresh active cook queues/dashboard on a bounded interval and foreground return, with a visible freshness state and duplicate-action protection.
5. **UX-006, P1/M:** expose cursor continuation on discovery and affected lists, preserving filters and permission boundaries.
6. **UX-007, P1/M:** reconcile server/configuration/ledger/UI with the owner's zero-platform-charge launch direction. Code still expresses commission concepts; no live charge is alleged.
7. **UX-008, P1/L:** deliver the approved manual direct-to-kitchen deposit scope in separately reviewed contract/UI/reconciliation slices. No real-money activation is authorized by this audit.
8. **UX-009, P1/M:** make the exact policy version readable from checkout before acceptance and preserve the basket on return. Missing approved legal copy remains a release dependency.
9. **UX-021, P2/S:** prevent narrow financial layouts from splitting monetary digits; reuse historical issue MBQA-006 rather than duplicating it.
10. **UX-010/011, P2/M each:** make reorder truthful/useful for all prior lines and retain discovery context across detail/back navigation. Revalidate availability and current prices before a new order.

## Coverage and product status

| Surface | Inspected screens and status |
|---|---|
| Customer web | Eight implemented route families: discovery `#/`, meal `#/meal/:uuid`, kitchen `#/kitchen/:slug`, checkout `#/checkout`, orders `#/orders`, order detail `#/orders/:uuid`, account `#/account`, support `#/support/:uuid`. Account contains favorites, inbox and address/support functions. Real browser suite plus targeted probes exercised these surfaces with local synthetic records. |
| Kitchen web | Dashboard, orders, meals/create/publication/active-history prices, slots, statement, settings; onboarding state screen. Existing suite visits permission-allowed sections; targeted no-application onboarding and statement probes captured concrete issues. Onboarding completion/content maintenance remain incomplete. |
| Back office | Cook application review, catalogue/review moderation, support cases, order exceptions, finance, configuration, staff; visited through permitted panels, not standalone invented routes. |
| Customer mobile | Source review of Discover, Favorites, Orders, Account, Meal, Cart, Order, Notifications and Support. Native runtime/device/accessibility remains UNVERIFIED. |
| Planned/incomplete scope | Manual direct-to-kitchen deposits and zero-charge commercial reconciliation require work against latest owner direction. Bespoke catering, structured person-count filtering and optional paid growth are deferred; no activation or new major flow is authorized. |

The [journey matrix](USER-JOURNEY-MATRIX.md) evaluates all requested customer scenarios and kitchen scenarios in 21 rows: 12 customer plus 9 kitchen, with receiving/processing orders separately assessed. It covers discovery, cognitive load, proposed steps, decision clarity, trust, recovery, navigation, mobile, accessibility and Arabic. Proposed counts are not measured user journeys. No interviews or analytics were invented.

## Execution evidence and limitations

The five existing real-API Chromium tests passed using local synthetic users and orders. Customer component tests passed 41/41; operations passed 18/18. Layout checks exercised Arabic RTL/English LTR across desktop and phone widths; existing keyboard and automated axe checks ran within their documented thresholds. Additional diagnostic probes reproduced seven master findings in actual React rendering with intercepted synthetic APIs or component fixtures. Captured screenshots and exact scope are indexed in [limitations and evidence](UX-AUDIT-LIMITATIONS.md).

Passing happy-path/layout tests does not invalidate the consent, pagination or long-money findings; their triggers are outside those assertions. No native device, assistive-technology, participant, production or live-money testing occurred. Accessibility conformance and conversion gains are not claimed.

## Agent registration and handoff

**Agent registration VERIFIED** for Codex CLI 0.159.2 using strict effective-config discovery. **UX Researcher ACTIVE / UX Architect ACTIVE** for this audit through the hosted runtime's supported explicit-instructions fallback; native custom-role dispatch was not verified. Exactly two upstream agents were installed, preserving original bodies and appending project responsibilities. Pin, installation commands, hashes, reusable definitions and registration evidence are in [agents/README.md](agents/README.md).

Claude has eight bounded implementation batches, with objectives, scope, acceptance criteria, tests, risks, dependencies and verification. Small defect batches can begin from the pinned baseline after normal review; commercial/deposit/policy slices require their stated contract/content dependencies and existing release gates. No batch authorizes a major redesign, paid-growth activation, public deployment or automatic merge. All findings remain OPEN.

## Report index

- [Research findings](UX-RESEARCH-FINDINGS.md)
- [Architecture review](UX-ARCHITECTURE-REVIEW.md)
- [User journey matrix](USER-JOURNEY-MATRIX.md)
- [Stable improvement backlog](UX-IMPROVEMENT-BACKLOG.md)
- [Regression test cases](UX-REGRESSION-TEST-CASES.md)
- [Limitations and evidence](UX-AUDIT-LIMITATIONS.md)
- [Claude implementation handoff](CLAUDE-UX-IMPLEMENTATION-HANDOFF.md)

The dedicated audit branch is intended to target `implementation/mvp` in a draft PR so its review contains audit documentation only. The existing implementation PR #1 is a separate deliverable. Final publication commit/PR are reported by the coordinator; a push does not establish CI success.

Publication-time fetch observed newer implementation head `d35cc133f7d163801a263c8b40c80edd4823f289`; audit results remain pinned to cc52fa4. See the limitations report for the inspected change boundary.
