# MB-028 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
Favorites, verified-order reviews, review reports and staff review moderation (backend, Flyway V20); moderation queue API-098 added to the contract and endpoint registry; customer web account page (profile name and preferred language, favorites, inbox, support cases, addresses, deletion request), meal page favorite toggle, share link and kitchen reviews with report, order page review and "order again"; operations web review queue in the catalogue tab; Playwright journey extended.

## Findings
1. Reviews (BR-039): only the order's customer, only COMPLETED orders, once per order (unique constraint, 409 on repeat), integer 1..5; hidden reviews leave public lists and ratings (integration test and browser journey). Public review lists carry no order or customer data.
2. Favorites (BR-040): PUT/DELETE are idempotent; unavailable meals stay listed with `available` false and are not linked in the account page; private orders stay owner-only (404 for others, 401 anonymous).
3. Reports are once per reporter and review, rate-limited, and the staff queue exposes counts and reasons only, never reporter identities (asserted in the integration test).
4. Moderation requires `content.moderate`, a reason and the current version; every change is audited (integration test; browser journey checks the audit row).
5. Basket replacement (BR-018 client part, TC-147): adding a meal of another kitchen or slot asks before replacing; keeping leaves the basket unchanged; replacement is refused while a placement from the current basket is pending (component tests and browser journey).
6. "Order again" opens the meal page so a new server quote applies today's price; the client never stores prices.

## Residual
- Visual design uses draft tokens until O05; P06 gate remains blocked on O05.
- Deletion request uses the draft retention policy version (`retention-policy-draft`) until the retention policy is approved.
- Share links are the public meal page address; no kitchen profile page in the customer web yet.
- Notification channel preferences have no API in the contract; only language preference is offered.
- Accessibility audit is MB-029.
- The customer screens use the approved palette and Cairo via tokens, but their layout still follows the earlier draft; restyling to the approved homepage hierarchy (intent tiles, stacked food cards, dishes/kitchens switch, bottom navigation) is remaining UI work.

## Evidence attempts
- Attempt 1 at `fe94d8b`: BACKEND-VERIFY, WORKSPACE-CLEAN-CHECKOUT, LOCAL-STACK, BROWSER-E2E and SECRET-SCAN PASS; GUARDRAIL-TESTS FAIL (`test_scope`: traceability matrix stale after adding TC-147/TC-148). Repaired in `cb64230` by regenerating the matrix; the full suite is re-run at the repaired commit.
