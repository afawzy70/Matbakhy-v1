# native-android test definitions

Definitions, not execution results. Generated from cases.json; SELF_REVIEWED, never owner-approved. Canonical product acceptance remains planning/scenarios.json. Read the eligibility matrix and run report for the executed boundary of each variation.

## N-AUTH — PKCE authentication, logout and account isolation

Purpose: PKCE authentication, logout and account isolation.

References: TC-048,TC-131. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. On a fresh app/OS/data environment, sign in syntheticA using system browser and real PKCE credentials. Terminate, reopen and verify Account identity.
2. Sign out, verify unauthenticated state, then sign in syntheticB and verify its distinct identity.
3. Separately repeat after A has a persisted basket, favorite and order; check B cannot see A cached private state. This variation is not satisfied by the empty-state auth round trip.

Expected: Real A restored then cleared; B identity alone; no stale A private state.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-REPLACE — Native discovery and basket keep/replace

Purpose: Native discovery and basket keep/replace.

References: TC-148. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Browse One12000/slot51/kitchenD1; add. Browse Two9000/slot52/kitchenD2; trigger Keep, verify original One/kitchenD1/slot51. Repeat and Replace, verify onlyTwo/kitchenD2/slot52, quantity1/estimated9000 and Basket reachable.
2. Separately provision two OPEN slots for one kitchen, test Keep and Replace and read resulting meal/kitchen/slot. Base one-slot native seed does not exercise this variation.

Expected: Prompt before mutation; keepOne12000/slot51; replaceTwo9000/slot52; basket reachable.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-RECOVERY — Pending submission killed, offline reopened and reconciled

Purpose: Pending submission killed, offline reopened and reconciled.

References: TC-049,132. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P0.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Calculate Two pickup; accept; submit behind response-holding proxy.
2. Assert real request reached server and response pending; terminate.
3. Make API unreachable; cold reopen; restore API; retry same request.

Expected: Unconfirmed reachable offline; one recovered order9000; one allocation/reservation/cash/outbox/event.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-Q1-RESTORED — Quantity and restored lowered policy

Purpose: Quantity and restored lowered policy.

References: TC-152. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. At fifteen available increase tolimit10 and add.
2. Lower syntheticlimit5; relaunch; openbasket; reduce to5.

Expected: Quantity stops10; restored10>5 flagged; no quote before correction; state recoverable.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-DELIVERY — Native delivery server total parity

Purpose: Native delivery server total parity.

References: TC-021,132. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Choose saved native synthetic zoneAaddress after correction5One.
2. Quote, accept and Place.

Expected: Fee3000; subtotal60000,total63000; same stored kitchenA/slot51/DELIVERY/ownaddress snapshot.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-LINK — Warm and cold order deep links

Purpose: Warm and cold order deep links.

References: TC-050,133. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Open the exact owned core order deep link while foregrounded; verify ID/state/total. Terminate then cold-open the same link and verify identical identity.
2. In a separate B session, open A order link; verify authenticated denial and no leaked cached details.

Expected: Own exact orderreachable bothstarts; foreign403/404 without data.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-Q1-SESSION — Limit reduced while same process runs

Purpose: Limit reduced while same process runs.

References: TC-152. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Create basket10 atlimit10; keep app running.
2. Lower activepolicy5; navigate out/back toCart, no relaunch.
3. Check quote blocked and reduce5.

Expected: Reads currentAPI-001 on focus; warns/blocks overlimit; correctedquotevalid.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-EDIT-LAYOUT — Edit, remove, persistence, locale and lifecycle

Purpose: Edit, remove, persistence, locale and lifecycle.

References: TC-018,046,049,151. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. With One quantity10, use the actual decrement control and verify every intermediate visible quantity to4. Assert EGP480.00; remove the last line and inspect empty state.
2. Add One again. Background/foreground without killing, verify same basket. Terminate and cold reopen, verify same persisted basket.
3. Switch AR then EN on Account; inspect rendered labels and independently inspect pixel alignment/direction, keyboard/scrolling/safe areas. Label changes alone do not establish RTL layout.

Expected: Exact quantities/estimatedsum; last removalempty; basket/session persists safely; arRTL/enLTR usable.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-OPTIONAL — Text scaling and notification permission states

Purpose: Text scaling and notification permission states.

References: TC-047,050; MB-033. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Enable OSlargefont; open meal/basket; deny then allow notificationpermission when requested.

Expected: Readable/reachable controls; denial does not block ordering; permission accurately presented.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-FAVORITES — Native favorite ownership and list

Purpose: Native favorite ownership and list.

References: TC-040; MB-028/032. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Sign inA; openOne and Add to favorites.
2. Open Favorites,selectOne,remove; inspect list and stored favorite.

Expected: Own favorite persists in actual native list and removal removes own row. Foreign-account authorization is a separate API/access case; no native B-account favorite-cache claim.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-SUPPORT — Native public support reply reachability

Purpose: Native public support reply reachability.

References: TC-041; MB-024/028/032; MBQA-004. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Seed exact own native order case CS-DA877001 with PUBLIC marker and STAFF_ONLY marker as prerequisite only.
2. Open Account→Support,locatecase and try opening thread; inspect rendered public marker and reply control.

Expected: Own public reply reachable/readable; private marker absent; customer can reply while OPEN.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-CANCEL — Native pending cancellation and immutable history

Purpose: Native pending cancellation and immutable history.

References: TC-028/029; MB-019/032. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Sign in as real customer-a with fresh unchanged-policy session.
2. Open actual earlier delivery order by own deep link; scroll to Cancel order and cancel.
3. Verify same public number and Cancelled state; cancellation control absent; compare DB allocation/capacity/events/immutable amounts.

Expected: Owning pending order CANCELLED; one release/event, no duplicate effect; original63000 total unchanged; other recovered order unaffected.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.

## N-SUPPORT-CREATE — Native owned support-case creation and keyboard

Purpose: Native owned support-case creation and keyboard.

References: TC-041; MB-024/032. Roles: customer Android/iOS separately. Platforms: native-android. Locales: en,ar; baseline en unless explicitly parameterized. Priority: P1.

Preconditions/fixtures: Android emulator or iOS simulator, independently; native seed One12000/Two9000 slots51/52; fresh isolated known seed described in README.

1. Open Account→Support; select exact own earlier order.
2. Type a synthetic explanation in real multiline field; dismiss keyboard through real category control; scroll to Send.
3. Submit and verify case receipt plus persisted own order/customer/category, then refresh own case list.

Expected: Real UI/API creates one valid owned case; text input/keyboard/scrolling permit submission; no foreign-order data.

Persisted/API invariants: no responsive-web/unit substitution; fresh OS/app/DB per campaign platform

Cleanup/reset: New emulator/simulator and fresh independently seeded API/Postgres/Keycloak per full native campaign; bounded legal cancellation releases delivery stock. Test-only loopback fixture controller restores quantity policy; do not share or erase another campaign.

Automation/manual reference: .github/workflows/independent-qa-regression-native.yml; qa/regression-d877a15/native/instrument.py and collect.py; pinned application native auth/order scripts (independently executed, assertion meaning reviewed).

Review: SELF_REVIEWED. Questions: Commercial values are synthetic, O04 remains open. Applicant legal documents O06, support contacts O09; no owner approval inferred.
