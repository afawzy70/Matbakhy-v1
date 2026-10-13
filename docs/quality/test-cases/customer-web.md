# customer-web test definitions

Definitions, not execution results. Review status is SELF_REVIEWED, never owner-approved. Canonical product acceptance remains `planning/scenarios.json`; these executable independent cases reference it.

## C-AUTH — Sign out and restore session

Purpose: Sign out and restore session.

References: TC-001..007, TC-109; MBQA-003. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: customer-a/customer-b; fresh isolated known seed described in README.

1. Sign in as customer-a through Keycloak; reload Account.
2. Press Sign out; wait for the identity-provider logout redirect, reload, and query /me.
3. Sign in as customer-b in that browser; request customer-a order and staff endpoints.

Expected: Session maps A before logout; /me is 401 afterwards; B maps only B; private foreign objects return404 and staff operations403.

Persisted/API invariants: auth/session stores revoked; no private A state in B.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/customer.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-BROWSE — Discovery, language and content

Purpose: Discovery, language and content.

References: TC-011,016,017,046,149,150; D11/D12; MBQA-001. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: anonymous or customer-a; baseline four purchasable meals; fresh isolated known seed described in README.

1. Open Home, A1 details and Kitchen A; switch Arabic then English on each.
2. Search QA Meal B1; inspect current network Accept-Language and returned/displayed content.

Expected: Four baseline cards; exact A1/kitchen identity; EGP unit price120.25; availability matches API; each language change sends current header and translates text; ar RTL/en LTR; unique search returns B1; deferred portion filters absent.

Persisted/API invariants: public results exclude paused kitchen, closed/past stock; no fabricated optional description.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/customer.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-BASKET — Edit, remove, persist and estimate basket

Purpose: Edit, remove, persist and estimate basket.

References: TC-018,019,151; FG-001. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: customer-a; A1/A2 in slotA1 cap3 each; fresh isolated known seed described in README.

1. Empty basket; add A1×2 and A2×1 to A1.
2. Increase A2, decrease A1; reload and switch language.
3. Remove A1 then A2.

Expected: Subtotals33600→43150→31125→19100 minor; exact kitchen/slot/quantities survive reload/language; final empty state and absent cart; no fees represented as final estimate.

Persisted/API invariants: quote invalidated after edit; estimates integer item sums.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/customer.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-STOCK — Mixed stock selected slot

Purpose: Mixed stock selected slot.

References: TC-013,015,017,150; MBQA-002. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1 slotA1 capacity3, other future slot positive; customer-b; fresh isolated known seed described in README.

1. B places A1×3 in A1 via real API.
2. A opens A1 details and explicitly selects exhausted A1; attempt Add.
3. B legally cancels; A reloads and enters99 in quantity.

Expected: Selected exhausted slot remains identifiable, Add disabled, no basket mutation; cancellation restores3; quantity clamps3.

Persisted/API invariants: both slot counters reserve/release exactly3.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/customer.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-Q1 — Server quantity and same-session policy refresh

Purpose: Server quantity and same-session policy refresh.

References: TC-152; Q1. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1 slotA5 cap25; active synthetic limit10; fresh isolated known seed described in README.

1. Enter11 on meal; open basket.
2. Directly request quote11.
3. Change only synthetic policy max to5 while browser runs; navigate away/back to basket.
4. Reduce ten to five and calculate pickup quote; restore policy10.

Expected: UI clamps10; API422 for11; basket revisited without process restart flags10>5 and blocks quote; no quote created while invalid; five quote60125.

Persisted/API invariants: API-001 limit10/5 authoritative; no state lost.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/customer.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-QUOTE — Quote boundary and validation

Purpose: Quote boundary and validation.

References: TC-019,020,021,044. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1×2 in A1; fresh isolated known seed described in README.

1. Calculate pickup quote; inspect subtotal, fee, tax, total, terms.
2. Try Place before terms.
3. Increase quantity; submit forged totalMinor to quote API.

Expected: 201 quote24050 subtotal/total; zero pickup fee/tax; disabled Place before terms; edit removes quote; estimate36075; forged field400.

Persisted/API invariants: quote reserves no stock; integer backend amounts.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/customer.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-ORDER — Place and locate pickup order

Purpose: Place and locate pickup order.

References: TC-019,022,023,044,127. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: customer-a/b; staff-created connected slot; fresh isolated known seed described in README.

1. Add A1×2 in exact connected slot; quote and accept terms; Place.
2. Open confirmation, history and details.
3. Query same order in DB; B attempts read/request lookup/cancel.

Expected: 201 PENDING_ACCEPTANCE,total24050; exact order/kitchen/slot; history matches; B foreign404.

Persisted/API invariants: one row/clientRequestId; immutable money; both capacities reserved2.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/staff.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-SUPPORT — Read and reply to public support thread

Purpose: Read and reply to public support thread.

References: TC-041,124,128; MBQA-004. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: completed C-ORDER; customer-a/b; support-a; fresh isolated known seed described in README.

1. A opens order case from Account.
2. Support sends STAFF_ONLY synthetic marker then PUBLIC marker.
3. A follows case link, reads public reply and sends reply.
4. B reads case; cook/customer request ops thread.

Expected: A can read PUBLIC and reply; private marker never visible or in customer DTO; foreign404; unauthorized ops403.

Persisted/API invariants: messages tied exact case/order; visibility enforced backend.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/expanded.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-TRUST — Review, favorites and pending cancellation

Purpose: Review, favorites and pending cancellation.

References: TC-028,029,039,040. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: completed C-ORDER; customer-a/b; B1 available; fresh isolated known seed described in README.

1. A reviews completed order4; repeat and B review.
2. Add/remove A1 favorite through UI, inspect list.
3. Create B1 pickup pending order; cancel through UI.

Expected: Review201 once,duplicate409,foreign404; favorite idempotent/list reflects remove; pending CANCELLED with release.

Persisted/API invariants: review count1; canceled meal reserved0.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/expanded.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-STALE — Unpublish after quote

Purpose: Unpublish after quote.

References: TC-017,022. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1×2 in A5 quoted; cook-a; fresh isolated known seed described in README.

1. Pause A1 after quote through cook API.
2. Place existing quote and inspect recovery action; republish in finally.

Expected: Placement409,no order; usable calculate-again recovery, basket retained.

Persisted/API invariants: no partial reservations/order; publication restored.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/expanded.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-DELIVERY — Address, delivery and cash-free fulfilment

Purpose: Address, delivery and cash-free fulfilment.

References: TC-019,021,030,031,033. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: customer-a,cook-a; A1 A5; configured zonefee3000; fresh isolated known seed described in README.

1. Empty address save fails; fill generated synthetic complete own address.
2. Add A1×1; delivery quote and Place.
3. Cook Accept/Prepare/Ready; try handover before dispatch; dispatch then correct customer code.

Expected: Invalid address422; valid201; quote/order subtotal12025+fee3000=15025,tax0; before-dispatch409; final COMPLETED.

Persisted/API invariants: commission1203 HALF_UP excludes delivery; saved snapshots correct.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/more.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-AFTER-ACCEPT — Cancellation request and operational exception

Purpose: Cancellation request and operational exception.

References: TC-028,029,124. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: B1 B2; customer-a,cook-b,ops-review freshOTP; fresh isolated known seed described in README.

1. Create pending B order; correct cook accepts.
2. Customer requests cancellation,repeat key,and attempts direct cancel.
3. Cook prepares; cook attempts ops exception; ops cancels with case/reason.

Expected: Accepted UI request only; direct409; same-key onecase; cookops403; authorized exception CANCELLED.

Persisted/API invariants: consumed1 retained both counters, no automatic restoration.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/remaining.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## P-PRECOMMIT — Informed repricing before commit

Purpose: Informed repricing before commit.

References: D13,TC-154; MB-049. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: A1 quoted; kitchenprice+100,zonefee+125 separately; fresh isolated known seed described in README.

1. Show oldquote; change mealprice then Place.
2. Inspect attributed old/new prices and updated total; try Place before explicit acceptance.
3. Accept freshquote; repeatfee/pricechanges and edit/leave.

Expected: Kitchen attribution only meal; actual fee component attributed; clear old/new/total; explicitreconfirm required; no silent submit.

Persisted/API invariants: noorder/effects on stale; current serverquote only.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: NOT_AUTOMATED: implementation gap at pinned candidate.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-REPLACE-K-CANCEL — Confirm basket replacement

Purpose: Confirm basket replacement.

References: TC-018,147. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1×2/A1 basket; target B1/B1 for K or A2/A2 for S; fresh isolated known seed described in README.

1. Add target from different kitchen; inspect modal before mutation.
2. Select Keep my current cart; open checkout.

Expected: Before consent original untouched; cancel preservesA/A1/A1/2; confirm onlytarget kitchen/meal/slot/1; exact stored values and estimate match fixture.

Persisted/API invariants: single kitchen/slot; correct integer estimate.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-REPLACE-K-CONFIRM — Confirm basket replacement

Purpose: Confirm basket replacement.

References: TC-018,147. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1×2/A1 basket; target B1/B1 for K or A2/A2 for S; fresh isolated known seed described in README.

1. Add target from different kitchen; inspect modal before mutation.
2. Select Replace cart; open checkout.

Expected: Before consent original untouched; cancel preservesA/A1/A1/2; confirm onlytarget kitchen/meal/slot/1; exact stored values and estimate match fixture.

Persisted/API invariants: single kitchen/slot; correct integer estimate.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-REPLACE-S-CANCEL — Confirm basket replacement

Purpose: Confirm basket replacement.

References: TC-018,147. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1×2/A1 basket; target B1/B1 for K or A2/A2 for S; fresh isolated known seed described in README.

1. Add target from different slot; inspect modal before mutation.
2. Select Keep my current cart; open checkout.

Expected: Before consent original untouched; cancel preservesA/A1/A1/2; confirm onlytarget kitchen/meal/slot/1; exact stored values and estimate match fixture.

Persisted/API invariants: single kitchen/slot; correct integer estimate.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-REPLACE-S-CONFIRM — Confirm basket replacement

Purpose: Confirm basket replacement.

References: TC-018,147. Roles: customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A1×2/A1 basket; target B1/B1 for K or A2/A2 for S; fresh isolated known seed described in README.

1. Add target from different slot; inspect modal before mutation.
2. Select Replace cart; open checkout.

Expected: Before consent original untouched; cancel preservesA/A1/A1/2; confirm onlytarget kitchen/meal/slot/1; exact stored values and estimate match fixture.

Persisted/API invariants: single kitchen/slot; correct integer estimate.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.


## C-FILTER — Server budget/sort and empty-state recovery

Purpose: Server budget/sort and empty-state recovery. References: TC-016/017/046; D11/D12. Role customer-a/b and cook for setup; Chromiumweb/API/DB; en/ar; priorityP1.

Preconditions: Baseline four published A/B meals in isolated seed.

1. Open Filters,enter maximum100EGP,Show results.
2. Sort PRICE_ASC; reset maximum price to empty and sort NEWEST through the filter form; search unique QA-NO-MATCH-877; use the empty-state Clear to recover.

Expected: Budget sends10000minor; results A2/A3/B1 only, sorted8025,8025,9550; empty shows actionable clear; clearing restoresfour.

Invariants: server queries, no client-only filtering or hidden portion filter. Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/additional.cjs. Review SELF_REVIEWED. Questions: O04 synthetic policies not approved commercial values.

## C-CONCURRENCY — Concurrent last portion and payload idempotency

Purpose: Concurrent last portion and payload idempotency. References: TC-022/024/025; MB-037. Role customer-a/b and cook for setup; Chromiumweb/API/DB; en/ar; priorityP1.

Preconditions: Fresh own A2slot max1/mealcap1 opened via authenticatedcook; customer-a/b distinct.

1. Both customers quote the finalportion; verify0reservation.
2. Hold test-only stock row; start both real placements; observe2lock waits; release.
3. Verify one201,one409STOCK_CHANGED; replay winner; change payload withsame key; legallycancel.

Expected: Exactlyoneorder andone meal/kitchen reservation; replay sameorder; payloadcollision409; cleanup releasesonce.

Invariants: transactional all-or-nothing, oneevent/cash/allocation, no oversell. Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/additional.cjs. Review SELF_REVIEWED. Questions: O04 synthetic policies not approved commercial values.
