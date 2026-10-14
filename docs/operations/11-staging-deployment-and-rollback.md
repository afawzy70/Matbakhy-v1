# Staging: deployment workflow, smoke tests, rollback and the remote E2E window

Status: **prepared, not deployed.** Written 2026-10-14 for the Hetzner 2 vCPU / 4 GB server (PREVIEW profile, owner-approved). No server access is assumed and no remote test has been run. Everything marked "rehearsed" ran only on the agent's machine ([rehearsal results](#6-what-was-rehearsed-on-the-agents-machine-and-what-was-not)); it is not evidence about the Hetzner host. What the owner does on the server: [12](12-owner-server-setup.md).

## 1. Principles

- **Immutable images.** Our two images are built once, in CI, at one commit and referenced by digest (`name@sha256:…`). Third-party images are pinned by digest in `compose.yaml`. Nothing is built on the server; `deploy.sh up` refuses a tag without a digest, a short SHA and any placeholder.
- **One change at a time, with a backup first.** `up` takes a database backup (restored into a scratch database and reconciled) before it touches a running stack, then pulls only missing images, starts in dependency order and waits for health.
- **Forward-only database.** Flyway never goes back. Rollback is "previous images on a schema they still understand", or a restore (section 4).
- **A recorded history.** Every successful `up` appends a line to `$BACKUP_DIR/releases.log`: time, release commit, both image digests, schema version before and after, and the pre-deploy backup. `./deploy.sh rollback-plan` reads it.
- **Deny by default.** Nothing is public until `CUSTOMER_ALLOWED_IPS` says so; privileged operations (`ops.`, `/api/v1/ops/*`, `/api/v1/cook/*`) follow `STAFF_ALLOWED_IPS` only.

## 2. Release workflow

| # | Step | Who | Evidence / command |
|---|---|---|---|
| 1 | The release commit has a green Product CI run and a QA candidate with the six local checks | agent | `verification/runs/<candidate>/evidence.json` |
| 2 | Migrations since the previous release are listed with their class | agent | `python3 scripts/check_migration_compat.py --list --baseline <previous schema version>` (CI fails a breaking or tightening migration without a reason) |
| 3 | Build and push the two images at that commit | **owner approves**, then dispatches `preview-images.yml` (it also refuses a commit without a green CI) | job summary: `RELEASE_COMMIT`, `API_IMAGE`, `FRONT_IMAGE` (digests) |
| 4 | Put the three values into the server's `.env`; check `COMPOSE_OVERRIDE=compose.4gb.yaml` | owner | `./deploy.sh status` shows configured against running |
| 5 | Deploy | owner | `./deploy.sh up` (backup, pull missing, start, health, buckets, release log) |
| 6 | Read-only smoke | owner | `./deploy.sh smoke` |
| 7 | Browser smoke with a simulated deposit order | owner (own computer) | `apps/operations-web`: `pnpm exec playwright test -c playwright.staging.config.ts` with the `STAGING_*` variables |
| 8 | Record the outputs; first time only: AUD-01, LOG-01, OPS-01..03, TC-057 | owner runs, agent prepares/records | stay NOT_RUN until real outputs exist |
| 9 | If anything fails: stop, `./deploy.sh rollback-plan`, decide (section 4) | owner | |

The first deploy runs `./deploy.sh seed` once (demo catalogue, accounts, a clearly fake deposit recipient).

## 3. Smoke tests (what each one covers)

| Area | Read-only `smoke.sh` | Browser smoke `e2e-staging` |
|---|---|---|
| Customer web | served over HTTPS; HSTS, nosniff and frame-deny headers; the demo notice is in the build | Arabic RTL, the demo notice visible (Arabic and English), a sign-in link |
| Operations web | served (or refused, from outside the staff class: `SMOKE_OPS=skip`); demo notice in the build | the kitchen signs in and sees the workspace; the demo notice is visible |
| Backend API | public configuration reports `PREVIEW`, `demo: true`; discovery lists items; no actuator, no metrics | configuration, profile, meal list, quote, order |
| Authentication | issuer `https://id.<domain>/realms/matbakhy`; sign-in redirects there and back; Keycloak admin console and the master realm answer 404; profile, staff and kitchen APIs refuse a request without a session | sign-in through the identity provider as a customer and as a kitchen |
| Simulated order and deposit | not applicable (read-only) | a deposit quote (50%, demo recipient), an order, the customer's transfer **report**, the kitchen cannot prepare before it reports receipt, then can; no text claims bank verification; the customer and the kitchen are refused on staff operations |

Both leave no secret in their output. The browser smoke creates one demo order per run in a database that holds only synthetic data; it needs no database access.

## 4. Rollback and database migration compatibility

### 4.1 Policy: expand, then contract; the previous release must keep working

Flyway migrations are forward-only. So every migration after V28 must be **expand-only**: new tables, new nullable or defaulted columns, new indexes, wider constraints. Anything else needs a one-line reason in the file and is a conscious decision at release time:

| Class | Examples | Meaning for a rollback |
|---|---|---|
| expand-only (default) | `CREATE TABLE`, `ADD COLUMN … NULL` or `DEFAULT`, `CREATE INDEX`, wider `CHECK` | the previous API keeps working on the new schema: images can be rolled back |
| `-- rollback-compat: tightening <reason>` | new unique index or constraint on an existing table, `SET NOT NULL` | the previous API still works for what it does today, but writes it makes may be refused: rehearse before relying on an image-only rollback |
| `-- rollback-compat: breaking <reason>` | `DROP`, `RENAME`, column type change, `NOT NULL` column without a default, `DELETE` | the previous API will not work: roll back by restoring the pre-deploy backup |

`scripts/check_migration_compat.py` enforces the annotation (CI: the guardrail tests). It is a review aid, not a proof. Contract steps (dropping what no release uses any more) wait one full release after the code stopped using it. V1 to V28 predate the policy; `--all --list` shows how they would classify.

### 4.2 Deciding what to do

| Situation | Action |
|---|---|
| The new release is bad; **no migration** since the last release | image-only rollback: `./deploy.sh rollback-plan`, put the printed three values into `.env`, `./deploy.sh up`. Data is kept |
| The new release is bad; migrations since then are all **expand-only** | image-only rollback as above. The older API ignores migrations it does not know (Flyway's default for applied migrations newer than the code). Run the smoke tests afterwards |
| The new release is bad; a migration is **tightening** | rehearse the older image on a copy first (section 6 shows how); if writes fail, use the restore path |
| A migration is **breaking**, or data is damaged | restore the pre-deploy backup (4.3). Data written after that backup is **lost**; say so to the testers |
| The deploy itself failed half-way | `./deploy.sh status`, read the API and identity logs, fix forward or roll back by the rows above. `up` is repeatable |

`./deploy.sh rollback-plan [N]` only prints. For the release N entries before the last one it shows the commit, the three `.env` values, the current schema version and a verdict (schema unchanged: image-only; schema moved: which migrations, the expand-only condition, and the backup to restore).

### 4.3 Restore path (breaking migration or damaged data)

1. Stop writes: `docker compose stop api`.
2. Restore the backup named by `rollback-plan` into a scratch database: `./deploy.sh restore-db /backups/db-<stamp>/matbakhy-<stamp>.dump`. It restores into `matbakhy_restore` and reconciles orders, allocations and the ledger.
3. Swap the databases (nobody connected): `docker compose exec -T postgres psql -U matbakhy -d postgres -c "ALTER DATABASE matbakhy RENAME TO matbakhy_failed" -c "ALTER DATABASE matbakhy_restore RENAME TO matbakhy"`.
4. Put the previous images into `.env` and `./deploy.sh up` (the API re-applies the runtime role's grants on start).
5. Smoke tests. Keep `matbakhy_failed` until the cause is understood, then drop it deliberately.

Identity data (Keycloak, its own database) is not touched by either path. Image storage is restored from the storage archive that `./deploy.sh backup` writes next to the dump.

## 5. Remote browser E2E window

The six-workflow browser suite (`apps/operations-web/e2e`) proves the real journeys, but it starts from an **empty application database** and prepares its synthetic data itself. Run against a deployed preview it works like this, and only like this:

1. **No database access from the runner.** The suite sends a fixture *name* and parameters on the standard input of `E2E_FIXTURE_CMD`; it never sends SQL. On the server, `e2e-fixture.sh` maps the name to a checked-in file in `e2e-fixtures/`, accepts only the parameters that file declares (each once, at most 512 characters, no control characters) and runs it through `psql -v` variables (never spliced into the SQL). Fixtures are plain `SELECT`/`INSERT`/`UPDATE` (a guardrail test refuses DDL, `DELETE`, `TRUNCATE`, `COPY`).
2. **Not a network service.** `E2E_FIXTURE_CMD` is `["ssh","-i","<key>","matbakhy@<server>","/srv/matbakhy-preview/src/deploy/web-preview/e2e-fixture.sh"]`; on the server that key is restricted to this one command in `authorized_keys`: `command="…/e2e-fixture.sh",restrict ssh-ed25519 AAAA… e2e-runner`. Nothing listens for it and nothing public reaches it.
3. **Off unless the host says so.** The script refuses (exit 3) unless the marker file `.e2e-enabled` exists next to it. The owner creates it for the window and removes it afterwards.
4. **A window, not a mode.** Before the run the owner narrows public access (`CUSTOMER_ALLOWED_IPS` unset, `./deploy.sh up`) and runs `E2E_RESET_CONFIRM=<domain> ./deploy.sh e2e-reset`. It refuses without the marker, with public customer hosts, or without the typed confirmation; it takes a verified backup, stops the API, recreates the **empty** `matbakhy` database (all application data is wiped; Keycloak users stay) and restarts the API (migrations, synthetic policy and zones). Testers' data is gone: never during a tester session.
5. **Run.** From the owner's computer, with `E2E_API_URL`, `E2E_OPS_URL`, `E2E_CUSTOMER_URL` (the https hosts), `E2E_USER_PASSWORD` (`PREVIEW_USER_PASSWORD`), `E2E_FIXTURE_CMD`, and `E2E_USERS` mapping the suite's four logical accounts to the preview realm: `{"cook-a":{"username":"preview-cook-1","subject":"7d3f0a52-1c0e-4b8e-9a3e-0000000000a1"},"customer-a":{"username":"preview-customer-1","subject":"7d3f0a52-1c0e-4b8e-9a3e-000000000c01"},"customer-b":{"username":"preview-customer-2","subject":"7d3f0a52-1c0e-4b8e-9a3e-000000000c02"},"ops-a":{"username":"preview-ops-2","subject":"7d3f0a52-1c0e-4b8e-9a3e-0000000000f2"}}` (`preview-ops-2` is a staff-style account without any grant; `preview-ops-1` is the bootstrap administrator and would already hold staff grants, which the suite does not expect), then `pnpm exec playwright test` in `apps/operations-web`.
6. **Clean up.** The suite leaves its synthetic users, orders and kitchen behind, and the signed-in preview accounts now exist under their login-created ids, so `deploy.sh seed` would collide with them. Wipe again with the same guarded command (`E2E_RESET_CONFIRM=<domain> ./deploy.sh e2e-reset`), then remove the marker, then `./deploy.sh seed` restores the demo catalogue, then re-widen access if it was public and run `./deploy.sh smoke`. Order matters: the marker is removed only after the second reset.

Not verified: the SSH forced-command transport itself (the local rehearsal runs the same script directly) and anything on the Hetzner host.

## 6. What was rehearsed on the agent's machine, and what was not

Rehearsed on the agent's own machine against the local https name `mb.localhost`, with locally built images and synthetic data, at source commit `ea159c1` (clean volumes, fresh realm). Logs: `verification/runs/20261014-STAGING-REHEARSAL/README.md`. This is self-review evidence, not evidence from the Hetzner host.

| Item | Result |
|---|---|
| Stack healthy from cold start (4 GB profile) | 133 s; startup order held (postgres, identity with realm imported, storage, api, proxy) |
| Smoke (read-only) and browser smoke with a simulated deposit order | passed; browser smoke 3 of 3 |
| Load: 10 clients, 60 s, with a database and storage backup running at the same time | 17,482 requests, all 200, no errors; backup exit 0 |
| Memory: sum of per-service peaks | 1,310 MiB; tightest is identity at 77% of its 768 MiB ceiling, then storage at 66% of 192 MiB |
| Out-of-memory kills and restarts | none |
| Persistence across down/up with volumes kept | order count unchanged; smoke passed after restart |
| API process killed with SIGKILL | restart policy brought it back healthy after 21 s |
| Older API image on the current schema (28) | started; config, meals and zones answered 200 |
| Remote-mode browser E2E window (fixture channel, no direct database access from the runner) | 6 of 6 passed; guarded reset twice, marker removed, catalogue seeded again |

Defects found by this rehearsal and fixed in the same change set: Caddy directive order, JVM heap flag precedence, `deploy.sh check` matching a comment, Keycloak readiness before realm import, deposit orders rejected by reconcile, CSRF cookie not shared across subdomains, bootstrap admin holding grants.

Not rehearsed, and not claimed: the Hetzner host itself, the SSH forced-command transport for the fixture channel, `deploy.sh up` with real image digests, registry pulls, public DNS and certificate issuance, and the outbound network allowlist.
