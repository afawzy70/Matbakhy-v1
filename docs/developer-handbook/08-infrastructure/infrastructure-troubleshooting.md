# Infrastructure troubleshooting

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — 23 numbered failure scenarios for the Contabo Preview, built from repository code and the live observations of 2026-10-21; none of the corrective actions was rehearsed on the live host.
> **Language / اللغة:** English · [العربية](infrastructure-troubleshooting-ar.md)

This page is the infrastructure counterpart of the developer-side [troubleshooting page](../05-developer-workflow/troubleshooting.md), which covers toolchain, test and local-environment failures and is not repeated here. Order, audit, dead-letter and telemetry diagnosis is in the [operations troubleshooting runbook](../../operations/08-troubleshooting-runbook.md), and the original runbooks are in [operational runbooks](../../operations/02-runbooks.md). Related pages: [server inventory](contabo-server-inventory.md), [administrator read-only verification](admin-readonly-verification.md), [runtime architecture](runtime-architecture.md), [deployment pipeline](deployment-pipeline.md), [DNS and HTTPS](dns-https-sslip-io.md), [backup, restore and disaster recovery](backup-restore-disaster-recovery.md), [rebuild guide](server-rebuild-guide.md) and [live Preview access](../05-developer-workflow/live-preview-access.md).

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 by the unprivileged Linux user aiagent on the Contabo VPS itself (read-only, no sudo, no Docker socket). Only the recorded live facts count. |
| VERIFIED IN CODE | Read in this repository (file named next to the claim). |
| DOCUMENTED ONLY | A repository document or a vendor manual says it; not confirmed here or live. |
| OUTDATED / INCORRECT | Conflicts with newer evidence, for example Hetzner-era text. |
| NOT VERIFIED | Nobody has confirmed it. The text says how an authorized administrator can check it. |

## How to use this page

1. Start with the symptom list in the scenario index, open the scenario, and read its seven parts in order: symptoms, likely causes, safe diagnostic commands, expected diagnostic evidence, corrective action, risk level, recovery verification.
2. Run only the diagnostic commands (the D codes in the next section). They are read-only. The corrective codes (the F codes) change state and each carries a warning.
3. Commands that need the Docker socket or the deployment directory are run by an authorized administrator. In the Contabo inventory the Linux user devops is the only member of the docker group and owns the deployment directory (VERIFIED LIVE), but which account the CI pipeline connects as is stored in the repository secret STAGING_SSH_USER and is NOT VERIFIED. This page therefore says "authorized administrator" and never assigns a duty to an account name.
4. Agent and developer sessions (for example the aiagent user) must not use sudo, must not join the docker group, must not read the server .env file and must not run any F command. They may run the D commands that need only network access, and may read directory listings that their account can already see.
5. Do not run the corrective commands against the live Preview without the owner's authorization when the scenario says so. Rebuild and restore rehearsals belong on a new isolated server ([rebuild guide](server-rebuild-guide.md)).

Shorthand used below (all VERIFIED IN CODE in `deploy/web-preview/deploy.sh` and `deploy/web-preview/compose.yaml`):

| Shorthand | Meaning |
|---|---|
| `<domain>` | The Preview domain, documented as 167-86-90-174.sslip.io (public in the repository). |
| `<deploy-dir>` | The deployment directory on the server. The CI pipeline writes to /srv/matbakhy-staging/deploy/web-preview (VERIFIED IN CODE, `.github/workflows/deploy-preview.yml`; the directory exists and is owned by devops, VERIFIED LIVE). |
| `<compose>` | `docker compose --env-file .env -f compose.yaml`, run from `<deploy-dir>`. `deploy.sh` adds `-f <COMPOSE_OVERRIDE>` when the .env file sets it; whether the live .env sets it is NOT VERIFIED. `--env-file` supplies only variable interpolation for Compose; it does not set shell variables, so an interactive shell does not know BACKUP_DIR or the other .env values unless they are read as in D22 or sourced as `deploy.sh` does. |
| `<host>` | One of app, ops, api, id, kc-admin or media (VERIFIED IN CODE, `deploy/web-preview/Caddyfile`). |

## Scenario index

| No. | Scenario | No. | Scenario |
|---|---|---|---|
| [1](#1-website-unavailable) | Website unavailable | [13](#13-docker-image-pull-failure) | Docker image pull failure |
| [2](#2-dns-not-resolving) | DNS not resolving | [14](#14-github-actions-deployment-failure) | GitHub Actions deployment failure |
| [3](#3-https-certificate-errors) | HTTPS certificate errors | [15](#15-incorrect-image-version) | Incorrect image version |
| [4](#4-caddy-not-routing) | Caddy not routing | [16](#16-database-migration-failure) | Database migration failure |
| [5](#5-http-403) | HTTP 403 | [17](#17-persistent-volume-permission-errors) | Persistent-volume permission errors |
| [6](#6-http-404) | HTTP 404 | [18](#18-full-disk) | Full disk |
| [7](#7-http-502-and-503) | HTTP 502 and 503 | [19](#19-memory-pressure) | Memory pressure |
| [8](#8-backend-container-unhealthy) | Backend container unhealthy | [20](#20-certificate-renewal-failure) | Certificate renewal failure |
| [9](#9-postgresql-unavailable) | PostgreSQL unavailable | [21](#21-broken-media-urls) | Broken media URLs |
| [10](#10-keycloak-login-redirect-failure) | Keycloak login redirect failure | [22](#22-failed-smoke-tests) | Failed smoke tests |
| [11](#11-oidc-issuer-mismatch) | OIDC issuer mismatch | [23](#23-failed-deployment-requiring-rollback) | Failed deployment requiring rollback |
| [12](#12-keycloak-admin-mfa-problems) | Keycloak admin MFA problems | | |

The brief for this page names the set as 22 scenarios but lists 23 titles; all 23 are kept and numbered.

## Diagnostic command catalogue (read-only)

Every command here only reads. The "Linux user" column says who may run it; "authorized administrator" means a person the owner has authorized to use the Docker socket and the deployment directory on the VPS. Commands in the "authorized administrator" rows are NOT VERIFIED LIVE (nobody without that access has run them on this host); they are taken from repository scripts or are standard Docker and Linux commands.

| Code | Command | Machine | Linux user | Prerequisite | Expected result | Likely failure and action |
|---|---|---|---|---|---|---|
| D1 | `curl -sS -o /dev/null -w '%{http_code} %{content_type} %{redirect_url}\n' https://<host>.<domain>/<path>` | Your laptop or any machine with curl | Any | Network access; your public IP may matter (see scenario 5) | Status code, content type and redirect target for that path; see the table in [live Preview access](../05-developer-workflow/live-preview-access.md) | Exit code 6 means the name did not resolve (scenario 2); 7 means connection refused; 28 means timeout; 35 or 60 means a TLS failure (scenario 3) |
| D2 | `curl -sSI https://<host>.<domain>/<path>` | Your laptop | Any | As D1 | Response headers: Strict-Transport-Security, X-Content-Type-Options and X-Robots-Tag come from the Caddyfile headers block (VERIFIED IN CODE) | Missing headers are a symptom of configuration or routing, not proof that a different server answered: the Caddyfile defines headers inside individual host routes, so a host or path handled by another route can lack them. Corroborate with the certificate (D3 and D1), the destination address, the running image (D14) and the proxy logs (D9) |
| D3 | `getent hosts <host>.<domain>` | Your laptop | Any | A resolver | The line starts with 167.86.90.174 (VERIFIED LIVE for the Preview names) | No output means no answer for that name; see scenario 2 |
| D4 | Inspect: `openssl s_client -connect <host>.<domain>:443 -servername <host>.<domain> </dev/null 2>/dev/null \| openssl x509 -noout -issuer -subject -dates -ext subjectAltName`. Validate: `curl -sS -o /dev/null -w '%{http_code} verify=%{ssl_verify_result}\n' https://<host>.<domain>/` | Your laptop | Any | OpenSSL 1.1.1 or later and curl installed | The inspection command only prints the certificate that the server presents; it does not validate it. Read the subjectAltName line for the host name, the issuer and a not-after date in the future. The validation command keeps TLS verification on: it exits 0 and prints `verify=0` only when the chain and the host name validate (the HTTP code may legitimately be 403 from an access-policy answer); exit status 60 or a non-zero verify value is a certificate failure. Issuer Let's Encrypt with about 90 days of validity was observed only for the app host (VERIFIED LIVE: issuer CN YE1, 2026-10-18 to 2027-01-16); the other hosts' issuer and dates are NOT VERIFIED LIVE, and the DNS page documents a possible fallback certificate authority, so read the issuer instead of assuming it | An empty inspection result means the TLS handshake failed; the proxy may not hold a certificate for that name yet. Never replace the validation command with `curl -k` |
| D5 | `sh deploy/web-preview/smoke.sh <domain>` (add `SMOKE_OPS=skip` in front when running outside the staff class) | Your laptop or the server | Any (server: whoever may reach the public URLs) | A checkout of the repository; curl | Ends with `PASS: web preview smoke test for <domain>`; the script states that it creates no account, order or upload (VERIFIED IN CODE) | A line starting with `FAIL:` names the broken check; see scenario 22 |
| D6 | `ssh <admin-user>@<vps-address>` then `cd <deploy-dir>` | Your laptop to the VPS | Authorized administrator | An SSH key authorized for that account (the readable sshd directive PasswordAuthentication no is VERIFIED LIVE, but the effective sshd configuration is NOT VERIFIED LIVE because a drop-in file is unreadable; an administrator confirms key-only login with `sshd -T` as root) | A shell in the deployment directory | A permission or key error means you are not authorized; stop and ask the owner. Do not work around it |
| D7 | `./deploy.sh status` | The VPS, in `<deploy-dir>` | Authorized administrator (needs the Docker socket and the .env file) | The .env file exists (mode 600, VERIFIED LIVE; content not read) | Container table with status and image, schema version, the configured RELEASE_COMMIT, API_IMAGE and FRONT_IMAGE, and the last five release rows (VERIFIED IN CODE) | "`.env missing`" means you are in the wrong directory; an empty container table means the stack is not running |
| D8 | `<compose> ps` | The VPS, in `<deploy-dir>` | Authorized administrator | As D7 | Every service Up; api, postgres and identity show (healthy) | A service Restarting or Exited: read its log with D9 |
| D9 | `<compose> logs --tail 100 <service>` where service is one of proxy, postgres, identity, storage, api, loki, alloy, prometheus, alertmanager, alert-sink, node-exporter, grafana | The VPS, in `<deploy-dir>` | Authorized administrator | As D7 | Recent log lines. Docker keeps at most 3 files of 10 MB per container (VERIFIED IN CODE, `x-logging`), so old lines may be gone; Loki holds a longer history | Logs may carry request IDs, never copy them into a public place if they might contain personal data |
| D10 | `docker inspect -f '{{.State.Health.Status}}' "$(<compose> ps -q api)"` | The VPS, in `<deploy-dir>` | Authorized administrator | As D7 | The word healthy (this is the exact expression `deploy.sh` waits on, VERIFIED IN CODE) | starting means still within the health window; unhealthy: scenario 8 |
| D11 | `<compose> exec -T postgres pg_isready -U matbakhy -d matbakhy` | The VPS, in `<deploy-dir>` | Authorized administrator | The postgres container is running | "accepting connections" (the same probe as the container health check, VERIFIED IN CODE) | "no response" means PostgreSQL is not ready; scenario 9 |
| D12 | `<compose> exec -T postgres psql -U matbakhy -d matbakhy -At -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"` | The VPS, in `<deploy-dir>` | Authorized administrator | The database accepts connections | The highest version with success equal to t; this is a read of the same table `deploy.sh` queries for the schema version (VERIFIED IN CODE) | A row with success equal to f marks a failed migration; scenario 16 |
| D13 | `<compose> config --images` | The VPS, in `<deploy-dir>` | Authorized administrator | As D7 | The image references that the stack would run, taken from the .env file | An error "set X in .env" names a missing variable |
| D14 | `docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' <image reference>` | The VPS | Authorized administrator | The image is present locally | The full commit SHA the image was built from. The build sets this label on both of our images (VERIFIED IN CODE, `.github/workflows/deploy-preview.yml` and `.github/workflows/preview-images.yml`); the labels on the images of the running containers are NOT VERIFIED LIVE | An empty value means a third-party image or an image not built by this pipeline |
| D15 | `tail -n 5 <backup-dir>/releases.log` | The VPS | Authorized administrator (the backups directory is mode 750 devops, VERIFIED LIVE) | `<backup-dir>` is BACKUP_DIR from the .env file; the default is /srv/matbakhy-backups (VERIFIED IN CODE) | Tab-separated rows: time, commit, API image, front image, schema before, schema after, backup path | No file: no release was recorded there, or BACKUP_DIR points elsewhere (check the value, scenario 17) |
| D16 | `./deploy.sh rollback-plan [N]` | The VPS, in `<deploy-dir>` | Authorized administrator | At least N+1 rows in the release log. The numbering counts only recorded successful releases: N=1 is the row before the last one, and a `up` that failed before the stack became healthy is not a row (scenario 23) | Prints the target release, the three .env values and a verdict; "prints only; changes nothing" (VERIFIED IN CODE) | "no release log" or "only N release(s) recorded": there is nothing to roll back to by this route |
| D17 | `<compose> exec -T proxy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile` | The VPS, in `<deploy-dir>` | Authorized administrator | The proxy container is running; the Caddyfile is baked into the front image at /etc/caddy/Caddyfile (VERIFIED IN CODE, `deploy/web-preview/front.Dockerfile`) | "Valid configuration" | An error names the line; the fix is a code change and a new front image (F9) |
| D18 | `df -h` and `docker system df` | The VPS | `df`: any user; `docker system df`: authorized administrator | None | Disk /dev/sda1 290 GB with 18 GB used (6 percent) when observed on 2026-10-21 (VERIFIED LIVE; it will have changed) | Use above 85 percent: scenario 18 |
| D19 | `free -h` and `uptime` | The VPS | Any user | None | About 23 GiB total and about 3.4 GiB used when observed (VERIFIED LIVE) | Little available memory or a very high load average: scenario 19 |
| D20 | `docker stats --no-stream` and `docker inspect -f '{{.State.OOMKilled}} {{.RestartCount}}' <container>` | The VPS | Authorized administrator | The containers exist | Memory use below each service limit; OOMKilled false | OOMKilled true records only that a process in that container was killed for memory. It does not say whether the container's own limit or host-wide memory exhaustion caused it; correlate it with kernel or cgroup evidence before naming the cause (scenario 19) |
| D21 | `ls -ld <path>` and `stat -c '%U:%G %a %n' <path>` | The VPS | Any user for paths the account may traverse | The path exists | For example /srv/matbakhy-staging is devops:devops 755 (VERIFIED LIVE) | "Permission denied" on a listing is itself evidence; do not change it, escalate |
| D22 | `grep -E '^(PREVIEW_DOMAIN\|GENERATED_DIR\|BACKUP_DIR\|COMPOSE_OVERRIDE\|CUSTOMER_ALLOWED_IPS\|STAFF_ALLOWED_IPS\|RELEASE_COMMIT)=' .env` | The VPS, in `<deploy-dir>` | Authorized administrator | Mode 600 file; the pattern prints only these non-secret keys | The effective values of those seven keys. Never print the whole file | Printing the whole file would expose secrets; do not |
| D23 | `gh run list --workflow deploy-preview.yml --limit 5` then `gh run view <run id> --log-failed` | Your laptop | Any user with GitHub access to the repository | GitHub CLI authenticated | Run list. The default list does not show the commit; to see it use `gh run list --workflow deploy-preview.yml --branch develop --limit 5 --json headSha,status,conclusion,createdAt`. The failed step log | A step name tells you which stage failed (scenario 14) |
| D24 | `systemctl is-active docker` and `systemctl is-enabled docker` | The VPS | Any user | None | Both active and enabled (the docker service is active, VERIFIED LIVE) | inactive: the whole stack is down (scenario 1) |

## Corrective command catalogue (state-changing)

Every row changes state. Obtain the owner's authorization before running any of them on the live Preview unless the row says otherwise, record who ran it and why, and re-run the recovery verification of the scenario. Never run a destructive command (anything that deletes data or volumes) without a verified backup and explicit owner authorization.

| Code | Command | Machine and Linux user | Warning | Prerequisite | Expected result | Verification and likely failure |
|---|---|---|---|---|---|---|
| F1 | `./deploy.sh up` | The VPS, in `<deploy-dir>`, authorized administrator | It takes a verified pre-deploy database backup, renders the generated configuration, pulls missing images, starts the stack and may apply new database migrations. It refuses to run if another change holds the shared lock (`.preview-mutation.lock` in BACKUP_DIR). Do not run it to "see what happens" (VERIFIED IN CODE) | The .env file has no placeholders, image references pinned by digest and a full 40-character RELEASE_COMMIT | Ends with `up: <commit> (schema A -> B)` | D7, then `./deploy.sh smoke`. Failure messages: "`.env still has placeholders`", "must be pinned by digest", "another preview change holds" (wait or find the other operator), "API did not become healthy" (scenario 8) |
| F2 | `<compose> up -d --force-recreate <service>` (use this after any .env or compose.yaml change) or `<compose> restart <service>` (only when the configuration did not change) | The VPS, in `<deploy-dir>`, authorized administrator | Briefly interrupts that service. `restart` keeps the container's existing environment, so it does not apply a changed .env value or a changed compose file; only recreation does. Run it with the same `<compose>` as `deploy.sh` uses, including `-f <COMPOSE_OVERRIDE>` when the .env file sets one (check with D22), or the recreated container loses the override. Restarting or recreating postgres interrupts all API traffic; proxy interrupts all web traffic | The cause is understood and logged | The service returns to Up (healthy where it has a health check) | D8 and D5. If it flaps again, stop and use the scenario's other steps |
| F3 | Edit the three release values (RELEASE_COMMIT, API_IMAGE, FRONT_IMAGE) in the .env file to the values printed by D16, then F1 | The VPS, authorized administrator | An image-only rollback is safe only when the database schema has not moved or every later migration is expand-only (scenario 23). Owner authorization required | D16 output saved; verdict read | Previous images running on the current schema | D7 shows the target commit; D5 passes |
| F4 | `./deploy.sh backup` | The VPS, authorized administrator | Stops the storage service for about a minute while the object-storage archive (`storage-*.tgz`) is written, so uploads pause (VERIFIED IN CODE). It restores the dump into a scratch database to prove it | Free disk space (scenario 18) | `backup verified` and `backups in <backup-dir>` | A newer file in the backup directory; the BackupStale alert clears |
| F5 | `./deploy.sh admin-console <status\|enable\|disable\|verify\|create-admin NAME\|rollback>` | The VPS, authorized administrator | enable and create-admin write secret material into a private file under the generated directory; never paste it anywhere. enable also first writes a Keycloak database dump and a state file through an internal function of `admin-console.sh`; the `admin-console` dispatcher has no `backup` subcommand of its own (VERIFIED IN CODE). disable closes the console host immediately | The identity container is running | See [Keycloak admin console](../../operations/14-keycloak-admin-console.md) | `status` lists each master account as otp or no-otp |
| F6 | `docker image prune` (dangling images only) | The VPS, authorized administrator | Never add the flags that also remove volumes. The commands `docker volume prune` and `docker system prune --volumes` can destroy the PostgreSQL, SeaweedFS, Caddy and monitoring data and are forbidden here | `docker system df` read first | Reclaimed space reported | D18 shows more free space; D8 unchanged |
| F7 | `./deploy.sh prune-qa` (list only) and `./deploy.sh prune-qa --apply` (deletes) | The VPS, authorized administrator | The `--apply` form deletes QA artifacts older than QA_ARTIFACT_RETENTION_DAYS. Always run the list form first (VERIFIED IN CODE, usage text in `deploy/web-preview/deploy.sh`) | A reviewed list | The deleted files are the listed ones | D18 |
| F8 | `gh run rerun <run id> --failed` or `gh workflow run deploy-preview.yml --ref develop` | Your laptop, a user with write access to the repository | A rerun deploys the commit of that run; a manual run deploys the head of develop. The workflow runs one deployment at a time (concurrency group, VERIFIED IN CODE). Never rerun a deployment because it is slow; rerun only after a failure is understood and fixed | Fix merged or secret corrected | A new run reaches `DEPLOYED <sha>` | D23 and D5 |
| F9 | Open a pull request that changes the repository file (Caddyfile, compose.yaml, a script or a workflow), let CI pass, merge, and let the deploy workflow ship it | Your laptop | Never edit the files on the server by hand: the pipeline overwrites deploy, infra and scripts from Git on every deployment (VERIFIED IN CODE), so a manual edit is lost or causes drift | A reviewed change with the Documentation Impact declaration | The new image or file is deployed | D17 and D5 |
| F10 | `./deploy.sh render` | The VPS, in `<deploy-dir>`, authorized administrator | Rewrites the secret-bearing generated configuration (realm import and storage credentials file) and re-applies its modes. It does not restart services (VERIFIED IN CODE). Never print the generated files | The .env file is valid | Files under the generated directory with the modes in scenario 17 | D21 on those files; F2 (recreate form) for the services that read them |
| F11 | Restore from a backup (`./deploy.sh restore-db <dump>` and the steps in [staging deployment and rollback](../../operations/11-staging-deployment-and-rollback.md), section 4.3) | The VPS, authorized administrator | Data written after the backup is lost. Restoring over live data needs the owner's explicit authorization; rehearse on a new isolated server first ([backup, restore and disaster recovery](backup-restore-disaster-recovery.md)) | A verified, checksummed dump placed on the host under BACKUP_DIR (the postgres container sees that directory as /backups, so pass the /backups path, for example `/backups/db-<stamp>/matbakhy-<stamp>.dump`), with its `.dump.sha256` companion beside it and, for acceptance, the matching `.manifest`; the API stopped | The restored database reconciles (orders, allocations, ledger). `scripts/db-restore.sh` refuses a checksum mismatch, but without the matching manifest it prints a warning, skips the row-count comparison and can still print RECONCILED; accept a restore only with the manifest present and no MISMATCH line (VERIFIED IN CODE) | Reconciliation output; D5 |

## 1. Website unavailable

**Symptoms.** The customer web (app host) or the operations web (ops host) does not load: timeout, connection refused, browser error page, or a blank page.

**Likely causes.**
- The VPS is down, the network path is broken, or the provider has a problem (NOT VERIFIED; the provider status page is outside this repository).
- The Docker service is stopped, so no container runs (the docker service was active on 2026-10-21, VERIFIED LIVE).
- The proxy container is not running. The proxy starts only after the API reports healthy (`depends_on` with `service_healthy`, VERIFIED IN CODE), so an unhealthy API keeps the proxy down at start-up.
- Ports 80 and 443 are blocked. Both were listening on all interfaces when observed (VERIFIED LIVE); the firewall state is NOT VERIFIED LIVE.
- DNS or certificate trouble (scenarios 2 and 3).
- The caller is outside the allow-list and receives 403, which is not an outage (scenario 5).

**Safe diagnostic commands.** D1 for the app and ops hosts; D3; D4; then, by an authorized administrator, D24, D8 and D9 for the proxy and the API.

**Expected diagnostic evidence.** Healthy: app and ops return 200 from an allowed network (VERIFIED LIVE, from the server itself). If D1 returns exit code 7 or 28 while D3 resolves, the host or ports are the problem. If D8 shows the proxy missing and the API unhealthy, go to scenario 8. If the proxy is Up and D1 returns 403 with the text "Not available", go to scenario 5.

**Corrective action.** Fix the cause that D8 and D9 show, using the matching scenario. If the Docker service is stopped, an authorized administrator starts it with the system service manager; the compose services have `restart: unless-stopped` (VERIFIED IN CODE), so they should return after the daemon starts (not rehearsed). For a provider or network outage, wait and contact the provider through the owner. Do not recreate the stack from scratch for an outage.

**Risk level.** Medium. Restarting services interrupts the Preview but touches no data; recreating volumes would destroy data and must not be done.

**Recovery verification.** D5 passes, D1 returns 200 for app and ops, and `./deploy.sh status` shows every service Up.

## 2. DNS not resolving

**Symptoms.** Browsers report "server not found"; curl exits with 6; D3 prints nothing for one or more Preview names.

**Likely causes.**
- The caller's resolver cannot reach the sslip.io service or filters it. The names under 167-86-90-174.sslip.io resolve to 167.86.90.174 by design: sslip.io answers with the IP address written inside the name (DOCUMENTED ONLY, see [DNS and HTTPS](dns-https-sslip-io.md)). All six names resolved to that address when observed from the server (VERIFIED LIVE).
- A misspelled name, for example a wrong digit in the IP part. Whether other spellings of the embedded address, such as dots in place of dashes, resolve is NOT VERIFIED.
- The Preview domain was changed in the repository or the front image but the address behind it was not (domain replacement is described in [DNS and HTTPS](dns-https-sslip-io.md)).
- sslip.io itself is unavailable (outside the repository; NOT VERIFIED).

**Safe diagnostic commands.** D3 for each of app, ops, api, id, kc-admin and media; D3 against another resolver by using a different network or a mobile connection.

**Expected diagnostic evidence.** Every name answers 167.86.90.174. A failure only on your network points to your resolver; a failure everywhere points to the name or sslip.io.

**Corrective action.** Correct the name you typed. Change resolver or network. Nothing on the VPS needs to change for a DNS problem with sslip.io names, so do not restart containers. If the Preview moves to another address, follow the domain replacement runbook in [DNS and HTTPS](dns-https-sslip-io.md) through a pull request. That change reaches beyond the .env file: the Caddy site names, the front image (built with the domain) and the redirect and post-logout URIs of the application client in the persisted Keycloak realm all carry the domain. Restoring only the previous .env file and images does not reverse the realm client settings, so a rollback of a domain change needs an explicit decision by the owner and has not been rehearsed (NOT VERIFIED). Recreating the proxy with a new PREVIEW_DOMAIN also removes the old host names at once, so a cutover has downtime for them. The repository contains no dual-domain routing, and none has been designed or rehearsed (NOT VERIFIED); whether to build one is an owner decision.

**Risk level.** Low for diagnosis. A domain replacement is High because it invalidates certificates, the Keycloak realm hostnames and the front image build argument.

**Recovery verification.** D3 returns the expected address for all names, then D1 for each host returns the expected status codes in [live Preview access](../05-developer-workflow/live-preview-access.md).

## 3. HTTPS certificate errors

**Symptoms.** The browser warns about an invalid, expired or untrusted certificate; curl exits with 35 or 60; the smoke script fails on the first request.

**Likely causes.**
- Wrong host name in the URL, or an IP address used instead of the name (the certificate is for the sslip.io names).
- The certificate has expired because renewal failed (scenario 20). The app certificate was valid from 2026-10-18 to 2027-01-16 when observed (VERIFIED LIVE).
- A new host (for example kc-admin) has no certificate yet. The smoke script waits up to 90 seconds for a new host and only warns if it is still unreachable (VERIFIED IN CODE, `deploy/web-preview/smoke.sh`).
- The caller's clock is wrong.
- A different server answers the address.
- The ACME challenge cannot complete (ports, DNS or rate limits, scenario 20).

**Safe diagnostic commands.** D4 for the failing host; D3; the caller's own clock; D9 for the proxy (authorized administrator).

**Expected diagnostic evidence.** A subjectAltName that covers the host, a not-after date in the future, and a validation command (D4) that exits 0 with verify=0, which is what shows the issuer is trusted. Only the app certificate was observed (Let's Encrypt, VERIFIED LIVE); do not assume the same issuer or dates for the other hosts (NOT VERIFIED LIVE). A subject that does not name the host, or an empty D4 output, means the proxy has no certificate for it. Proxy log lines about obtaining a certificate show progress or the error text (DOCUMENTED ONLY: Caddy logs ACME errors to its standard error output, which Docker captures).

**Corrective action.** Use the correct host name. Fix the caller's clock. For missing or expired certificates follow scenario 20. Never disable certificate verification (curl -k) outside the local rehearsal that the smoke script documents; and never copy a private key out of the caddy-data volume.

**Risk level.** Medium; the Preview is unusable over HTTPS while it lasts, but nothing is lost.

**Recovery verification.** For every host, the D4 inspection shows a subjectAltName that covers the host and an unexpired date, the D4 validation exits 0 with verify=0, and D5 passes.

## 4. Caddy not routing

**Symptoms.** A host answers but with the wrong content: the API host returns 404 for a valid /api path, the id host returns the web app, a change to the Caddyfile does not take effect, or every host answers the same page.

**Likely causes.**
- The running front image does not contain the Caddyfile you expect. The Caddyfile is baked into the front image at build time, not mounted (VERIFIED IN CODE, `deploy/web-preview/front.Dockerfile`), so editing the file on the server changes nothing.
- The front image is from an older commit (scenario 15).
- A site block is wrong: the api host exposes only /api/*, /oauth2/*, /login/oauth2/* and /logout and answers 404 for everything else; the id host hides /admin and /realms/master; the kc-admin host is closed unless the flag file exists (VERIFIED IN CODE, `deploy/web-preview/Caddyfile`).
- The environment variables the Caddyfile reads (PREVIEW_DOMAIN, ACME_EMAIL, CUSTOMER_ALLOWED_IPS, STAFF_ALLOWED_IPS) have unexpected values in the .env file.
- The request reached a different host name than intended, for example a path under the wrong host.

**Safe diagnostic commands.** D1 with the path in question on each host; D2; D14 for the front image; D17; D22 (authorized administrator).

**Expected diagnostic evidence.** Compare the answer with the routing table in [runtime architecture](runtime-architecture.md) and the live behaviour in [live Preview access](../05-developer-workflow/live-preview-access.md). D17 prints "Valid configuration" for a healthy file. The label on the front image shows which commit built it.

**Corrective action.** A wrong rule is fixed in the repository Caddyfile through a pull request (F9). The repository test `scripts/test_proxy_access.py` runs the real Caddyfile in a throwaway container and is the place to add a regression test. A wrong environment value is corrected by the owner or an authorized administrator in the server .env file, followed by F2 in its recreate form for the proxy (a plain restart keeps the old environment). Do not edit the Caddyfile on the server.

**Risk level.** Medium; a bad routing change can expose a path that should be closed. Review every access-class change against the Caddyfile header comments.

**Recovery verification.** D1 on the affected paths gives the documented codes; D5 passes (it checks that actuator, the master realm and metrics are not published).

## 5. HTTP 403

**Symptoms.** A host answers 403.

**Likely causes.** There are three different 403 answers, and they do not mean the same thing.
- A Caddy access-policy answer: the body is the text "Not available". The Caddyfile returns it to a caller outside the customer class (app, api, id, media hosts) or the staff class (ops host, and the /api/v1/ops and /api/v1/cook paths). Both classes default to ALLOWED_CLIENT_IPS, and the customer class becomes public only when the owner sets CUSTOMER_ALLOWED_IPS (VERIFIED IN CODE, Caddyfile header comments). This is an access decision, not an outage. The result depends on the caller's public IP address, so a status seen from another network can differ (VERIFIED LIVE: requests were made from the server's own address). The kitchen and staff API paths (/api/v1/ops and /api/v1/cook) on the api host are checked against the staff list and then also against the customer list, so a staff tester must be in both lists; sign-in uses customer-class hosts too (VERIFIED IN CODE, Caddyfile api block). A staff-only address can load the operations web and still fail its API calls and sign-in.
- The media host root: https://media.<domain>/ returned 403 with content type application/xml and an x-amz-request-id header, which shows the answer comes from the SeaweedFS S3 gateway behind Caddy and not from Caddy's own text (VERIFIED LIVE). The reading is that the S3 gateway refuses an anonymous request for the bucket list at the root. The exact error body was not stored, so the specific S3 error code is NOT VERIFIED. Objects under /matbakhy-public/ are the public ones (VERIFIED IN CODE: the API's public base URL is https://media.<domain>/matbakhy-public, and the public bucket is readable anonymously by the generated S3 configuration). A 403 at the media root is therefore expected; it is not by itself a broken media service.
- An application-level 403 from the API, for example a missing XSRF token. The compose file notes that the cookie must be set for the parent domain or every browser mutation is refused with 403 (VERIFIED IN CODE, comment on WEB_COOKIE_DOMAIN in `deploy/web-preview/compose.yaml`).

**Safe diagnostic commands.** D1 with the content type in the output, and D2; compare the same URL from an allowed and a non-allowed network; D22 for CUSTOMER_ALLOWED_IPS and STAFF_ALLOWED_IPS (authorized administrator).

**Expected diagnostic evidence.** Caddy's own 403: short plain text "Not available" and no x-amz-request-id. S3's 403: XML content type and x-amz-request-id. API 403: a JSON error body with a requestId (DOCUMENTED ONLY: [operations troubleshooting runbook](../../operations/08-troubleshooting-runbook.md), section 1). The smoke script itself expects 403 from the ops host when run outside the staff class with SMOKE_OPS=skip (VERIFIED IN CODE).

**Corrective action.** Do not widen an access list to make a 403 disappear. If a legitimate tester is blocked, the owner decides whether to add the tester's public address to the allow-list; the change is made in the server .env file by an authorized administrator, then F2 in its recreate form for the proxy (the Caddyfile reads these values from the container environment). A tester who needs the staff paths must be added to both CUSTOMER_ALLOWED_IPS and STAFF_ALLOWED_IPS. For the media root, no action is needed. For an object that should be public, see scenario 21. For the XSRF case, check WEB_COOKIE_DOMAIN against the Preview domain.

**Risk level.** Medium for any access-list change: it changes who can reach the Preview. Making customer hosts public is explicitly the owner's step.

**Recovery verification.** The tester's address gets 200 on the app host; a non-listed address still gets 403 on ops and the staff API paths; D5 passes.

## 6. HTTP 404

**Symptoms.** A host or path answers 404.

**Likely causes.** Several 404 answers are designed behaviour (all VERIFIED IN CODE, Caddyfile and smoke script):
- api host: https://api.<domain>/actuator/health returns 404 because only /api/*, /oauth2/*, /login/oauth2/* and /logout are forwarded; actuator runs on the management port 8081 that the proxy never forwards (also VERIFIED LIVE for /actuator/health).
- id host: /admin, /realms/master and their sub-paths return 404 on the customer identity host by design. The smoke script requires this.
- kc-admin host: every path returns 404 with the text "Not available" unless the flag file admin-console-public exists in the directory the proxy mounts at /srv/flags. A 302 from the kc-admin root means the flag exists and the console is open (VERIFIED LIVE: it answered 302 to /admin/master/console/, so the flag was present). The flag directory is read from the generated directory in the .env file; its live location is NOT VERIFIED LIVE.
- The web apps answer unknown paths with the single-page index, not 404 (`try_files {path} /index.html`).

Real problems that also give 404: the front image is older than expected (scenario 15), or the path is mistyped.

**Safe diagnostic commands.** D1 and D2 for the path; D14 for the front image revision; for the kc-admin host, `./deploy.sh admin-console status` (F5, read-only status form, authorized administrator).

**Expected diagnostic evidence.** The documented codes in [live Preview access](../05-developer-workflow/live-preview-access.md). If an unexpected 404 appears on a documented route, compare D14 with the commit you expected.

**Corrective action.** Nothing for the designed 404 answers. To open or close the kc-admin console use F5 with the owner's authorization. A real routing defect is fixed through a pull request (F9).

**Risk level.** Low for diagnosis; opening the admin console is High (it publishes an administration surface; the console requires password and one-time password, DOCUMENTED ONLY in [Keycloak admin console](../../operations/14-keycloak-admin-console.md)).

**Recovery verification.** The path returns its documented code, and D5 passes.

## 7. HTTP 502 and 503

**Symptoms.** A host returns 502 (Bad Gateway) or 503. Or the API returns a JSON error with code DEPENDENCY_UNAVAILABLE.

**Likely causes.**
- Caddy cannot reach its upstream (the api, identity or storage container) because that container is down, restarting or still starting. Caddy answers 502 in that case (DOCUMENTED ONLY: Caddy reverse proxy behaviour; not reproduced here).
- The API returns a retryable 503 DEPENDENCY_UNAVAILABLE while PostgreSQL is unreachable, with no database detail; it recovers without a restart when the database returns (DOCUMENTED ONLY: [operational runbooks](../../operations/02-runbooks.md), "Database unavailable"; the same text cites DatabaseOutageRecoveryIntegrationTest as evidence).
- A container was killed for memory (scenario 19) or is unhealthy (scenario 8).
- A deployment is in progress.

**Safe diagnostic commands.** D1 on the failing host; then, by an authorized administrator, D8, D9 for proxy and the upstream service, D10, D11, D20.

**Expected diagnostic evidence.** D8 shows which upstream is not Up or not healthy. The proxy log names the upstream that refused the connection. A 503 JSON body from the API with the dependency code points to PostgreSQL (scenario 9).

**Corrective action.** Fix the failing upstream with its scenario. A short 502 during a deployment is normal; wait for the pipeline to finish before acting. Do not restart a healthy proxy to clear a 502 that is caused by a stopped upstream.

**Risk level.** Medium.

**Recovery verification.** D1 returns the documented code on the affected host, D8 shows all services healthy, and D5 passes.

## 8. Backend container unhealthy

**Symptoms.** The api service shows unhealthy or keeps restarting; `./deploy.sh up` ends with "API did not become healthy" and prints the last 80 log lines of the api and identity services; the proxy never starts.

**Likely causes.**
- The API starts only after postgres and identity are healthy (VERIFIED IN CODE). The identity health check passes only when the matbakhy realm answers, which can take minutes after a first import.
- The API cannot connect to the database as the runtime role matbakhy_app, or Flyway cannot migrate as the owner role matbakhy (scenarios 9 and 16).
- A required variable is missing; compose refuses to start with "set X in .env" for the mandatory ones (VERIFIED IN CODE).
- Out of memory (the api limit is 2 GiB, scenario 19).
- The management port check fails: the health check calls http://127.0.0.1:8081/actuator/health inside the container and expects status UP (VERIFIED IN CODE).
- The image is the wrong one or cannot start (scenarios 13 and 15).

**Safe diagnostic commands.** D8, D10, D9 for api, identity and postgres, D11, D12, D13, D20 (authorized administrator).

**Expected diagnostic evidence.** Log lines are structured JSON (ECS) with service.version equal to the release commit (VERIFIED IN CODE, `LOGGING_STRUCTURED_FORMAT_CONSOLE`). Look for the first error after "Started", a Flyway failure, a connection failure to postgres or to the identity issuer, or OOMKilled true in D20. The API health window is 60 retries of 5 seconds (5 minutes) and `deploy.sh` waits up to 90 checks of 5 seconds (VERIFIED IN CODE).

**Corrective action.** Apply the matching scenario to the first error found. If the cause is a transient start-up order problem, F2 on the api service only after postgres and identity are healthy. Do not delete the container to clear a state you have not read, and do not delete volumes.

**Risk level.** Medium; the whole Preview is down while the API is unhealthy, but data is not at risk unless a volume is removed.

**Recovery verification.** D10 prints healthy, the proxy is Up, `./deploy.sh smoke` passes.

## 9. PostgreSQL unavailable

**Symptoms.** API calls return 503 with DEPENDENCY_UNAVAILABLE; the api health check fails; `pg_isready` does not answer; Prometheus alert DatabaseUnavailable (DOCUMENTED ONLY, [operations troubleshooting runbook](../../operations/08-troubleshooting-runbook.md), section 4).

**Likely causes.**
- The postgres container is stopped, restarting or out of memory (its limit is 1536 MiB, VERIFIED IN CODE).
- The data volume is damaged, full or unreadable (scenarios 17 and 18).
- The database is still starting. Its health check retries 30 times at 5-second intervals (VERIFIED IN CODE).
- A first start with changed init scripts: the scripts in /docker-entrypoint-initdb.d run only when the data directory is empty (DOCUMENTED ONLY: PostgreSQL image behaviour), so changing them later has no effect.
- A wrong password after the .env file changed: the database keeps the passwords it was initialised with, so changing the .env values alone does not change the stored passwords (DOCUMENTED ONLY).

**Safe diagnostic commands.** D8, D9 for postgres, D11, D20, D18 (authorized administrator).

**Expected diagnostic evidence.** D11 prints "accepting connections" when healthy. Crash loops show an error in the postgres log (permission, disk, memory or corruption). The API recovers by itself when the database returns, so a persistent API failure after D11 is healthy points elsewhere.

**Corrective action.** Start the container if it is stopped (F2 for postgres only, owner aware because all traffic pauses). Fix disk or permissions first when those are the cause. If the data directory is damaged, restore is a last resort: stop the API, restore the latest verified backup (F11), accept the data loss since that backup, and get the owner's explicit authorization. Never remove the postgres-data volume to "start fresh".

**Risk level.** High; it carries all application data. Restore actions are High and need the owner.

**Recovery verification.** D11 accepting connections, D12 shows the expected schema version, the api is healthy, and `./deploy.sh smoke` passes. After a restore, the reconciliation query of `./deploy.sh restore-db` reports every check OK; a restore without the matching manifest is not accepted, even if RECONCILED is printed, because the row counts were not compared.

## 10. Keycloak login redirect failure

**Symptoms.** Pressing sign-in leads to an error page, a redirect loop, a "Invalid redirect URI" message from Keycloak, or a redirect to a wrong host; the smoke check "unexpected sign-in redirect" fails.

**Likely causes.**
- The sign-in starts at https://api.<domain>/oauth2/authorization/keycloak and must redirect to https://id.<domain>/realms/matbakhy/protocol/openid-connect/auth with redirect_uri https://api.<domain>/login/oauth2/code/keycloak (VERIFIED IN CODE, smoke script). The API builds those URLs from X-Forwarded headers (SERVER_FORWARD_HEADERS_STRATEGY native); if the proxy headers are missing, the wrong scheme or host appears.
- The imported realm contains redirect addresses for another domain. The realm is rendered from the template with the Preview domain when `deploy.sh render` runs and imported on start with --import-realm; an existing realm is not rewritten by a re-import (DOCUMENTED ONLY: Keycloak import behaviour; see the domain replacement runbook in [DNS and HTTPS](dns-https-sslip-io.md)).
- The caller is outside the customer class, so the id host returns 403 (scenario 5).
- The identity container is unhealthy (scenario 8).
- A browser blocks the cookie: the XSRF cookie domain WEB_COOKIE_DOMAIN must be the parent domain (VERIFIED IN CODE).
- Seeing a 302 at the id root is not a failure: https://id.<domain>/ answers 302 to https://kc-admin.<domain>/admin/ because KC_HOSTNAME_ADMIN is set to the kc-admin host in compose.yaml (VERIFIED LIVE and VERIFIED IN CODE). This is expected Keycloak behaviour, not an error. Customers never use the root of the id host.

**Safe diagnostic commands.** `curl -s -o /dev/null -D - 'https://api.<domain>/oauth2/authorization/keycloak?returnTo=https://app.<domain>/'` (the Location header; the same request the smoke script makes), D1 on the id host paths, D5, and D9 for api and identity (authorized administrator).

**Expected diagnostic evidence.** A Location header beginning `https://id.<domain>/realms/matbakhy/protocol/openid-connect/auth?` containing the api redirect_uri. If the host or scheme in the header is wrong, forwarded headers are the problem. If Keycloak shows an error page, the Keycloak log names the rejected redirect.

**Corrective action.** Correct the realm configuration through the documented realm template and the domain replacement procedure; do not hand-edit clients in production-like data without the owner. A redirect URI mismatch after a domain change requires an updated realm and a decision about reimport, which the owner must approve because it affects accounts.

**Risk level.** High if a realm change is needed (accounts and sessions); otherwise Low.

**Recovery verification.** The redirect check above returns the expected Location, D5 passes, and a synthetic customer account can sign in on a test device (account names only, for example customer-a; passwords are obtained from the authorized administrator as described in [live Preview access](../05-developer-workflow/live-preview-access.md)).

## 11. OIDC issuer mismatch

**Symptoms.** The API logs a token or issuer validation failure; every authenticated call returns 401; the smoke check "identity issuer is ..." fails.

**Likely causes.**
- The issuer string the API expects, OIDC_ISSUER, is https://id.<domain>/realms/matbakhy (VERIFIED IN CODE, compose.yaml). Keycloak puts its configured public hostname into the issuer: KC_HOSTNAME is https://id.<domain>. If PREVIEW_DOMAIN in the .env file differs from the domain Keycloak was started with, or the proxy does not pass X-Forwarded headers, the two differ.
- The API fetches keys over the internal address OIDC_BACKCHANNEL_ISSUER (http://identity:8080/realms/matbakhy); KC_HOSTNAME_BACKCHANNEL_DYNAMIC is true so back-channel calls use the internal name. If the identity container is not reachable by that name, validation fails.
- The domain was changed in one place only (the domain appears in several files, see [DNS and HTTPS](dns-https-sslip-io.md)).
- The caller used the master realm token against the matbakhy API; they are separate issuers.

**Safe diagnostic commands.** `curl -s https://id.<domain>/realms/matbakhy/.well-known/openid-configuration` and read the issuer value (the smoke script does exactly this); D22 for PREVIEW_DOMAIN (authorized administrator); D9 for api.

**Expected diagnostic evidence.** The issuer equals https://id.<domain>/realms/matbakhy character for character (the smoke check compares with `[ "$issuer" = "https://id.$d/realms/matbakhy" ]`, VERIFIED IN CODE). Any difference of host, scheme or trailing path is the mismatch.

**Corrective action.** Make PREVIEW_DOMAIN, KC_HOSTNAME and OIDC_ISSUER agree by correcting the .env value (owner or authorized administrator), then F10 and F2 in its recreate form for identity and api (a restart would keep the old issuer values). If a domain replacement was intended, follow the full runbook in [DNS and HTTPS](dns-https-sslip-io.md) instead of patching single values.

**Risk level.** High; wrong issuer settings lock every user out, and changes to the realm affect accounts.

**Recovery verification.** The discovery document issuer matches, the api is healthy, D5 passes, and a test sign-in works.

## 12. Keycloak admin MFA problems

**Symptoms.** An administrator cannot sign in to https://kc-admin.<domain>/admin/master/console/: the one-time password is rejected, the account has no one-time password, a lost phone, or the account is locked after failed attempts.

**Likely causes (all DOCUMENTED ONLY in [Keycloak admin console](../../operations/14-keycloak-admin-console.md); the live realm was not inspected).**
- Master-realm sign-in requires the password and a one-time password; there is no public enrolment. An account without a one-time password cannot sign in and is offered no enrolment. The bootstrap account has none, so after hardening it cannot sign in to the console by design.
- The authenticator app has the wrong key or the phone clock is wrong (codes are 6 digits with a 30-second step).
- Brute-force protection throttled the account after failed attempts.
- The host answers 404 because the switch file is absent (the console is closed).
- A synthetic verification administrator was left behind by an interrupted run.

**Safe diagnostic commands.** D1 on the kc-admin root and on /admin/realms (the Admin REST path without a token should answer 401 when the console is open; VERIFIED IN CODE, smoke script). `./deploy.sh admin-console status` (F5, read-only form) lists every master account as otp or no-otp(cannot-sign-in), authorized administrator.

**Expected diagnostic evidence.** Open console: root 302 to /admin/master/console/, and the Admin REST path answers 401 without a token. Closed console: 404. An account listed as no-otp(cannot-sign-in) is the expected explanation for "I cannot sign in".

**Corrective action.** Only an authorized administrator performs these, with the owner's approval. Create or replace an administrator with F5 `create-admin NAME` (replacing a lost phone uses the documented REPLACE=1 form). The command writes a private mode-600 file containing the password and the one-time-password key under the generated directory; the person reads it on the server, adds the key to an authenticator app, signs in, and deletes the file. Never paste or log its contents, never send it by chat or ticket. Do not weaken the policy (do not re-enable the password grant, do not disable the one-time password) to get someone in; use rollback only on the owner's decision.

**Risk level.** High; it controls the identity provider for the whole Preview. Secret material is involved.

**Recovery verification.** `./deploy.sh admin-console status` (read-only) shows the account as otp; optionally `./deploy.sh admin-console verify`, which is not read-only because it creates and removes a synthetic read-only administrator for the run, with the owner's approval. After either, the administrator signs in with password and code; the private file is deleted.

## 13. Docker image pull failure

**Symptoms.** `./deploy.sh up` or the deployment job stops during the pull with an error such as "manifest unknown", "unauthorized", "denied", "no space left on device", "TLS handshake timeout" or "toomanyrequests".

**Likely causes.**
- Our two images are pulled from GitHub Container Registry (ghcr.io) by digest. The deployment logs in to ghcr.io on the server using the workflow token for the duration of the run, in a temporary Docker configuration directory that is deleted afterwards (VERIFIED IN CODE). If the token lacks permission, the pull is denied.
- The digest does not exist (the build did not push, or the image was deleted).
- Third-party images are pinned by digest and pulled from their own registries; a registry rate limit or outage can block a pull (DOCUMENTED ONLY: registry rate limits; the live situation is NOT VERIFIED).
- The server cannot reach the registry (network, DNS or firewall; outbound rules NOT VERIFIED LIVE).
- Disk full (scenario 18).
- The .env file holds an image reference that is not pinned by digest; `deploy.sh up` refuses to start in that case (VERIFIED IN CODE).

**Safe diagnostic commands.** D13 (authorized administrator) for the references; D18; D23 for the build job; `getent hosts ghcr.io` from the VPS.

**Expected diagnostic evidence.** The error text names the image reference and the reason. `compose pull --policy missing` pulls only images not already present, so an image that is present locally is not the cause (VERIFIED IN CODE).

**Corrective action.** Correct the reference or rerun the build if the digest is missing (F8). If the token is the issue, the repository owner fixes the package permission or the workflow permissions; do not paste a personal token onto the server. For a registry outage, wait. For a disk problem, scenario 18. Do not switch to unpinned `latest` tags; that breaks the immutable-release rule.

**Risk level.** Medium; a failed pull leaves the previous containers running, because `up` pulls before starting (VERIFIED IN CODE).

**Recovery verification.** The pull completes, `./deploy.sh status` lists the expected images, the services are healthy and D5 passes.

## 14. GitHub Actions deployment failure

**Symptoms.** The Deploy Matbakhy preview to Contabo workflow ends red.

**Likely causes.** The workflow has two jobs, build and deploy (VERIFIED IN CODE, `.github/workflows/deploy-preview.yml`). Identify the failing step:

| Failing step | What it means |
|---|---|
| Wait for passing Product CI at this exact commit | Product CI for that commit failed, was cancelled, or did not finish within 30 minutes (60 checks of 30 seconds). Fix CI first |
| Build API jar, or Build and publish immutable images | A compile or Docker build error, a registry login failure, or a push failure |
| Configure verified SSH host | A required secret is empty: the step names the missing variable among HOST, USER, SSH_KEY, HOST_PUBKEY, API_IMAGE, FRONT_IMAGE |
| Transfer versioned deployment files | The remote check `test -f .env && test -w /srv/matbakhy-staging` failed (no .env file, or the connecting account cannot write the directory), or SSH host key verification failed (the workflow uses StrictHostKeyChecking=yes with the stored host public key) |
| Deploy digest-pinned images, wait for health, HTTPS smoke | `./deploy.sh up` or `./deploy.sh smoke` failed on the server (scenarios 8, 13, 16, 22) |
| Publish and verify the Keycloak admin console | `./deploy.sh admin-console enable` failed and closed the console again (fail closed, DOCUMENTED ONLY in [Keycloak admin console](../../operations/14-keycloak-admin-console.md)) |

The workflow deploys only on pushes to develop and on manual dispatch, one run at a time (concurrency group matbakhy-preview-deploy, cancel-in-progress false), so a queued run waits for the previous one. The values of the secrets (STAGING_SSH_HOST, STAGING_SSH_USER, STAGING_SSH_KEY, STAGING_SSH_HOST_PUBKEY) are not in the repository and the connecting account is NOT VERIFIED.

**Safe diagnostic commands.** D23; the Actions run page; D7 and D15 on the server for what was actually deployed (authorized administrator).

**Expected diagnostic evidence.** The failing step name from D23, and its error line. A successful run ends with `DEPLOYED <sha>` in the deploy step. The latest successful run is not proof of what runs now: a newer run may have deployed new images and then failed at the admin-console step, which leaves those images running. Compare D7 with the run list (D23) before naming the current release.

**Corrective action.** Fix the specific cause (CI, build, secret, host key, server state). Rerun only after the cause is fixed (F8). Do not rerun a healthy running job because it is slow. Never print secrets in a log while debugging. The restore of the previous .env file happens in one place only: inside the remote deploy script, when `./deploy.sh up` or `./deploy.sh smoke` fails (VERIFIED IN CODE). A failure before that point (missing .env, registry login, the .env update itself) exits without any restore, and a failure of the later "Publish and verify the Keycloak admin console" step restores nothing: the new images and the new .env stay in place and only the console is closed again. The restore covers the .env file only; the containers are not recreated and the database is not rolled back (scenarios 15 and 23).

**Risk level.** Medium; a half-finished deployment can leave the server on mixed configuration: after a failed `up` or `smoke` the .env file is the old one while some containers may already run the new images.

**Recovery verification.** A run reaches `DEPLOYED <sha>` and finishes green, and D7 shows that sha as RELEASE_COMMIT.

## 15. Incorrect image version

**Symptoms.** The Preview shows older behaviour than the latest merge; the frontend and API disagree; the version in logs is not the expected commit; an old Caddy rule is still active.

**Likely causes.**
- The deployment did not finish or was queued behind another run.
- A failed deployment restored the old .env file but the containers were already recreated with the new images, or the other way around. The remote script restores the file only; it does not run compose again (VERIFIED IN CODE), so the .env file and the running containers can disagree after a failure.
- RELEASE_COMMIT, API_IMAGE and FRONT_IMAGE were edited by hand and are inconsistent with each other.
- The front image is built for one Preview domain through a build argument (VERIFIED IN CODE), so an image built for another domain will call the wrong API address.

**Safe diagnostic commands.** D7 (configured values, running images and last releases), D13, D14 for both images, D15, and the API log field service.version (D9).

**Expected diagnostic evidence.** RELEASE_COMMIT in the .env file, the revision label of both images and the service.version in the API logs should be the same 40-character commit. A shorter commit shown by status is only a truncated display (`substr` to 12 characters, VERIFIED IN CODE).

**Corrective action.** To realign, run the pipeline for the intended commit (F8) or, by an authorized administrator, set all three values from a recorded release (D16) and run F1. Do not mix an API image of one commit with a front image of another. Anything that moves the database schema backwards is not an image fix (scenario 23).

**Risk level.** Medium.

**Recovery verification.** D7, D14 and the log version all show the intended commit; D5 passes.

## 16. Database migration failure

**Symptoms.** The API never becomes healthy after a deployment; its log shows a Flyway error; D12 shows a row with success equal to f; deployment ends with "API did not become healthy".

**Likely causes.**
- A new migration contains an error or conflicts with existing data.
- Flyway connects as the owner role matbakhy (SPRING_FLYWAY_USER) while the API runs as the least-privilege matbakhy_app; a missing grant is applied by the afterMigrate callback (VERIFIED IN CODE, compose.yaml comment). A permission error therefore usually points to the owner password.
- A migration was changed after it was applied (checksum mismatch, DOCUMENTED ONLY: standard Flyway behaviour).
- Disk or memory shortage during the migration (scenarios 18 and 19).

**Safe diagnostic commands.** D9 for api, D12, D16, D15, D11.

**Expected diagnostic evidence.** The log names the migration version that failed. Before every `up` that finds a running PostgreSQL container, `deploy.sh` takes a backup and restores it into a scratch database to verify it (VERIFIED IN CODE); the output shows the lines "backup ... written" and "backup verified: ... restored and reconciled" with the dump path. The release log is different: `record_release` runs only after the stack has started and become healthy (VERIFIED IN CODE), so the backup of a deployment that failed during migration is NOT listed by D15. Locate it from that deployment output (the Actions log or the console of the person who ran `up`) or from the newest `db-<UTC stamp>` directory under BACKUP_DIR (inside the postgres container the path is /backups/db-<UTC stamp>), and check the `.sha256` and `.manifest` files that `scripts/db-backup.sh` writes next to the dump. D15 and D16 count only successful releases, so the numbering of `rollback-plan [N]` does not include an attempt that failed before the stack became healthy (an attempt that failed later, for example at `smoke`, is recorded). The first deployment on an empty server takes no backup because no database container exists yet (VERIFIED IN CODE).

**Corrective action.** Migrations are forward-only (DOCUMENTED ONLY, [staging deployment and rollback](../../operations/11-staging-deployment-and-rollback.md), section 4). Choose by the table in scenario 23: a fix-forward migration through a pull request, an image-only rollback when every migration since the last good release is expand-only (`scripts/check_migration_compat.py` enforces the annotation in CI), or a restore from the pre-deploy backup for breaking migrations (F11). Do not edit the flyway_schema_history table or delete rows by hand, and do not run ad hoc repair commands on live data without the owner.

**Risk level.** High; this touches the schema of the single source of business data.

**Recovery verification.** D12 shows the highest version with success equal to t, the API is healthy, the reconciliation query reports OK after a restore, and D5 passes.

## 17. Persistent-volume permission errors

**Symptoms.** A container exits at start with "permission denied" or "read-only file system"; Keycloak cannot import the realm; SeaweedFS cannot read its credentials file; backups cannot be written; `deploy.sh up` fails to create the shared lock.

**Likely causes.**
- The stack uses named Docker volumes for state (postgres-data, storage-data, caddy-data, caddy-config, loki-data, alloy-data, prometheus-data, alertmanager-data, alert-sink-data, grafana-data) and host paths for generated configuration, flags and backups (VERIFIED IN CODE, compose.yaml). The path of the Docker data root, the host locations of the named volumes and the volume contents are NOT VERIFIED LIVE (they need the Docker socket); establish them by volume inspection.
- compose.yaml bind-mounts BACKUP_DIR/metrics into the node-exporter container (VERIFIED IN CODE). If that host directory does not exist when the stack first starts, Docker creates it itself (DOCUMENTED ONLY: Docker behaviour for a missing bind source), typically owned by root, and the backup metric file written by the deployment account may then fail. The directory has to be created in advance with an owner that lets the deployment account write to it; the rebuild sequence does not yet say so (documentation gap, NOT VERIFIED LIVE).
- Host paths: the generated directory should be mode 700 with identity and storage subdirectories 755 and files 644, so containers can read them while other host users cannot reach them by path (VERIFIED IN CODE, `render` in `deploy/web-preview/deploy.sh`). On the live host /srv/matbakhy-staging/generated is drwx------ owned by devops (VERIFIED LIVE).
- GENERATED_DIR and BACKUP_DIR in the .env file may point to a directory the account cannot write. The shipped example and the compose default still use /srv/matbakhy-preview/generated, while a stale copy of that name exists on the live host (VERIFIED LIVE) and the live value of GENERATED_DIR is NOT VERIFIED. Confirm it with D22 before changing anything.
- Someone ran a command as the wrong account and created root-owned files in a directory the deployment account must write.

**Safe diagnostic commands.** D21 on the generated directory, flags, identity, storage and backup paths; D22; D9 for the failing container; `ls -ld /srv /srv/matbakhy-staging /srv/matbakhy-backups`.

**Expected diagnostic evidence.** Compare with the modes above. /srv is root:root 755, /srv/matbakhy-staging is devops:devops 755, /srv/matbakhy-backups is devops:devops 750 (VERIFIED LIVE). A file with the wrong owner or mode is the evidence; "Permission denied" for a non-privileged listing is expected for protected directories and is not itself a fault.

**Corrective action.** Prefer the script that sets the modes: F10 (`./deploy.sh render`), then F2 (recreate form) for the services that read the files. For any other path, an authorized administrator corrects the owner or mode of the named path only, after recording the current values with D21. Do not apply recursive broad permissions (for example mode 777), do not make the generated directory world-readable, and do not add the aiagent account to the docker or sudo group to read these paths.

**Risk level.** Medium; permission mistakes can expose secrets or break services.

**Recovery verification.** D21 shows the expected owner and mode, the failing container starts and stays healthy, and D5 passes.

## 18. Full disk

**Symptoms.** Services crash or fail to write; PostgreSQL stops accepting writes; "no space left on device" appears in logs or pulls; the DiskPressure alert fires.

**Likely causes.** On 2026-10-21 the root disk /dev/sda1 had 290 GB with 18 GB used (VERIFIED LIVE), so this is not the current state. Likely growth sources, by configuration (VERIFIED IN CODE unless marked):
- Container logs: 10 MB times 3 files per container.
- Loki log store with a proposed 720-hour retention, and Prometheus metrics with 15 days and a 2 GB size cap. Both are proposed defaults, not approved policies.
- Backups written to BACKUP_DIR, each with a database dump and an object-storage archive, kept until someone removes them (no automatic pruning of backups is visible in the repository; NOT VERIFIED whether a live cron does it, because the crontabs of other users are not readable).
- Old images (every release pulls two new images by digest).
- QA artifacts (the script `deploy.sh prune-qa` exists for them).
- Unrelated tools on the same host (the AI supervisor directories exist under /srv, VERIFIED LIVE; they are not part of the Matbakhy runtime).

**Safe diagnostic commands.** D18; `du -xh --max-depth=1 /srv` (directories the account cannot read will report errors; an administrator runs it for full results); `docker system df` (authorized administrator).

**Expected diagnostic evidence.** Use above about 85 percent on the root filesystem, or `docker system df` showing large reclaimable image space, or a large BACKUP_DIR.

**Corrective action.** In this order, stopping when enough space is free: F7 list form, then F7 with --apply for old QA artifacts; F6 for dangling images; move older backups to storage off the host and delete the older copies only with the owner's authorization and after confirming the newest backup restored successfully (BackupStale metric). Never delete or prune volumes (database, object storage, logs, metrics), and never delete the newest backup. Do not run F4 while the disk is nearly full; it needs space.

**Risk level.** High if the database volume sits on the full disk; deleting the wrong item is irreversible.

**Recovery verification.** D18 shows free space, postgres accepts connections (D11), the stack is healthy (D8) and the next F4 completes (a fresh backup file and the metric updated).

## 19. Memory pressure

**Symptoms.** A container restarts with exit code 137; slow responses; the host swaps; D20 shows OOMKilled true; the Preview has random 502 answers.

**Likely causes.**
- A service reached its own limit. The compose file sets per-service memory limits: proxy 256 MiB, postgres 1536 MiB, identity 1 GiB, storage 512 MiB, api 2 GiB, loki 384 MiB, alloy 256 MiB, prometheus 512 MiB, alertmanager 128 MiB, alert-sink 64 MiB, node-exporter 64 MiB, grafana 256 MiB; the sum computed from compose.yaml is 7040 MiB, about 6.9 GiB (VERIFIED IN CODE).
- The host has about 23 GiB with about 3.4 GiB used when observed (VERIFIED LIVE), so host-wide exhaustion by this stack alone is unlikely; other tools on the same host (an AI supervisor and a Java process of unknown ownership, both seen in the live listening sockets, VERIFIED LIVE) are not established as causes.
- Java services use a percentage of the container limit (MaxRAMPercentage 70 for the API and Keycloak), so a limit set too low leaves little heap (VERIFIED IN CODE).
- The 4 GB sizing override compose.4gb.yaml was written for a proposed Hetzner server (OUTDATED / INCORRECT for the 23 GiB Contabo host). Whether the live .env sets COMPOSE_OVERRIDE is NOT VERIFIED; check with D22, because the override lowers limits.

**Safe diagnostic commands.** D19, D20, D22 (COMPOSE_OVERRIDE), and on the host, the kernel log for out-of-memory events by an authorized administrator (`journalctl -k --since "1 hour ago"`, requires membership of a group that may read the journal; NOT VERIFIED which accounts may).

**Expected diagnostic evidence.** OOMKilled true for one named container, with its RestartCount rising; free memory normal at host level. OOMKilled alone does not show whether the container limit or host memory was exhausted: confirm with the kernel log (a memory-cgroup kill names the container's cgroup, a host-wide kill does not) before assigning the cause.

**Corrective action.** Raise the limit of that service only, through a pull request that changes compose.yaml and is deployed by the pipeline (F9); do not raise unrelated limits. Restart the affected service (F2, `restart` form, which applies no configuration change) to restore service while the change is prepared. Find and fix the cause (for example a long-running query or a memory leak) rather than only increasing limits. Do not run the stack with limits removed.

**Risk level.** Medium; repeated kills of postgres risk crash recovery time, and of the API, failed requests.

**Recovery verification.** D20 shows OOMKilled false and a stable RestartCount, response times normal, D5 passes.

## 20. Certificate renewal failure

**Symptoms.** The certificate date from D4 is close to expiry or past it; browsers start showing expiry warnings; the proxy log shows ACME errors.

**Likely causes.**
- Caddy obtains and renews certificates from Let's Encrypt automatically for every site name in the Caddyfile (DOCUMENTED ONLY: Caddy behaviour; the issuer and 90-day validity were observed, VERIFIED LIVE, but a renewal itself has NOT been verified). The ACME contact is ACME_EMAIL.
- Port 80 or 443 is no longer reachable from the internet (host firewall state NOT VERIFIED LIVE; provider network rules NOT VERIFIED).
- The sslip.io names do not resolve to the server (scenario 2).
- The caddy-data volume, where Caddy stores certificates and account keys, was lost or is unwritable (scenario 17).
- Let's Encrypt rate limits for repeated failed or repeated new orders (DOCUMENTED ONLY: vendor policy; the exact limits for sslip.io names are not recorded here).
- The ACME_EMAIL still holds the example placeholder; `deploy.sh up` refuses placeholders (VERIFIED IN CODE).

**Safe diagnostic commands.** D4 for each host, and `openssl s_client -connect app.<domain>:443 -servername app.<domain> </dev/null 2>/dev/null | openssl x509 -noout -checkend 1209600` (exit status 0 means still valid in 14 days); D3; D9 for proxy; D22 for the Preview domain value (D22 deliberately does not print ACME_EMAIL).

**Expected diagnostic evidence.** For the app host, a not-after date more than 30 days away is normal for the 90-day Let's Encrypt certificate that was observed; the other hosts' issuer and dates are NOT VERIFIED LIVE, so read each host's own D4 output. The proxy log shows the failing ACME step (challenge type, DNS or connection error).

**Corrective action.** Restore reachability of ports 80 and 443 and name resolution first; Caddy retries by itself (DOCUMENTED ONLY). If the proxy must be restarted to retry sooner, use F2 for proxy only. Do not delete the caddy-data volume, do not copy certificates by hand, and do not disable HTTPS. If rate limits apply, wait; do not request many new certificates by changing domains.

**Risk level.** Medium to High; an expired certificate makes the Preview unusable in browsers.

**Recovery verification.** The D4 inspection shows a new not-after date and a subjectAltName that covers the host, the D4 validation exits 0 with verify=0, and D5 passes.

## 21. Broken media URLs

**Symptoms.** Meal photos do not display in the web apps; image requests to the media host fail; uploads fail.

**Likely causes.**
- The media host root returns 403 by design (scenario 5); using it as the test is misleading.
- The object is missing from the bucket, or the object key in the database does not match the file.
- The public bucket matbakhy-public does not exist. `deploy.sh up` creates the buckets with a best-effort command whose failure is ignored (`|| true`, VERIFIED IN CODE), so a failure is silent.
- The public base URL the API generates is wrong: OBJECT_STORAGE_PUBLIC_BASE_URL must be https://media.<domain>/matbakhy-public and the client endpoint https://media.<domain> (VERIFIED IN CODE).
- The caller is outside the customer class (the media host follows the customer access list, scenario 5).
- The storage container is down or unhealthy (scenario 7), or the storage-data volume is damaged or full.
- The web apps' content security policy lists images only from the app host itself, data URIs and the media host (VERIFIED IN CODE), but the Caddyfile sends it as Content-Security-Policy-Report-Only. That policy can report a violation and cannot itself block an image, so it does not explain a broken image while it stays Report-Only.

**Safe diagnostic commands.** D1 on a real image URL taken from the page source or the API (`https://media.<domain>/matbakhy-public/<object key>`); D2 to read the content type; D8 and D9 for storage (authorized administrator); the browser developer-tools network tab.

**Expected diagnostic evidence.** A working image answers 200 with an image content type. 403 with XML and x-amz-request-id means the S3 gateway refused that request. 404 means no such object. A Caddy "Not available" 403 means the caller is outside the customer class.

**Corrective action.** For a missing bucket or object, an authorized administrator re-runs F1 (which recreates the buckets idempotently) or re-uploads through the application; do not write into the storage volume directly. For a wrong URL, fix the environment value through the owner and F2 in its recreate form for the api. For storage down, scenario 7. Restore of image storage uses the archive from F4 and needs the owner.

**Risk level.** Low to Medium; photos are public marketing data in the Preview, but private documents live in the private bucket and must never be exposed.

**Recovery verification.** The image URL returns 200 with an image content type, the page shows the photo, and a fresh upload on a test account displays.

## 22. Failed smoke tests

**Symptoms.** `deploy.sh smoke` or D5 prints `FAIL: ...` and exits 1; the deployment job fails at the smoke step.

**Likely causes.** Each smoke check has a meaning (VERIFIED IN CODE, `deploy/web-preview/smoke.sh`):

| Failing message contains | Meaning | Go to |
|---|---|---|
| does not serve the web app, or lacks the header | The app or ops host is not serving the build or the proxy headers are missing | 1, 4, 5 |
| build lacks the demo environment notice | The front image is not the Preview build | 15 |
| API-001 not reachable, or does not report the PREVIEW demo environment | The API is down or reports another environment | 7, 8 |
| discovery not reachable | The meals endpoint fails | 8, 9 |
| answered X without a session (expected 401 or 403) | A protected endpoint is open or erroring: a security regression to treat as urgent | 4, 5 |
| actuator must not be published, or metrics must not be published | The proxy exposes the management port | 4 |
| identity issuer is ... | Issuer mismatch | 11 |
| id.<domain>/... must not be published | The master realm or admin console is reachable on the customer identity host | 4 |
| kc-admin ... must serve the console or answer 404 | The admin host answers an unexpected code; code 000 only warns | 6, 12 |
| unexpected sign-in redirect | Login redirect problem | 10 |

Two environmental causes: the machine running the smoke test is not in the allow-list (use SMOKE_OPS=skip for the ops host; for other hosts run from an allowed machine), or the stack was only seconds old. Retries differ per check (VERIFIED IN CODE): requests made through the script's `get` helper (page, script, configuration, meals and discovery requests) are retried up to 6 times at 5-second intervals; status-code probes (`code`), the security-header request and the final sign-in redirect request are made once with no retry; and the kc-admin probe uses its own loop of up to 18 attempts at 5-second intervals, about 90 seconds. A single failure of a check without retry can therefore be transient, so run D5 once more before treating it as a confirmed fault; a repeated identical failure is real.

**Safe diagnostic commands.** D5 again, once, to rule out a start-up race in the checks that are not retried; D1 on the exact URL in the message; then the commands of the scenario in the table.

**Expected diagnostic evidence.** The message names the first failing check; the script stops at the first failure.

**Corrective action.** Follow the scenario in the table. Never edit `smoke.sh` to make it pass, and never skip a check; tests are not to be weakened to obtain a green result (project rule). On the server, a failing smoke test after `up` makes the remote deploy script restore the previous .env file and exit with failure; containers are not recreated and the database is not rolled back (scenarios 14 and 15).

**Risk level.** Medium; some failures indicate exposure of protected endpoints.

**Recovery verification.** D5 prints the final PASS line.

## 23. Failed deployment requiring rollback

**Symptoms.** A deployment left the Preview unhealthy, with errors that retrying does not remove, or a release introduced a defect; the owner decides to go back.

**Likely causes.** A bad application change, a failed or breaking migration (scenario 16), an image problem (scenarios 13 and 15), or mixed state after a failed run.

**Safe diagnostic commands.** D7, D15, D16 (read the next paragraph before choosing N), D12, D9, D23.

**Choosing the rollback target.** `rollback-plan [N]` counts only recorded successful releases, because `record_release` runs once the stack is healthy (VERIFIED IN CODE). Decide first whether the failed deployment was recorded by comparing the commit in the last row of D15 with the failing commit from D23. (a) If the deployment failed before the stack became healthy, it is not recorded and the last row is the latest successful release; N=1 would skip it, and with a single row `rollback-plan` refuses. (b) If it failed after becoming healthy (for example at `smoke`), it is the last row and N=1 selects the release before it. In case (a) `rollback-plan` has no option that selects the last row; the script exists only in the N>=1 form. What exists: the last row of D15 holds the commit, API image and front image to put into .env by hand (fields 2 to 4), and its schema-after field (field 6) to compare with D12. What is missing: a script verdict for that row. This route is read from the code and has not been rehearsed (NOT VERIFIED); adding a script option would be a code change through a pull request and an owner decision.

**Expected diagnostic evidence.** D16 prints the target commit, the three .env values and a verdict. Verdict "schema has not moved": an image-only rollback keeps data. Verdict "schema moved": image-only rollback is safe only if every migration since then is expand-only; otherwise the backup recorded before the first deploy that applied them is the route (VERIFIED IN CODE, `rollback_plan` in `deploy/web-preview/deploy.sh`).

**Decision table** (DOCUMENTED ONLY: [staging deployment and rollback](../../operations/11-staging-deployment-and-rollback.md), section 4.2; the helper that prints the verdict is VERIFIED IN CODE):

| Situation | Action |
|---|---|
| New release is bad; no migration since the last good release | Image-only rollback: set the three values from D16 and run F3. Data is kept |
| Migrations since then are all expand-only | Image-only rollback; the older API ignores migrations it does not know. Run the smoke test afterwards |
| A migration is tightening | Rehearse the older image on a copy first; if writes fail, use the restore path |
| A migration is breaking, or data is damaged | Restore the pre-deploy backup (F11). Data written after that backup is lost; tell the testers |
| The deployment failed half-way | D7, read the api and identity logs, then fix forward or roll back by the rows above. `up` is repeatable |

**Corrective action.** Rollback is a decision for the owner. Record the failing commit, the target commit and the reason. Take a fresh backup first if the database is still readable (F4), unless the disk or the database state makes that unsafe. Then perform F3, or F11 for the restore path (stop the API first; swap databases only with nobody connected; keep the failed database until the cause is understood). Note that the pipeline's own failure handling restores the .env file only when `up` or `smoke` fails in the remote script (not when the later admin-console step fails), recreates no container and performs no automatic database rollback (VERIFIED IN CODE, `.github/workflows/deploy-preview.yml`). Rehearse restores on a new isolated server, never first on the live Preview ([backup, restore and disaster recovery](backup-restore-disaster-recovery.md), [rebuild guide](server-rebuild-guide.md)).

**Risk level.** High for any rollback that touches the database; Medium for image-only rollback.

**Recovery verification.** D7 shows the target commit and schema; the api is healthy; `./deploy.sh smoke` passes; a synthetic order flow in the operations web or the owner's acceptance cases still works; and the incident (cause, commit, backup used, data loss if any) is recorded.

## Known limitations and documentation gaps

- Docker runtime state (containers, images, volumes, limits) is NOT VERIFIED LIVE because the aiagent account cannot read the Docker socket. Every Docker-based diagnostic in this page is NOT VERIFIED LIVE until an authorized administrator runs it ([administrator read-only verification](admin-readonly-verification.md)).
- The live values of GENERATED_DIR, BACKUP_DIR and COMPOSE_OVERRIDE are NOT VERIFIED LIVE. The compose default and `.env.example` still name /srv/matbakhy-preview/generated, which matches a stale copy that exists on the host, while the deployment is at /srv/matbakhy-staging (documentation gap: confirm with D22).
- The brief for this page says 22 scenarios but lists 23; all 23 are present.
- `compose.4gb.yaml`, the Hetzner preparation document ([staging preparation for Hetzner](../../operations/09-staging-hetzner-preparation.md)) and parts of other operations documents describe a proposed Hetzner host and are OUTDATED / INCORRECT for the Contabo host (8 vCPU, about 23 GiB RAM). Where they conflict with this page, this page and the [server inventory](contabo-server-inventory.md) govern.
- The troubleshooting runbook in operations says backups live in a backups folder inside the deployment directory while `deploy.sh` and compose.yaml default BACKUP_DIR to /srv/matbakhy-backups (VERIFIED IN CODE); this is a conflict in the older document. The operations runbook itself states that it was not exercised on a deployed environment.
- Firewall (ufw) rules, sshd effective settings, sudo policy, other users' cron jobs, the Docker data root and certificate renewal itself are NOT VERIFIED LIVE; they need root or Docker access.
- The deploying SSH account (STAGING_SSH_USER) is a repository secret and is NOT VERIFIED.
- Alerts go only to a local test sink until the owner approves a real recipient (DOCUMENTED ONLY, operations runbook), so an alert firing is visible only to someone reading Grafana or the sink log.
- None of the corrective commands was run for this page; the "expected result" columns for them come from the scripts, not from a rehearsal.
