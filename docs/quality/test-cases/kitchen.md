# kitchen test definitions

Definitions, not execution results. Generated from cases.json; SELF_REVIEWED, never owner-approved. Canonical product acceptance remains planning/scenarios.json. Read the eligibility matrix and run report for the executed boundary of each variation.

## S-AUTH — Role navigation and kitchen isolation

Purpose: Role navigation and kitchen isolation.

References: TC-001,006,007,111,122,126. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: cook-a/b; support-a; finance-a; ops-review; ops-a; fresh isolated known seed described in README.

1. Sign in separately with real password/OTP where required.
2. Inspect exact tabs per seeded permissions; reload cook session.
3. A attempts B meal publication; admin without cook.review requests cook queue.
4. Cook signs out.

Expected: Cook tabs Home/Orders/Meals/Slots/Statement/Settings; support Support; finance Finance; reviewer review/moderation/orders; admin config/permissions; foreign kitchen404; no implicit superuser403; logout401.

Persisted/API invariants: denied mutations preserve versions/events

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/staff.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-CATALOG — Publish and create meal with media

Purpose: Publish and create meal with media.

References: TC-011,012,113,114,122. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: cook-a; customer-a; solid synthetic PNG; fresh isolated known seed described in README.

1. Pause A1 through Meals.
2. Customer opens A1; republish A1.
3. Create draft QA Created Dish at101.25 with generated PNG through signed upload.

Expected: Pause removes public lookup404; publication restores correct offering; safe real local upload creates draft201 price10125.

Persisted/API invariants: immutable meal revisions; media approved before public exposure

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/staff.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-SLOTS — Create and enable connected availability

Purpose: Create and enable connected availability.

References: TC-013,014,015,115. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: cook-a/b; A1/A2; fresh isolated known seed described in README.

1. Create date+8days13:00–15:00 Cairo,max8,lead120,A1/A2 cap3.
2. Open exact dated slot in UI.
3. B attempts mutation of this slot.

Expected: 201 DRAFT→OPEN200; UTC instants correspond Cairo inputs; exact foreign slot404.

Persisted/API invariants: new connected slot ID captured; max8 and meal capacities3

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/staff.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-DRAFT-CLOSE — Legal draft-slot controls

Purpose: Legal draft-slot controls.

References: TC-115,026; MBQA-005. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: cook-a; fresh date+7days draft max8/A1cap3; fresh isolated known seed described in README.

1. Create fresh draft via own API and reload Slots.
2. Inspect controls; directly submit DRAFT→CLOSED.
3. Open draft and inspect OPEN controls.

Expected: DRAFT offers Open without Close; direct Close409 leaves DRAFT; Open200; OPEN offers Pause and Close.

Persisted/API invariants: no event/version mutation on invalid transition

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/expanded.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-INVALID — Invalid and foreign order commands

Purpose: Invalid and foreign order commands.

References: TC-013,024,026. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: C-ORDER pending order; cook-a/b/customer-a; fresh isolated known seed described in README.

1. Attempt ready before accept; B reads/accepts A order; customer attempts cook accept.
2. Try lowering slot max to1 with2reserved.

Expected: Invalid transition409; foreign404; customer403; below-reservation capacity409.

Persisted/API invariants: event count and capacity unchanged after denials

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/staff.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## X-FULFILL — Connected pickup and customer status

Purpose: Connected pickup and customer status.

References: TC-022,026,029,030,031,032. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: C-ORDER pending order; cook-a/customer-a; fresh isolated known seed described in README.

1. Correct cook locates exact public number; Accept.
2. Replay same accept key.
3. Prepare, Ready, retrieve owning customer code, confirm pickup handover.
4. Refresh customer details and compare counters/events.

Expected: Customer sees ACCEPTED then Delivered/COMPLETED for same ID; one accept; completion requires correct customer code.

Persisted/API invariants: reserved0 allocated0 consumed2 for meal and kitchen; five events; cash staysDUE

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/staff.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-SETTINGS — Pause sales with historical integrity

Purpose: Pause sales with historical integrity.

References: TC-008,017,122. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: cook-a/customer-a; completed C-ORDER; fresh isolated known seed described in README.

1. Pause sales through Settings; request public meal and historical order.
2. Resume; edit public description and restore it.

Expected: Paused public404; existing completed order accessible same24050; resume ACTIVE public200; owndescription saved.

Persisted/API invariants: order snapshots immutable despite kitchen changes

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/remaining.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## P-HISTORY — Active and genuine meal price history

Purpose: Active and genuine meal price history.

References: D14,TC-153; MB-048. Roles: owning cook/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions/fixtures: cook-a/b; A1 original12025; committed C-ORDER; fresh isolated known seed described in README.

1. Change A1price12125 then12225 through kitchen UI; edit description only via API.
2. Open history EN/AR, compare saved revision times/Cairo formatting.
3. B and customer query history; re-read historical order; restore original via new price revision.

Expected: Current12225 clearlyactive; prior12125/12025 newestfirst; description-only adds nopricechange; foreign404/customer403; committedorder24050 unchanged.

Persisted/API invariants: immutable revisions; no fabricated dates; no historywrite endpoint

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation/manual reference: qa/regression-d877a15/pricing.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.
