# Preview environment

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — a synthetic-data preview on one VPS is deployed from `develop` by `.github/workflows/deploy-preview.yml`; it is not staging and not production. Server state was not inspected while writing (NOT VERIFIED).

## What it is

An access-restricted, demo-labelled environment with synthetic data, run with Docker Compose from `deploy/web-preview` (`compose.yaml`, optional `compose.4gb.yaml` sizing override, Caddy reverse proxy in `deploy/web-preview/Caddyfile`, Keycloak realm template under `deploy/web-preview/identity`, S3-compatible storage under `deploy/web-preview/storage`, Grafana/Loki/Prometheus config under `deploy/web-preview/observability`). The API runs with `APP_ENV=PREVIEW`, which the code treats as synthetic (fixtures and non-sending providers allowed; see `DeploymentEnvironment` in `backend/app/src/main/java/com/matbakhy/app/config/MatbakhyProperties.java`). Public configuration reports `{"environment":{"name":"PREVIEW","demo":true}}`, which `deploy/web-preview/smoke.sh` asserts.

Background: [06-web-preview.md](../../operations/06-web-preview.md), [09-staging-hetzner-preparation.md](../../operations/09-staging-hetzner-preparation.md), [11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md), [12-owner-server-setup.md](../../operations/12-owner-server-setup.md).

## Addresses

Hosts follow the pattern below. The concrete `<PREVIEW_DOMAIN>` lives in the server's `.env` and is baked into the front image by the deploy workflow; it is deliberately not copied into this handbook. Ask the owner, or read the owner guide [owner-guide-ar.md](../../quality/owner-acceptance/owner-guide-ar.md).

| Host | Purpose | Access class |
|---|---|---|
| `https://app.<PREVIEW_DOMAIN>` | customer web | customer |
| `https://ops.<PREVIEW_DOMAIN>` | cook workspace and back office (operations web) | staff |
| `https://api.<PREVIEW_DOMAIN>` | API (`/api/v1/*`; `/api/v1/ops` and `/api/v1/cook` are staff-class) | customer, staff paths staff |
| `https://id.<PREVIEW_DOMAIN>` | Keycloak realm `matbakhy` (login) | customer |
| `https://kc-admin.<PREVIEW_DOMAIN>` | Keycloak admin console, Preview only, opened by `deploy.sh admin-console enable`; created with hardening and closed again on failed verification | see [14-keycloak-admin-console.md](../../operations/14-keycloak-admin-console.md) |
| `media.<PREVIEW_DOMAIN>` | public media bucket | customer |

No credentials are listed here. Synthetic account names: `preview-customer-1`, `preview-customer-2`, `preview-cook-1`, `preview-ops-1`, `preview-ops-2`, and the `owner-e2e-*` set once provisioned. Passwords are held only in the server's private files and handed over by the owner.

## Who can reach it

Deny by default. The proxy has two classes (header comment in `deploy/web-preview/Caddyfile`):

- customer class: `CUSTOMER_ALLOWED_IPS` for `app.`, the customer API, `id.` and `media.`;
- staff class: `STAFF_ALLOWED_IPS` for `ops.` and `/api/v1/ops`, `/api/v1/cook`.

Both default to `ALLOWED_CLIENT_IPS`; nothing is public until the owner sets `CUSTOMER_ALLOWED_IPS` to open access. An outsider receives HTTP 403 "Not available". If you see 403, your IP is not on the list: send the owner your public IP and the host you need; never ask for the list to be opened to everyone. Master-realm and `/admin` paths on `id.` answer 404.

## Using it

1. Get your IP allow-listed and the account you need from the owner.
2. Open `https://app.<PREVIEW_DOMAIN>` (Arabic RTL by default; the demo notice must be visible).
3. Sign in through Keycloak; staff features need an account with the right grants ([actors-and-permissions.md](../01-business/actors-and-permissions.md)).
4. Run the read-only smoke from your machine: `sh deploy/web-preview/smoke.sh <PREVIEW_DOMAIN>` (use `SMOKE_OPS=skip` if you are not in the staff class).
5. Browser smoke: `pnpm --filter @matbakhy/operations-web e2e:staging` with the `STAGING_*` variables ([test-execution.md](test-execution.md)). It creates one demo order per run.
6. Which release is deployed: ask the operator to run `./deploy.sh status` (running images, schema version, last releases). Compare the commit with `git log`. The preview may be older than `develop` while a deployment is queued.

## What it is NOT

- Not production: no real customers, no real money, no live SMS/email/push (providers are fakes; `PUSH_DELIVERY_ENABLED` defaults to false in `application.yaml`), no legal/provider compliance sign-off.
- Not staging with real integrations (that is MB-040 in `planning/backlog.json`, status to be checked there).
- Not highly available: one server, backups are local to the host until copied off, alerts go to a local test sink until the owner approves a recipient ([08-troubleshooting-runbook.md](../../operations/08-troubleshooting-runbook.md)).
- Not a place for real personal data or for exploratory destructive tests; destructive and race cases belong on an isolated stack ([owner-e2e-execution-guide.md](../../quality/owner-e2e-execution-guide.md)).
- Not authorization to expose Keycloak admin or weaken access control to make a test pass.

## Operating it (operators only)

Run on the server from the deployed `deploy/web-preview` directory; developers normally do not need this. Script header in `deploy/web-preview/deploy.sh`. Highlights: `status`, `smoke`, `backup`, `rollback-plan`, `prune-qa`, and `./migrate-mfa.sh status` before any MFA case (see [13-keycloak-mfa-migration.md](../../operations/13-keycloak-mfa-migration.md)). Fixture provisioning: `owner-e2e-provision.sh` via the request file ([ci-cd.md](ci-cd.md)). Logs: Grafana (tunnel only) and `docker compose logs` ([troubleshooting.md](troubleshooting.md)).

## Local rehearsal

`scripts/rehearse-preview.sh` (`build|up|smoke|load|persist|rollback|e2e|down`) rehearses the preview package on a developer machine with an internal certificate authority. It is not evidence about the real host (script header says so). Its results were recorded by the implementation agent; running it yourself: NOT VERIFIED here.

## Known limitations and documentation gaps

- `docs/operations/06-web-preview.md` and `deploy/web-preview/README.md` still describe the preview as "prepared, not deployed", while the workflow deploys it on every push to `develop`. Trust the workflow and `docs/quality/owner-acceptance/run-2026-10-19.md` for the current state; verify with `deploy.sh status`.
