# Infrastructure Day 2 - Docker Compose services, networking and persistent volumes

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — lab for reading the Preview Compose definition; live Docker inspection is for an authorized administrator only.
> **Language / اللغة:** English · [العربية](infra-day-2-docker-services-ar.md)

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 on the Contabo VPS by the unprivileged user aiagent (see the [server inventory](../08-infrastructure/contabo-server-inventory.md)). |
| VERIFIED IN CODE | Read in this repository (file named). It shows intent, not necessarily what runs. |
| DOCUMENTED ONLY | A document states it; nothing confirms it. |
| OUTDATED / INCORRECT | Written for the proposed Hetzner host or superseded. |
| NOT VERIFIED | Not observed. The administrator commands are on [admin read-only verification](../08-infrastructure/admin-readonly-verification.md). |

Docker runtime configuration of the live Preview (running containers, images in use, volumes on disk, resource limits as applied, the contents of .env) is NOT VERIFIED LIVE. The unprivileged account cannot read the Docker socket. This lab therefore works from the Compose file in the repository.

## Objectives

- Read `deploy/web-preview/compose.yaml` and list every service, its image source, its network, its volumes and its published ports.
- Explain the three networks (edge, ops, internal) and why the database and identity provider have no route to the Internet.
- Map each named volume to the data it holds and say what is lost if that volume is lost.
- Explain why the images are pinned and why nothing is built on the server.
- Know what live checks need an administrator and which commands are safe to read in the repository.

## Prerequisites

- Day 1 completed: [Infrastructure Day 1](infra-day-1-topology-accounts.md).
- Read [runtime architecture](../08-infrastructure/runtime-architecture.md) sections on container topology, database persistence and media storage.
- Docker is NOT needed for the repository-only tasks. Do not install or run anything on the Contabo VPS.

## Rules for this lab

- All tasks read files in the repository on your laptop. Nothing is started.
- Do not run `docker`, `docker compose config` (it expands secrets from .env), or any command on the live server. Live inspection is listed on the [admin page](../08-infrastructure/admin-readonly-verification.md) and is run only by an authorized administrator.
- Do not read any .env file. The file `deploy/web-preview/.env.example` is the public template with no secret values and is allowed.

## Guided tasks

### Task 1. List the services

| Item | Detail |
|---|---|
| Command | `grep -nE '^  [a-z-]+:$' deploy/web-preview/compose.yaml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Prerequisite | repository checked out |
| Expected result | The service names (and the top-level keys networks and volumes mixed in): proxy, postgres, identity, storage, api, loki, alloy, prometheus, alertmanager, alert-sink, node-exporter, grafana |
| Verification | Count 12 services. The project name is on the line `name: matbakhy-preview` near the top of the file |
| Likely failure | The output contains other two-space keys; ignore keys that are not services |

Fill in this table from the file (VERIFIED IN CODE: `deploy/web-preview/compose.yaml`). The first rows are given as a guide; complete the rest yourself.

| Service | Image source | Networks | Persistent volume | Published port |
|---|---|---|---|---|
| proxy | FRONT_IMAGE from .env (Caddy with both web builds, built by CI) | edge, internal | caddy-data, caddy-config | 80 and 443 on all addresses |
| postgres | postgres:16.15-alpine pinned by digest | internal | postgres-data | none |
| identity | keycloak 26.8.0 pinned by digest | internal | complete from the file | none |
| storage | SeaweedFS pinned by digest | internal | storage-data | none |
| api | API_IMAGE from .env (built by CI) | internal | complete from the file | none |
| grafana | grafana 12.1.1 pinned by digest | internal, ops | grafana-data | 127.0.0.1:3000 only |
| the other six | observability components | complete from the file | complete from the file | none |

### Task 2. Understand the three networks

| Item | Detail |
|---|---|
| Command | `sed -n '/^networks:/,/^volumes:/p' deploy/web-preview/compose.yaml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | edge, ops and internal; only internal carries the setting `internal: true` |
| Verification | Answer: which service is on edge? Which is on ops? What does `internal: true` do? |

Expected answers: the proxy is on edge and internal; grafana is on ops and internal; a Compose network marked internal has no route out to the Internet and is not reachable from outside the Docker host (standard Docker behaviour, DOCUMENTED ONLY). The comment in the file states the intent: only the proxy is published. Whether the live Docker networks match is NOT VERIFIED LIVE; the live facts only show that three bridge networks exist on the host (VERIFIED LIVE: interfaces br-* with the ranges 172.18, 172.19 and 172.20).

### Task 3. Map volumes to data and to loss impact

| Item | Detail |
|---|---|
| Command | `sed -n '/^volumes:/,$p' deploy/web-preview/compose.yaml` and `grep -n "postgres-data\|storage-data\|caddy-data" deploy/web-preview/compose.yaml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Ten named volumes |
| Verification | Complete the table below; compare with the [runtime architecture](../08-infrastructure/runtime-architecture.md) persistence sections |

| Volume | What is stored (from the mount path) | What is lost if it is lost |
|---|---|---|
| postgres-data | PostgreSQL data directory | All application data: orders, accounts, catalogue. Recoverable only from a verified backup |
| storage-data | SeaweedFS data directory | Uploaded images. Recoverable only from the storage archive made by the backup command |
| caddy-data | Caddy data directory, where certificates and keys are kept (DOCUMENTED ONLY: Caddy convention) | Certificates; Caddy requests new ones on start, subject to Let's Encrypt rate limits, see [DNS and HTTPS](../08-infrastructure/dns-https-sslip-io.md) |
| loki-data, prometheus-data, alertmanager-data, alloy-data, alert-sink-data, grafana-data | Logs, metrics and dashboards state | History only; the application keeps working |

Where the volumes sit on the VPS disk and how large they are is NOT VERIFIED LIVE; an administrator can read that with commands on the admin page.

### Task 4. Which files are mounted from the host directory

| Item | Detail |
|---|---|
| Command | `grep -nE '^\s+- (\./|\.\./|/|\$\{)' deploy/web-preview/compose.yaml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Bind mounts: observability configuration files, `infra/db/reconcile.sql`, the generated flags directory mounted read-only into the proxy, host paths used by alloy and node-exporter, and a few more |
| Verification | For each mount, say whether it is read-only (suffix :ro). Explain why the proxy gets its flags directory read-only |

Why it matters: the flags directory switch controls whether the Keycloak admin console host answers (see the comments in `deploy/web-preview/Caddyfile`). Day 3 covers the routing. The default value of the directory variable in the file points at /srv/matbakhy-preview/generated, the stale copy, so the real value comes from the server .env, which is NOT VERIFIED.

### Task 5. Resource limits and the 4 GB override

| Item | Detail |
|---|---|
| Command | `grep -nE 'mem_limit|cpus' deploy/web-preview/compose.yaml` then `sed -n 1,8p deploy/web-preview/compose.4gb.yaml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Per-service memory ceilings in the main file, and a second file written for a 2 vCPU, 4 GB Hetzner host |
| Verification | Answer: is compose.4gb.yaml in use on the Contabo host? Correct answer: NOT VERIFIED. It is only used when the server .env sets COMPOSE_OVERRIDE (VERIFIED IN CODE: comment in the file and `deploy/web-preview/deploy.sh`). The live host has 8 vCPU and about 23 GiB RAM (VERIFIED LIVE), so a 4 GB sizing file is historical (OUTDATED / INCORRECT for the host size) unless an administrator confirms it is set |

### Task 6. Pinned images and no build on the server

| Item | Detail |
|---|---|
| Command | `grep -n "image:" deploy/web-preview/compose.yaml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Third-party images carry a version and a sha256 digest; our two images come from variables |
| Verification | Explain how API_IMAGE and FRONT_IMAGE get their values. Answer: the deployment workflow writes them into the server .env after CI built and published the images, see [deployment pipeline](../08-infrastructure/deployment-pipeline.md) (VERIFIED IN CODE: `.github/workflows/deploy-preview.yml`) |

### Task 7. The read-only operator view

| Item | Detail |
|---|---|
| Command | `sed -n 1,20p deploy/web-preview/deploy.sh` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only (reading the file) |
| Expected result | The list of subcommands: init-secrets, render, up, seed, smoke, backup, restore-db, prune-qa, status, rollback-plan, admin-console, e2e-reset |
| Verification | Mark which subcommands only read (status and rollback-plan say so in the header; smoke is documented as a smoke test) and which change state (up, seed, backup, restore-db, e2e-reset, prune-qa with --apply, admin-console). Running any of them is the job of an authorized operator on the server, never part of this lab |

Hand-off to an administrator: to see the real container list, images and volumes, ask an authorized administrator to run the Docker commands on the [admin page](../08-infrastructure/admin-readonly-verification.md) and return sanitised output. Never ask for raw `docker inspect` or `docker compose config` output, which print secrets.

## Verification checkpoints

| Checkpoint | Pass when |
|---|---|
| Services table | All 12 services have image source, network, volume and port filled in, and every entry is labelled VERIFIED IN CODE |
| Networks | You can explain edge, ops and internal in two sentences each |
| Volumes | Each of the ten volumes has a loss-impact sentence |
| Live state | Every statement about running containers is marked NOT VERIFIED LIVE |

## Completion criteria

- [ ] I listed the services, networks and volumes from the Compose file without running Docker.
- [ ] I can say which services publish a port and why only the proxy is public.
- [ ] I can name the two volumes whose loss means data loss and how they are backed up.
- [ ] I treated every live Docker claim as NOT VERIFIED LIVE.
- [ ] I did not read any .env file and did not touch the server.

## Where to read next

[Runtime architecture](../08-infrastructure/runtime-architecture.md), [backup, restore and disaster recovery](../08-infrastructure/backup-restore-disaster-recovery.md), [admin read-only verification](../08-infrastructure/admin-readonly-verification.md).

Next: [Infrastructure Day 3](infra-day-3-dns-https-keycloak.md).

## Known limitations and documentation gaps

- No Docker state of the live host has been observed; everything about containers comes from the repository.
- Whether the live host uses the 4 GB override, what the real GENERATED_DIR is, and which image digests run are NOT VERIFIED.
- Backup schedule and backup freshness on the live host are NOT VERIFIED; no Matbakhy backup scheduled job was visible to the unprivileged account.
