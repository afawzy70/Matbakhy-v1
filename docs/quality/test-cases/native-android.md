# native-android test definitions

Definitions, not execution results. Review status is SELF_REVIEWED, never owner-approved. Canonical product acceptance remains `planning/scenarios.json`; these executable independent cases reference it.

## N-AUTH — PKCE authentication, logout and account isolation

Purpose: PKCE authentication, logout and account isolation.

References: TC-048,TC-131. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Launch native app; use system browser to sign inA; terminate/relaunch; logout; sign inB.

Expected: Real A restored then cleared; B identity alone; no stale A private state.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-REPLACE — Native discovery and basket keep/replace

Purpose: Native discovery and basket keep/replace.

References: TC-148. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Browse One; add; browse Two; Keep then Replace; open basket.

Expected: Prompt before mutation; keepOne12000/slot51; replaceTwo9000/slot52; basket reachable.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-RECOVERY — Pending submission killed, offline reopened and reconciled

Purpose: Pending submission killed, offline reopened and reconciled.

References: TC-049,132. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Calculate Two pickup; accept; submit behind response-holding proxy.
2. Assert real request reached server and response pending; terminate.
3. Make API unreachable; cold reopen; restore API; retry same request.

Expected: Unconfirmed reachable offline; one recovered order9000; one allocation/reservation/cash/outbox/event.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-Q1-RESTORED — Quantity and restored lowered policy

Purpose: Quantity and restored lowered policy.

References: TC-152. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. At fifteen available increase tolimit10 and add.
2. Lower syntheticlimit5; relaunch; openbasket; reduce to5.

Expected: Quantity stops10; restored10>5 flagged; no quote before correction; state recoverable.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-DELIVERY — Native delivery server total parity

Purpose: Native delivery server total parity.

References: TC-021,132. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Choose saved native synthetic zoneAaddress after correction5One.
2. Quote, accept and Place.

Expected: Fee3000; subtotal60000,total63000; same stored kitchenA/slot51/DELIVERY/ownaddress snapshot.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-LINK — Warm and cold order deep links

Purpose: Warm and cold order deep links.

References: TC-050,133. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Open saved own deliveryorder link from running Discover then after force-stop.
2. Attempt foreign orderlink.

Expected: Own exact orderreachable bothstarts; foreign403/404 without data.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-Q1-SESSION — Limit reduced while same process runs

Purpose: Limit reduced while same process runs.

References: TC-152. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Create basket10 atlimit10; keep app running.
2. Lower activepolicy5; navigate out/back toCart, no relaunch.
3. Check quote blocked and reduce5.

Expected: Reads currentAPI-001 on focus; warns/blocks overlimit; correctedquotevalid.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-EDIT-LAYOUT — Edit, remove, persistence, locale and lifecycle

Purpose: Edit, remove, persistence, locale and lifecycle.

References: TC-018,046,049,151. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Add One×2; edit/decrease/remove belowfold controls; re-add.
2. Background/foreground and terminate/reopen; switchAR/EN; inspect keyboard,scroll,safeareas.

Expected: Exact quantities/estimatedsum; last removalempty; basket/session persists safely; arRTL/enLTR usable.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-OPTIONAL — Text scaling and notification permission states

Purpose: Text scaling and notification permission states.

References: TC-047,050; MB-033. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Enable OSlargefont; open meal/basket; deny then allow notificationpermission when requested.

Expected: Readable/reachable controls; denial does not block ordering; permission accurately presented.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform.

Cleanup/reset: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: .github/workflows/independent-qa-regression-native.yml, apps/customer-mobile/scripts/{auth,order}-roundtrip-{android,ios}.sh.

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.


## N-FAVORITES — Native favorite ownership and list

Purpose: Native favorite ownership and list. References: TC-040; MB-028/032. Role customer; platform native-android; locales en/ar; P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README; support message SQL is test data, not a staff workflow PASS.

1. Sign inA; openOne and Add to favorites.
2. Open Favorites,selectOne,remove; inspect list and stored favorite.

Expected: A favorite persists in actual list and removal removes own row; B cannot read A favorites.

Invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform. Cleanup: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/native/instrument.py; fixture-control.py; generated native UI harness. Review SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-SUPPORT — Native public support reply reachability

Purpose: Native public support reply reachability. References: TC-041; MB-024/028/032; MBQA-004. Role customer; platform native-android; locales en/ar; P1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README; support message SQL is test data, not a staff workflow PASS.

1. Seed exact own native order case CS-DA877001 with PUBLIC marker and STAFF_ONLY marker as prerequisite only.
2. Open Account→Support,locatecase and try opening thread; inspect rendered public marker and reply control.

Expected: Own public reply reachable/readable; private marker absent; customer can reply while OPEN.

Invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform. Cleanup: Use independent browser state/cart reset without erasing pending submissions; legal cancellation/restoration in finally; connected C-ORDER→X-FULFILL→support/finance is one declared fixture chain, not a resettable case. Fresh Compose volumes for each new FULL REGRESSION; never share prior campaign DB.

Automation: qa/regression-d877a15/native/instrument.py; fixture-control.py; generated native UI harness. Review SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-CANCEL — Native pending cancellation and immutable history

Purpose: Native pending cancellation and immutable history. References: TC-028/029; MB-019/032. Customer-a; native-android; en/ar; priorityP1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Sign in as real customer-a with fresh unchanged-policy session.
2. Open actual earlier delivery order by own deep link; scroll to Cancel order and cancel.
3. Verify same public number and Cancelled state; cancellation control absent; compare DB allocation/capacity/events/immutable amounts.

Expected: Owning pending order CANCELLED; one release/event, no duplicate effect; original63000 total unchanged; other recovered order unaffected.

Invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform. Cleanup: Synthetic cancellation is terminal and allowed; subsequent full regression starts with fresh native fixture. No production or real messages.

Automation: qa/regression-d877a15/native/instrument.py; independent iOS Hsegment; Android variation requires separately executed control. Review SELF_REVIEWED. Questions: no native coverage inferred from other platforms.

## N-SUPPORT-CREATE — Native owned support-case creation and keyboard

Purpose: Native owned support-case creation and keyboard. References: TC-041; MB-024/032. Customer-a; native-android; en/ar; priorityP1.

Preconditions: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Open Account→Support; select exact own earlier order.
2. Type a synthetic explanation in real multiline field; dismiss keyboard through real category control; scroll to Send.
3. Submit and verify case receipt plus persisted own order/customer/category, then refresh own case list.

Expected: Real UI/API creates one valid owned case; text input/keyboard/scrolling permit submission; no foreign-order data.

Invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform. Cleanup: Synthetic cancellation is terminal and allowed; subsequent full regression starts with fresh native fixture. No production or real messages.

Automation: qa/regression-d877a15/native/instrument.py; independent iOS Hsegment; Android variation requires separately executed control. Review SELF_REVIEWED. Questions: no native coverage inferred from other platforms.
