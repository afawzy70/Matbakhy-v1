# Hetzner staging: what the owner does on the server (exact steps)

> **HISTORICAL (OUTDATED / INCORRECT as current instructions).** These owner steps target the Hetzner proposal (a `matbakhy` operating user, `/srv/matbakhy-preview`, a Hetzner cloud firewall). The observed Contabo host has the accounts `ubuntu`, `devops` and `aiagent` and deploys to `/srv/matbakhy-staging`. Use the [server rebuild guide](../developer-handbook/08-infrastructure/server-rebuild-guide.md) and the [Contabo server inventory](../developer-handbook/08-infrastructure/contabo-server-inventory.md). Kept unchanged below for history.

Status: **instructions only. No server access is assumed; the agent has not touched any server, DNS record or account.** Nothing below has been run on the Hetzner host. The commands were checked as far as they could be on a local machine (see [11](11-staging-deployment-and-rollback.md), "What was rehearsed"). Adjust them to the operating system you choose; they assume Ubuntu 24.04 LTS.

Decisions already made (owner, 2026-10-14): the server is a Hetzner VPS with 2 vCPU and 4 GB RAM; the first Internet-accessible staging runs the **PREVIEW** profile (synthetic data, simulated deposits and payments, no real notification, a visible demo notice); push stays off (`PUSH_DELIVERY_ENABLED=false`); the proposed staging retention defaults stay (logs 30 days, debug logs 7 days, QA artifacts 90 days; not production policies).

## 0. Before you start

You need: the Hetzner project and a payment method (your decision, not the agent's); a domain you control; your own public IP address (and each kitchen or staff tester's, see section 8); the approval to run the image workflow (section 5); a place to keep the secrets file (on the server only).

## 1. Create the server

1. Create a Cloud server in an EU location, the 2 vCPU / 4 GB plan, Ubuntu 24.04, **IPv4 and IPv6**. Availability and price are for you to check on Hetzner's page.
2. Add your SSH public key (ed25519) at creation. Do not set a password.
3. Cloud firewall (Hetzner console): inbound **22** only from your address; **80 and 443** from anywhere (the certificate authority must reach port 80, and the proxy enforces the customer and staff allow-lists itself); everything else closed. Do not open 3000, 5432, 8080, 8081, 8180 or 8333.
4. Optional: Hetzner backups (extra cost; unverified). They do not replace `deploy.sh backup`.

## 2. DNS

Create `A` (and `AAAA`) records pointing to the server for: `app.<domain>`, `ops.<domain>`, `api.<domain>`, `id.<domain>`, `media.<domain>`. Check with `dig +short app.<domain>` before the first start, or the certificate requests fail and the certificate authority's rate limit can lock you out for a while.

## 3. Prepare the machine (as root, once)

```sh
adduser --disabled-password --gecos "" matbakhy && usermod -aG sudo matbakhy
mkdir -p /home/matbakhy/.ssh && cp ~/.ssh/authorized_keys /home/matbakhy/.ssh/ && chown -R matbakhy:matbakhy /home/matbakhy/.ssh
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/; s/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config && systemctl reload ssh
apt-get update && apt-get -y install unattended-upgrades ca-certificates curl gnupg git
# 2 GiB swap file: a short memory peak must not become an out-of-memory kill (the 4 GB profile's ceilings are not measured use)
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab
# Docker Engine and the Compose plugin (Docker's own repository)
install -m 0755 -d /etc/apt/keyrings && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list
apt-get update && apt-get -y install docker-ce docker-ce-cli containerd.io docker-compose-plugin && usermod -aG docker matbakhy
mkdir -p /srv/matbakhy-preview /srv/matbakhy-backups /srv/matbakhy-qa-artifacts && chown -R matbakhy:matbakhy /srv/matbakhy-*
```

Log in again as `matbakhy` (key only) and check: `docker compose version`, `free -m` (swap 2 GiB), `df -h /` (the plan's disk), `sudo ufw status` (not used; the cloud firewall is).

## 4. Put the package on the server at the release commit

```sh
mkdir -p /srv/matbakhy-preview/src && cd /srv/matbakhy-preview
git clone --no-checkout https://github.com/afawzy70/Matbakhy-v1.git repo && git -C repo archive <RELEASE_COMMIT> | tar -x -C src
cd src/deploy/web-preview
```

`<RELEASE_COMMIT>` is the full 40-character SHA of a commit whose Product CI passed. The scripts under `src/scripts/` and `src/infra/db/reconcile.sql` are used from there.

## 5. Images (needs your approval)

1. Approve and dispatch the workflow **Web preview images** for the same commit (GitHub, Actions, `preview-images.yml`, input: the domain). It pushes two images to GHCR under the repository owner and prints three values (`RELEASE_COMMIT`, `API_IMAGE`, `FRONT_IMAGE`, both images pinned by digest). The agent does not dispatch it. Check the package visibility yourself: the images hold no secrets, but the front image contains the domain.
2. If the packages are private, create a read-only token on GitHub, run `docker login ghcr.io -u <user>` on the server and paste the token once. The token never goes into the repository or the chat.

## 6. Configuration file (on the server only)

```sh
./deploy.sh init-secrets        # creates .env (mode 600) with generated secrets; refuses to overwrite
nano .env
```

Set: `RELEASE_COMMIT`, `API_IMAGE`, `FRONT_IMAGE` (the three values from section 5), `PREVIEW_DOMAIN`, `ACME_EMAIL`, `ALLOWED_CLIENT_IPS` (your address, `/32`; quote the value when it holds more than one), `COMPOSE_OVERRIDE=compose.4gb.yaml`. Leave `CUSTOMER_ALLOWED_IPS` and `STAFF_ALLOWED_IPS` unset for now (everything stays restricted to `ALLOWED_CLIENT_IPS`), and keep `PUSH_DELIVERY_ENABLED=false`. `deploy.sh` refuses placeholders, tags without a digest and short SHAs.

## 7. First start

```sh
./deploy.sh up           # backs up first when a database exists, pulls missing images, starts in order, waits for health, records the release
./deploy.sh seed         # first deploy only: demo catalogue, demo accounts, demo deposit recipient
./deploy.sh status       # images, schema version, last releases
./deploy.sh smoke        # from a machine in ALLOWED_CLIENT_IPS
```

Browser smoke with a simulated deposit order (from your own computer, which needs the repository and Node; creates one demo order per run):

```sh
cd apps/operations-web && STAGING_APP_URL=https://app.<domain> STAGING_OPS_URL=https://ops.<domain> STAGING_API_URL=https://api.<domain> \
  STAGING_USER_PASSWORD=<PREVIEW_USER_PASSWORD from the server .env> pnpm exec playwright test -c playwright.staging.config.ts
```

Test accounts (realm `matbakhy`, the same password): `preview-customer-1`, `preview-customer-2`, `preview-cook-1`, `preview-ops-1` (the bootstrap staff administrator) and `preview-ops-2` (a staff-style account without grants, for the E2E suite and for testing the grant workflow). A realm already imported before this change does not get `preview-ops-2` by itself; create it in Keycloak with the same password. Keycloak is administered through `docker compose exec identity /opt/keycloak/bin/kcadm.sh` or an SSH tunnel; its console is published only on the Preview's `kc-admin` host behind a required one-time password (docs/operations/14-keycloak-admin-console.md); the master realm is not on the customer host.

## 8. Letting people in

- **Default (restricted):** only `ALLOWED_CLIENT_IPS` can reach any host.
- **Public customers (your explicit decision):** set `CUSTOMER_ALLOWED_IPS="0.0.0.0/0 ::/0"` in `.env`, run `./deploy.sh up`, then `./deploy.sh smoke`. The customer web, the customer API, sign-in (`id.`, realm `matbakhy` only) and photos become public. **`ops.`, `/api/v1/ops/*` and `/api/v1/cook/*` stay on the staff class** (`STAFF_ALLOWED_IPS`, default `ALLOWED_CLIENT_IPS`): kitchen and staff testers need their address added there. From a machine outside the staff list, `SMOKE_OPS=skip ./deploy.sh smoke` must pass and must show `ops.` refusing it.
- Every screen shows the demo/test notice and the API reports `environment: PREVIEW, demo: true`. Tell testers it is a demo; no real order, payment or message exists.

## 9. Backups and logs

- Daily backup: `crontab -e` for `matbakhy`: `17 3 * * * cd /srv/matbakhy-preview/src/deploy/web-preview && ./deploy.sh backup >> /srv/matbakhy-backups/backup.log 2>&1`. A backup counts only after it was restored and reconciled in a scratch database; the `BackupStale` alert fires after 24 hours without one.
- Backups stay on the same server until you copy them elsewhere (your choice of destination; not configured). Retention of backups is an O06 decision; nothing prunes them.
- QA artifacts: `./deploy.sh prune-qa` lists files older than 90 days in `/srv/matbakhy-qa-artifacts`; add `--apply` to delete them.
- Logs: 30 days (debug 7 days) in Loki; Grafana is optional (`COMPOSE_PROFILES=tools ./deploy.sh up`, reached through an SSH tunnel on 127.0.0.1:3000).

## 10. Remote browser E2E (optional, only in a window)

Read [11](11-staging-deployment-and-rollback.md), section 5. In short: the suite needs an empty database, so it wipes all application data; it needs a marker file, non-public customer hosts and a typed confirmation, and it takes a verified backup first. Never run it while real testers are using the environment.

## 11. What to send back so the agent can continue

After section 7, send the outputs of `./deploy.sh status`, `./deploy.sh smoke` and the browser smoke (no secrets in them), and say which of section 8 you chose. The agent then prepares the AUD-01, LOG-01, OPS-01..03 and TC-057 runs as commands for you to execute, or works from time-boxed access you decide to grant; it will not assume either. Until real outputs exist, those checks stay NOT_RUN.

## Do not

Open database, Keycloak, storage, management or Grafana ports; set `APP_ENV` to anything but PREVIEW; enable `PUSH_DELIVERY_ENABLED`; put real customer, payment or document data in this environment; put the `.env` file, tokens or keys in the repository, a chat or a ticket.
