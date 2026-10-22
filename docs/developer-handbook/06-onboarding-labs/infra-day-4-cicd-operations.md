# Infrastructure Day 4 - GitHub Actions, deployment, monitoring and troubleshooting

> **Owner role:** Infrastructure and deployment
> **Status:** REFERENCE — lab for reading the CI and deployment workflows, following a deployment on GitHub, reading the monitoring design and practising troubleshooting from the repository and the public API.
> **Language / اللغة:** English · [العربية](infra-day-4-cicd-operations-ar.md)

## Evidence legend

| Label | Meaning |
|---|---|
| VERIFIED LIVE | Observed on 2026-10-21 on the Contabo VPS by the unprivileged user aiagent. |
| VERIFIED IN CODE | Read in this repository (file named). It shows intent, not necessarily what runs. |
| DOCUMENTED ONLY | A document states it; nothing confirmed it here. |
| OUTDATED / INCORRECT | Written for the proposed Hetzner host or superseded. |
| NOT VERIFIED | Not observed. |

## Objectives

- Explain the path from a pull request to the running Preview and name each gate.
- Read `.github/workflows/deploy-preview.yml` and say what each step does, which secrets it uses (names only) and what is not rolled back.
- Follow a deployment on GitHub with read-only `gh` commands without cancelling or rerunning healthy jobs.
- Describe the monitoring design (logs, metrics, alerts) and its limits.
- Diagnose a reported symptom using the public API configuration, the HTTP behaviour of the hosts and the troubleshooting page.

## Prerequisites

- Days 1 to 3 completed: [Day 1](infra-day-1-topology-accounts.md), [Day 2](infra-day-2-docker-services.md), [Day 3](infra-day-3-dns-https-keycloak.md).
- Read [deployment pipeline](../08-infrastructure/deployment-pipeline.md) and [CI/CD](../05-developer-workflow/ci-cd.md).
- The GitHub command line `gh` installed and authenticated on your laptop with your own login (`gh auth status`). Read access to the repository is enough for every command below.

## Rules for this lab

- Read-only. Do not trigger a deployment, do not use `gh workflow run`, do not rerun or cancel a run, do not push to develop, do not edit repository settings.
- Do not create a polling loop. Run each command once and read the result. A job that takes a long time is not a failed job; only a failed step justifies action, and then you investigate the real failure first.
- The deployment SSH user and key are repository secrets. You may name the secrets; you must never try to read their values (GitHub does not show them) or guess them.
- No command runs on the Contabo VPS. The server .env is never read.

## Guided tasks

### Task 1. Read the workflow chain

| Item | Detail |
|---|---|
| Command | `ls .github/workflows` then `sed -n 1,16p .github/workflows/deploy-preview.yml` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Nine workflow files, including ci.yml, doc001.yml, handbook-parity.yml, native-build.yml, preview-images.yml and deploy-preview.yml (the count is of the checkout you have; it can change). The deploy workflow is named "Deploy Matbakhy preview to Contabo", runs on a push to develop and on manual dispatch, and uses the concurrency group matbakhy-preview-deploy without cancelling an earlier run |
| Verification | Write in one line what the concurrency setting means for two merges close together: the second deployment waits for the first (VERIFIED IN CODE: cancel-in-progress is false) |

### Task 2. Walk the deploy workflow step by step

Open `.github/workflows/deploy-preview.yml` and fill in the table. All entries are VERIFIED IN CODE unless stated.

| Job and step | What it does | Secrets used (names only) | Failure effect |
|---|---|---|---|
| build: wait for passing Product CI | Polls GitHub for a passing ci.yml run at this exact commit before building anything | the built-in workflow token | The build fails or times out; nothing is deployed |
| build: images | Builds and publishes the two images to the container registry and outputs their references | the built-in token (packages write) | No deployment starts |
| deploy: configure verified SSH host | Writes the SSH key and the pinned host key and refuses to continue if any required value is empty | STAGING_SSH_HOST, STAGING_SSH_USER, STAGING_SSH_KEY, STAGING_SSH_HOST_PUBKEY | Stops before connecting |
| deploy: transfer versioned deployment files | Checks the server .env exists and the directory is writable, then streams the deploy, infra, scripts folders and one fixtures file into /srv/matbakhy-staging with tar over SSH | the SSH secrets | Stops; the server .env is not overwritten because the workflow checks out only tracked files and archives only the listed paths from that clean checkout (tar itself does not honour .gitignore, so a manual archive taken from a working copy with a local .env would need explicit exclusions) |
| deploy: deploy digest-pinned images | Updates three lines of the server .env, then runs `./deploy.sh up` and `./deploy.sh smoke`; if either fails it copies the previous .env back and exits non-zero (a failure before that point, such as the registry login, exits without restoring) | the SSH secrets and the built-in token for the registry login (the registry user name is written into the generated remote script) | Only the .env file is restored: containers already recreated keep running the new images until someone reruns `up` with the restored pins, and there is no automatic database rollback. Before restarting with older pins, check that the older application can run against the schema the failed release may already have migrated (use `./deploy.sh rollback-plan`). Its number N counts only successful releases that `up` recorded (VERIFIED IN CODE): a failed `up` is not recorded, so after a failed deploy N=1 can skip the latest successful release, and the plan can never target the last recorded line itself. An administrator must read that last line of the release log (it holds the commit, image digests and schema) and compare it with the plan; no tested sequence for that exists in the handbook (not rehearsed: NOT VERIFIED) |
| deploy: publish and verify the Keycloak admin console | Runs `./deploy.sh admin-console enable` on the server, after the previous step succeeded | the SSH secrets | No .env restoration exists for this step: if it fails, the new images stay deployed and the run is red. The step name states that the console closes itself again if verification fails; that is a claim in the workflow, and the behaviour itself is NOT VERIFIED. The hardening changes settings stored in Keycloak, which restoring .env would not undo. See the Keycloak admin console document |

Questions:

1. Which job has `environment: preview`? deploy.
2. Which branch may deploy? Only develop (the condition on the deploy job).
3. What value does the pipeline never show you? The Linux user that STAGING_SSH_USER holds. It is NOT VERIFIED; the ownership of /srv/matbakhy-staging by devops fits but does not prove it.
4. What does the pipeline not roll back? The database schema and data (VERIFIED IN CODE: the failure message). Read the rollback decision tables in [backup, restore and disaster recovery](../08-infrastructure/backup-restore-disaster-recovery.md).

### Task 3. Follow runs on GitHub (read-only)

| Command | Machine and user | Kind | Prerequisite | Expected result | Verification | Likely failure and action |
|---|---|---|---|---|---|---|
| `gh run list --workflow deploy-preview.yml --branch develop --limit 5` | your laptop, your GitHub login | read-only | gh authenticated | The latest runs with status, conclusion and head commit | Note the newest run's commit and conclusion. The latest successful run is not proof of the deployed commit: the workflow prints DEPLOYED before the admin-console step, so a newer run can deploy and then fail that step, leaving the latest successful run pointing at an older release. Open newer failed runs and check which steps succeeded; confirming the running image references needs an authorized administrator (NOT VERIFIED here) | Authentication error: run `gh auth status`. No rows: wrong repository; run inside the checkout |
| `gh run view <run-id>` | your laptop | read-only | a run id from the previous row | Jobs and steps with their states | Identify which step is running or failed | Do not rerun a running or healthy job |
| `gh run view <run-id> --log-failed` | your laptop | read-only | the run failed | Log of failing steps only | Find the first failing line | If empty the run did not fail; do not conclude anything else |
| `gh pr checks <pr-number>` | your laptop | read-only | a pull request number | The required checks of the PR and their state | Count how many are pending, passing, failing | Pending is normal while jobs run |
| `gh api repos/<owner>/<repo>/rulesets/25447480` | your laptop | read-only | token that can read rules | JSON with enforcement active and required status checks; the ruleset is DOCUMENTED ONLY in [documentation maintenance](../05-developer-workflow/documentation-maintenance.md) | Compare the required contexts with the list in [deployment pipeline](../08-infrastructure/deployment-pipeline.md) | 403 or 404 means your token cannot read rules, not that the rules are gone |

The gh command syntax is the usual one for the tool (DOCUMENTED ONLY here). What these commands return today is not recorded in this handbook; record your own observation with the date.

Decision exercise: a deploy run shows the step "Deploy digest-pinned images" running for 20 minutes. Is it stalled? The job timeout is 30 minutes (VERIFIED IN CODE) and the step waits for container health, so a long run alone is not a failure. Check the step list with `gh run view` and the log tail; act only on an actual failed step.

### Task 4. Read the public configuration and the Preview state

| Item | Detail |
|---|---|
| Command | `curl -sS https://api.<preview-domain>/api/v1/configuration/public` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | JSON with orderingAvailable, orderLimits and environment name PREVIEW with demo true (VERIFIED LIVE on 2026-10-21 from the server's own address) |
| Verification | Confirm environment.name is PREVIEW and demo is true. The endpoint does not return a commit or version, so it cannot prove which release runs |
| Likely failure | 403 with body "Not available": your address is outside the customer class (an access-policy answer, not an outage). Connection error: DNS or port 443; repeat the Day 3 checks |

Also run `curl -sS -o /dev/null -w '%{http_code}\n' https://api.<preview-domain>/actuator/health` and confirm 404, by design.

Optional, and read-only: `smoke.sh` creates no account or order (VERIFIED IN CODE: header of the script). Use the command that matches your network. From a machine in the staff class: `sh deploy/web-preview/smoke.sh <preview-domain>`. From a machine outside the staff class: `SMOKE_OPS=skip sh deploy/web-preview/smoke.sh <preview-domain>`; without SMOKE_OPS=skip the script requests the operations web, gets 403 and fails, which is an access-policy answer and not a defect. A customer-only run is also not proof that the staff class works. If you do not run it, record NOT RUN.

### Task 5. Read the monitoring design

| Item | Detail |
|---|---|
| Command | `sed -n 1,40p deploy/web-preview/observability/alerts.yml` and `sed -n '/^## 9. Observability/,/^## 10. Deployment path/p' docs/developer-handbook/08-infrastructure/runtime-architecture.md` |
| Machine | your laptop |
| Linux user | your own |
| Kind | read-only |
| Expected result | Alert rules and the observability flow: Docker logs → Alloy → Loki; metrics → Prometheus; Alertmanager → a local alert-sink; Grafana on a loopback port |
| Verification | Answer: who is notified when an alert fires? Nobody: alerts go to alert-sink, a local test receiver (VERIFIED IN CODE). How does an engineer reach Grafana? Through an SSH tunnel to the loopback port by an authorized engineer; Grafana is bound to 127.0.0.1:3000 (VERIFIED IN CODE). A listener on 127.0.0.1:3000 exists on the host (VERIFIED LIVE as a socket), but that it is Grafana is NOT VERIFIED because the container list cannot be read. Are the monitoring containers healthy now? NOT VERIFIED LIVE |

Note for later: backup staleness has an alert (BackupStale: no verified backup in 24 hours, VERIFIED IN CODE). Whether any backup job exists on the live host is NOT VERIFIED.

### Task 6. Troubleshooting drills (reading only)

Open [infrastructure troubleshooting](../08-infrastructure/infrastructure-troubleshooting.md). For each symptom below, find the matching scenario, then write the first read-only check, the likely cause and who must act (you, or an authorized administrator).

1. The site loads but sign-in fails with a redirect error.
2. A deployment run is red at the smoke step.
3. The Preview answers 403 "Not available" for you but works for a colleague.
4. The media host answers 403 at its root.
5. The Keycloak admin host answers 404.

Expected reasoning, from this handbook: item 3 is an access class answer (Day 3); item 4 is the expected S3 denial at the root path; item 5 means the admin-console flag is absent and the host fails closed. Items 1 and 2 must be answered from the troubleshooting page and the workflow logs; do not guess a cause without evidence.

## Verification checkpoints

| Checkpoint | Pass when |
|---|---|
| Workflow table | All six rows completed and checked against the file |
| gh commands | You ran at least the first, second and fourth commands once each and recorded your own results with the date, or wrote NOT RUN |
| Decision exercise | You explain why you would not cancel or rerun a healthy long job |
| Monitoring | You can state that alerts reach nobody and that Grafana is loopback only |
| Troubleshooting | Each drill has a first read-only check and an owner |

## Completion criteria

- [ ] I can trace a merge to develop through CI, image build, SSH transfer, deploy, smoke and the admin-console step.
- [ ] I can say what is and is not rolled back on failure.
- [ ] I used only read-only commands and did not run a polling loop.
- [ ] I recorded NOT RUN for anything I did not run.
- [ ] I treated the SSH user, the server .env and the live container state as NOT VERIFIED.

## Where to read next

[Deployment pipeline](../08-infrastructure/deployment-pipeline.md), [infrastructure troubleshooting](../08-infrastructure/infrastructure-troubleshooting.md), [runtime architecture](../08-infrastructure/runtime-architecture.md), [CI/CD](../05-developer-workflow/ci-cd.md).

Next: [Infrastructure Day 5](infra-day-5-rebuild-recovery.md).

## Known limitations and documentation gaps

- The current deployment state of the Preview (commit, images, schema version) is not exposed publicly; confirming it needs an authorized administrator. The latest successful workflow run may predate a newer run that deployed and then failed the admin-console step.
- The ruleset details and the required check list are DOCUMENTED ONLY until re-read with the gh command by a user whose token can read rules.
- The Hetzner-era deployment text in other documents is historical; this lab and the deployment pipeline page describe the Contabo workflow.
