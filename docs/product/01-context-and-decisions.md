# Context, confidence and decision register

Prepared 2026-10-11. This is a reconstruction from the current owner request, retrieved prior project discussions, and inspection of the supplied repository. It is not a claim that every historical conversation was retrieved.

## Confirmed requirements

| ID | Requirement | Basis |
|---|---|---|
| C01 | Matbakhy operates in Egypt | Owner correction on 2026-04-28: Egypt, not Kuwait |
| C02 | Shared Spring Boot backend; Android and iOS customer apps | Owner mobile discussion on 2026-04-28 and current request |
| C03 | Customer web and backoffice operations are part of this MVP | Current request, 2026-10-11 |
| C04 | AI-assisted implementation, detailed enough for Claude Code to execute | Current request; prior preference for minimal manual frontend/mobile work |
| C05 | Repeated verification, correction, continuation and durable repository knowledge | Current request |
| C06 | Branding/logo/slogan/trademark consideration starts early and remains editable | Owner direction on 2026-04-28 |
| C07 | Deliver the planning material to afawzy70/Matbakhy-v1 | Current request |

The recurring recovered product concept is a marketplace connecting home cooks and customers ordering homemade meals. It is the planning basis. Specific transactional/commercial policies below are proposed defaults, not recovered approvals.

## Proposals that must not be misrepresented as prior decisions

- Early Kuwait plans included KNET, Kuwait payment/delivery vendors and Kuwait currency. Egypt supersedes that geography; those integrations are not carried over.
- Earlier React/Material UI, React/Tailwind/shadcn, Flutter and FlutterFlow options were recommendations, not a final approved technology baseline.
- July's Bolt exercise proposed a social-discovery prototype with mock data, follows, likes, comments and order requests without payments/delivery. A demonstration scope does not establish production transaction policy.
- Current instruction explicitly adds backoffice and mobile to the MVP. Earlier suggestions to defer them no longer define the deliverable.
- No confirmed city, delivery contract, commission rate, legal entity, tax policy, launch budget, identity vendor or payment provider was recovered.
- No usable existing product code, approved visual system or deployed environment was found in this repository.
- Instructions recovered for other projects, such as a developer-console visual direction, are not imported into Matbakhy.

## Proposed build baseline

| Decision | Default for implementation | Why / change boundary |
|---|---|---|
| D01 Architecture | Modular monolith, PostgreSQL, containers | Transaction safety and low operational complexity; split services only with measured need |
| D02 Web | React + TypeScript, separate customer and operations apps | Shared contract/client packages; role-specific UI |
| D03 Mobile | React Native + Expo, source controlled here | CLI-based AI workflow; share contracts/types, not all UI; Flutter remains an owner-selectable alternative before P01 toolchain lock |
| D04 Ordering | Transactional marketplace; one cook + one dated slot per order | Clear capacity/pricing/cancellation semantics |
| D05 Payments | **Owner-updated 2026-10-13:** COD/pickup where eligible, plus manual direct customer-to-cook deposit via established Egyptian payment rails (InstaPay/mobile wallets/bank transfers), with cook-reported receipt confirmation | No Matbakhy custody or custom wallet; implementation & live activation gated by permissible merchant accounts, Egyptian legal/consumer/privacy review and truthful receipts/refund workflow. See [MVP direct-payment decision](../strategy/mvp-direct-deposit-payment.md) |
| D06 Fulfillment | Cook-arranged delivery to configured zones, or customer pickup | No driver app, live driver map or courier API required |
| D07 Social | Favorites and shareable public links; feed/comments/follows deferred | Validate ordering first; preserve social proposal as an extension |
| D08 Identity | OIDC provider (local Keycloak reference), email sign-in; verified phone at first checkout | Avoid custom password/token infrastructure; mobile PKCE; production IdP/SMS selection is a launch dependency |
| D09 Locales | Arabic default, English available; EGP; Africa/Cairo | Applies to web, mobile, notifications and ops |
| D10 Commercial model | Configurable per-order commission; no customer service fee in baseline | Commission remains provisional; configure before live ordering |

These choices authorize a coherent local/staging implementation plan when the owner hands the start prompt to Claude. They do not approve financial terms, legal text, branding, provider contracts or production publication.

## Open decisions with a bounded impact

Machine-readable status, owners and deadlines are in [decisions.json](../../planning/decisions.json).

| ID | Owner input needed | Latest gate / allowed work meanwhile |
|---|---|---|
| O01 | Launch city, exact service zones and pickup policy | Before live serviceability config; synthetic zones can be built now |
| O02 | **Commercial direction confirmed:** COD where eligible + direct-to-cook manual deposit via familiar methods. **Open:** permitted merchant recipient, disclosure/refund/confirmation process and legal approval | Before checkout release scope freeze; implementable flow can be planned/tested with fakes; no real collections before approval |
| O03 | Who delivers, collects cash, pays delivery costs and carries failed-delivery cost | Before live fulfillment; baseline cook-delivery can be tested with fixtures |
| O04 | Commission, refund/cancellation rules, taxes, invoices, settlement cycle | Before financial release; use clearly labelled synthetic policies |
| O05 | Brand name clearance, logo, typography, approved screen direction | Before final branded UI; create reviewable prototype, continue backend |
| O06 | Food-business onboarding, licensing/document requirements, allergens process, privacy/retention and consumer terms | Before public pilot; owner obtains current local professional review |
| O07 | SMS/identity/email/push providers and credentials, hosting/budget/domain | Before staging integrations/live operations; adapters may use isolated fakes |
| O08 | Apple/Google developer accounts, identifiers, signing access and testers | Before signed beta/store release; simulators/emulators can proceed |
| O09 | Customer support contact, business hours, operators and emergency escalation | Before pilot |
| O10 | Confirm whether social feed/follows/comments are launch-critical | P00 scope review; default deferred, never describe as previously rejected |

Resolve decisions by recording actual owner text/reference, date, status, affected requirements, migration/contract implications and approval scope. Do not put personal transcripts or private credentials in this public repository. A missing answer blocks only dependent work.

## Owner decision — 2026-10-13: familiar payment rails in MVP

The owner approves adding a **manual direct-to-cook deposit option** (InstaPay and eligible Egyptian mobile wallets/bank transfers) alongside eligible cash on delivery/pickup to the MVP functional scope. This replaces cash-only as the exclusive checkout path; it does **not** assert production legal or payment-provider clearance. No proprietary wallet, platform custody, escrow or automated gateway required for the initial controlled pilot. The cook must independently see and acknowledge incoming funds; Matbakhy records the cook's statement rather than claiming bank verification. Preparation for deposit-required orders is blocked pending acknowledgement and cook acceptance. Disputes, no-show/cancellation, refunds and complete price snapshots must remain auditable. No unilateral rewrite of already placed orders. For details and explicit release gates see [MVP direct-payment decision](../strategy/mvp-direct-deposit-payment.md). AI Concierge remains future-only (issue #4); advanced guarantee/insurance and automated payment reconciliation remain future-only (issue #3).
