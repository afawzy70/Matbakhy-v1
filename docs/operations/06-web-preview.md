# Web preview on one VPS (prepared, not deployed)

**Owner direction (2026-10-12):** try the web application first. Apple Developer, Google Play and TestFlight setup are deferred.

This is an **early web preview** for the owner's own test journeys. It is not the controlled staging environment of MB-040, and it is not a release. Nothing has been purchased, deployed, published or put into DNS.

## What it is

| Service | Image | Exposure |
|---|---|---|
| Proxy and web apps | Our `matbakhy-preview-front` image: Caddy plus the customer web and operations web builds | Ports 80/443. Hosts `app.`, `ops.`, `api.`, `id.` and `media.<domain>`, each restricted to `ALLOWED_CLIENT_IPS` |
| API | Our `matbakhy-api` image, `APP_ENV=PREVIEW` | Internal; only `/api/*` and the OIDC login paths go through the proxy, actuator does not |
| Identity | Keycloak 26.8.0 in production mode (`start`) on PostgreSQL, preview realm imported | Internal; the proxy blocks `/admin` |
| Database | PostgreSQL 16.15 (two databases: `matbakhy`, `keycloak`) | Internal only, persistent volume |
| Image storage | SeaweedFS (S3-compatible) | Internal; `media.<domain>` for signed uploads and public meal photos |

- **Images.** Images are immutable: ours are built in CI at one commit (`.github/workflows/preview-images.yml`, manual) and deployed by digest. Third-party images are pinned by digest. Nothing is built on the server.
- **What `APP_ENV=PREVIEW` means** (added in this change, with tests):
  - It keeps the synthetic fixtures, the synthetic commerce policy and providers that never send anything: no SMS, no e-mail, no push, and a fake malware scanner.
  - It requires everything a deployed environment requires: a real phone pepper, handover and push-token keys, real storage credentials with a public base URL, a confidential web client secret and secure cookies.
  - STAGING and PRODUCTION still refuse every fake.

**Why not STAGING.** `APP_ENV=STAGING` cannot start today:
- there is no SMS, e-mail, push or malware-scanning provider (O07);
- there is no approved commerce policy (O04).

This is intended, and it is unchanged.

## Files

`deploy/web-preview/`:

| File | Purpose |
|---|---|
| `compose.yaml` | Services, internal network, health checks, resource limits, bounded logs |
| `Caddyfile` | HTTPS, IP allow-list, security headers |
| `front.Dockerfile` | Proxy plus both web builds |
| `.env.example` | Placeholders only |
| `deploy.sh` | Commands: `init-secrets`, `render`, `up`, `seed`, `smoke`, `backup`, `restore-db` |
| `identity/matbakhy-realm.preview.json` | Realm template |
| `storage/s3.json.template` | Storage identities and access |
| `postgres/10-keycloak.sh` | Creates the Keycloak database on first start |
| `seed-preview.sql` | Synthetic catalogue and accounts |
| `smoke.sh` | Read-only HTTPS smoke test |

## Verified locally (self-review, 2026-10-12)

The rehearsal used the real `compose.yaml` and `deploy.sh render` with generated secrets. It ran without the proxy, because Docker Hub rate-limited the Caddy pull.

Results are in `verification/runs/20261012-STAGING-PREP/rehearsal.log`:
- Keycloak production mode on PostgreSQL started healthy with the preview realm; its issuer is `https://id.<domain>/realms/matbakhy`.
- The API in PREVIEW mode started healthy with real secrets and SeaweedFS.
- Both buckets were created; anonymous listing is refused for both.
- The seed loaded 2 kitchens, 3 published meals and 6 open slots. Discovery serves them, and API-001 returns the per-line limit (10).
- **Sign-in behind the proxy headers:**
  - redirects to `https://id.<domain>/…` with `redirect_uri=https://api.<domain>/login/oauth2/code/keycloak`;
  - the session and XSRF cookies are `Secure`, and the session cookie is `HttpOnly; SameSite=Lax`.
- Logs are ECS JSON.
- **Backup and restore:**
  - database backup: dump with checksum and manifest, restored into a new database, every reconciliation check OK;
  - image-storage archive: taken with storage briefly stopped.
- **Memory after start:**

  | Service | Memory |
  |---|---|
  | API | 277 MiB |
  | Keycloak | 557 MiB |
  | PostgreSQL | 84 MiB |
  | SeaweedFS | 69 MiB |
  | Total | about 1 GiB, idle |

- Both web apps build with the preview API address embedded.

**NOT_RUN** (needs the server or a registry):
- Caddy, TLS certificates and the IP allow-list;
- the image workflow, and therefore the base image tags `caddy:2.10.2-alpine` and `node:24.21.0-alpine`;
- an anonymous read of a public meal photo;
- a full browser sign-in and order through the proxy;
- the read-only `smoke.sh` against real HTTPS.

## Sizing

The provisional target is **4 vCPU, 8 GB RAM, at least 80 GB disk**. Idle use measured about 1 GiB, and the container limits add up to about 5.3 GiB. Builds and heavy tests stay in CI. 4/8 leaves room for Keycloak and JVM peaks and for backups; 2 vCPU / 4 GB would probably run, but has no headroom. Load is not measured (MB-038).

## Cost (UNVERIFIED)

The official pricing pages (hetzner.com, digitalocean.com) were not reachable from the implementation environment, so every figure below comes from third-party trackers and must be checked on the provider's page before buying. The figures are unverified, and the trackers disagree on some of them.

| | Recommended: Hetzner Cloud CX33 (EU, shared x86) | Fallback: DigitalOcean Basic Droplet |
|---|---|---|
| Size | 4 vCPU, 8 GB RAM, 80 GB disk, 20 TB traffic | 4 vCPU, 8 GB RAM, 160 GB disk, about 5 TB transfer |
| Price | About €8.99 per month after the mid-2026 increase (earlier €5.49); one tracker reports a "currently not available" label. If unavailable, the CPX32 (4 vCPU, 8 GB, 160 GB) is reported at about €35.49–35.99 per month after the increase. Prices exclude VAT. | About US$48 per month; one source lists this tier with 2 vCPU |
| Billing | Hourly up to a monthly cap; no prepaid commitment needed | Hourly up to a monthly cap |
| Public IPv4 | About €0.50 per month extra (excluding VAT) | Included |
| Provider backups | About +20 % of the server price | About +20 % weekly / +30 % daily |
| Domain | The owner's existing domain, or a new one at about US$10–15 per year (registrar-dependent) | Same |

Neither price is a prepaid promotional price. The script's own backups are free, but they stay on the same server until they are copied off it.

**Recommendation:** Hetzner CX33 if it is available in an EU location, otherwise the fallback. Both have EU regions close enough to Egypt for a preview. Expected total: about €10–12 per month with IPv4 and provider backups, or about US$58 per month on the fallback, plus the domain. Unverified.

## Owner inputs needed before anything is created

1. **Hosting and budget:**
   - approve the provider and plan above, or another;
   - create the account and payment method, and the server (Ubuntu 24.04 LTS);
   - give SSH access with a key.
2. **Domain:**
   - the domain or subdomain to use;
   - DNS `A` (and `AAAA`) records for `app.`, `ops.`, `api.`, `id.` and `media.` pointing at the server, created by the owner when ready.
3. **Access:** the testers' public IP addresses or ranges for `ALLOWED_CLIENT_IPS`.
4. **Certificates:** an e-mail address for the certificate authority (`ACME_EMAIL`).
5. **Images:** approval to run `preview-images.yml`, which pushes two images to GHCR under the repository owner. Package visibility must be checked: the images contain no secrets, but the front image contains the domain.
6. **Who may use it before the audit and log checks pass:** the owner alone, or nobody (see Readiness).
7. **Limitations to acknowledge:**
   - synthetic data only;
   - no real SMS, e-mail, push or malware scanning;
   - a synthetic commerce policy (O04 open);
   - staff actions that need MFA are not available, because no OTP is configured in the preview realm.

## Procedure (after the inputs exist)

1. **Server setup:** install Docker Engine and the Compose plugin. Firewall: allow 22 from the owner's address only, and 80/443.
2. **Copy the repository at the release commit:** `git archive <RELEASE_COMMIT> | tar -x -C /srv/matbakhy-preview/src`. The scripts under `scripts/` and `infra/db/reconcile.sql` are used from there.
3. **Secrets:** in `src/deploy/web-preview`, run `./deploy.sh init-secrets`. Then set `PREVIEW_DOMAIN`, `ACME_EMAIL` and `ALLOWED_CLIENT_IPS` in `.env`.
4. **Images:** run the image workflow for the same commit with the domain. Copy `RELEASE_COMMIT`, `API_IMAGE` and `FRONT_IMAGE` (digests) from the job summary into `.env`. `deploy.sh` refuses tags without a digest, placeholders, and short SHAs.
5. **Start and check:**
   1. `./deploy.sh up`: backs up first if a database exists, then pulls, starts, waits for health and creates the buckets.
   2. `./deploy.sh seed`: first deploy only.
   3. `./deploy.sh smoke`.
6. **Test accounts** (realm `matbakhy`, password `PREVIEW_USER_PASSWORD` from `.env`):
   - `preview-customer-1` and `preview-customer-2` (phones pre-verified);
   - `preview-cook-1` (owns "مطبخ المعاينة"; pickup, and delivery to Test zone A);
   - `preview-ops-1` (first staff administrator through `STAFF_BOOTSTRAP_SUBJECT`).
7. **Keycloak administration:** `docker compose exec identity /opt/keycloak/bin/kcadm.sh …`. The console is not published.

## Migrations, rollback and recovery

- **Migrations:** Flyway runs forward-only at API start. `up` always takes a database backup first.
- **Rollback without schema change:** put the previous `API_IMAGE`/`FRONT_IMAGE` digests back into `.env`, then run `./deploy.sh up`.
- **Rollback after a migration:** the schema cannot be downgraded. To roll back:
  1. stop the API;
  2. restore the pre-deploy dump with `./deploy.sh restore-db /backups/db-<stamp>/matbakhy-<stamp>.dump` (it restores into the new database `matbakhy_restore` and reconciles it);
  3. point `DATABASE_URL` at it, or rename it;
  4. start the previous images.

  Data written after that backup is lost.
- **Backups:**
  - `./deploy.sh backup` writes the database dump (checksum and manifest) and a storage archive to `BACKUP_DIR`.
  - Copy them off the server, owner's choice; until then they share the server's fate.
  - The retention period is O06 (open); keep 7 days as a proposal.
- **Logs:** each container keeps at most 3 × 10 MB. The API logs ECS JSON with request IDs and no personal data in messages (MB-039).

## Readiness

- **Audit and log readiness (owner direction 2026-10-12, [05-observability-readiness.md](05-observability-readiness.md)):**
  - AUD-01, LOG-01 and OPS-01..03 must be shown on the preview itself before external testers get access.
  - Today this package provides only part of them: ECS JSON logs, bounded container log files and health checks.
  - It does not yet provide searchable log collection, restricted metrics, exercised alerts, the audit coverage matrix or the troubleshooting runbook. Those are queued under MB-039 and verified under MB-040.
  - All five checks are **NOT_RUN**. An owner-only smoke deployment would still need the owner's explicit acceptance of this gap; external testers stay blocked until the checks pass.
- **Early web preview:** package prepared and rehearsed locally (above), not deployed. Ready to deploy once the owner inputs exist; the remaining checks are listed under NOT_RUN.
- **MB-040 controlled staging:** not started and blocked by:
  - MB-034/035/036/038;
  - O07 providers;
  - O04 approved commerce policy;
  - O05 visual approval.

  Deployment files do not close MB-040.
