# QA candidate J: push behind a flag, Hetzner staging preparation, 2026-10-14

Implementation-agent handoff. Every result below is self-review by the implementation agent, not independent review.

## Candidate

- **Immutable candidate: `52e4e21cd3788e0429855ded44e52ca3041d03c5`** on `implementation/mvp`.
- The six local checks ran at this SHA from a clean worktree with fresh synthetic data ([evidence](../../verification/runs/20261014-CANDIDATE-J/evidence.json)).
  - BACKEND-VERIFY, WORKSPACE-CLEAN-CHECKOUT, LOCAL-STACK, BROWSER-E2E (6/6), SECRET-SCAN (368 commits plus the committable tree) and GUARDRAIL-TESTS: all PASS.
- Candidate J includes everything in candidate I. The app source (web and native) is unchanged since candidate I; only the backend, deployment files and documents changed.
- MBQA-003, MBQA-004, MBQA-005, MBQA-006 and OBS-001 stay **FIXED_PENDING_INDEPENDENT_RETEST**.

## Delta since candidate I (`be33bc2`)

| Area | Change | Commits |
|---|---|---|
| O07 push flag | Push delivery is behind `PUSH_DELIVERY_ENABLED`, off by default. Off: in-app notifications and e-mail work, devices stay registered, no PUSH delivery is created, the API starts in every environment without a push provider. On: LOCAL, CI and PREVIEW use the logging stand-in, STAGING and PRODUCTION refuse to start (no FCM/APNs adapter exists) | `9e9364f` |
| Staging preparation | 2 vCPU / 4 GB memory profile, proposed retention defaults (logs 30 days, debug 7 days, QA artifacts 90 days; not approved policies), QA-artifact prune (dry run by default), guardrail tests. **Nothing was deployed** | `ec9b54f` |
| Docs | [Staging preparation](../operations/09-staging-hetzner-preparation.md), [allowlist request](../operations/10-agent-network-allowlist.md), status | `4e87bd4`, `eac2a13` |

## What to retest independently

1. **Push flag off (default):**
   - A customer registers a device, the kitchen accepts the order.
   - Expected: the customer's inbox has «ORDER_ACCEPTED», and `notification_delivery` has an EMAIL row and **no PUSH row** for that notification. The device registration is still there.
2. **Push flag on in local development** (`PUSH_DELIVERY_ENABLED=true`): the PUSH delivery is created and sent by the logging stand-in; the log shows the delivery id and template only, never the token.
3. **Deployed environments:** `APP_ENV=STAGING` or `PRODUCTION` with the flag on must fail at start with "No push notification provider is configured".
4. **Retention config:** `docker run grafana/loki:3.5.3 … -verify-config` accepts `observability/loki.yaml`; the effective values are 30 days and, for `{level="DEBUG"}`, 7 days.
5. **QA prune:** `QA_ARTIFACT_DIR=<temp dir> ./deploy.sh prune-qa` lists only files older than 90 days and deletes nothing; `--apply` deletes only those.
6. Everything in the handoffs for [I](13-qa-candidate-i-payment-case.md), [H](12-qa-candidate-h-erasure-native.md), [G](11-qa-candidate-g-insights-followups.md) and [F](10-qa-candidate-f-deposit-payments.md).

## Known gaps

- No staging environment exists; AUD-01, LOG-01, OPS-01..03, TC-057 and TC-139 stay NOT_RUN.
- The memory ceilings of the 4 GB profile are not measured use.
- The apps do not read the push flag yet; no push adapter exists; push needs credentials, device tests (BLK-004) and the owner's approval.
- Retention policies are O06; the defaults above are staging proposals only.
- Screen readers (BLK-003) and physical devices (BLK-004) are open. The large-text screenshots have no visual review (artifact host blocked).
- Live money is not approved.
