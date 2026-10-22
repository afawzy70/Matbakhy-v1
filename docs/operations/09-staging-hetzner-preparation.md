# Hetzner staging: preparation (prepared, not deployed)

> **HISTORICAL (OUTDATED / INCORRECT as current instructions).** Written on 2026-10-14 for a *proposed* Hetzner 2 vCPU / 4 GB server that was not the host that was finally used. The running Preview is on a Contabo VPS (8 vCPU, about 23 GiB RAM, deployed to `/srv/matbakhy-staging`). Use the current, evidence-labelled pages instead: [Contabo server inventory](../developer-handbook/08-infrastructure/contabo-server-inventory.md), [server rebuild guide](../developer-handbook/08-infrastructure/server-rebuild-guide.md) and the [drift report](../developer-handbook/07-reference/infrastructure-documentation-coverage.md). Kept unchanged below for history.

Implementation-agent record of 2026-10-14, self-review. **No staging environment exists.** Infrastructure access and deployment are not ready; nothing was bought, configured, deployed or tested on a server. Every statement below about the server is a plan or a recommendation until the owner has set the server up and the checks in section 7 have run.

## 1. Owner direction (2026-10-14)

- A Hetzner VPS with 2 vCPU and 4 GB RAM is selected for public staging. The owner configures the server separately.
- The agent prepares configuration and integration only: no deployment, no purchases.
- Proposed staging retention defaults: application logs 30 days, debug logs 7 days, non-sensitive QA artifacts 90 days. They are not approved production or customer-data retention policies. O06 stays open.
- O07: in-app notifications continue; push is prepared behind a disabled feature flag and is not activated without credentials, device testing and explicit approval.
- Screen-reader (BLK-003) and physical-device (BLK-004) gates stay open until genuinely verified.
- Once staging is available, the first runs are AUD-01, LOG-01, OPS-01..03, TC-057 and the relevant real-API regression suite.

## 2. Which application environment the server runs

The package in `deploy/web-preview` starts the API with `APP_ENV=PREVIEW`: synthetic data, providers that never send anything, real secrets, HTTPS only, access restricted at the proxy.

`APP_ENV=STAGING` cannot start today **by design**. `ProviderConfiguration` refuses it without real SMS, e-mail, push and malware-scan providers and object-storage credentials (O07: no provider is chosen). Sandbox providers do not exist yet.

So the first public staging on the Hetzner server runs the PREVIEW profile, with synthetic customers and kitchens. **Owner decision (2026-10-14, later): approved.** The first Internet-accessible staging runs the PREVIEW profile: synthetic data, simulated deposits and payments, real external notifications disabled, and a persistent notice on every screen saying it is a demo/test environment (customer web, operations web; the API reports `environment: PREVIEW, demo: true`). Privileged operations are not exposed publicly: `ops.`, `/api/v1/ops/*` and `/api/v1/cook/*` follow a separate staff allow-list; only the customer hosts can be made public, by the owner's explicit setting. Real-provider staging still needs O07 provider decisions.

## 3. The 2 vCPU / 4 GB profile

`deploy/web-preview/compose.4gb.yaml` lowers the memory ceilings; set `COMPOSE_OVERRIDE=compose.4gb.yaml` in `.env` (`deploy.sh` adds the file to every compose call).

| Service | Default ceiling | 4 GB profile |
|---|---|---|
| API | 2 GiB | 1 GiB |
| Keycloak | 1 GiB | 768 MiB |
| PostgreSQL | 1.5 GiB | 512 MiB |
| Storage | 512 MiB | 192 MiB |
| Proxy | 256 MiB | 128 MiB |
| Loki / Alloy / Prometheus | 384 / 256 / 512 MiB | 256 / 128 / 192 MiB |
| Alertmanager / alert sink / node exporter | 128 / 64 / 64 MiB | 64 / 32 / 48 MiB |
| Grafana | 256 MiB | 192 MiB, optional (profile `tools`) |
| **Total** | about 6.9 GiB | about 3.3 GiB, 3.45 GiB with Grafana |

- **These are ceilings, not measurements.** Idle use of the whole stack in the local rehearsal was about 1.2 GiB. Peaks, a backup running beside the API, and Keycloak start-up on 768 MiB are unmeasured. The earlier sizing note said 2 vCPU / 4 GB "would probably start, but has no headroom"; that still stands until the staging run measures it.
- **Recommended on the host (owner server configuration):** a 2 GiB swap file, so a short peak is not an OOM kill; enough disk for the database, image storage, logs (30 days), metrics (2 GB cap) and backups. The disk size is a recommendation to be checked against real growth (alert `DiskPressure` exists).
- **Grafana:** `COMPOSE_PROFILES=tools ./deploy.sh up` starts it; otherwise the alerts and logs are still collected.
- **Guardrail:** `scripts/test_staging_profile.py` fails if the profile's ceilings reach 3.5 GiB, if a service has no ceiling, or if the default profile changes.

## 4. Retention (proposals; O06 unresolved)

| Data | Staging default (proposal, 2026-10-14) | Where it is applied |
|---|---|---|
| Application logs | 30 days | Loki `retention_period` (`LOG_RETENTION=720h`); verified with the Loki 3.5.3 binary (`-verify-config`: 30d) |
| Debug-level logs | 7 days | Loki `retention_stream` for `{level="DEBUG"}` (`DEBUG_LOG_RETENTION=168h`; verified: 1w) |
| Non-sensitive QA artifacts (synthetic screenshots, reports) | 90 days | `./deploy.sh prune-qa` lists files older than `QA_ARTIFACT_RETENTION_DAYS` in `QA_ARTIFACT_DIR`; `--apply` deletes them (dry run by default; refuses system paths) |
| Metrics | 15 days or 2 GB | Prometheus (unchanged rehearsal value) |

The API's default level is INFO, so debug lines appear only when someone raises the level.

**Unresolved under O06 (legal and business decisions; not covered by the defaults above):**

- retention of customer personal data and of order and payment history;
- retention and archival of the append-only audit log (it must stay append-only);
- support-case messages, device registrations and organic-activity rows;
- database backup retention (the backup script writes dated dumps; no automatic pruning is implemented);
- production log retention for logs that can relate to customers;
- the 14-day account-deletion grace period (already a proposal);
- the customer privacy notice.

## 5. Push notifications behind a flag (O07)

- **Flag:** `PUSH_DELIVERY_ENABLED` (property `matbakhy.notifications.push-enabled`), **false by default** in the API and in the deployment files.
- **With the flag off:**
  - in-app notifications and the inbox work as before;
  - device registrations are accepted and kept, with tokens encrypted at rest;
  - no PUSH delivery is created, so nothing reaches a push provider and nothing dead-letters;
  - the API starts in every environment without a push provider; real token keys are still required outside LOCAL/CI.
- **With the flag on:** LOCAL, CI and PREVIEW use the logging stand-in (nothing is sent); STAGING and PRODUCTION still refuse to start, because **no FCM or APNs adapter exists**. The `PushSender` port (payload carries only an event hint) is the integration point.
- **Tests:** `PushFlagIntegrationTest` (flag off: customer notified in-app, e-mail delivery present, no PUSH delivery, device kept), `ProviderConfigurationTest` (every environment), and the existing `NotificationIntegrationTest` and `LogRedactionIntegrationTest` with the flag on.
- **Needed before it can be switched on:** the owner's approval; FCM/APNs credentials held outside the repository; an adapter written against them; device tests on physical Android and iOS devices (BLK-004); a client-side signal. Known gap: the apps do not read the flag, so the Account screen may still offer the system notification prompt while the server sends no push. A `pushAvailable` field in the public configuration is the likely fix, to be built with the adapter.

## 6. What the owner configures on the server (not done by the agent)

1. Create the server and a non-root operator account; key-only SSH, firewall open for 80 and 443 and for SSH from known addresses only.
2. DNS names `app.`, `ops.`, `api.`, `id.` and `media.` under the chosen domain, pointing at the server.
3. Install Docker and Compose; a 2 GiB swap file; the backup and QA-artifact directories.
4. Build the two images at a release commit and pin them by digest (see `deploy/web-preview/README.md`, "Images"). Building is not done on the server.
5. `./deploy.sh init-secrets`, fill in the remaining inputs (including `ALLOWED_CLIENT_IPS`) and set `COMPOSE_OVERRIDE=compose.4gb.yaml`. Secrets stay on the server.
6. `./deploy.sh up`, `seed`, `smoke`. The agent has not run any of these on a server.

Deployment, DNS, purchases and tester access need the owner's explicit go; owning or paying for a server does not by itself authorize the agent to deploy to it.

## 7. Checks to run when staging is available (in this order)

| Check | What | Notes |
|---|---|---|
| AUD-01 | Audit coverage and reliability on the deployed API | Local evidence: `07-audit-and-log-readiness.md`; the runtime database role must be verified on the real stack |
| LOG-01 | Request ID correlation and searchable structured logs | Loki search by request ID |
| OPS-01 | Log retention across redeploy and restart, collector outage | Local rehearsal passed; the full-disk drill is still NOT_RUN |
| OPS-02 | Metrics and alerts to the approved operator route | Alerts go to the local test sink until the owner names a route |
| OPS-03 | Operator follows `08-troubleshooting-runbook.md` | Includes the outbox retry |
| TC-057 | Restore of a staging backup with measured RPO and RTO | `./deploy.sh backup` already verifies a restore into a scratch database; the timings still need measuring |
| Real-API regression | The six browser journeys and the API integration scenarios against the deployed stack | **Prepared, not run on a server.** The suite no longer touches the database: it sends fixture names to a restricted server-side script (marker file, allow-listed SQL files, parameters as `psql` variables) and needs an empty database, so it runs in a guarded window with `./deploy.sh e2e-reset` ([11](11-staging-deployment-and-rollback.md), section 5). Rehearsed against a local https stack, not against Hetzner |

External testers stay blocked until AUD-01, LOG-01 and OPS-01..03 pass on the actual environment.

## 8. Further documents

[11 Deployment, smoke tests, rollback and the E2E window](11-staging-deployment-and-rollback.md) · [12 What the owner does on the server](12-owner-server-setup.md) · [10 Network allowlist request](10-agent-network-allowlist.md)

## 9. Still open

BLK-003 (screen readers), BLK-004 (physical devices), O06 (retention and privacy policy), O07 (providers, push, budget, domain), O04 (commercial limits), live money (D16 gates).
