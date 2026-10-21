# Infrastructure and deployment

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — local stack and an access-restricted synthetic-data preview exist; there is no staging or production deployment, and the preview is NOT production
> **Language / اللغة:** English · [العربية](infrastructure-and-deployment-ar.md)

Diagrams: [CI/CD](diagrams.md#13-cicd) and [preview infrastructure](diagrams.md#14-preview-infrastructure). Day-to-day procedures belong to [preview environment](../05-developer-workflow/preview-environment.md) and [CI/CD](../05-developer-workflow/ci-cd.md); this page explains what the pieces are and how they fit.

## Environments at a glance

| Environment | Where it runs | Config | Data | Purpose |
|---|---|---|---|---|
| Local development | Developer machine, Docker Compose + local JDK/Node | `infra/compose.yaml`, `scripts/dev.sh` | Synthetic fixtures from the `dev` profile | Everyday work |
| CI | GitHub-hosted runners | `.github/workflows/*.yml` | Testcontainers and throwaway compose stacks | Build, test, native checks |
| Preview | One VPS running Docker Compose (project `matbakhy-preview`) | `deploy/web-preview/` | Synthetic only, simulated deposits, fake notifications, visible demo notice | Early access-restricted web preview for owner and invited testers |
| Staging, Production | None | Names exist in code only | n/a | PLANNED; the application refuses to start there without real providers |

Do not describe the preview as production: it runs `APP_ENV=PREVIEW` with fake providers, development-profile fixtures, no real money, and a single host without redundancy.

## Local stack (`infra/compose.yaml`)

`scripts/dev.sh up` (see its header for `doctor`, `up`, `smoke`, `down`, `reset`, `verify`, `e2e`) generates random local secrets into the git-ignored file infra/.env, renders the realm into a generated directory, builds the API jar and starts these services, all bound to `127.0.0.1`:

| Service | Image | Port (default) |
|---|---|---|
| `postgres` | `postgres:16.15-alpine` (digest pinned) | 5432 |
| `identity` | Keycloak 26.8.0, `start-dev --import-realm` | 8180 |
| `object-storage` | Adobe s3mock 5.2.3 with buckets `matbakhy-private`, `matbakhy-public` | 9090 |
| `mail` | Mailpit | 8025 UI, 1025 SMTP |
| `api` | built from `backend/Dockerfile`, `APP_ENV=LOCAL`, profile `dev` | 8080 |

The web apps run outside Docker via Vite (`pnpm --filter ... dev`, ports 5173 and 5174). Details: [local environment](../05-developer-workflow/local-environment.md). The mail sink exists for later email work; today's email adapter only logs (see [integration and events](integration-and-events.md)).

## Preview stack (`deploy/web-preview`)

`compose.yaml` defines (all images pinned by digest unless built by CI):

| Service | Role | Notes |
|---|---|---|
| `proxy` | Caddy 2.10 inside the "front" image: serves both SPA builds, reverse proxy, TLS (automatic ACME), allow-lists, security headers | Only service publishing ports (80, 443); memory 256 MB |
| `api` | Spring Boot image `ghcr.io/<owner>/matbakhy-api@sha256:...` | `APP_ENV=PREVIEW`, profile `dev`, ECS JSON logs, management port 8081, database user `matbakhy_app`, Flyway user `matbakhy`; ceiling 2 GB |
| `postgres` | PostgreSQL 16 with two databases (`matbakhy`, `keycloak`) and init scripts `postgres/10-keycloak.sh`, `postgres/20-runtime-role.sh` | Backups mounted at `/backups` |
| `identity` | Keycloak `start` with a Postgres database; hostnames `id.<domain>` and `kc-admin.<domain>` | Realm imported from the rendered preview realm |
| `storage` | SeaweedFS S3 (:8333) with generated `s3.json`; buckets created idempotently by `deploy.sh up` | Public bucket readable anonymously |
| `loki`, `alloy`, `prometheus`, `alertmanager`, `alert-sink`, `node-exporter` | Logging, metrics and alerts | Config in `deploy/web-preview/observability`; retention from `.env` (logs 30 days, debug 7 days, metrics 15 days; proposed staging defaults, not production policy) |
| `grafana` | Dashboards on `127.0.0.1:3000` only (reach by SSH tunnel) | Profile `tools` in the 4 GB override |

Networks: `edge` (proxy), `internal` (declared `internal: true`: no internet route for database, identity, storage, API), `ops` (Grafana loopback only). `compose.4gb.yaml` lowers memory ceilings for a 2 vCPU / 4 GB host (about 3.3 GiB total, not measured use). Both files are used together via `COMPOSE_OVERRIDE` in the server's `.env`.

### Hostnames and routing (`Caddyfile`)

| Host | Serves | Access class |
|---|---|---|
| `app.<domain>` | customer web (static, SPA fallback) | customer |
| `ops.<domain>` | operations web | staff |
| `api.<domain>` | `/api/*`, `/oauth2/*`, `/login/oauth2/*`, `/logout` to `api:8080`; `/api/v1/ops/**` and `/api/v1/cook/**` additionally staff-only | customer + staff for privileged paths |
| `id.<domain>` | Keycloak realm `matbakhy` (admin and master realm blocked) | customer |
| `media.<domain>` | SeaweedFS S3 (public media, presigned URLs) | customer |
| `kc-admin.<domain>` | Keycloak admin console, only while the flag file exists | not IP-restricted; protected by OTP |

Access classes, headers, CSP and the admin console boundary are explained in [identity and security](identity-and-security.md#browser-and-proxy-hardening). `scripts/test_proxy_access.py` runs the Caddyfile against these rules.

### Secrets and configuration on the server

`deploy.sh init-secrets` creates the git-ignored file deploy/web-preview/.env from `deploy/web-preview/.env.example` with generated values (mode 600, never committed; the example holds only `REPLACE_ME` placeholders). `deploy.sh render` writes the realm and storage configuration (which contain secrets) into `GENERATED_DIR` on the server. `compose.yaml` refuses to start without each required variable. `.env` keys that matter for behaviour: `PREVIEW_DOMAIN`, `ALLOWED_CLIENT_IPS`, `CUSTOMER_ALLOWED_IPS`, `STAFF_ALLOWED_IPS`, `PUSH_DELIVERY_ENABLED` (false), `COMPOSE_OVERRIDE`, retention values, `RELEASE_COMMIT`, `API_IMAGE`, `FRONT_IMAGE`.

## Delivery pipeline

### Continuous integration (`.github/workflows/ci.yml`, "Product CI")

Triggered on every push and pull request. Jobs: **workspace** (`scripts/check_workspace.sh`: clean clone, pinned Node/pnpm/JDK 21 check, `./mvnw verify`, `pnpm install --frozen-lockfile`, OpenAPI lint and client drift, typecheck, lint, unit tests, builds, Android and iOS Hermes bundles), **local-stack** (`scripts/dev.sh verify`-style bootstrap and smoke), **design** (tokens, specimens, journeys with the runner's Chrome) and **secrets** (Gitleaks over history and tree). Node is installed from a checksum-verified tarball. Other workflows: `blueprint-integrity.yml` (planning files), `native-build.yml` (Android/iOS native builds and emulator/simulator checks; see [mobile architecture](mobile-architecture.md#native-build-and-device-test-assets)), `owner-e2e-fixtures.yml` (validates owner acceptance fixtures), `preview-images.yml` (manual image build for a chosen domain, deploys nothing).

### Continuous deployment to the preview (`.github/workflows/deploy-preview.yml`)

Triggered by every push to `develop` (and manual dispatch). Serialised by concurrency group `matbakhy-preview-deploy` (no cancellation).

1. **build** job: waits (polling up to ~30 minutes) for a *successful Product CI push run at the exact commit SHA*; builds the API jar with Maven; builds two images (`matbakhy-api`, `matbakhy-preview-front`) tagged with the full SHA and pushes them to GHCR; outputs digests. The front image bakes the preview domain into the web bundles through `VITE_API_BASE_URL`, so one front image fits one domain.
2. **deploy** job (environment `preview`, only on `develop`): installs the SSH key and a pinned host key from repository secrets, checks the server's `.env` exists, copies `deploy/`, `infra/`, `scripts/` and the fixtures file by `tar` over SSH, then runs a remote script that rewrites `RELEASE_COMMIT`, `API_IMAGE`, `FRONT_IMAGE` in the server `.env`, logs in to GHCR with the job token, and runs `./deploy.sh up` and `./deploy.sh smoke`. If either fails the previous `.env` is restored; **there is no automatic database rollback**.
3. Finally `./deploy.sh admin-console enable` hardens and publishes the Keycloak admin console (preview only), verifying it live and closing it again on failure.

`./deploy.sh up` itself: checks pinned images and secrets, takes the shared change lock (`flock` on `.preview-mutation.lock` in the backup directory), renders configuration, takes a **pre-deploy database backup** when a database exists, pulls missing images, `compose up -d`, waits for health, creates buckets, and appends a line to the release log (`releases.log` in the backup directory) with commit, image digests, schema version before and after and the backup path. The API container applies Flyway migrations on start.

`owner-e2e-provision.yml` reacts to changes of `deploy/web-preview/owner-e2e-run.request` on `develop`: after the deployment of the same commit, it runs the owner E2E provisioning on the preview (plan = read-only; apply = verified backup, Keycloak partial import, SQL seeds, slot refresh, read-only reconciliation) under its own concurrency group; server-side `flock` serialises it with deployments and the admin console.

The workflow title says "Contabo" while operations documents describe a Hetzner server; see the gap below.

## Smoke checks and health

`deploy.sh smoke` runs `deploy/web-preview/smoke.sh <domain>`: read-only HTTPS checks of the customer web (page, headers, demo notice in the build), operations web, API, authentication and the boundary of privileged operations; it creates no data. The browser smoke with a simulated deposit order is `apps/operations-web/e2e-staging/smoke.staging.ts`. Health: container health checks (API: `GET :8081/actuator/health` on the management port; Keycloak: OIDC discovery document; Postgres: `pg_isready`); Compose waits for them (`wait_healthy`).

## Backup, restore and rollback

* **Backup** (`deploy.sh backup`, also before every deploy): custom-format `pg_dump` with SHA-256 checksum and a manifest, verified by restoring into a scratch database and running `infra/db/reconcile.sql`, plus an archive of image storage. Output goes to `BACKUP_DIR` (outside Git). The backup age is exported as a metric and alerted (`BackupStale`).
* **Restore** (`deploy.sh restore-db <dump>`): restores into `matbakhy_restore` and reconciles it; switching the application to it is a manual owner step. Data written after the backup is lost.
* **Rollback** (`deploy.sh rollback-plan [N]`, read-only): prints the three `.env` values of the release N entries back and a verdict. If the schema version has not moved, an image-only rollback is safe (edit `.env`, `./deploy.sh up`). If it has, image-only rollback is safe only when every migration in between is expand-only; `scripts/check_migration_compat.py` forces authors of migrations after V28 to annotate `-- rollback-compat: breaking|tightening|expand-only`, and CI enforces it. Otherwise restore the pre-deploy backup. Runbook: `docs/operations/11-staging-deployment-and-rollback.md`; rehearsal: `scripts/rehearse-preview.sh rollback`.
* **E2E reset** (`deploy.sh e2e-reset`): wipes the application database; refuses unless the host has an E2E marker file, customer hosts are not public, and the operator types the domain; takes a backup first.
* Tests: `backend/app/src/test/java/com/matbakhy/app/reliability/BackupRestoreIntegrationTest.java`, `scripts/test_preview_backup.py`, `scripts/test_preview_release.py`.

## Monitoring and alerts

API logs are ECS JSON with request IDs; Alloy reads container logs into Loki; Prometheus scrapes the API's management port, node-exporter, Loki, Alloy, Alertmanager; rules in `deploy/web-preview/observability/alerts.yml` (tests in `alerts.test.yml`) include `ApiDown`, `DatabaseUnavailable`, `LedgerUnbalanced`, `AuditWriteFailed`, `OutboxStale`, `OutboxFailed`, `NotificationsDeadLettered`, `AcceptanceExpiryLagging`, `OrderPlacementFailures`, `BackupStale`, `DiskPressure`, `LogPipelineDown`. Alertmanager routes to `alert-sink` (a small local Python receiver that stores alerts), so **no human is paged**; an external alert channel is not configured (NOT VERIFIED for the live host). Readiness criteria: `docs/operations/05-observability-readiness.md` and `docs/operations/07-audit-and-log-readiness.md`.

## Production-readiness boundaries (what does not exist)

* No production or staging environment, domain, TLS design, WAF, CDN or managed database; one host, no redundancy, no autoscaling.
* No real SMS, email or push providers, no malware scanner service, no object storage service chosen (owner decisions O07, O08).
* Live money is gated by compliance (owner decision D16); no payment integration exists.
* Customer access is restricted by IP allow-list until the owner explicitly opens `CUSTOMER_ALLOWED_IPS`; CSP is Report-Only; the admin console host is public by design (preview only).
* Retention, privacy and backup-offsite policies are proposed defaults pending owner decision O06; backups stay on the same host (an off-host copy was not found in the scripts: NOT VERIFIED).
* Mobile: only development identifiers; no store submission; physical-device verification not done.

## Known limitations and documentation gaps

* **Provider naming conflict.** `.github/workflows/deploy-preview.yml` and `.github/workflows/owner-e2e-provision.yml` say "Contabo", `docs/operations/09-staging-hetzner-preparation.md`, `12-owner-server-setup.md` and `deploy/web-preview/README.md` say "Hetzner". The workflow is what actually deploys (host from the `STAGING_SSH_HOST` secret, domain `167-86-90-174.sslip.io` baked in the front image build); the provider is NOT VERIFIED from the repository. The server path used by CD is `/srv/matbakhy-staging`, while `.env.example` says `/srv/matbakhy-preview`: another leftover of the "staging" naming.
* `deploy/web-preview/README.md` still says "Nothing here has been deployed", which is out of date now that CD deploys on every `develop` push.
* The preview runs the `dev` Spring profile (allowed because `PREVIEW` counts as a synthetic environment), so development fixture migrations are active there.
