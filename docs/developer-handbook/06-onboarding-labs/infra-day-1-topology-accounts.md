# Infrastructure Day 1 - Topology, Contabo host, Linux users and permissions

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — lab for reading the Contabo server inventory and the Preview topology without running any privileged command.
> **Language / اللغة:** English · [العربية](infra-day-1-topology-accounts-ar.md)

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 on the Contabo VPS by the unprivileged user aiagent, read-only, as recorded in the [server inventory](../08-infrastructure/contabo-server-inventory.md). |
| VERIFIED IN CODE | Read in this repository (file named). It shows intent, not necessarily what runs. |
| DOCUMENTED ONLY | A document states it; nothing confirms it. |
| OUTDATED / INCORRECT | Written for the proposed Hetzner host or superseded. |
| NOT VERIFIED | Not observed. The read-only administrator commands are on [admin read-only verification](../08-infrastructure/admin-readonly-verification.md). |

## Objectives

After this lab you can:

- Draw the Preview topology from memory: one Contabo VPS, one Docker Compose project, one public reverse proxy.
- State the verified host facts (Ubuntu 24.04.5 LTS, 8 vCPU, about 23 GiB RAM, deployment directory /srv/matbakhy-staging) and say which evidence class each belongs to.
- Read the account and group table and explain why the docker group is equivalent to root and why aiagent must never join it or the sudo group.
- Explain which parts of the server are NOT VERIFIED and who may verify them.
- Tell historical Hetzner text from the current Contabo instructions.

## Prerequisites

- The repository checked out (see [local environment](../05-developer-workflow/local-environment.md)). No server access is needed.
- Read [infrastructure and deployment](../03-architecture/infrastructure-and-deployment.md) and the [runtime architecture](../08-infrastructure/runtime-architecture.md) overview.
- You have read the [Contabo server inventory](../08-infrastructure/contabo-server-inventory.md) once, quickly.

## Rules for this lab

- This lab is read-only and repository-only. The trainee runs no command on the server, uses no sudo, does not switch Linux users, does not read any .env file and does not try to bypass a Linux permission.
- Anything that needs root or the Docker socket is performed only by an authorized infrastructure administrator, from the list on the [admin page](../08-infrastructure/admin-readonly-verification.md).
- No account, group or permission is created or changed by this lab. Rebuilding accounts is Day 5, and only on an isolated new server.

## Guided tasks

### Task 1. Draw the topology from the repository

| Item | Detail |
|---|---|
| Command | `sed -n 1,60p docs/developer-handbook/08-infrastructure/runtime-architecture.md` (or open the page in a Markdown viewer so the diagrams render) |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Prerequisite | repository checked out |
| Expected result | You see the network topology diagram: Internet, the proxy container publishing ports 80 and 443, and the internal services behind it |
| Verification | On paper, list the three Compose networks (edge, ops, internal) and which one has no route to the Internet. Check with `grep -n "internal: true" deploy/web-preview/compose.yaml` (VERIFIED IN CODE) |
| Likely failure | Mermaid does not render in a plain viewer. Use the GitHub page or any Markdown viewer with Mermaid support |

Write down which services publish a port to the host. The answer is in `deploy/web-preview/compose.yaml`: run `grep -n -A3 "ports:" deploy/web-preview/compose.yaml` and read each result. Compare with the live listening sockets in the inventory (public 22, 80 and 443 are VERIFIED LIVE). Note that some loopback-only ports on the server are not shown by the Compose file: 3080 is a Node process of the AI supervisor tooling, and a Java process listens on 18080 and 38189 whose owner is not established (VERIFIED LIVE as listeners only). Do not assume any of them is Matbakhy.

### Task 2. Read the verified host facts

| Item | Detail |
|---|---|
| Command | `sed -n '/^## 1. Host/,/^## 2. Accounts/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | A table of host facts |
| Verification | Fill in this table from the page, and add the evidence label of each row |

| Fact | Your answer | Label |
|---|---|---|
| Operating system | Ubuntu 24.04.5 LTS (Noble) | VERIFIED LIVE |
| CPU and RAM | 8 vCPU, about 23 GiB | VERIFIED LIVE |
| Deployment directory | /srv/matbakhy-staging | VERIFIED LIVE (the directory) and VERIFIED IN CODE (`.github/workflows/deploy-preview.yml` deploys there) |
| Is ufw active and with which rules? | not known | NOT VERIFIED (needs root) |
| Which containers run right now? | not known | NOT VERIFIED (needs the Docker socket) |

Likely failure: you write "verified" for something that is only in the compose file. Compose describes intent; only the live facts describe the server.

### Task 3. Read the accounts and groups

| Item | Detail |
|---|---|
| Command | `sed -n '/^## 2. Accounts and groups/,/^## 3. SSH policy/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Three accounts: ubuntu (uid 1000), devops (uid 1001), aiagent (uid 1002), with their groups |
| Verification | Answer the questions below without looking back |

Questions to answer in your notes:

1. Which accounts are in the sudo group? (ubuntu and devops, VERIFIED LIVE as group membership; what the sudo policy actually allows is NOT VERIFIED because /etc/sudoers.d is not readable)
2. Which accounts are in docker? (only devops, VERIFIED LIVE)
3. Why must aiagent never be added to docker? Membership of the docker group lets a user start a container that mounts the host filesystem, which is equivalent to root. This is general Linux and Docker behaviour (DOCUMENTED ONLY here) and the inventory states the rule that aiagent stays out of sudo and docker.
4. What does each account do operationally? The correct answer is: it is not established. Do not infer a role from an account name. The deployment SSH user is the repository secret STAGING_SSH_USER and its value is NOT VERIFIED.
5. Which group has no documented meaning in the repository? users (gid 100).

### Task 4. Read the directory ownership

| Item | Detail |
|---|---|
| Command | `sed -n '/^## 4. Directories/,/^## 5. Network/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | A table of /srv entries with owner, group and mode |
| Verification | For /srv/matbakhy-staging answer: owner, mode, who can write there, and what the .env file mode is. Expected: devops:devops, 755, by mode only the owner (and root) can write, .env is 600 |
| Likely failure | Confusing /srv/matbakhy-preview (an older, stale copy) with the current /srv/matbakhy-staging. The inventory section "The stale /srv/matbakhy-preview copy" explains the difference |

Notes to keep: the content of .env and of /srv/matbakhy-staging/generated was deliberately not read (VERIFIED LIVE as modes only). The backup directories /srv/matbakhy-backups and /srv/backups are 750 devops, so aiagent cannot list them; whether a backup exists and how fresh it is, is NOT VERIFIED.

### Task 5. Find where the repository describes the SSH policy

| Item | Detail |
|---|---|
| Command | `sed -n '/^## 3. SSH policy/,/^## 4. Directories/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | The readable SSH directives (PasswordAuthentication no, PubkeyAuthentication yes). They are readable directives only; the effective configuration is NOT VERIFIED |
| Verification | Explain why the effective PermitRootLogin value is NOT VERIFIED even though a readable line says no. Answer: a later drop-in can override an earlier line, one drop-in file is unreadable to the unprivileged user, and only the root command that prints the effective configuration can settle it (it is on the admin page) |

### Task 6. Separate Hetzner from Contabo

| Item | Detail |
|---|---|
| Command | `sed -n '/^## 9. Historical Hetzner/,/^## Known limitations/p' docs/developer-handbook/08-infrastructure/contabo-server-inventory.md` then `sed -n 1,12p docs/operations/09-staging-hetzner-preparation.md` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | The inventory lists the Hetzner-era statements that no longer apply; the operations document is written for a proposed Hetzner host |
| Verification | Complete this sentence: "The running Preview is on ___, so statements about ___ are historical (OUTDATED / INCORRECT)." Expected: Contabo; Hetzner |

### Task 7. Plan an administrator request

Without running anything, choose three items from the NOT VERIFIED summary in the inventory (section 8) and write a one-line request to an infrastructure administrator naming the command number on the [admin page](../08-infrastructure/admin-readonly-verification.md). Example shape: "Please run command <n> from the admin page and return sanitised output; do not include secrets." Do not send the request; do not run the command.

## Completion criteria

- [ ] I drew the topology and named the three Compose networks and the one that is internal.
- [ ] My host table has the right evidence label on every row and marks ufw and container state as NOT VERIFIED.
- [ ] I can state the group memberships of ubuntu, devops and aiagent and explain the docker and sudo risk.
- [ ] I did not claim any account's responsibility from its name.
- [ ] I can tell which directory is current (/srv/matbakhy-staging) and which is stale.
- [ ] I ran no privileged command, did not read any .env file and did not touch the server.

## Where to read next

[Contabo server inventory](../08-infrastructure/contabo-server-inventory.md), [admin read-only verification](../08-infrastructure/admin-readonly-verification.md), [runtime architecture](../08-infrastructure/runtime-architecture.md), [live Preview access](../05-developer-workflow/live-preview-access.md).

Next: [Infrastructure Day 2](infra-day-2-docker-services.md).

## Known limitations and documentation gaps

- The inventory is a snapshot of 2026-10-21. It does not update itself; re-verify before relying on it after a deployment or an administrator change.
- The role of each Linux account, the sudo rules, the effective SSH configuration, the firewall state and the container state are NOT VERIFIED and require an authorized administrator.
- The Hetzner-era documents remain in the repository for history; this lab points to them only to contrast them with the Contabo host.
