# Infrastructure Day 5 - Isolated rebuild exercise and backup-recovery assessment

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — lab that prepares an exercise on an isolated clean server (or the local stack) and a backup-recovery assessment; the exercise has NOT been run by the documentation authors.
> **Language / اللغة:** English · [العربية](infra-day-5-rebuild-recovery-ar.md)

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 on the Contabo VPS by the unprivileged user aiagent (see the [server inventory](../08-infrastructure/contabo-server-inventory.md)). |
| VERIFIED IN CODE | Read in this repository (file named). It shows intent, not necessarily what runs. |
| DOCUMENTED ONLY | A document or a tool's usual behaviour states it; nothing confirmed it here. |
| OUTDATED / INCORRECT | Written for the proposed Hetzner host or superseded. |
| NOT VERIFIED | Not observed. |

## Important statement: this exercise has not been run

The documentation authors did not run the Day 5 exercise. No clean server was built, no backup was taken or restored, and no recovery time was measured while writing this lab. Every expected result below is derived from reading the repository scripts (VERIFIED IN CODE) or from general tool behaviour (DOCUMENTED ONLY). When you run it, record your own observed results and mark anything you skip as NOT RUN.

## Objectives

- Prepare an isolated environment where a Preview-like stack can be built from scratch and destroyed without any risk to the live Preview.
- Practise recreating Linux accounts, groups, directory ownership and permissions in the pattern recorded for the Contabo host, on a throw-away server only.
- Practise a logical database backup and a verified restore into a new empty database with the repository scripts.
- Assess the real backup and recovery posture of the Preview: what exists in code, what is NOT VERIFIED on the live host, and what an administrator must confirm.

## Prerequisites

- Days 1 to 4 completed: [Day 1](infra-day-1-topology-accounts.md), [Day 2](infra-day-2-docker-services.md), [Day 3](infra-day-3-dns-https-keycloak.md), [Day 4](infra-day-4-cicd-operations.md).
- Read [server rebuild guide](../08-infrastructure/server-rebuild-guide.md) and [backup, restore and disaster recovery](../08-infrastructure/backup-restore-disaster-recovery.md).
- Choose ONE isolation option (below) and have written authorization for it from the infrastructure owner. A paid throw-away VPS spends money and needs that authorization; the local option does not.

## Isolation rules (mandatory)

1. Never run any exercise command against the live Preview, its domain 167-86-90-174.sslip.io, its IP, or the Contabo VPS.
2. The exercise server or stack must have its own domain or a local-only name, its own secrets generated for the exercise, and synthetic data only.
3. Do not reuse any secret, SSH key, .env file or backup from the live Preview. Do not copy a backup of real data into the exercise (backups hold personal data, see the header of `scripts/db-backup.sh`).
4. Do not point the exercise at the repository secrets STAGING_SSH_HOST, STAGING_SSH_USER, STAGING_SSH_KEY or STAGING_SSH_HOST_PUBKEY. Do not run the deployment workflow for the exercise.
5. Destroy the throw-away server or stack at the end and record the destruction.
6. The trainee still never uses sudo, Docker or another user on the live host. On the exercise server the trainee is the administrator of that disposable machine only.

## Isolation options

| Option | Where | Needs | Covers | Does not cover |
|---|---|---|---|---|
| A. Local stack | your laptop, Docker | Docker, the repository | Services start, database backup and restore scripts, smoke | Linux accounts, SSH, public DNS, certificates, Contabo specifics |
| B. Throw-away VPS | a new server you are authorized to create and delete, with its own domain | A fresh Ubuntu 24.04 server, a test domain, owner authorization | The full rebuild from the [server rebuild guide](../08-infrastructure/server-rebuild-guide.md) including accounts, firewall, DNS, certificate and deployment | Nothing about the live host's unknown state |

Option A: the repository provides `scripts/dev.sh` with subcommands doctor, up, smoke, down, reset and verify (VERIFIED IN CODE). `scripts/dev.sh verify` runs doctor, up and smoke in an isolated throw-away compose project and cleans up. This is a local development stack, not the Preview stack: it does not use `deploy/web-preview/compose.yaml`.

## Guided tasks

### Task 1. Check the local prerequisites (Option A)

| Item | Detail |
|---|---|
| Command | `scripts/dev.sh doctor` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only checks (it only tests that tools exist) |
| Prerequisite | repository checked out |
| Expected result | A line "ok: ..." for each prerequisite, or "MISSING: ..." with a hint (VERIFIED IN CODE: the doctor function in `scripts/dev.sh`) |
| Verification | Exit status 0 means all prerequisites are present |
| Likely failure | MISSING: docker daemon. Start Docker and rerun. Do not install anything on a server |

### Task 2. Boot and smoke a throw-away stack (Option A)

| Item | Detail |
|---|---|
| Command | `scripts/dev.sh verify` |
| Machine | your laptop |
| Linux user | your own (must be able to use Docker locally) |
| Kind | state-changing on your laptop only: builds the API, starts containers in a throw-away compose project, then removes them. WARNING: it uses local Docker resources and time; never run it on the Contabo VPS |
| Prerequisite | Task 1 passed |
| Expected result | Doctor, up and smoke succeed, then cleanup (VERIFIED IN CODE: header of `scripts/dev.sh`) |
| Verification | The command exits 0 and `docker ps` on your laptop shows no leftover containers of that project |
| Likely failure | A port already in use or a build failure: read the first error, fix locally, rerun. If you do not run it, record NOT RUN |

### Task 3. Rebuild exercise on a throw-away server (Option B), accounts and permissions

Follow the [server rebuild guide](../08-infrastructure/server-rebuild-guide.md) for the full ordered procedure; it is the authority for the exact steps. This task is the account and permission part only, as an exercise to compare against the recorded live state. The fenced block is an outline for a disposable server and uses placeholders; it is not for the live host. The block is hypothetical and not run by the authors.

```bash
# Throw-away server only. Run as the administrator of THAT server. Not for the live Preview.
# 1. Accounts recorded on the live host (names are reproduced from the inventory; their roles are not established):
sudo adduser --disabled-password --gecos "" --uid 1000 ubuntu    # may already exist on a cloud image
sudo adduser --disabled-password --gecos "" --uid 1001 devops
sudo adduser --disabled-password --gecos "" --uid 1002 aiagent
# 2. Group memberships recorded on the live host:
sudo usermod -aG sudo ubuntu
sudo usermod -aG sudo,docker,users devops
sudo usermod -aG users aiagent          # NO sudo and NO docker for aiagent, ever
# 3. SSH: key-only login for each account that needs it, using public keys you generate for the exercise
sudo install -d -m 700 -o <user> -g <user> /home/<user>/.ssh
# append the exercise public key to /home/<user>/.ssh/authorized_keys, then: chmod 600 and chown <user>:<user>
# 4. Deployment directory ownership and modes recorded on the live host:
sudo install -d -m 755 -o devops -g devops /srv/matbakhy-staging
# the .env file inside deploy/web-preview is created by deploy.sh init-secrets with mode 600 (see the rebuild guide)
```

Before using the outline, confirm each line against the [Contabo server inventory](../08-infrastructure/contabo-server-inventory.md) and the rebuild guide. The lines that mirror the recorded accounts are reproduction of observed state, not a recommendation to give devops sudo or docker; deciding whether a new server should use the same pattern is an owner and administrator decision. The numeric uids are the recorded ones (VERIFIED LIVE), which only matter if file ownership must match a restored copy.

Verification on the exercise server, read-only:

| Command | Expected result |
|---|---|
| `id aiagent` | groups aiagent and users only; no sudo, no docker |
| `getent group docker` | members: devops only |
| `getent group sudo` | members: ubuntu and devops |
| `ls -ld /srv/matbakhy-staging` | 755 devops:devops |
| `ssh -o PasswordAuthentication=no <user>@<exercise-host> true` | works with the exercise key; password login is refused (the live policy is key-only, VERIFIED LIVE, with the root login value NOT VERIFIED) |

Likely failure: the SSH daemon accepts passwords. Fix the exercise server's SSH configuration and check the effective value with the daemon's own test output on that server.

### Task 4. Logical database backup and verified restore (either option)

Read `scripts/db-backup.sh` and `scripts/db-restore.sh` first (VERIFIED IN CODE). Use only a throw-away database holding synthetic data.

| Step | Command (placeholders) | Machine and user | Kind | Expected result | Verification | Likely failure |
|---|---|---|---|---|---|---|
| 1 | Set `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE` for the throw-away database; set the password with the standard libpq mechanism | your laptop or the exercise server | environment only | Variables point to the throw-away database and nothing else | `echo $PGHOST` shows the throw-away host | Pointing at a real database. Stop and check before every run |
| 2 | `scripts/db-backup.sh <backup-dir-outside-git>` | same | state-changing: reads the database, writes files | A dump, a checksum file and a manifest with row counts, and a line "backup ... written in Ns" | `ls <backup-dir>` shows three files; the checksum file lists the dump | "refusing: ... inside a Git working tree": choose a directory outside the repository |
| 3 | `RESTORE_CONFIRM_TARGET=<new-db> scripts/db-restore.sh <dump> <new-db>` | same | state-changing: creates a new database and restores into it | Exit 0 means reconciled; 1 reconciliation failed; 2 refused (VERIFIED IN CODE) | Compare the restore output with the manifest row counts | "refusing: set RESTORE_CONFIRM_TARGET": set the variable to the same name as the target. Never restore over a database that already has tables; the script refuses |
| 4 | Drop the throw-away databases and delete the backup files | same | state-changing: destructive, throw-away only | Nothing remains | Listing shows nothing | None expected |

The script comments state the rule that a backup is not accepted until a restore has been reconciled (VERIFIED IN CODE: header of `scripts/db-restore.sh`). The server-side wrapper is `./deploy.sh backup` (also archives the image storage) and `./deploy.sh restore-db <dump>`; those run on a Preview-like server by an authorized operator and are exercised only on the exercise server in Option B.

### Task 5. Backup-recovery assessment (reading and reasoning)

Fill in this table from the repository and the live facts. No command runs on the live host.

| Question | Where the answer comes from | Your answer | Evidence label |
|---|---|---|---|
| Is there a backup procedure? | `deploy/web-preview/deploy.sh` subcommands backup and restore-db; `scripts/db-backup.sh`; `scripts/db-restore.sh` | Yes, in code | VERIFIED IN CODE |
| Does `./deploy.sh up` take a database backup before changing anything? | the usage header of `deploy/web-preview/deploy.sh` ("back up the database") | Documented in the header | VERIFIED IN CODE |
| Does a scheduled backup job exist on the live host? | live cron listing | No Matbakhy backup job was visible to the unprivileged account; other users' crontabs were not readable | NOT VERIFIED |
| Do backups exist, and how fresh are they? | /srv/matbakhy-backups is 750 devops:devops and not listable by aiagent | unknown | NOT VERIFIED |
| Has a restore been proven on the live data? | no evidence in this handbook | unknown | NOT VERIFIED |
| Where are backups stored relative to the server? | the directories are on the same VPS disk (/srv/...); off-server copies are not documented in this handbook | unknown whether off-server | NOT VERIFIED |
| Is the image storage backed up? | `./deploy.sh backup` archives it (briefly stopping the storage service) | Procedure exists | VERIFIED IN CODE |
| Are certificates and keys recoverable? | Caddy re-issues certificates when its data volume is empty; subject to Let's Encrypt rate limits | Re-issue is possible | DOCUMENTED ONLY |
| Are the secrets recoverable? | The server .env holds them; whether an off-server record exists is not stated | unknown | NOT VERIFIED |
| What recovery time and data-loss window apply? | no measured value exists | none claimed | NOT VERIFIED |

Then write a one-page assessment with three parts: what is proven (only what is VERIFIED IN CODE as capability), what is unproven (everything NOT VERIFIED), and the minimum read-only requests for an administrator (select commands from the [admin page](../08-infrastructure/admin-readonly-verification.md) and the decision tables of [backup, restore and disaster recovery](../08-infrastructure/backup-restore-disaster-recovery.md)). Do not state that the Preview is recoverable; state what would have to be demonstrated.

### Task 6. Close the exercise

Destroy the throw-away server or run `scripts/dev.sh down` (or `scripts/dev.sh reset` for the local data volume; the reset refuses non-local env files, VERIFIED IN CODE). Write a closing note: what you ran, what you skipped (NOT RUN), what differed from the guide, and which pages need correction. Submit corrections through the normal pull request process with the Documentation Impact declaration, see the [documentation policy](../05-developer-workflow/documentation-policy.md).

## Verification checkpoints

| Checkpoint | Pass when |
|---|---|
| Isolation | You can show that no command targeted the live host, domain or secrets |
| Accounts | Option B only: the verification table matches the recorded groups, and aiagent has neither sudo nor docker |
| Backup and restore | The dump, checksum and manifest exist, the restore ended with exit 0, or you recorded NOT RUN |
| Assessment | Every unknown is labelled NOT VERIFIED and has an administrator request attached |
| Cleanup | The exercise environment is destroyed and the note says so |

## Completion criteria

- [ ] I understood the exercise is prepared but was not run by the authors, and I recorded my own run honestly.
- [ ] I used an isolated environment with its own secrets and synthetic data only.
- [ ] I can explain why a backup counts only after a reconciled restore.
- [ ] I produced the backup-recovery assessment without claiming recoverability that no one has demonstrated.
- [ ] I did not give aiagent sudo or Docker access anywhere, and did not change the live host.

## Where to read next

[Server rebuild guide](../08-infrastructure/server-rebuild-guide.md), [backup, restore and disaster recovery](../08-infrastructure/backup-restore-disaster-recovery.md), [admin read-only verification](../08-infrastructure/admin-readonly-verification.md), [infrastructure documentation coverage](../07-reference/infrastructure-documentation-coverage.md).

Return to [Infrastructure Day 1](infra-day-1-topology-accounts.md) for the start of this lab series.

## Known limitations and documentation gaps

- The exercise and every command in the outline have not been run by the authors; the account outline mirrors the recorded state and must be checked against the rebuild guide.
- The live backup schedule, backup contents, off-server copies, secret recovery and any recovery time are NOT VERIFIED.
- The local stack in Option A is not the Preview stack, so it does not prove that the Preview compose file or the Caddy certificate flow work on a clean server.
- The Hetzner-era staging documents describe a different host; do not follow them for the Contabo rebuild.
