# cross-cutting test definitions

Definitions, not execution results. Review status is SELF_REVIEWED, never owner-approved. Canonical product acceptance remains `planning/scenarios.json`; these executable independent cases reference it.

## C-RECOVERY — Lost response and lookup failure

Purpose: Lost response and lookup failure.

References: TC-023,044,045; OBS-001; D13/TC-156. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: A1×1 A5; customer-a; committed response withheld only in transport; fresh isolated known seed described in README.

1. Quote,accept terms; route real POST upstream then withhold201response; abort lookup.
2. Assert server committed and same pending keys persisted before disconnect; set context offline.
3. Reconnect,reload while lookup unavailable; restore lookup and same-key retry.
4. Compare DB then legally cancel.

Expected: Reachable uncertain state; no unhandled lookup rejection; original same order recovered at original price; pending clears only after reconciliation.

Persisted/API invariants: one clientRequestId/order/allocation/cash/event; no duplicate reservation.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/expanded.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## C-CAPACITY-STALE — Capacity reduced after quote

Purpose: Capacity reduced after quote.

References: TC-013,022,025. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: A2 A5 no obligations; customer-a/cook-a; fresh isolated known seed described in README.

1. Quote A2×3.
2. Cook reduces A2cap to1 through authorized API.
3. Place old quote; compare counts; restore25.

Expected: STOCK_CHANGED409; no order and no reservation; actionable recovery.

Persisted/API invariants: all-or-nothing capacity; no partial effects.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/final-segments.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## W-A11Y — Responsive RTL, semantic tables and keyboard

Purpose: Responsive RTL, semantic tables and keyboard.

References: TC-046,047. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: six real screens: Home,A2,Cart,CookStatement,Support,Config; fresh isolated known seed described in README.

1. For each screen run widths1440/390/320 and localesen/ar.
2. Check direction,overflow,axe serious/critical,errors.
3. Tab to controls; inspect scoped statement headers/cells.

Expected: 36 separate variations recorded; no serious/critical axe violations or documentoverflow; visible focus; five scoped headers and five cells.

Persisted/API invariants: practical checks only; screen-reader requirement stays separate.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/more.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## P-SERIALIZE — Update-placement transaction serialization

Purpose: Update-placement transaction serialization.

References: D13,TC-155; MB-049 server segment. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: A2 A5; customer-a/cook-a; own DB observer; fresh isolated known seed described in README.

1. Quote oldprice; hold authorized meal update transaction row lock until update ready.
2. Start placement concurrently; establish waiting lock through pg_stat_activity; commit update first; inspect response/effects.
3. Run placement-first while row lock holds and later price change.

Expected: Updatefirst stale409 with0business effects; placementfirst createdoriginalsnapshot; later changes preserve committedvalues.

Persisted/API invariants: one reservation/order; locks observed, not sleeps as concurrencyproof.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## P-REFRESH-FAIL — Failed refresh and repeated repricing

Purpose: Failed refresh and repeated repricing.

References: D13,TC-154; MB-049. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: P-PRECOMMIT UI delivered; transport-only failure; fresh isolated known seed described in README.

1. Reject stalequote; fail freshquote retrieval.
2. Restore connection; requote; change price again before acceptance/placement.

Expected: Basket/mode/address retained; refreshfailure actionable; secondchange needs fresh review/acceptance; no unchecked total.

Persisted/API invariants: each refused placement leaves0effects.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: NOT_AUTOMATED: implementation gap at pinned candidate.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## P-LOST-PRICE — Lost committed response after later price changes

Purpose: Lost committed response after later price changes.

References: D13,TC-156; MB-049. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: committed validorder response withheld; original snapshots read; fresh isolated known seed described in README.

1. Commit order and withholdresponse; then update kitchenprice,deliveryfee.
2. Reopen offline pending; reconnect and recover same clientRequestId.
3. Read order and accounting rows.

Expected: Sameorder recovered at originalprice/fee/total; updated currentquote never replaces committedrecord.

Persisted/API invariants: exactlyoneorder/allocation/cash/event; no duplicateeffects.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: NOT_AUTOMATED: implementation gap at pinned candidate.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## M-SCREENREADER — Critical journeys with screen reader

Purpose: Critical journeys with screen reader.

References: TC-047. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: actual NVDA/VoiceOver/TalkBack/Orca; fresh isolated known seed described in README.

1. Use assistive technology for browse,basket,quote,order,status andstafftable.

Expected: Names,focus,errors,updates and direction announced understandably.

Persisted/API invariants: scopeexplicit; blocked tools never pass.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## M-SMS — Positive verified phone journey

Purpose: Positive verified phone journey.

References: TC-005,110. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: documented sandbox SMSprovider and isolatednumber; fresh isolated known seed described in README.

1. RequestOTP; retrieve sandboxdelivery; verify; repeat consumedcode.

Expected: Deliveredcode verifies once; replay rejected; no auth bypass.

Persisted/API invariants: scopeexplicit; blocked tools never pass.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## M-DEPLOYED — Deployed runtime roles, logs, metrics and recovery

Purpose: Deployed runtime roles, logs, metrics and recovery.

References: AUD-01,LOG-01,OPS-01..03; MB-039/040. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: authorized private staging; separate runtimerole/logsink; fresh isolated known seed described in README.

1. Exercise audit mutationdenial,syntheticcanaries,correlation,alerts and documentedrestore.

Expected: Actual deployed invariants and controls demonstrated.

Persisted/API invariants: scopeexplicit; blocked tools never pass.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## M-LOG-AUDIT — Local audit and synthetic privacy canaries

Purpose: Local audit and synthetic privacy canaries.

References: TC-056,111. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: real localAPI/Postgres; generatedprivate canaries; fresh isolated known seed described in README.

1. Trigger denied foreign reads,supportviews/messages andordercontactview with unique requestIds.
2. Read authorizedaudit; inspect local logs for canary values.

Expected: Auditread audited; targetedstaffreads/messages correlated; no credential/contact/address/OTPcanaries in logs or auditdetail.

Persisted/API invariants: scopeexplicit; blocked tools never pass.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## M-SECRETS — Nonzero full-history and workingtree scan

Purpose: Nonzero full-history and workingtree scan.

References: TC-108,056. Roles: actors named in preconditions. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: fetchedGitrefs; digestpinned gitleaks; fresh isolated known seed described in README.

1. Run scripts/check_secrets.sh; record scannedcommitcount and status; separately review currentQAevidence.

Expected: Zero scannedcommits fails; nonzero scope reported; no reusable secrets/PII in QA artifacts.

Persisted/API invariants: scopeexplicit; blocked tools never pass.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.


## W-MONEY-REFLOW — Financial amounts remain readable at narrow widths

Purpose: Detect split numeric tokens missed by axe, semantic table checks and document overflow assertions. References: BR-046/TC-046; MB-022/025/029. Roles cook-a, finance-a; Chromiumweb; en/ar; widths320/390/1440; priorityP1.

Preconditions: Own kitchen A with two completed synthetic orders and real commission/cash summary; statements rendered by authorized roles; all contacts masked in evidence.

1. Open Cook Statement; open Finance summary for kitchen A as finance-a.
2. For each role inspect320,390,1440 widths in EN and AR; compare displayed amounts with actual statement/DB values.
3. Measure DOM ranges for whole decimal/grouped number tokens and visually inspect summary/table screenshots; inspect header/cell mapping and overflow.

Expected: Amounts remain readable as intact numeric tokens, with currency grouped intelligibly; no single-glyph monetary columns or broken order-number digits; ordinary line breaks between whole words allowed; desktop baseline also measured.

Invariants: UI formatting preserves authoritative EGP minor-unit amounts; read-only actions never alter finance/order data. Cleanup: Restore1440/en; close only own browser contexts; do not mutate order/cash/ledger.

Automation: qa/regression-d877a15/layout.cjs (TARGETED discovery; integrate in next fresh FULL REGRESSION). Review SELF_REVIEWED. Questions: No owner approval inferred. This tests functional readability under BR-046, not branding preferences; screen reader remains separate BLOCKED check.
