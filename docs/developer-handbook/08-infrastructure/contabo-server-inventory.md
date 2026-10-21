# Contabo server inventory

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — the host below is running and was observed read-only as an unprivileged user; everything that needs root or the Docker socket is NOT VERIFIED
> **Language / اللغة:** English · [العربية](contabo-server-inventory-ar.md)

This page records what is known about the single server that runs the Matbakhy Preview. It separates what was observed on the machine from what the repository says. It never contains secrets.

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 on the server itself by the unprivileged user aiagent: read-only commands, no sudo, no Docker socket, no protected files. |
| VERIFIED IN CODE | Read in this repository (file named in the row). It describes intended behaviour, not necessarily what runs. |
| DOCUMENTED ONLY | A document states it; nothing confirms it. |
| OUTDATED / INCORRECT | Written for the proposed Hetzner host or superseded; conflicts with newer evidence. |
| NOT VERIFIED | Not observed and not in the repository. The administrator command that would verify it is on [admin read-only verification](admin-readonly-verification.md). |

The Preview is a synthetic-data environment, not production and not a release environment ([infrastructure and deployment](../03-architecture/infrastructure-and-deployment.md)).

## 1. Host

| Item | Value | Evidence |
|---|---|---|
| Provider | Contabo VPS (the running host; the earlier plan named Hetzner, see [section 9](#9-historical-hetzner-text-versus-the-current-host)) | VERIFIED LIVE (workflow title "Deploy Matbakhy preview to Contabo" is VERIFIED IN CODE in `.github/workflows/deploy-preview.yml`) |
| Operating system | Ubuntu 24.04.5 LTS (Noble) | VERIFIED LIVE |
| Kernel | 6.8.0-147-generic | VERIFIED LIVE |
| CPU | 8 vCPU | VERIFIED LIVE |
| Memory | 23 GiB total; about 3.4 GiB used at the time of observation | VERIFIED LIVE |
| Disk | /dev/sda1, 290 GB, 18 GB used (6 percent) | VERIFIED LIVE |
| Time zone | Europe/Berlin (CEST) is the host time zone. Which time zone each scheduler or logger uses is not established: `deploy.sh` writes release-log timestamps in UTC (`deploy/web-preview/deploy.sh`, function record_release), so do not assume Berlin time for cron entries, application logs or container logs | VERIFIED LIVE (host time zone only); VERIFIED IN CODE (release-log timestamps in UTC); cron and log conventions NOT VERIFIED |
| Docker Engine | 29.9.1 | VERIFIED LIVE (`docker --version`) |
| Docker Compose plugin | v5.6.0 | VERIFIED LIVE (`docker compose version`) |
| Installed packages seen | docker-ce, docker-ce-cli, containerd.io, docker-ce-rootless-extras, ufw, unattended-upgrades | VERIFIED LIVE |
| Not installed | fail2ban and Caddy on the host. Caddy runs inside the proxy container | VERIFIED LIVE for the host packages; VERIFIED IN CODE for the container (`deploy/web-preview/compose.yaml`, service proxy) |
| Active services seen | ssh, cron, docker, containerd, tailscaled, unattended-upgrades, systemd-resolved, systemd-networkd, systemd-timesyncd, rsyslog | VERIFIED LIVE |
| Deployed Preview domain | 167-86-90-174.sslip.io, public IPv4 167.86.90.174 (already public in the repository) | VERIFIED LIVE for DNS resolution; see [DNS and HTTPS](dns-https-sslip-io.md) |

The 4 GB sizing override `compose.4gb.yaml` and the "2 vCPU and 4 GB" figures in older documents describe the Hetzner plan. They do not describe this host (OUTDATED / INCORRECT for the host size). Which sizing override the live `.env` selects (`COMPOSE_OVERRIDE`) is NOT VERIFIED.

## 2. Accounts and groups

Only accounts with uid 1000 or higher are listed. Data comes from /etc/passwd, id and getent.

| Account | uid and gid | Home | Verified groups | Evidence |
|---|---|---|---|---|
| ubuntu | 1000, 1000 | /home/ubuntu (750 ubuntu:ubuntu), bash | ubuntu, adm, cdrom, sudo, dip, lxd | VERIFIED LIVE |
| devops | 1001, 1001 | /home/devops (750 devops:devops), bash | devops, sudo, users, docker | VERIFIED LIVE |
| aiagent | 1002, 1002 | not recorded, bash | aiagent, users | VERIFIED LIVE |

Group memberships that matter:

| Group | Members (verified) | Why it matters |
|---|---|---|
| sudo (gid 27) | ubuntu, devops | Membership conventionally grants sudo access, but the effective privileges are NOT VERIFIED: the sudo policy files are unreadable to aiagent, so the exact rules (password required or not, command limits) are unknown. |
| docker (gid 988) | devops | Membership is equivalent to root on the host. Only devops is a member. |
| users (gid 100) | devops, aiagent | No repository file gives this group a meaning. |

Rules that must stay true:

- aiagent is not in sudo and not in docker. It must never receive sudo or Docker rights. It cannot read /var/run/docker.sock, which is why container state is NOT VERIFIED here.
- No permission, group membership or account was changed to produce this page.

### Responsibilities

The operational responsibility of each account is not established from the account name. Do not infer a role from a name.

| Account | Responsibility | What evidence exists |
|---|---|---|
| ubuntu | not established from the account name | Member of sudo, adm, cdrom, dip and lxd (VERIFIED LIVE). Nothing shows what it is used for. |
| devops | not established from the account name | Member of sudo and docker. Owns /srv/matbakhy-staging, its .env, the backup directories and /srv/deployments (VERIFIED LIVE). This is circumstantial only: the deployment SSH user is a GitHub repository secret (see below). |
| aiagent | not established from the account name | Owns /srv/ai-supervisor and /srv/ai-workspaces, which hold AI tooling and are not part of the Matbakhy runtime (VERIFIED LIVE). Has no sudo and no Docker access. |

The Linux user that the deployment workflow connects as is the repository secret `STAGING_SSH_USER` (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`, step "Transfer versioned deployment files"). Its value is NOT VERIFIED. Ownership of /srv/matbakhy-staging by devops fits devops being that user but does not prove it. How to confirm: an administrator correlates the accepted SSH logins (command 13 on the [admin page](admin-readonly-verification.md)) with the timestamp of one known workflow run and reports any remaining ambiguity, because accepted-login records alone do not show which session belonged to GitHub Actions. Checking which secret names exist in the repository settings only shows that `STAGING_SSH_USER` is set; a name reveals no username and values cannot be read.

## 3. SSH policy

Readable parts of /etc/ssh/sshd_config and /etc/ssh/sshd_config.d were read by aiagent.

| Setting | Readable value | Evidence and caveat |
|---|---|---|
| PasswordAuthentication | no | VERIFIED LIVE (file text) |
| KbdInteractiveAuthentication | no | VERIFIED LIVE (file text) |
| PubkeyAuthentication | yes | VERIFIED LIVE (file text) |
| UsePAM | yes | VERIFIED LIVE (file text) |
| X11Forwarding | yes | VERIFIED LIVE (file text). Not required by Matbakhy; a hardening review may turn it off. |
| PermitRootLogin | no in the readable text, but an earlier line says yes and a drop-in that is read after it in the processing order (not a newer file) overrides it | The effective value is NOT VERIFIED. /etc/ssh/sshd_config.d/50-cloud-init.conf is unreadable to aiagent and may set it again. Confirm with `sshd -T` as root (command 1 on the admin page). |

Not readable by aiagent and therefore NOT VERIFIED: 50-cloud-init.conf, every authorized_keys file, the host private keys, and /etc/sudoers.d. The number of authorized keys per account and which key belongs to which person or to GitHub Actions are unknown, and so is the effective key source (AuthorizedKeysFile or AuthorizedKeysCommand).

The deployment workflow pins the server's host key from the secret `STAGING_SSH_HOST_PUBKEY` and connects with `StrictHostKeyChecking=yes` (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`, step "Configure verified SSH host").

## 4. Directories

Listing made with `ls -ld` as aiagent. The "purpose" column is from code or documents and is labelled; it is not proof of how the live files are used.

| Path | Owner and mode (VERIFIED LIVE) | Purpose and its evidence |
|---|---|---|
| /srv | root:root 755 | Parent directory. |
| /srv/matbakhy-staging | devops:devops 755; modified Oct 18 and Oct 20 | Target of continuous deployment: the workflow unpacks deploy, infra, scripts and one owner-acceptance fixture file into it (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`, step "Transfer versioned deployment files"). It holds a repository-like copy (.github, apps, backend, contracts, deploy, docs, infra, scripts and others), VERIFIED LIVE. |
| /srv/matbakhy-staging/deploy/web-preview | devops:devops 755 | Working directory of every `deploy.sh` command (VERIFIED IN CODE: `deploy/web-preview/deploy.sh` does `cd` to its own directory). Contains compose.yaml, compose.4gb.yaml, Caddyfile, deploy.sh, admin-console.sh, smoke.sh, seed SQL and the directories identity, observability, postgres, storage and e2e-fixtures (VERIFIED LIVE). |
| /srv/matbakhy-staging/deploy/web-preview/.env | devops:devops 600 | The server-only configuration file with all secrets. Its content was not read and must never be copied (VERIFIED LIVE for existence, owner and mode). `deploy.sh init-secrets` is the supported way to create it (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`); how this particular file was created is NOT VERIFIED. |
| /srv/matbakhy-staging/generated | owner devops, mode 700 (drwx------); group not recorded | Probably the rendered realm and storage configuration, because `deploy.sh render` creates a mode 700 GENERATED_DIR with identity, storage and flags inside (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`, function render). That the live .env sets GENERATED_DIR to this path is NOT VERIFIED. Content not read. |
| /srv/matbakhy-backups | devops:devops 750; 28 entries, not listable by aiagent | Default BACKUP_DIR: database dumps, image-storage archives, the release log and the shared lock (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`, `deploy/web-preview/deploy.sh`). That the live BACKUP_DIR is this path, and what the 28 entries are, is NOT VERIFIED. compose.yaml also bind-mounts `BACKUP_DIR/metrics` read-only into node-exporter (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`), so that subdirectory must exist with suitable ownership before the first start; whether it exists here is NOT VERIFIED. |
| /srv/backups | devops 750 (group not recorded) | Purpose not established. No repository file refers to this path. |
| /srv/deployments | devops 750 (group not recorded); not listable | Purpose not established. No repository file refers to this path. |
| /srv/matbakhy-preview | devops:devops 775 | Stale copy of the deploy files from Oct 17 to 18; see [section 7](#7-the-stale-srvmatbakhy-preview-copy). |
| /srv/ai-supervisor | aiagent, 750 (group not recorded) | AI tooling, not the Matbakhy runtime (VERIFIED LIVE). |
| /srv/ai-workspaces | aiagent, 750 (group not recorded) | AI tooling, not the Matbakhy runtime (VERIFIED LIVE). |
| /srv/flags | does not exist on the host filesystem | The name appears only inside the proxy container: the Caddyfile reads /srv/flags/admin-console-public and compose.yaml mounts GENERATED_DIR/flags there read-only (VERIFIED IN CODE: `deploy/web-preview/Caddyfile`, `deploy/web-preview/compose.yaml`). Its location on the host is under the generated directory and is NOT VERIFIED. |
| /srv/matbakhy-qa-artifacts | not in the observed listing | Default QA_ARTIFACT_DIR (VERIFIED IN CODE: `deploy/web-preview/.env.example`). Whether the directory exists on this host is NOT VERIFIED. |

Docker volume locations are NOT VERIFIED LIVE; establish them by inspecting the volumes (commands 8 and 9 on the admin page). Their names and sizes are NOT VERIFIED LIVE too. The volumes the compose file declares are listed in the next section.

### Volumes declared by the compose file

VERIFIED IN CODE (`deploy/web-preview/compose.yaml`, project name matbakhy-preview): postgres-data, storage-data, caddy-data, caddy-config, loki-data, alloy-data, prometheus-data, alertmanager-data, alert-sink-data, grafana-data. Whether each exists, and under which prefixed names, is NOT VERIFIED LIVE (commands 8 and 9 on the admin page).

## 5. Network interfaces and listening ports

| Item | Observation | Evidence |
|---|---|---|
| eth0 | public IPv4 167.86.90.174/24 and public IPv6 2a02:c207:2367:3442::1/64 | VERIFIED LIVE |
| tailscale0 | present; Tailscale daemon active; private tailnet address. Which people or machines are on the tailnet and what it is used for is NOT VERIFIED | VERIFIED LIVE (interface and daemon) |
| docker0 | 172.17.0.1/16, state DOWN | VERIFIED LIVE |
| Docker bridges | three br-* networks on 172.18.0.1/16, 172.19.0.1/16 and 172.20.0.1/16, UP, with three veth interfaces. The compose file declares three networks (edge, ops, internal), which is consistent but not proven to be the same ones | VERIFIED LIVE (interfaces); VERIFIED IN CODE (declared networks) |

Listening sockets seen with `ss -tlnp` as aiagent:

| Listener | Observation | Reading |
|---|---|---|
| 0.0.0.0 and [::] port 22 | public | SSH. VERIFIED LIVE. |
| 0.0.0.0 and [::] ports 80 and 443 | public | The proxy container publishes "80:80" and "443:443" (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`). The listener's process is not visible to aiagent, so the link to the container is inferred, not proven. |
| 127.0.0.1 port 3000 | loopback only | Matches the Grafana binding "127.0.0.1:3000:3000" in the compose file; the owning process is NOT VERIFIED. |
| 127.0.0.1 port 3080 | loopback only | A node process of the AI supervisor tooling, not Matbakhy (VERIFIED LIVE). |
| 127.0.0.53 and 127.0.0.54 port 53 | loopback only | Local resolver (systemd-resolved). |
| tailscale0 high ports | tailnet only | Tailscale. |
| 127.0.0.1 ports 18080 and 38189 | loopback only | A Java process whose owner is not established. Do not assume it is Matbakhy: the compose file publishes no such ports. |

No database, Keycloak, storage, API or management port is public. The compose file publishes only the proxy ports and the Grafana loopback port (VERIFIED IN CODE), which agrees with the listing. The firewall that would enforce this is NOT VERIFIED (ufw is installed; its state needs root).

## 6. Scheduled work

| Item | Observation | Evidence |
|---|---|---|
| matbakhy-claude-recover.timer | A systemd timer for an AI-supervisor recovery job. Not part of the Matbakhy application | VERIFIED LIVE (existence); purpose from the live notes |
| /etc/cron.d | e2scrub_all, staticroute, sysstat | VERIFIED LIVE |
| Matbakhy backup schedule | Not visible. Crontabs of other users are unreadable to aiagent. Older documents tell the operator to add a daily `deploy.sh backup` cron entry for the operating user | NOT VERIFIED on this host; DOCUMENTED ONLY in `docs/operations/12-owner-server-setup.md`, section 9 |

The repository takes a pre-deploy backup in `deploy.sh up` (VERIFIED IN CODE: `deploy/web-preview/deploy.sh`, function up). Whether any other backup is scheduled or run by hand is NOT VERIFIED: the crontabs of other users, the full systemd timer list and any manual practice have not been inspected (commands 12 and 15 on the admin page). The absence of a visible cron entry does not show that no scheduled or manual backup exists. See [backup, restore and disaster recovery](backup-restore-disaster-recovery.md).

## 7. The stale /srv/matbakhy-preview copy

- VERIFIED LIVE: /srv/matbakhy-preview (devops:devops, 775) holds an older copy of the deploy files dated Oct 17 to 18; its deploy.sh is smaller than the one in /srv/matbakhy-staging.
- VERIFIED IN CODE: the default for GENERATED_DIR in both `deploy/web-preview/compose.yaml` and `deploy/web-preview/.env.example` is still /srv/matbakhy-preview/generated. The deployment workflow, however, targets /srv/matbakhy-staging.
- Consequence: the repository default and the live deployment directory disagree. If the live .env does not override GENERATED_DIR, rendered secrets and the flags directory would sit under the old path (BACKUP_DIR is a separate setting and does not decide where rendered configuration goes). What the live .env sets is NOT VERIFIED; command 2 on the admin page reads only those key names.
- Do not delete or reuse the old directory until that is confirmed. Treat it as legacy and do not run `deploy.sh` from it.

## 8. NOT VERIFIED LIVE (summary)

Everything below needs an authorized infrastructure administrator. The command numbers refer to [admin read-only verification](admin-readonly-verification.md).

| Topic | Why it is unknown | Command |
|---|---|---|
| Effective SSH settings, including PermitRootLogin | sshd -T and 50-cloud-init.conf need root | 1 |
| Firewall state and rules (ufw) | Needs root | 3 |
| sudo policy files and rules | /etc/sudoers.d unreadable | 4 |
| Running containers, images, health, restart counts | Docker socket not accessible to aiagent | 5, 7 |
| Container limits (memory, CPU) actually applied | Same | 7 |
| Docker volumes, their names and sizes | Same | 8, 9, 14 |
| Docker networks | Same | 6 |
| Systemd timers (full list, including any Matbakhy timer) | Only partly seen | 15 |
| Certificate files and renewal state in the Caddy volume | Volume path not readable | 10 |
| Contents of .env (values) | Secret; only a handful of non-secret key names may be checked | 2 |
| Contents of /srv/matbakhy-backups, /srv/backups, /srv/deployments | Not listable | 11 |
| Cron entries of devops and other users, and system schedules | Not readable | 12 |
| Commit, image digests and revision labels actually deployed | Docker socket and .env not accessible to aiagent | 17, 18 |
| Which user the deployment SSH key logs in as | Secret STAGING_SSH_USER; log evidence needs root | 13 |
| Number of authorized SSH keys per account (command 13 gives a line count only) and the effective key source | Not readable | 1, 13 |
| Process owner of the loopback ports 3000, 18080 and 38189 | Not visible to aiagent | 16 |
| Tailnet membership and purpose | Not visible | not covered (tailnet admin console) |

## 9. Historical Hetzner text versus the current host

The first infrastructure documents were written for a proposed Hetzner Cloud host. They are kept as history. Do not follow them for the live Contabo server.

| Source | Hetzner-era statement | Status for the live host |
|---|---|---|
| `docs/operations/09-staging-hetzner-preparation.md` | Hetzner project, cloud firewall, 2 vCPU and 4 GB plan | OUTDATED / INCORRECT for the host (Contabo, 8 vCPU, 23 GiB). Still useful as a design rationale. |
| `docs/operations/12-owner-server-setup.md` | Operating user named matbakhy, package under /srv/matbakhy-preview/src, Hetzner console firewall, `sudo ufw status` "not used" | OUTDATED / INCORRECT: no matbakhy account exists on the live host (accounts above), the deployment directory is /srv/matbakhy-staging, and the firewall arrangement is NOT VERIFIED. |
| `deploy/web-preview/compose.4gb.yaml` and the 4 GB notes in `deploy/web-preview/README.md` | Memory ceilings for a 4 GB machine | OUTDATED / INCORRECT for sizing; whether the override is still selected is NOT VERIFIED. |
| `deploy/web-preview/.env.example` | GENERATED_DIR under /srv/matbakhy-preview | Differs from the live directory; see [section 7](#7-the-stale-srvmatbakhy-preview-copy). |

The current instructions are this page, [admin read-only verification](admin-readonly-verification.md), [deployment pipeline](deployment-pipeline.md) and [server rebuild guide](server-rebuild-guide.md).

## Known limitations and documentation gaps

- Container, volume, firewall, sudo and cron facts could not be read without privilege; the table in section 8 is the complete list of what remains open.
- The group of several /srv directories and the home directory of aiagent were not recorded.
- `docs/operations/12-owner-server-setup.md` still describes the matbakhy account and /srv/matbakhy-preview as the live arrangement; it conflicts with the observed host. The correction belongs to the documentation owner and is tracked on the coverage page ([infrastructure documentation coverage](../07-reference/infrastructure-documentation-coverage.md)).
- The purpose of /srv/backups and /srv/deployments is not documented anywhere in the repository.
