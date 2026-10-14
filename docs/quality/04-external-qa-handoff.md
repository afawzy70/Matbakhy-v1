# External QA handoff — web journeys (draft, 2026-10-12)

For an external tester working against a **local or owner-approved staging stack with synthetic data only**. Everything below has been exercised by the implementation agent's own automated browser tests (self-review). It has not been through independent QA. Nothing here is deployed, and no real payment, SMS, email or push provider is connected.

## 1. What is in scope

| Area | Web app | Journeys |
|---|---|---|
| Customer | customer web (`http://localhost:5173`) | Browse and search meals; kitchen page; meal page; cart; sign-in; phone verification; addresses; server quote; place order; follow order status; cancel or request cancellation; review a completed order; favorites; notifications inbox; support case; account deletion request |
| Cook | operations web (`http://localhost:5174`) | Dashboard with acceptance deadlines; accept, reject, prepare, ready, dispatch, hand over with the customer's code; cash report; meals with photos; slots and capacity; statement; pause and resume |
| Staff | operations web | Cook applications; catalogue and review moderation; support cases; order monitoring and exception cancellation; finance (refunds, settlements); operating configuration; staff permissions |

Out of scope for this round: Android and iOS apps; screen-reader testing; real providers (SMS, email, push, payments); load and security testing; production data.

## 2. Expected business behaviour (what to check)

The server is the authority; the screens only show what it decides. The numbered rules are in `docs/product/03-business-rules.md`.

- **One kitchen and one slot per order (BR-018).** Adding a meal from another kitchen or slot must ask before replacing the cart; "keep" leaves the cart unchanged.
- **Prices come from the server (BR-019, BR-020).** The meal page total is a preview. The quote in the cart is authoritative, is valid for 5 minutes and reserves no stock. Amounts are whole piastres shown as EGP.
- **Stock (BR-013, BR-025).** Quantities cannot exceed the slot's remaining portions; two customers racing for the last portion must not both succeed.
- **Placing an order is safe to retry (BR-044, BR-023).** A lost response or reload must never create a second order.
- **Order states (BR-026–BR-031).** The cook must accept before a deadline. The customer cancels directly only while pending; after acceptance they can only request cancellation. Completion needs the customer's single-use handover code. Delivery goes READY → OUT_FOR_DELIVERY → COMPLETED.
- **Cash (BR-032–BR-036).** Completing an order does not mean cash was collected. The cook's cash report and finance verification are separate steps. A refund cannot exceed verified collection.
- **Reviews and favorites (BR-039, BR-040).** Only the customer of a COMPLETED order can review it, once, 1 to 5. Hidden reviews leave the public list and the rating. Favorites are idempotent, and unavailable meals stay listed as unavailable.
- **Privacy and permissions (BR-006, BR-007, BR-041).** Customers see only their own orders, cases and addresses. Cooks see only their own kitchen. Staff-only case notes never reach customers or cooks. Staff actions need the matching permission; sensitive ones need fresh MFA.
- **Language and layout (BR-046, BR-047).** Arabic right-to-left is the default and English left-to-right is available. At 320–1440 px nothing is cut off and nothing needs sideways scrolling, apart from wide tables, which scroll inside their own box. Every control is reachable and operable by keyboard with a visible focus.
- **Owner decisions.** D11: there is no "one person / two people" filter; its absence is correct. D12: a card shows the meal's own description when it has one.
- **Cook approval.** Approval is operational verification, not a food-safety guarantee. Wording claiming otherwise is a bug.

## 3. Synthetic test data needed

Use only synthetic data. Never use real names, phone numbers, addresses or photos of real people.

- **Accounts (local identity provider):** customer-a and customer-b (customers), cook-a (an approved kitchen), ops-a (staff). The password is set in the local stack configuration and is shared privately, never in tickets.
- **Staff permissions:** granted per test through the staff permissions screen, or by the local setup script. Each grant needs a reason.
- **Phone verification:** the local SMS sender is fake, and codes are not delivered. Testers need a local helper to mark a phone verified, or access to the local fake-SMS log. Real numbers must not be used.
- **Kitchen setup:** one approved kitchen with at least two published meals. At least one meal needs an allergen declaration of UNKNOWN, and one needs a long title and description. Use synthetic photos (solid colours are fine).
- **Slots:** at least two open slots on different days, with small capacities (1–3), to test sold-out and last-portion behaviour.
- **Zones:** one synthetic delivery zone with a fee, and pickup enabled.
- **Policy:** the synthetic commerce policy used in local and CI. A "synthetic policy" banner in the cart is expected there.
- **Resetting:** after each test cycle, run `scripts/dev.sh` with the throwaway stack. Never point tests at a shared database without a reset plan.

## 4. Bug report template

```
Title: [Area] short description of the wrong behaviour

Environment: local / staging (URL) · browser and version · device or window width · language (AR/EN)
Build: the tested commit SHA given with the test round
Account and role: e.g. customer-b / cook-a / ops-a (+ permissions granted)

Steps to reproduce:
1. …
2. …
3. …

Expected (cite the rule, e.g. BR-018, if known):
Actual:

Frequency: always / intermittent (n of m tries)
Severity: critical (money, stock, privacy, safety wording) / high / medium / low
Evidence: screenshot or screen recording; order number (MB-…); case reference (CS-…); approximate time (Africa/Cairo);
          request ID from the error message if shown. No passwords, tokens or real personal data.
```

## 5. Staging readiness

| Need | State | Who decides / cost |
|---|---|---|
| Hosting, domain, TLS, managed PostgreSQL with backups, object storage | **Not chosen (O07 open).** The app runs as containers locally; no staging exists | Owner: provider choice and monthly budget. Expect recurring hosting, database and storage fees |
| Identity provider for staging (Keycloak or managed OIDC) with MFA for staff | Local Keycloak only; MFA not available locally | Owner: choice and any subscription |
| SMS (OTP), email, push providers | Fakes and logging stand-ins only in local/CI; deployed environments refuse to start without real adapters | Owner: providers, sandbox accounts and per-message costs (O07) |
| Malware scanning for uploads | Fake scanner in local/CI only; deployed environments refuse it | Owner: scanner choice |
| Real commerce policy (commission, fees, refunds) | Synthetic in local/CI; real policies need an approval reference (O04) | Owner decision |
| Launch zones and pickup policy | Synthetic zones only (O01) | Owner decision |
| CI | GitHub Actions green on the implementation branch (see `docs/status/latest.md` for the exact revision) | Already in place (public repository) |
| Visual approval | Homepage direction approved; whole-product review pending (O05) | Owner review of the screenshots in `docs/design/review/` |

Conclusion: the code can be run end to end on a local throwaway stack today. **A staging environment needs owner approval of O07 (hosting, budget, domain, providers) before it can be built.** That means recurring costs for hosting, database, storage and messaging, and an identity setup with staff MFA. Nothing has been deployed or purchased.
