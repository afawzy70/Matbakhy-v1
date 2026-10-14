# Native run 43 (`ae9fdbf`)

Workflow: https://github.com/afawzy70/Matbakhy-v1/actions/runs/38392564050 (conclusion: failure, because the iOS job failed). Source `ae9fdbf` = `49a55f1` (MB-053 native screens and deposit phase) plus a status-only commit. Android emulator (API 35, x86_64, Google APIs image) and iOS simulator, GitHub-hosted runners, synthetic data on a local stack. Not physical devices (BLK-004); no screen reader (BLK-003).

## Android: every step PASS

Result lines from the job log (job 115280292051), quoted verbatim:

- `PASS: Android release-variant build launched; Discover tab visible; no fatal errors`
- `PASS: Android system-browser PKCE round trip; secure-storage restore; sign-out and account switch leave no previous-user session or data`
- `side effects (orders|allocations|meal-slot reserved|cook-slot reserved|cash obligations|ORDER_PLACED events|order events): 1|1|1|1|1|1|1`
- `order MB-100001; database (number|total_minor): MB-100001|9000; app shows text="Total: EGP 90.00"; quote showed text="Total: EGP 90.00"`
- `ok: the control stopped at 10 of 15 available portions and is disabled there`
- `ok: the saved basket was flagged, no quote was requested, and reducing to 5 cleared it`
- `delivery order MB-100002 (mode|fee|total|address snapshot|kitchen|state): DELIVERY|3000|63000|true|00000000-0000-4000-8000-00000000a0d1|PENDING_ACCEPTANCE`
- `ok: the order link opens order MB-100002 warm and cold`
- `ok: same process (8567): limit 10 -> 5 while running; reopening the cart flagged the line`
- `repriced order MB-100003 (total|unit|quantity): 13000|13000|1` and `ok: refused at the stale price with no order; notice with old and new prices; placed only after acceptance at 130.00`
- `ok: [en] discover, meal, cart, checkout actions, orders, order, notifications and support at font scale 2.0`
- `ok: [ar] discover, meal, cart, checkout actions, orders, order, notifications and support at font scale 2.0`
- `ok: not asked on launch; Allow opened the system dialog; Don't allow left the app usable with the Settings path and in-app notifications`
- **MB-053:** `deposit order MB-100004 (method|state|half|recipient): DEPOSIT|WAITING_FOR_DEPOSIT|true|synthetic-kitchen@instapay` and `ok: cash refused above the limit; deposit, recipient and direct-payment note shown; order MB-100004 waits for the deposit; the customer's report is shown as theirs, not as payment` (the script then checked `CUSTOMER_TRANSFER_REPORTED` in the database).
- Final: `PASS: Android ... deposit checkout and transfer report shown honestly`.

33 screenshots uploaded as the `android-screenshots` artifact (ID 11812727482, retention 7 days). Visual review of the large-text screenshots is still open.

## iOS: FAIL in the first sign-in step (harness)

`SignInRoundTripTests.swift:98: ... failed - customer-a not signed in`. The hierarchy shows the iOS "Save Password?" alert still open over the app, with "Sign in" disabled while the redirect waits. The dismissal looked in three places one after another, for 4 s each, and the prompt appeared later. Ordering, D13, large-text, refusal and deposit phases did not run on iOS in this run (NOT_RUN, not PASS). Harness fix in `5efc139` (poll all three places together for up to 30 s); rerun in run 44.
