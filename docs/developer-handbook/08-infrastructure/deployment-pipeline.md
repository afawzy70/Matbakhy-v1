# Deployment pipeline

> **Owner role:** Infrastructure and deployment
> **Status:** IMPLEMENTED — the path from a pull request to the Contabo Preview is automated in `.github/workflows/deploy-preview.yml`; what happens on the server after the deploy step is only partly verifiable without privileged access.
> **Language / اللغة:** English · [العربية](deployment-pipeline-ar.md)

This page follows one change from a pull request to the running Preview and shows how to check each stage. It complements [ci-cd.md](../05-developer-workflow/ci-cd.md) (workflow inventory) and [git-and-pr-workflow.md](../05-developer-workflow/git-and-pr-workflow.md) (branches and reviews). The server itself is described in [contabo-server-inventory.md](contabo-server-inventory.md) and [runtime-architecture.md](runtime-architecture.md).

## Evidence legend

| Label | Meaning on this page |
|---|---|
| VERIFIED LIVE | Observed on the Contabo VPS by the non-privileged user `aiagent` on 2026-10-21 (read-only). |
| VERIFIED IN CODE | Read in the repository file named next to the claim. This proves what the code does when it runs, not that a given run happened. |
| DOCUMENTED ONLY | A repository document says it; it was not confirmed here. |
| OUTDATED / INCORRECT | Conflicts with newer evidence (for example Hetzner-era text). |
| NOT VERIFIED | Nobody has confirmed it; the page says how an authorized person can. |

Hetzner note: the documents [11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md) and [06-web-preview.md](../../operations/06-web-preview.md) were written for a proposed Hetzner host and describe a manual, owner-run release (dispatch `preview-images.yml`, edit `.env`, run `deploy.sh up`). Treat that manual flow as historical background (OUTDATED / INCORRECT as a description of the current automatic path); the current target is Contabo and the current automatic path is below.

## The whole path

```mermaid
sequenceDiagram
  autonumber
  participant Dev as "Developer"
  participant GH as "GitHub pull request"
  participant Rules as "Ruleset 25447480 on develop"
  participant CI as "Checks (ci.yml, doc001.yml, native-build.yml, blueprint-integrity.yml)"
  participant Build as "deploy-preview.yml job build"
  participant GHCR as "GHCR registry"
  participant Dep as "deploy-preview.yml job deploy (environment preview)"
  participant VPS as "Contabo VPS /srv/matbakhy-staging"
  Dev->>GH: "Open pull request into develop"
  GH->>CI: "pull_request events start the checks"
  CI-->>Rules: "8 required check results"
  Rules-->>GH: "Merge allowed only when all pass"
  Dev->>GH: "Merge (owner authorization)"
  GH->>CI: "push to develop starts Product CI again"
  GH->>Build: "push to develop starts the deploy workflow"
  Build->>CI: "Poll the API for a passing ci.yml push run at this SHA"
  Build->>GHCR: "Push matbakhy-api and matbakhy-preview-front tagged with the SHA"
  Build-->>Dep: "Outputs: both image references with sha256 digests"
  Dep->>VPS: "SSH: check .env exists, tar deploy/ infra/ scripts/ fixtures.json"
  Dep->>VPS: "scp remote-deploy.sh, run it; GitHub token piped on stdin"
  VPS->>GHCR: "docker login (temporary config), pull missing images by digest"
  VPS->>VPS: "Rewrite 3 lines in .env, deploy.sh up, deploy.sh smoke"
  VPS-->>Dep: "DEPLOYED sha, or failure with .env restored"
  Dep->>VPS: "deploy.sh admin-console enable"
```

The diagram reads top to bottom. Steps 1 to 5 are the pull request gate and the merge, steps 6 to 10 are the post-merge CI and the build, and steps 11 to 16 are the deployment to the server. The server-side steps are VERIFIED IN CODE only; none of them is VERIFIED LIVE.

## Stage 1: pull request, reviews and required checks

| Stage | Automated, manual or proposed | Detail | Evidence |
|---|---|---|---|
| Open a PR into `develop` | Manual (developer) | Template `.github/pull_request_template.md` with the DOC-001 Documentation Impact block | VERIFIED IN CODE |
| Product CI | Automated | `.github/workflows/ci.yml`: jobs `workspace`, `local-stack`, `design`, `secrets` on every push and pull request; concurrency group per ref cancels older runs | VERIFIED IN CODE |
| Blueprint integrity | Automated | `.github/workflows/blueprint-integrity.yml` job `blueprint-integrity` on push and pull request | VERIFIED IN CODE |
| Native builds | Automated | `.github/workflows/native-build.yml` jobs `android` and `ios` run on a pull request into `develop` (and manually); a push to `develop` does not start them | VERIFIED IN CODE |
| Documentation gate | Automated | `.github/workflows/doc001.yml` on pull requests into `develop` or `main`; runs the gate script from the base SHA and reads the PR as data | VERIFIED IN CODE |
| Handbook parity | Automated, not required | `.github/workflows/handbook-parity.yml` runs when handbook files change; it is not among the required checks | VERIFIED IN CODE |
| Owner E2E seed check | Automated, not required | `.github/workflows/owner-e2e-fixtures.yml`, path-filtered | VERIFIED IN CODE |
| Required checks and no bypass | Enforced by GitHub | Ruleset id 25447480 "develop protection (PR + required checks + DOC-001)", target `develop`, no bypass actor, 8 required status checks (listed below), pull request required with 0 approving reviews, stale reviews dismissed on push | DOCUMENTED ONLY (in [documentation-maintenance.md](../05-developer-workflow/documentation-maintenance.md)); can be re-read with the `gh api` command in the verification section |
| Human review | Manual, not enforced | Approving reviews required is 0. A governance change needs a non-author approval by the DOC-001 gate and the Code Owners rule (`.github/CODEOWNERS`). The single shared GitHub identity means independent approval cannot currently be satisfied | DOCUMENTED ONLY |
| Independent QA and automated code review as a gate | Proposed / not part of the pipeline | Codex QA records exist under `docs/quality`; nothing in the workflows calls a reviewer | VERIFIED IN CODE (absence in workflows) |
| Merge | Manual (owner authorization) | Agents do not merge without owner authorization (see [git-and-pr-workflow.md](../05-developer-workflow/git-and-pr-workflow.md)) | DOCUMENTED ONLY |

The 8 required status checks, as recorded in [documentation-maintenance.md](../05-developer-workflow/documentation-maintenance.md) (DOCUMENTED ONLY):

1. `Documentation Consistency / DOC-001`
2. `Workspace from clean checkout (P01-CHECK-1/2)`
3. `Local stack bootstrap and smoke (P01-CHECK-3)`
4. `Design tokens, specimens and prototype journeys (P00-CHECK-2)`
5. `Secret scan`
6. `blueprint-integrity`
7. `Android debug APK (Gradle)`
8. `iOS simulator build (Xcode, unsigned)`

The branch need not be up to date with `develop` before merging. The path-filtered seed check and the deploy jobs are deliberately not required.

Known limitation, DEFERRED by owner decision (2026-10-20): because DOC-001 runs on the `pull_request` event, a PR that rewrites `.github/workflows/doc001.yml` can neutralise the check (disposable PR #33 passed in 2 seconds). The interim control is owner review of every diff that touches `.github/workflows/`. Nothing in this handbook change remediates it.

## Stage 2: merge, build and GHCR publish

Everything below is VERIFIED IN CODE in `.github/workflows/deploy-preview.yml`.

| Item | Behaviour |
|---|---|
| Trigger | push to `develop`, or manual `workflow_dispatch` |
| Permissions | `contents: read`, `packages: write`, `actions: read` |
| Concurrency | group `matbakhy-preview-deploy`, `cancel-in-progress: false` (a running deployment is never cancelled by a newer push) |
| Job `build` | `runs-on: ubuntu-24.04`, timeout 75 minutes |
| CI gate inside the build | polls `gh api repos/<repo>/actions/workflows/ci.yml/runs?head_sha=<SHA>` up to 60 times with 30 s sleeps; it needs a `push` event run of Product CI at the same SHA with conclusion `success`; `failure`, `cancelled` or `timed_out` fails the build; no result after 60 attempts fails with "Timed out waiting for Product CI" |
| API jar | `./mvnw -B -ntp -DskipTests package` in `backend` (tests ran in Product CI, not here) |
| Images | `ghcr.io/<lower-case owner>/matbakhy-api:<full SHA>` built from `backend`; `ghcr.io/<lower-case owner>/matbakhy-preview-front:<full SHA>` built from `deploy/web-preview/front.Dockerfile` with build argument `PREVIEW_DOMAIN=167-86-90-174.sslip.io` (the domain is fixed in the workflow); both labelled `org.opencontainers.image.revision=<SHA>` |
| Registry login | `docker login ghcr.io` with the job token (`github.token`) |
| Digests | after `docker push`, the workflow reads `RepoDigests[0]` of each image and passes `name@sha256:<digest>` to the deploy job as job outputs `api` and `front` |

The Preview domain is baked into the front image at build time. A different domain needs a workflow change (see [dns-https-sslip-io.md](dns-https-sslip-io.md)) and more than the image: `PREVIEW_DOMAIN` in the server `.env` drives the Caddy host names, Keycloak host names and the API origins (`Caddyfile`, `compose.yaml`), and the realm file `identity/matbakhy-realm.preview.json` carries the client redirect URIs, but an already imported realm is not re-imported (`migrate-mfa.sh` header), so its redirect URIs need a separate change. An older front image keeps the old domain baked in, so a rollback of the image after a domain change brings the old domain back. Recreating the proxy with a new `PREVIEW_DOMAIN` removes the old host names, so a cutover has downtime for the old names unless dual-domain routing is separately designed and rehearsed; the repository contains no such design. No domain replacement or rollback has been rehearsed: NOT VERIFIED.

The manual `.github/workflows/preview-images.yml` builds the same two images for an arbitrary domain and prints `RELEASE_COMMIT`, `API_IMAGE` and `FRONT_IMAGE` in the job summary; it deploys nothing (VERIFIED IN CODE). It belongs to the manual Hetzner-era flow.

## Stage 3: deploy to /srv/matbakhy-staging

Job `deploy` (VERIFIED IN CODE): `needs: build`, runs only when `github.ref` is `refs/heads/develop`, `environment: preview`, timeout 30 minutes.

| Step | What it does | Evidence |
|---|---|---|
| Secrets used (names only) | `STAGING_SSH_HOST`, `STAGING_SSH_USER`, `STAGING_SSH_KEY`, `STAGING_SSH_HOST_PUBKEY`; plus the automatic `github.token` | VERIFIED IN CODE |
| Which Linux user the deployment connects as | stored in the secret `STAGING_SSH_USER` | NOT VERIFIED (the deployment directory is owned by `devops`, which is circumstantial only) |
| SSH setup | fails if any required value is empty; writes the key to `~/.ssh/id_ed25519` with umask 077; writes `known_hosts` as `<host> <host public key>`; every later `ssh` call uses `StrictHostKeyChecking=yes` and `BatchMode=yes`, while the `scp` call sets `StrictHostKeyChecking=yes` only | VERIFIED IN CODE |
| Pre-check | over SSH: `test -f /srv/matbakhy-staging/deploy/web-preview/.env && test -w /srv/matbakhy-staging`; the deploy stops here if the server `.env` is missing | VERIFIED IN CODE |
| Transfer | `tar -cf - deploy infra scripts docs/quality/owner-acceptance/fixtures.json` streamed over SSH into `tar -xf - -C /srv/matbakhy-staging` | VERIFIED IN CODE |
| `.env` and data preserved | the workflow runs in a clean checkout, where the gitignored (`.gitignore`) server `.env` does not exist, so the tar stream normally does not contain one; the archive carries `.env.example`, which does not overwrite it. `tar` itself does not read `.gitignore`: an archive made by hand from a working copy that holds a real `.env` (for example during a manual rebuild) would include it, so exclude it explicitly (`--exclude=.env`) or archive tracked files only (`git archive`). `tar -x` only adds and replaces files, so files deleted in Git are not deleted on the server | VERIFIED IN CODE |
| Remote script | the job writes `remote-deploy.sh` and copies it to `/tmp/matbakhy-cd-<SHA>.sh` on the server, runs it with `bash`, then removes it; the GitHub token is sent on SSH standard input, never as an argument | VERIFIED IN CODE |
| Registry login on the server | `docker login ghcr.io -u afawzy70 --password-stdin` into a temporary `DOCKER_CONFIG` directory that is deleted on exit; the user name is hard-coded in the script | VERIFIED IN CODE |
| Pin the release in `.env` | a Python snippet drops any existing `RELEASE_COMMIT=`, `API_IMAGE=` and `FRONT_IMAGE=` lines, appends the three new ones, and sets mode 600 on `.env`; no other line is touched | VERIFIED IN CODE |
| Start | `./deploy.sh up` (see below) | VERIFIED IN CODE |
| Smoke | `./deploy.sh smoke` runs `smoke.sh` for `PREVIEW_DOMAIN`: customer web headers and demo notice, ops web, public configuration reports `PREVIEW` with `demo: true`, private endpoints answer 401 or 403, actuator and metrics answer 404, OIDC issuer, master realm hidden, sign-in redirect | VERIFIED IN CODE |
| Admin console step | `./deploy.sh admin-console enable` hardens the Keycloak master realm, opens the switch file and verifies through the public URL; the script closes it again when its own live verification fails or the script is interrupted before verification passes. After verification passed, if no administrator can sign in, the script runs `create-admin`; a failure in that later step does not close the switch, so after any failure of this step check the switch with `./deploy.sh admin-console status` and close it with `./deploy.sh admin-console disable` if it is not wanted (`deploy/web-preview/admin-console.sh`, `enable` branch). `enable` first writes a Keycloak database dump to `$BACKUP_DIR/keycloak/keycloak-db-<stamp>.dump`; whether it ran, how recent it is and whether it restores are NOT VERIFIED LIVE. Policy: [14-keycloak-admin-console.md](../../operations/14-keycloak-admin-console.md) | VERIFIED IN CODE |

### What `deploy.sh up` does

VERIFIED IN CODE (`deploy/web-preview/deploy.sh`, function `up`):

1. `check`: loads `.env`, refuses placeholders, requires `API_IMAGE` and `FRONT_IMAGE` to contain `@sha256:` (digest pinning) and `RELEASE_COMMIT` to be a 40-character hex SHA.
2. Takes a non-blocking `flock` on `${BACKUP_DIR:-/srv/matbakhy-backups}/.preview-mutation.lock`; if another change holds it, `up` stops with "another preview change holds ...".
3. `render`: rewrites the generated Keycloak realm and storage configuration under `GENERATED_DIR` (directory mode 700).
4. If a PostgreSQL container already runs: takes a pre-deploy database backup and proves it by restoring into a scratch database and reconciling (`backup_db`, `verify_backup`), and records the schema version before.
5. `compose pull --policy missing`, then `compose up -d`.
6. `wait_healthy`: polls the API container health every 5 s for up to 90 tries (about 7.5 minutes); on timeout it prints the last 80 log lines of `api` and `identity` and fails.
7. Attempts to create the two storage buckets (`matbakhy-public`, `matbakhy-private`) through the SeaweedFS shell. The command is repeatable, but its output is discarded and every failure is ignored (`>/dev/null 2>&1 || true`), so a successful `up` does not prove that either bucket exists. Verify the buckets separately (scenario 21 in [infrastructure-troubleshooting.md](infrastructure-troubleshooting.md) and Step 21 in [server-rebuild-guide.md](server-rebuild-guide.md); [admin-readonly-verification.md](admin-readonly-verification.md) has no bucket-list command).
8. `record_release`: appends one tab-separated line (time, commit, both digests, schema before and after, backup path) to `releases.log` under `BACKUP_DIR`.

Migrations: Flyway runs forward-only inside the API at start; there is no separate migration step in the workflow (DOCUMENTED ONLY in [06-web-preview.md](../../operations/06-web-preview.md); the container start path was not re-read for this page). The API container health check calls `http://127.0.0.1:8081/actuator/health` inside the container (VERIFIED IN CODE, `deploy/web-preview/compose.yaml`), which is separate evidence from the public answer. The public host returns 404 for `/actuator/health` (VERIFIED LIVE) because the Caddyfile proxies only `/api/*`, `/oauth2/*`, `/login/oauth2/*` and `/logout` (VERIFIED IN CODE, `deploy/web-preview/Caddyfile`), not because of the internal health check.

Where `GENERATED_DIR` and `BACKUP_DIR` point on the live host is NOT VERIFIED: `.env.example` defaults to `/srv/matbakhy-preview/generated` and `/srv/matbakhy-backups`, and the live `.env` cannot be read by `aiagent`. An administrator can read the effective values with `grep -E '^(GENERATED_DIR|BACKUP_DIR)=' /srv/matbakhy-staging/deploy/web-preview/.env`, which does not print secrets (see [admin-readonly-verification.md](admin-readonly-verification.md)).

### Failure behaviour and rollback

The exact failure branch of the remote script (VERIFIED IN CODE):

```bash
if ! ./deploy.sh up || ! ./deploy.sh smoke; then
  echo "Deployment failed. Restoring prior configuration; no automatic DB rollback."
  cp "$old" .env
  exit 1
fi
echo "DEPLOYED $sha"
```

What this means in practice:

| Question | Answer | Evidence |
|---|---|---|
| What is restored automatically? | Only the `.env` file, copied back from a mode-600 temporary copy taken before the three lines were rewritten | VERIFIED IN CODE |
| Is the database rolled back automatically? | No. The message says so, and no step restores a dump | VERIFIED IN CODE |
| Are the containers rolled back automatically? | No step restarts the stack after `.env` is restored. If `compose up -d` already replaced containers, they keep running the new images until someone runs `./deploy.sh up` again with the restored or corrected `.env`. Before restarting with restored image pins, assess whether the older application image can run against a schema that the new release may already have migrated (see the migration row below); do not restart blindly | VERIFIED IN CODE (absence of a restart step) |
| Does a failed deployment skip the admin console step? | Yes, the workflow step order stops at the first failing step | VERIFIED IN CODE |
| Does a failed admin console step undo the deployment? | No. The deployment already succeeded. The script closes the switch when its verification fails or it is interrupted, but not when the later automatic administrator creation fails: then the switch can stay open and the workflow run is marked failed. Verify the switch state and close it with `admin-console disable` if needed | VERIFIED IN CODE |
| Is the migration reversible? | Flyway is forward-only. Image-only rollback is safe only if the schema did not move or every later migration is expand-only (`scripts/check_migration_compat.py` and CI guardrail tests); otherwise restore the pre-deploy backup and accept loss of data written since | DOCUMENTED ONLY ([11-staging-deployment-and-rollback.md](../../operations/11-staging-deployment-and-rollback.md), policy written for the Hetzner host) and VERIFIED IN CODE (`rollback_plan` in `deploy.sh`) |
| A real rollback on the Contabo host | not rehearsed; NOT VERIFIED | NOT VERIFIED |

Operator tools that exist for the manual part (run on the server by an authorized engineer, never on the live Preview from a handbook exercise): `./deploy.sh status` (read-only), `./deploy.sh rollback-plan [N]` (prints only), `./deploy.sh restore-db <dump>`. Full decision tables: [backup-restore-disaster-recovery.md](backup-restore-disaster-recovery.md).

Choosing the `rollback-plan` target (VERIFIED IN CODE, `rollback_plan` and `record_release` in `deploy.sh`): `N` counts back through the lines of `releases.log`, and a line is appended only when `./deploy.sh up` completes. A deployment whose `up` failed leaves no line, so `rollback-plan 1` means "the release recorded before the newest recorded one", which is not necessarily "the release before the failed run". To find the right target, an authorized operator reads `releases.log` (or the last five lines in `./deploy.sh status`), compares the commits with the workflow run history, and passes the `N` whose printed `target release` is the commit wanted. The plan only prints; nothing picks the target automatically.

### Related post-deploy workflow

`.github/workflows/owner-e2e-provision.yml` runs when `deploy/web-preview/owner-e2e-run.request` changes on `develop`. It polls for a successful `deploy-preview.yml` run of the same SHA (up to 90 attempts, 30 s apart, which would take 45 minutes; the whole job has `timeout-minutes: 30`, so the job timeout ends polling earlier, after about 60 attempts), connects with the same four secrets in environment `preview`, and runs `./owner-e2e-provision.sh plan` or `apply` from `/srv/matbakhy-staging/deploy/web-preview`. It copies no files itself and uses its own concurrency group `matbakhy-preview-owner-e2e` so it does not block the deployment it waits for (VERIFIED IN CODE). Details: [fixtures.md](../../quality/owner-acceptance/fixtures.md).

## Operational verification

All commands here are read-only. Run `gh` on your own laptop after `gh auth login`; run `curl` from anywhere (results differ by caller IP for access-restricted hosts). None needs `sudo`, and none should be run under elevated privileges by an agent.

| Command | Machine and user | Read-only | Expected result | If it fails |
|---|---|---|---|---|
| `gh run list --workflow deploy-preview.yml --branch develop --limit 5 --json databaseId,headSha,status,conclusion,createdAt` | your laptop, your GitHub login | yes | the latest runs as JSON; `headSha` is the commit, `conclusion` is `success` for a fully successful run (the default table output has no SHA column). A run marked `failure` because of the admin console step may still have deployed: check its log for `DEPLOYED <sha>`, and do the same for every run newer than the latest `success` | `gh auth status`; confirm repository access |
| `gh run view <run-id> --log` | your laptop | yes | build and deploy step logs; the last deploy line is `DEPLOYED <sha>` on success | find the failing step name and read [infrastructure-troubleshooting.md](infrastructure-troubleshooting.md) |
| `gh api repos/afawzy70/Matbakhy-v1/rulesets/25447480` | your laptop; needs a token that can read repository rules | yes | JSON with enforcement `active`, no bypass actors, and the required status check contexts | 404 or 403 means the token cannot read rules, which is not proof the rules are gone |
| `gh pr checks <pr-number> --required` | your laptop | yes | only the required checks of a PR (without `--required` the output also lists optional checks) | pending checks are normal while jobs run; do not rerun healthy jobs |
| `curl -sS https://api.167-86-90-174.sslip.io/api/v1/configuration/public` | any machine | yes | JSON with `orderingAvailable`, `orderLimits` and `environment` set to name `PREVIEW`, demo `true` (VERIFIED LIVE on 2026-10-21 from the VPS itself) | a 403 "Not available" from Caddy is an access-policy answer for the caller IP, not an outage |
| `curl -sS -o /dev/null -w '%{http_code}\n' https://api.167-86-90-174.sslip.io/actuator/health` | any machine | yes | 404 by design (VERIFIED LIVE) | 200 would mean the actuator is published, which is a defect |

Finding the deployed commit. What the repository provides, and where it is NOT VERIFIED:

| Source | What it shows | Who can read it | Evidence |
|---|---|---|---|
| Latest `deploy-preview.yml` run on `develop` with conclusion `success` (`gh run list --json` above) | the SHA of the last fully successful run (supporting evidence of what was deployed, not proof of what runs now). The workflow prints `DEPLOYED <sha>` before its admin console step, so a newer run can have deployed and then failed that step; the latest successful run can therefore be older than what is deployed. Read the log of every newer failed run for `DEPLOYED <sha>` | any GitHub user with repository access | VERIFIED IN CODE (workflow prints `DEPLOYED <sha>`); that the containers still match it is NOT VERIFIED; the running image references are confirmed only by an administrator with the commands in [admin-readonly-verification.md](admin-readonly-verification.md) |
| Server `.env` lines `RELEASE_COMMIT`, `API_IMAGE`, `FRONT_IMAGE` | the pinned release | file mode 600 owned by `devops`; `aiagent` cannot read it | NOT VERIFIED LIVE |
| `./deploy.sh status` in `/srv/matbakhy-staging/deploy/web-preview` | running images, schema version, configured values, last 5 releases | needs Docker access (not available to `aiagent`) | VERIFIED IN CODE; not run |
| `releases.log` under the backup directory | one line per successful `up` | `/srv/matbakhy-backups` is 750 `devops:devops` | VERIFIED LIVE (permissions); content NOT VERIFIED |
| API log field `service.version` | `MATBAKHY_VERSION`, set from `RELEASE_COMMIT` in `compose.yaml` | needs Docker or the Grafana and Loki stack | VERIFIED IN CODE; not observed |
| Image label `org.opencontainers.image.revision` | the build commit | `docker image inspect` with Docker access | VERIFIED IN CODE (the build sets it in `deploy-preview.yml` and `preview-images.yml`); labels on the running containers are NOT VERIFIED LIVE |
| Public endpoint | the public configuration does not carry a commit or version | anyone | VERIFIED LIVE (the keys observed were `orderingAvailable`, `orderLimits`, `environment`) |

An administrator with authorized access can confirm the commit with the commands in [admin-readonly-verification.md](admin-readonly-verification.md). Agents and the `aiagent` account must not run them.

## Known limitations and documentation gaps

- NOT VERIFIED LIVE: the effective SSH user used by the pipeline, the live `.env` values, running containers and digests, whether the last deployed SHA matches the latest successful run, the contents of `releases.log`, and any Contabo rollback.
- The front image is built for the fixed domain `167-86-90-174.sslip.io`; changing the Preview domain requires editing the workflow, building new images, updating `PREVIEW_DOMAIN`, the Caddy routes and the realm client redirect URIs (see Stage 2). Recreating the proxy with a new `PREVIEW_DOMAIN` removes the old host names, so a cutover has downtime unless dual-domain routing is separately designed and rehearsed. Not rehearsed: NOT VERIFIED; the owner decides when and whether the domain changes.
- The remote deployment script generated inside `deploy-preview.yml` hard-codes the registry user name `afawzy70` (`deploy.sh` itself contains no registry login); a repository transfer or a different owner needs a workflow change.
- A failed deployment restores only `.env`; it does not restart containers or roll back the database (quoted above). The manual `rollback-plan` flow is the documented recovery. Complete-service recovery is BLOCKED: a complete recovery across all stores (database, Keycloak, storage) is not provided as one rehearsed sequence in the repository: it is NOT VERIFIED and needs an owner decision on a rehearsal on a new isolated server. What exists: the individual commands and [server-rebuild-guide.md](server-rebuild-guide.md). What is missing: a rehearsed, isolated end-to-end sequence. A rehearsal must use a separate repository (or isolated workflow) with its own environment, secrets and concurrency group; the `preview` environment is used by `deploy-preview.yml` and `owner-e2e-provision.yml`, so reusing it would redirect the live Preview automation to the rehearsal server.
- Required-check names depend on job names. The job `blueprint-integrity` has no `name:` field, so its context is the job id; renaming any of the 8 jobs silently stops it from satisfying the ruleset.
- Approving reviews required is 0 and independent review cannot currently be satisfied with one GitHub identity; the DOC-001 workflow-neutralisation limitation is DEFERRED by the owner and still open.
- Branch protection is configured on GitHub and not in the repository; the ruleset facts above are DOCUMENTED ONLY until re-read with `gh api`.
- `docs/operations/11-staging-deployment-and-rollback.md` and `06-web-preview.md` still describe the manual Hetzner-era release (OUTDATED / INCORRECT for the current automatic path); `deploy/web-preview/README.md` is also out of date, as noted in [ci-cd.md](../05-developer-workflow/ci-cd.md).
- `.env.example` defaults `GENERATED_DIR` to `/srv/matbakhy-preview/generated`, while the CD target is `/srv/matbakhy-staging`; `/srv/matbakhy-preview` holds an older copy of the deploy files (VERIFIED LIVE, stale). Which path the live `.env` uses is NOT VERIFIED.
