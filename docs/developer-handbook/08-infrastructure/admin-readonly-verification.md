# Admin read-only verification

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — a fixed list of read-only commands for an authorized infrastructure administrator; none of them has been run by the author of this page
> **Language / اللغة:** English · [العربية](admin-readonly-verification-ar.md)

The unprivileged account that produced the [Contabo server inventory](contabo-server-inventory.md) could not see everything. This page lists the smallest set of commands that closes the gaps, including the commit and image digests actually deployed (commands 17 and 18). Every command only reads. Results confirm or correct the inventory; they never authorize a change.

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on the server on 2026-10-21 by the unprivileged user aiagent. |
| VERIFIED IN CODE | Read in the repository (file named). |
| DOCUMENTED ONLY | A document or a tool's convention states it; not confirmed here. |
| OUTDATED / INCORRECT | Superseded or Hetzner-era. |
| NOT VERIFIED | Not observed; the commands below exist to verify it. |

## Rules for everyone

- Run these commands only as an authorized infrastructure administrator, on the Contabo VPS, under that person's own account. Agents never run them.
- aiagent must never receive sudo or Docker rights, not even temporarily, to run this list. An administrator runs the commands and returns sanitised output.
- All commands are read-only. If any command asks to change something, stop.
- Never run `docker inspect` without a `--format`, `docker compose config`, `docker compose exec` into a service, `cat` on .env, or anything that prints environment variables. They expose secrets.
- Never paste raw output into a public issue or pull request (the repository is public).
- Record the date and the administrator's role (not a personal name) with the sanitised result. Results older than the next deployment can be out of date.

## Placeholders

| Placeholder | Meaning |
|---|---|
| `<deploy-dir>` | /srv/matbakhy-staging/deploy/web-preview (VERIFIED LIVE as a path; the workflow deploys there, VERIFIED IN CODE in `.github/workflows/deploy-preview.yml`) |
| `<project>` | matbakhy-preview, the project name set in `deploy/web-preview/compose.yaml` (VERIFIED IN CODE) |
| `<caddy-volume>` | The name printed by command 8 for the Caddy data volume; with Compose naming it is expected to be `<project>_caddy-data` (DOCUMENTED ONLY, Compose convention) |
| `<mountpoint>` | The directory printed by command 9 |

## The commands

All commands run on the VPS, as an authorized administrator, with `sudo` unless the Docker rights of that administrator's account already apply. "RO" means read-only.

| # | Command | Run by | RO | Purpose and expected evidence | Do not paste, and likely failure |
|---|---|---|---|---|---|
| 1 | `sudo sshd -T` | administrator via sudo | yes | Effective SSH settings. Look for the lines permitrootlogin, passwordauthentication, kbdinteractiveauthentication, pubkeyauthentication, x11forwarding, allowusers and allowgroups. Expect passwordauthentication no; permitrootlogin shows the true effective value (the readable files were contradictory). | Paste only those lines. The full output also lists host key file paths; it holds no key material but is not needed. Failure: "command not found" means /usr/sbin is not on the path; use `sudo /usr/sbin/sshd -T`. |
| 2 | `sudo grep -E '^(PREVIEW_DOMAIN\|GENERATED_DIR\|BACKUP_DIR\|QA_ARTIFACT_DIR\|COMPOSE_OVERRIDE\|ALLOWED_CLIENT_IPS\|CUSTOMER_ALLOWED_IPS\|STAFF_ALLOWED_IPS)=' <deploy-dir>/.env` | administrator via sudo | yes | Confirms which directories and sizing override the live configuration uses, and which access classes are set. Expected from code: the key names exist (`deploy/web-preview/.env.example`); the values are NOT VERIFIED. | Never extend the pattern to other keys: everything else in .env is a secret. The three IP lists are access-control details; paste them only if the ticket is private. |
| 3 | `sudo ufw status verbose` and, when rule numbers are needed, `sudo ufw status numbered` | administrator via sudo | yes | Whether the host firewall is active and which inbound rules exist. Expect "Status: active" with 22, 80 and 443 allowed, or "inactive" if the provider firewall is used instead (NOT VERIFIED which). `verbose` shows defaults and rules without numbers; only `numbered` numbers them. | Rules may list source addresses of administrators. Paste rule numbers and ports, not addresses. |
| 4 | `sudo ls -l /etc/sudoers.d` | administrator via sudo | yes | Names of the sudoers drop-in files only. File names do not show what any account may do, and `visudo -c` only checks syntax. To establish effective privileges, also run `sudo -l -U ubuntu`, `sudo -l -U devops` and `sudo -l -U aiagent`, and compare with the verified sudo group (ubuntu, devops). Expect aiagent to be told it may not run sudo. | Never `cat` the files in a ticket. Return a sanitised summary of each `sudo -l -U` result (allowed command classes, for example "all commands" or "none"), not the raw rules. Use `sudo visudo -c` only if a syntax check is also wanted; it prints no rules. |
| 5 | `sudo docker ps --all --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'` and `sudo docker compose ls --all` | administrator with Docker rights | yes | Which Matbakhy containers exist and their health. Expect the services declared in `deploy/web-preview/compose.yaml` (proxy, postgres, identity, storage, api, loki, alloy, prometheus, alertmanager, alert-sink, node-exporter, grafana) under project matbakhy-preview. Images should be pinned by digest. Add `sudo docker images --digests` for the digests. | Images and names are not secret. Failure: "permission denied" means the account lacks Docker rights; do not add any account to the docker group for this. |
| 6 | `sudo docker network ls` and `sudo docker network inspect --format '{{.Name}} {{.Internal}}' $(sudo docker network ls -q)` | administrator with Docker rights | yes | Confirms the three project networks (edge, ops, internal) and that the internal one is flagged internal true, as `deploy/web-preview/compose.yaml` declares (VERIFIED IN CODE). Matches the three br-* bridges seen live. | No secrets are printed. The command substitution is part of one read-only line. |
| 7 | `sudo docker inspect --format '{{.Name}} mem={{.HostConfig.Memory}} cpu={{.HostConfig.NanoCpus}} restart={{.HostConfig.RestartPolicy.Name}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} restarts={{.RestartCount}}' $(sudo docker ps -aq)` | administrator with Docker rights | yes | Applied memory and CPU limits, restart policy and health per container. Compare with `mem_limit` and `cpus` in the compose file (VERIFIED IN CODE, for example api 2g and 1.5 CPUs). | The `--format` is mandatory. Plain `docker inspect` prints environment variables with passwords. |
| 8 | `sudo docker volume ls --filter label=com.docker.compose.project=<project>` | administrator with Docker rights | yes | Real volume names. Expect the ten volumes the compose file declares, with the project prefix. | Names only. |
| 9 | `sudo docker volume inspect --format '{{.Name}} {{.Mountpoint}}' <caddy-volume>` | administrator with Docker rights | yes | The host directory of the Caddy data volume (needed by command 10). Expected under /var/lib/docker/volumes (DOCUMENTED ONLY). | Path only. Do not read other volumes: postgres-data and storage-data hold application data. |
| 10 | `sudo find <mountpoint>/caddy/certificates -name '*.crt'` then, for each file listed, `sudo openssl x509 -noout -subject -issuer -dates -in <file>` | administrator via sudo | yes | Certificate subject, issuer and validity per host. Live evidence already shows the app host certificate from Let's Encrypt valid 2026-10-18 to 2027-01-16 (VERIFIED LIVE from outside); this confirms the certificates currently stored for every host and their validity. It does not show that automatic renewal succeeds; renewal stays NOT VERIFIED. The directory layout below caddy/ is Caddy's convention, not read in this repository (DOCUMENTED ONLY, NOT VERIFIED). | Never open files ending .key or anything under an acme account folder; they are private keys. Dates and subject names may be pasted. |
| 11 | `sudo find /srv -maxdepth 3 -printf '%M %u:%g %p\n'` | administrator via sudo | yes | Mode, owner and group of the /srv trees, including the group fields the unprivileged listing could not record, and the entries inside /srv/matbakhy-backups, /srv/backups and /srv/deployments (names only). | Names of backup files are not secret, but do not add `-exec cat`. Do not widen `-maxdepth` into the generated directory; its file names are enough. |
| 12 | `sudo crontab -l -u devops`, the same for `-u ubuntu` and `-u aiagent`, and `sudo ls -l /var/spool/cron/crontabs /etc/cron.d /etc/cron.daily /etc/cron.weekly` | administrator via sudo | yes | Whether a Matbakhy backup or prune job is scheduled, and for which account. Expect a `deploy.sh backup` entry if the practice in `docs/operations/12-owner-server-setup.md` (DOCUMENTED ONLY) was followed. Report each schedule separately as absent ("no crontab for <user>"), inaccessible (permission error) or not inspected (account or directory not checked). The spool listing shows file names only, not the jobs of other accounts, and system schedules in /etc/cron.* must be read or listed separately; timer units are covered by command 15. | Cron lines can embed credentials or tokens. Redact them locally before pasting anything; paste the schedule time and the script name only. Failure: "no crontab for devops" means that account has no crontab; it does not cover other accounts or system schedules. |
| 13 | `sudo journalctl -u ssh --since 2026-10-17 -g 'Accepted publickey' --no-pager` and `sudo wc -l /home/ubuntu/.ssh/authorized_keys /home/devops/.ssh/authorized_keys /home/aiagent/.ssh/authorized_keys` | administrator via sudo | yes | Which accounts accepted public-key logins (shows which accounts logged in by key; the repository secret STAGING_SSH_USER is unreadable) and how many keys each account trusts. These records show successful SSH logins only. To tie one to the deployment, match its timestamp to a known GitHub Actions run of the deployment workflow and report any remaining ambiguity (for example several accounts logging in at that time). The unit name `ssh` is the Ubuntu 24.04 name and matches the active ssh service (VERIFIED LIVE). | Do not paste client IP addresses or key fingerprints. Report the account name, count and date only. Never read the content of authorized_keys. A missing file is a valid answer. |
| 14 | `df -h` and `sudo docker system df` | administrator (Docker rights for the second) | yes | Free disk and how much Docker images, containers, volumes and build cache use. Live: 18 GB of 290 GB used on /dev/sda1. | Sizes only. |
| 15 | `systemctl list-timers --all --no-pager` | any administrator account | yes | Full timer list, including whether anything beyond the AI-supervisor recovery timer relates to Matbakhy. | Unit names only. |
| 16 | `sudo ss -tlnp` | administrator via sudo | yes | Process names behind the listeners, to confirm the proxy on 80 and 443, Grafana on 127.0.0.1:3000, and the owner of the Java process on 127.0.0.1 ports 18080 and 38189. | Paste process names and ports; process arguments may hold secrets, so do not use `ps` with full command lines in tickets. |
| 17 | `sudo -u devops <deploy-dir>/deploy.sh status` | administrator via sudo, as the account that owns the deploy directory (devops owns it, VERIFIED LIVE) | yes | The configured RELEASE_COMMIT, the pinned API_IMAGE and FRONT_IMAGE, the database schema version, container status and the last five release-log lines (VERIFIED IN CODE: function status and record_release in `deploy/web-preview/deploy.sh`; the header of that script calls status read-only). This is the configured and recorded release, not proof of what runs. | The script loads .env into its own process but prints only the fields above. Do not replace it with `cat .env`. Failure: "missing .env" means the account or directory is wrong; "none recorded" means no release-log file exists in BACKUP_DIR. |
| 18 | `sudo docker inspect --format '{{.Name}} {{.Config.Image}} {{.Image}} revision={{index .Config.Labels "org.opencontainers.image.revision"}}' $(sudo docker ps -q)` | administrator with Docker rights | yes | The image reference and image id each running container uses. Compare the digests with command 17. The build sets the revision label on both application images (VERIFIED IN CODE: `docker build --label org.opencontainers.image.revision` in `.github/workflows/deploy-preview.yml` and `.github/workflows/preview-images.yml`). That the labels exist on the running containers is NOT VERIFIED LIVE; an empty value would mean the running image was not built by these workflows (for example an older image), which is a finding to report, not proof of a source commit. | The `--format` is mandatory. Names, image references and ids are not secret. |

The pipe characters in command 2 are written escaped for the table. Type them unescaped (a single vertical bar) in the shell.

## What to return

Return the command number, the date, the sanitised output, and one line per item saying "matches the inventory" or "differs: <what>". Then the documentation owner updates [Contabo server inventory](contabo-server-inventory.md), changing the label of each confirmed item to VERIFIED LIVE with the date and the administrator's role.

## What this list deliberately does not do

- It does not restart, deploy, prune, renew, rotate or edit anything. Changes belong to [deployment pipeline](deployment-pipeline.md), [server rebuild guide](server-rebuild-guide.md) and [backup, restore and disaster recovery](backup-restore-disaster-recovery.md).
- It does not read secrets: .env values, Keycloak or database credentials, TLS and SSH private keys.
- It does not examine Tailscale; the tailnet is managed in its own admin console (NOT VERIFIED).

## Known limitations and documentation gaps

- The Caddy certificate directory layout (command 10) comes from Caddy's storage convention, not from this repository; if the path differs, search `<mountpoint>` for files ending .crt instead.
- Command 7 relies on Docker's inspect field names. An empty `health=` value is expected for a container that defines no healthcheck, whatever the Docker version, so read each field separately. Only when a field that every container defines (for example the restart policy) comes back empty may the Docker version differ from 29.9.1 (VERIFIED LIVE); then adjust the field rather than removing the format.
- Commands 5 and 7 do not show the source commit. Commands 17 and 18 add the configured release and the running image references, but a source commit is proven only by an image revision label on the running container (the build sets it, VERIFIED IN CODE; its presence on the running containers is NOT VERIFIED LIVE) or by a recorded workflow run; otherwise it stays NOT VERIFIED.
- Compose volume names with the project prefix are the usual Compose behaviour (DOCUMENTED ONLY); command 8 is the authority.
- Whether an off-server backup copy exists is not covered by any read-only command here; it is a question for the owner.
