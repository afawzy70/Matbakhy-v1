# backoffice test definitions

Definitions, not execution results. Review status is SELF_REVIEWED, never owner-approved. Canonical product acceptance remains `planning/scenarios.json`; these executable independent cases reference it.

## S-FINANCE — Cash report, dual-person refund approval

Purpose: Cash report, dual-person refund approval.

References: TC-032,033,034,035. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: completed pickup24050; cook-a; finance-a/b; fresh isolated known seed described in README.

1. Attempt refund before collection.
2. Cook reports exact cash separately; finance verifies.
3. Inspect statements; request full refund; requester attempts self-approval; other approves; reject unpaid synthetic obligation.

Expected: Before verification409; report201→VERIFIED; commission2405; requester403; separate approval stays unpaid; customer finance403.

Persisted/API invariants: balanced immutable ledger; completion does not imply cash verification.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/expanded.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-CONFIG — Versioned configuration and real step-up

Purpose: Versioned configuration and real step-up.

References: TC-007,042,043. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: ops-a fresh realOTP; password-only customer-a; fresh isolated known seed described in README.

1. Inspect synthetic10%policy.
2. Attempt self-escalation.
3. Grant customer config.write; customer tries sensitive flag write; revoke grant.
4. Change zone fee+125,inspect versions/audit,restore.

Expected: Self-escalation403; sensitive customer403 STEP_UP_REQUIRED despite grant; versioned fee200 and audit; restored fee; revoked read403.

Persisted/API invariants: failed write leaves flag/version; realOTP not fabricated claims.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/more.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-ONBOARD — Applicant submission to reviewer approval

Purpose: Applicant submission to reviewer approval.

References: TC-008,009,112,123. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: customer-b with no kitchen initially; ops-review freshOTP; fresh isolated known seed described in README.

1. Save synthetic applicant draft via API; check no cook privileges.
2. Submit immutable revision; reviewer opens application,starts review,approves with reason.
3. Applicant opens operations workspace.

Expected: DRAFT→SUBMITTED→UNDER_REVIEW→APPROVED; before approval403 cook; approved owner gets own cook tabs.

Persisted/API invariants: one approved kitchen; applicant editing UI/mandatory legal documents outside tested boundary.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/more.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-MODERATE — Moderation and operational order identity

Purpose: Moderation and operational order identity.

References: TC-017,039,123. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: ops-review; customer-a; A2 published; fresh isolated known seed described in README.

1. Reviewer hides A2 with reason; customer requests lookup.
2. Restore through UI; open Order monitoring and locate completed C-ORDER.

Expected: Hidden404; restored200; monitor contains exact completed customer order number.

Persisted/API invariants: moderation version/audit; order immutable.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/final-segments.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-SUPPORT-RESOLVE — Case assignment, resolution and closure

Purpose: Case assignment, resolution and closure.

References: TC-041,124. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: S-SUPPORT case; support-a/customer-a; fresh isolated known seed described in README.

1. Assign to currentstaff; resolve INFORMATION_PROVIDED; customer reads public thread.
2. Try stale update; close resolved case; retry stale close and customer reply.

Expected: Assignment/resolution/closure200; stale409; closed reply409; private notes excluded.

Persisted/API invariants: samecase/order; optimistic version; no duplicate mutation.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/support-final.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-SECURITY-REVOKED — Immediate revocation and last administrator

Purpose: Immediate revocation and last administrator.

References: TC-007,043. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: S-CONFIG grant revoked; soleops-a staff.grant with freshOTP; fresh isolated known seed described in README.

1. Customer tries sensitive flag mutation after actual revocation.
2. Confirm sole recoverable admin; attempt own staff.grant removal.
3. Read flag/grant state.

Expected: Revoked403 FORBIDDEN; lastadmin409; flagversion and recoverable grant unchanged.

Persisted/API invariants: server re-reads grants each request.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/security-final.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-FINANCE-FINAL — Refund bounds and settlement freeze

Purpose: Refund bounds and settlement freeze.

References: TC-035,036. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: S-FINANCE verified pickup; finance-a/b; fresh isolated known seed described in README.

1. Request full refund through UI; retry captured same key; selfapprove then separateapprove; over-refund; reject unpaid obligation.
2. Select eligible statement,create settlement draft; selfreview; separatereview; try unpaidreconcile.

Expected: Same refundid; self403; overrefund409; DRAFT→REVIEWED only separateactor; unpaidreconcile409.

Persisted/API invariants: unique settlementlines; balancedgroups; no transfer performed.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/finance-final.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## S-FINANCE-SYNTHETIC-FINAL — Synthetic accounting completion and replay

Purpose: Synthetic accounting completion and replay.

References: TC-033,035,036. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: reviewed settlement; finance-a/b; no real money; fresh isolated known seed described in README.

1. Record explicit QA-SYNTHETIC-NO-TRANSFER; selfreconcile then separate reconcile/replay.
2. Create/approve refund; record QA-SYNTHETIC-NO-REFUND-TRANSFER; selfverify then separateverify/replay.

Expected: Selfactions403; separate RECONCILED/VERIFIED; repeated same commands unchanged; this is synthetic state testing only.

Persisted/API invariants: one commission reversal2405; balanced ledger; completed order remains24050.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/finance-synthetic.cjs.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## P-FEE-STALE — Versioned fee invalidates unplaced quote

Purpose: Versioned fee invalidates unplaced quote.

References: D13,TC-020,042. Roles: permission-scoped staff/customer. Platforms: Chromium web + real API/DB. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: own address; A2A5 delivery; fee3000; fresh isolated known seed described in README.

1. Quote; admin changes fee3125; request newquote; submit originalquote.
2. Compare storedquote and ordercount; restorefee.

Expected: Oldplacement409 PRICE_CHANGED; newquote3125; oldsnapshotunchanged; noorder.

Persisted/API invariants: originalquote immutable; no stock/cash effects.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: MANUAL / planned automation.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.


## S-PERMISSION-UI — Live support permission revocation, UI and backend agreement

Purpose: Verify previously allowed staff read/write disappears per request and on UI refresh without logging the user out. References: TC-007,043. Admin/support; Chromiumweb/API; en; priorityP0.

Preconditions: Active support-a with exactly support.case grant; ops-a real freshOTP/staff.grant; existing own QAcase; different browser sessions; no parallel auth diagnostic.

1. Show support navigation and identify actual active support.case grant.
2. Administrator revokes that grant through real API; same staff session must still /me200.
3. Attempt case read/message write; refresh operations UI.
4. Verify403/no business mutation and no Support tab; restore grant through authorized API and confirm UI/API access returns.

Expected: Authenticated staff loses previously permitted actions immediately403; refreshing hides revoked navigation; denied write changes nothing; restoration201 returns access200 without auth bypass.

Invariants: server rereads actual grants; same authenticated identity; case messages/version unchanged after denial; recoverable administrator preserved.. Cleanup: Restore only the revoked synthetic support grant in finally through real admin API; no user/session deletion.

Automation: qa/regression-d877a15/permission-revocation.cjs (TARGETED extension; next fresh full). Review SELF_REVIEWED. Questions: no new access policy or owner approval inferred.
