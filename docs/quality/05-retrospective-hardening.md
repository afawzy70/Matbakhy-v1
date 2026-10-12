# Retrospective test-hardening pass (2026-10-12)

The owner asked for this pass. All tests in it were written **after** the features were implemented, so they are retrospective regression coverage, not test-driven development. Expected behaviour comes from the approved rules (`docs/product/03-business-rules.md`) and the screen specification (`docs/product/05-screen-specification.md`), not from the current code. All work is self-review by the implementation agent; this is not an independent review.

- **Tested application revision:** `eb1a671`. No product code changed during the pass.
- **Final revision with the new tests:** see "Candidate for independent QA" below.

## Method

1. Map each priority rule to the assertions that already test it.
2. Add a test only where a rule had no meaningful assertion.
3. Run each new test against the existing implementation before touching product code.
4. Handle the result:
   - **Pass:** record the test as retrospective coverage.
   - **Fail:** classify the cause as a product defect, a test or setup error, or an unclear requirement.

**Constraints followed:**
- No working code was removed to produce a failing test.
- No assertion was weakened.
- No schema constraint was changed to make a fixture fit.
- SQL fixtures appear only in test setup, never in the workflows under test.

## Requirement-to-test mapping

| Rule | Existing coverage reused | New retrospective coverage |
|---|---|---|
| BR-018 one kitchen and one slot; replacing needs confirmation (TC-018, TC-147, TC-148) | Server: `QuoteIntegrationTest.mixed_kitchens_unoffered_meals_and_invalid_baskets_are_rejected`. Web unit: replace prompt for another kitchen (keep, Escape, replace) and a pending order blocking replacement. Browser: `customer-journey.e2e.ts`, another slot (keep, then replace). Mobile: `ordering.test.ts` decision rules; `App.test.tsx` native prompt (keep, replace) | Web: same kitchen and slot adds without a prompt, and re-adding a meal updates its line instead of duplicating it (the server refuses duplicate lines). Mobile: same kitchen, another slot asks first |
| CW05 no surprise clearing | Web: lost-response retry keeps the keys | Mobile: a definitive refusal (409/422) keeps the basket and drops the spent keys |
| BR-013 / BR-025 capacity at meal and cook-slot level | Single-meal slot total (quote); 20-customer last-portion race (placement); capacity edits (`SlotIntegrationTest`) | Two meals that together exceed the cook-slot total: refused at quote, and at placement after another order used the slot, with no partial reservation and counters unchanged |
| BR-017 only published, visible meals are purchasable (CW03: cannot add an unavailable selection) | Discovery excludes them; favorites show them unavailable (TC-017); paused kitchen and slot, and cutoff, at quote | Server: paused, archived and moderation-hidden meals cannot be quoted inside an open slot. Web: a sold-out meal cannot be added |
| BR-019 / BR-020 / BR-021 server pricing, quote lifetime, serviceability | Exact totals and forged amounts refused; half-up tax rounding; stale quote refused with a recoverable reason; delivery and pickup without silent conversion | none needed |
| BR-022 / BR-023 / BR-024 / BR-044 atomic placement, no duplicate orders | Rollback on injected failure; retries after timeout and after idempotency retention create one order; key reuse with another body conflicts. Web: keys stored before sending, recovery after reload. Mobile: recovery with the same keys, offline uncertainty | none needed |
| BR-006 object scope | Another customer: order detail, events, list, cancel, cancellation request, review all return 404. Another cook: order and accept return 404 | Another customer cannot find an order through `GET /orders/by-request/{clientRequestId}`; the owner can |
| BR-026 legal, actor-scoped transitions; terminal orders immutable | Stale versions and terminal orders refused; wrong fulfilment path; racing accept, cancel and expire; deadline boundary | The ordering customer cannot run cook commands (accept, reject, prepare, ready, handover), and the cook cannot use the customer cancel; state, version and stock unchanged |
| BR-028 / BR-029 cancellation rules and stock release | Pending orders cancelled directly; after acceptance only a request; consumed stock never released twice | none needed |

## Tests added (all pass against `eb1a671`)

| File | Test |
|---|---|
| `apps/customer-web/src/App.test.tsx` | `adds another meal of the same kitchen and slot without asking, and re-adding a meal updates its line instead of duplicating it (BR-018)` |
| `apps/customer-web/src/App.test.tsx` | `cannot add a sold-out or unavailable meal to the basket (BR-017, CW03)` |
| `apps/customer-mobile/src/ordering.test.ts` | `keeps the basket after a definitive refusal (no surprise clearing) while dropping the spent keys (CW05)` |
| `apps/customer-mobile/src/ordering.test.ts` | `asks before replacing when the same kitchen offers another slot (BR-018)` |
| `backend/.../orders/QuoteIntegrationTest.java` | `lines_of_different_meals_together_cannot_exceed_the_cook_slot_total` |
| `backend/.../orders/QuoteIntegrationTest.java` | `paused_archived_and_hidden_meals_cannot_be_quoted_even_inside_an_open_slot` |
| `backend/.../orders/OrderPlacementIntegrationTest.java` | `a_two_meal_order_is_refused_without_partial_reservation_once_the_kitchen_slot_total_is_used_up` |
| `backend/.../orders/OrderPlacementIntegrationTest.java` | `another_customer_cannot_find_an_order_by_its_client_request_id` |
| `backend/.../orders/OrderLifecycleIntegrationTest.java` | `the_ordering_customer_cannot_run_cook_commands_and_the_cook_cannot_use_the_customer_cancel` |

**Failures on first run.** All were test-setup errors, corrected in the test only; none was a product defect:
- The web test expected the wrong confirmation text, and the meal route needs a UUID.
- An archived-meal fixture lacked `archived_at`, which the schema requires.
- A second slot was placed at the same start time as an existing one.

## Commands and results (local, PostgreSQL via Testcontainers)

| Command | Result |
|---|---|
| `./mvnw -B -o -q -pl app -am test -Dtest=QuoteIntegrationTest` | 11 tests, 0 failures |
| `./mvnw -B -o -q -pl app -am test -Dtest=OrderPlacementIntegrationTest` | 10 tests, 0 failures |
| `./mvnw -B -o -q -pl app -am test -Dtest=OrderLifecycleIntegrationTest` | 13 tests, 0 failures |
| `pnpm exec vitest run` (customer web) | 15 tests passed |
| `pnpm exec jest` (customer mobile) | 24 tests passed |
| `pnpm exec eslint apps/customer-web apps/customer-mobile`; web `tsc --noEmit` | clean |

The full six-check evidence run and CI on the final revision are reported in `docs/status/latest.md` once they complete.

## Confirmed defects

**None found by this pass.** Every new test passed against the existing implementation.

Earlier the same day, the native end-to-end work (MB-031/MB-032) found and fixed two product defects. They are listed here for completeness, not as results of this pass:

- **`64161d5`, sign-in after sign-out reused the previous login.** Interactive sign-in reused the identity provider's browser session, so the previous person was signed straight back in. Before: the native round trip could not reach the login form for the next user. After: `prompt=login` plus an ephemeral iOS session; the unit test fails when `prompt=login` is removed.
- **`eb1a671`, the app could not read any API response on device.** The request middleware returned the response object, which `openapi-fetch` rejects when the platform `Response` class is not the global one. Before: native run 21 signed in, but no profile was shown. After: the middleware returns nothing; the regression test reproduces the original error message with the old code and passes with the fix.

## Remaining gaps

1. **Native device verification of MB-032 is not yet complete.** It covers basket replacement (TC-148), app kill during submit with offline reopen (TC-049) and total parity (TC-132), on the Android emulator and iOS simulator. The run is pending on `eb1a671`; the unit and UI tests above pass.
2. **Delivery checkout is not exercised natively.** The native end-to-end runs use pickup only. Delivery is covered on the server and in the browser.
3. **Two-meal orders are not raced concurrently.** Concurrency is covered for single-meal orders (20 customers racing for the last portion). Two-meal orders use the same locking path and are covered sequentially only.
4. **Screen-reader testing is BLOCKED (BLK-003).** It is separate from this pass.

## Requirement question (recorded; behaviour not changed)

**Q1, maximum quantity per line.** The sources disagree:
- The screen specification proposes "quantity 1–20 per line" and says to "configure and match client/server limits".
- The server enforces the active commerce policy's `max_item_quantity`, which is 10 in the synthetic local and CI policies.
- The contract notes that the server bound "may be lower" but does not tell clients what it is.
- The web and mobile apps allow up to 20, or the slot's remaining portions if fewer.

So a customer can choose 11–20 portions and then receive a validation error at quote time. **Owner decision needed:** either expose the policy limit to clients (a contract change) or fix the policy limit at 20 (O04, commerce policy).

## Unfinished scope (outside this pass)

- MB-030, together with MB-029's screen-reader test (BLK-003) and O05 approval.
- MB-032 native evidence (in progress), and MB-033 to MB-036.
- MB-039 parts that need staging (O07) or the retention decision (O06).
- Anything not yet implemented.

## Candidate for independent QA (Codex)

- **Candidate SHA:** the head of `implementation/mvp` that contains this document; see `docs/status/latest.md` for the exact SHA and its CI.
- **Scope to re-run:**
  1. `cd backend && ./mvnw -B -ntp verify`;
  2. `pnpm -r test`;
  3. `scripts/dev.sh e2e` (browser journeys);
  4. the native workflow `.github/workflows/native-build.yml`.
- **Focus areas:**
  1. basket keep and replace on web and native;
  2. totals and quantity limits (see Q1);
  3. lost response and app kill during submit, with no duplicate order;
  4. cross-customer and cross-role 404/403 boundaries.
- **Data:** synthetic only (`apps/customer-mobile/e2e-native/seed.sql`, browser e2e fixtures); local stack only. Nothing is deployed.
