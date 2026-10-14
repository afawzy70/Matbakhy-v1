# Native run 50, 2026-10-14 (MB-033 large-text edge report)

Implementation-agent review of the CI job logs. This is not human approval and not a visual review of the screenshots.

- Run: [38508543661](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38508543661) at `d9b0873`. App source is the same as candidate I (`be33bc2`); the change is test code only.
- **Android** (job 115671913506): **every phase PASS.** This covers sign-in, recovery, limits, delivery, deep links, D13, the largest text (font scale 2.0) in English and Arabic, notification refusal, and the MB-053 deposit.
  - `EDGE-REPORT` for all 16 large-text screens (Discover, meal, cart, checkout, orders, order, notifications and support, × English and Arabic): screen width 320 px, **0 labelled elements past the edge** on every screen.
- **iOS** (job 115671913176): sign-in PASS; ordering phases **A–I PASS**, including `testI` (largest accessibility text, English and Arabic: `MB033-DYNAMIC-TEXT-OK en`/`ar`).
  - **Phase J FAIL** (harness): `Failed to tap "tab-account"` 10 s after `app.launch()`. The test tapped without waiting for the first screen, unlike the other phases. Phase J passed in run 48 on the same app source. **Phase K (deposit) was NOT_RUN** in this run, because the script stops at the first failure.
  - **The iOS `EDGE-REPORT` lines were not visible.** The script's log filter passed only `MB0xx-` markers. **iOS edge result: NOT_RUN.**
- **Fixes** (test code only), in the commit after this record:
  - phase J waits for the Account tab;
  - the iOS log filter passes `EDGE-REPORT`.

Limits:
- The edge report finds labelled elements outside the screen width. It does not find text truncated inside an element or overlapping elements.
- The screenshots themselves are not reviewed: the artifact host is blocked by this environment's network policy.

## Run 51 (after the harness fix)

- Run: [38513847516](https://github.com/afawzy70/Matbakhy-v1/actions/runs/38513847516) at `ef2dfd1` (same app source; test code only changed).
- **Android** (job 115688114943) and **iOS** (job 115688115025): **every phase PASS**.
  - iOS phase J PASS (`MB033-ALERTS-DENIED-SAFE`).
  - iOS phase K (MB-053 deposit) PASS: order MB-100004 is DEPOSIT|CUSTOMER_TRANSFER_REPORTED; the terms showed "Deposit now: EGP 65.00".
- **iOS `EDGE-REPORT`** for all 16 large-text screens (8 areas × English and Arabic): screen width 390 pt, **0 labelled elements past the edge** on every screen.
- **Combined with Android run 50:** 32 large-text screens on two platforms in two languages, no labelled element past the screen edge.
  - Implementation-agent review of the logs. It is not a visual review and not human approval.
  - Truncation inside elements is not checked.
