# Repairs for the independent cross-role QA assessment (2026-10-12)

Written by the implementation agent. This is **self-review**, not independent verification. Every finding below is **FIXED_PENDING_INDEPENDENT_RETEST** until Codex retests it. The QA report and branch are unchanged and were not merged.

## Source

| Item | Value |
|---|---|
| QA branch | `qa/independent-cross-role-4eaee3a-20261012` (read only) |
| Evidence commit | `f76664c777132e78e012c24ca50150ab13f59b09` (`report.md`, `repair-handoff.md`, `native-results.md`) |
| Application tested by QA | `4eaee3a1f53b929b9e7685fd30da689d470d095c` |
| **Repair candidate** | **`4555f599411da9683453bee209559c8d1f11cded`** on `implementation/mvp` (full SHA; retest this, not the branch head) |
| Evidence | Red logs and browser journeys: `verification/runs/20261012-QA-REPAIR-2/`. Six local checks at the candidate, all PASS (backend verify, clean-clone workspace with contract drift, local stack, browser journeys 5/5 incl. MBQA-003/004, secret scan, guardrails): `verification/runs/20261012-QA-REPAIR-2-CHECKS/evidence.json`. Native run 35 at the candidate: PASS on the Android emulator and iOS simulator (`NATIVE-RUN-35-4555f59.txt`) |

The candidate also contains everything committed after `4eaee3a`: Q1 clients, MB-032/MB-033 native work, MB-039 audit changes and the PREVIEW environment. See [07-codex-qa-repair.md](07-codex-qa-repair.md) for that history.

## Findings, fixes and status

| Finding | Reproduced here | Fix (files) | Regression tests | Status |
|---|---|---|---|---|
| **MBQA-003** Major: no customer web sign-out | Yes: 4 new component tests red on the previous code (`RED-MBQA-003-004-customer-web.log`) | `apps/customer-web/src/App.tsx`, `i18n.ts` (details below) | Component: CSRF header on `POST /api/v1/auth/logout`; basket, pending keys and profile cleared; IdP logout URL followed; English label; keyboard focus; warning before abandoning an unconfirmed order; network failure keeps the session. Browser on the real stack (`customer-journey.e2e.ts`): sign-out → `GET /api/v1/me` 401; basket storage empty; reload and back show no account data; the next sign-in shows the IdP login form | FIXED_PENDING_INDEPENDENT_RETEST |
| **MBQA-004** Major: customer cannot read public support replies | Yes: 4 component tests red (same log) | `apps/customer-web/src/Account.tsx` (`SupportCaseDetail`), `App.tsx` (route `#/support/{caseId}`), `Orders.tsx` (links from the order), `i18n.ts` | Component: opened from Account; public messages oldest first with "You" / "Support team"; reply with Idempotency-Key; a STAFF_ONLY message never rendered; 404 shown as an error with retry; no reply on a closed case. Browser on the real stack: case opened in the UI; staff public reply and staff-only note inserted as test-only SQL; the public reply is shown, the note is not; the customer replies; customer-a gets 404 (API) and sees nothing (UI); no overflow at 320/390 px; axe clean | FIXED_PENDING_INDEPENDENT_RETEST |
| **MBQA-005** Moderate: Close offered on a DRAFT slot | Yes: red (`RED-MBQA-005-ops-web.log`) | `apps/operations-web/src/cook/Slots.tsx`: actions per state mirror `SlotService` (DRAFT→Open; OPEN→Pause/Close; PAUSED→Open/Close; CLOSED/CANCELLED none). The backend is unchanged and still refuses DRAFT→CLOSED | Component: one card per state with exactly its actions; a stale OPEN card whose Close meets 409 STATE_CONFLICT shows "This action is no longer available. Refreshed." and re-renders from the server | FIXED_PENDING_INDEPENDENT_RETEST |
| **OBS-001** Low: unhandled rejection when the recovery lookup cannot reach the server | Yes: red, `TypeError: Failed to fetch` unhandled (`RED-OBS-001-web.log`) | `apps/customer-web/src/Checkout.tsx`: the lookup failure is caught; the pending keys and basket stay; the screen stays "unconfirmed" with the retry | Component test with an unhandled-rejection listener | FIXED_PENDING_INDEPENDENT_RETEST |

**MBQA-003 details:**
- Uses the existing Spring Security logout. The server invalidates the session and deletes the cookie, and CSRF applies as for every mutation.
- The response carries the Keycloak end-session URL, which ends the IdP session, so the next sign-in asks for credentials.
- Private state cleared: basket, pending order keys, profile.
- With an unconfirmed order in this browser, the customer is warned first.
- A page restored from the back-forward cache reloads.
- If the logout request fails, nothing is cleared and the customer stays signed in.

Shared adjacency: the sign-out button replaces "Sign in" in the header when signed in, and the Account and Orders links disappear after sign-out. The MBQA-001 language switch, MBQA-002 slot selection and FG-001 basket tests all still pass (customer web: 37 tests).

## Q1: what the discrepancy was and what is verified now

- **At `4eaee3a` (QA tested):** clients still used 20 (11/20 reachable on a high-stock slot). The server already enforced 10 (quote 422 `RANGE_1_TO_10`). QA's report is correct for that SHA.
- **Client integration came later, in `cdc592a`:**
  - web and native read API-001;
  - the meal page caps at min(limit, slot portions);
  - cart editing caps lines;
  - a restored basket above a lowered limit is flagged and gets no quote.
  - The red log from before that commit shows the web reaching 15 with limit 10 (`verification/runs/20261012-Q1/RED-web-Q1.log`).
  - **Classification: a difference between tested versions.**
- **Genuine remaining gap found during this review, now fixed:**
  - The gap: both clients read the limit only once, at page load or app launch. A policy lowered while the page stayed open was caught only by the server's 422 at quote time.
  - Web now re-reads it when the cart or a meal page opens; native re-reads it when the cart gains focus.
  - A failed re-read keeps the last limit instead of loosening the controls.
  - Red first: `RED-Q1-web-limit-refresh.log`, `RED-Q1-native-limit-refresh.log`.
- **Verified behavior at the candidate:**

  | Check | Web | Native |
  |---|---|---|
  | Initial entry capped at min(10, portions) | Component tests | Component tests; Android emulator run 33 and iOS simulator run 32: cap 10 of 15 |
  | Quantity editing in the basket capped | Component tests | Component tests |
  | Restored basket above a lowered limit flagged, no quote, recovers after reduction | Component tests | Component tests; Android run 33 and iOS run 32 (limit lowered to 5 in the synthetic CI policy) |
  | Policy lowered after the basket was created, page still open | Component test | Component test only. Native runs 32–35 lower the limit and then **relaunch** the app, so they exercise the restored-basket path, not the mid-session refresh; that refresh has no emulator/simulator execution yet |
  | Reduced availability on a restored basket | Server refuses (`INSUFFICIENT_PORTIONS`, shown as an error; quote not created) | Same |
  | Direct API above the limit | `QuoteIntegrationTest`: 11 under 10 → 422 | Same server |

The value 10 is the synthetic policy, not an owner decision; O04 stays open.

## RQ-004: stale quote after a fee change

The requirements settle it: TC-020, TC-042 and the API contract all say a stale quote is refused with a recoverable 409 and must be re-quoted. The behavior is unchanged. The staff UI text, the contract description and a BR-042 note now say so explicitly. One confirmation question is in [rq-004-stale-quote.md](../product/rq-004-stale-quote.md). For grading, the QA case S-CONFIG-QUOTES expectation is "refused, no order, old quote unchanged".

## Retest instructions

- **Revision:** check out `4555f599411da9683453bee209559c8d1f11cded` in a new isolated worktree. Use a new Compose project, new ports and a fresh synthetic seed. The previous campaign's database has terminal states; do not replay against it.
- **Identities (realm `infra/identity/matbakhy-realm.json`, synthetic):** customer-a, customer-b, cook-a, ops-a, and the other synthetic users. Passwords are generated per stack (`DEV_USER_PASSWORD` in `infra/.env`).
- **Fixtures:**
  - dev profile commerce policy version 1 (limit 10, tax 0, commission 10%);
  - zones Test zone A (fee 3000) and B;
  - your own catalogue SQL as before.
- **Retest:**
  1. **MBQA-003:**
     - sign in as customer-a; the header shows "تسجيل الخروج" / "Sign out";
     - sign out; check `GET /api/v1/me` 401, reload, back, then sign in as customer-b and see the login form;
     - also with a basket and with an unconfirmed order (expect the warning).
  2. **MBQA-004:**
     - open a case from Account;
     - staff send STAFF_ONLY then PUBLIC in the operations web;
     - the customer opens the case from Account or the order page, reads the public reply only, and replies;
     - customer-b gets 404;
     - check both languages at 320–390 px.
  3. **MBQA-005:** a DRAFT card shows only Open; check OPEN and PAUSED actions, and a stale Close on a card closed elsewhere.
  4. **Q1:** cap on the meal page and in the basket; policy lowered while the cart page is open.
  5. **Adjacency:** MBQA-001 language, MBQA-002 mixed-stock slot, FG-001 basket edits and replacements, totals.

## Native harness notes (for the iOS blockers in `native-results.md`)

The implementation agent's iOS runs completed sign-in at these SHAs; independent iOS coverage is still open. Differences that may explain QA's blockers:
- `scripts/launch-ios.sh` boots the simulator and waits (`simctl bootstatus -b`), installs, launches once (warm launch), and disables the hardware keyboard (`ConnectHardwareKeyboard false`).
- `e2e-ios/UITests/SignInRoundTripTests.swift` and `OrderingTests.swift`:
  - search the login fields in both the app and `com.apple.SafariViewService`;
  - dismiss the keyboard by tapping a hittable heading;
  - submit the password with a newline.
- The app uses an ephemeral browser session on iOS (`src/auth.ts`). No "Save Password" sheet appeared in our runs, so we have no handling to offer for it.
- On failure, our helper prints the hierarchy between `MB031-HIERARCHY-BEGIN/END` markers.

Android basket editing: our script `order-roundtrip-android.sh` scrolls relative to the real screen size (`wait_scrolled`, `wait_scrolled_up`). Below-the-fold controls were the cause of our own Android harness failures (runs 23–26, 32).

## Remaining gaps and questions

- **Independent coverage:** iOS authentication, ordering and recovery; Android basket editing and lifecycle; native mixed-stock and same-kitchen slot variants.
- **Not available:** screen reader (BLK-003; MB-029/TC-047 blocked); positive SMS verification (fake sender; a simulated provider is not real delivery); physical devices (BLK-004).
- **Owner questions:** RQ-001/002/003; RQ-004 confirmation; O04 limit value; O06/O07/O09.

## Corrections and changes after the candidate (2026-10-13)

- **Secret scan at the candidate:**
  - The repair-candidate local SECRET-SCAN (`20261012-QA-REPAIR-2-CHECKS`) ran from a git worktree and scanned 0 commits of history while printing PASS. Its working-tree scan is valid.
  - Product CI's Gitleaks job was failing for an unrelated reason: synthetic idempotency keys in the QA branch were flagged.
  - Both are fixed in `ed2ac56`, and a full-history scan (which covers `4555f59`) found no secret in `implementation/mvp`. See [the record](../../verification/runs/20261013-SECRET-SCAN-FIX/README.md).
  - The candidate's code is unchanged and it remains the retest target.
- **RQ-004 wording:** the earlier sentence that the screen "never presents a total the server would not accept" was wrong.
  - An old quote stays on screen until the customer presses Place order. Only then does the server revalidate and refuse it (409 `PRICE_CHANGED`).
  - The clients then remove the quote, show "The price changed. Calculate the total again." and require a new quote and a fresh terms acceptance. No new total is accepted silently.
  - See the corrected [rq-004-stale-quote.md](../product/rq-004-stale-quote.md). Client tests for that path are listed as the next MB-033/RQ-004 task.
- **Product changes after the candidate.** These are not part of the retest; they are backend MB-039 changes, each tested red first with focused integration tests (`verification/runs/20261012-MB-039-LOCAL/`):

  | Commit | Change |
  |---|---|
  | `885824b` | Staff support-case views audited (`access.support_case`) |
  | `3d88aaa` | Staff support messages audited with the request ID |
  | `00b5189` | Audit-detail redaction keeps whole-value UUIDs |
  | `1e8ca5d`, `0d0621d` | ECS log lines carry `service.name`, `service.environment` and `service.version` |
  | `b01453e` | Draft troubleshooting runbook (documentation) |

  The full six-check set has not been re-run after `4555f59`. A newer retest candidate will be published explicitly if one is needed.
