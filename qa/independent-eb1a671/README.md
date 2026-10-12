# Reuse the independent QA assessment

Read [report.md](report.md), [frozen cases](cases.md), and [reviewed results](evidence/reviewed-results.json). Product source is always `eb1a6717b3304d3a02e6a46d06c77ed5d43ac144`; the QA/evidence commit does not change it. These scripts reproduce failures and collect observations; they do not approve any release gate or make product fixes.

## Prerequisites

Use an isolated checkout with this QA directory and unchanged application paths from that SHA. Repository instructions apply. Needed: full **JDK 21** (not only a JRE), **Node 24.21.0**, **pnpm 10.34.6**, Docker/Compose, Python 3, curl, and existing Chromium (`E2E_CHROMIUM` can select its path for the main runner; reproduction uses `/usr/bin/chromium`). Install workspace dependencies with `pnpm install --frozen-lockfile`. Playwright is the existing operations-web dependency; no new testing framework or paid service is needed.

Cloud proxy/CA requirements belong to the execution environment. Preserve its proxy and Docker credential configuration. Maven requires a machine-local proxy settings file and a trusted Java CA store here; no credential values are printed. Set cache/build paths in writable QA directories, not in product files. For this execution:

```bash
export JAVA_HOME=/workspace/qa-tools/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:/workspace/qa-tools/node-v24.21.0-linux-x64/bin:/workspace/qa-tools/pnpm/node_modules/.bin:$PATH"
export MAVEN_USER_HOME=/workspace/qa-tools/m2
export MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
export MAVEN_ARGS='-s /workspace/qa-tools/maven-settings.xml -Dmaven.repo.local=/workspace/qa-tools/m2/repository'
```

Do not copy private settings or generated realm/env files into Git. `setup.py` generates random local credentials under `/workspace/qa-tools/stack`, outside this repository. Scripts currently share that explicit state path; adapt them together if using a different workspace. Inspect running containers/ports before startup: web 25173, API 28080, DB 25432, IdP 28180, storage 29090, mail 28025/21025. If occupied by another session, choose new ports/project and update setup, runner and reproduction together. Do not stop another session to make these ports available.

## Execution

```bash
bash qa/independent-eb1a671/stack.sh up
bash qa/independent-eb1a671/stack.sh seed
# Separate terminal; stop only this owned process after testing:
bash qa/independent-eb1a671/stack.sh web
# With the web server running:
bash qa/independent-eb1a671/stack.sh test
# Remove only this dedicated project and its synthetic data:
bash qa/independent-eb1a671/stack.sh down
```

Seed is for a **fresh QA database**; it intentionally refuses duplicate inserts rather than overwriting shared data. It directly creates synthetic approved catalogue fixtures, not proof of cook onboarding/publication. Solid-colour PNGs are uploaded to the dedicated local emulator. Supplementary reproduction changes only owned stock counters and restores them in `finally`; inspect/reset this dedicated stack if interrupted during mutation. No orders are placed.

`run.cjs` records every check's expected/actual values, screenshots, DOM state, saved basket IDs, public API bodies and console/errors. It now exits 1 for FAIL/BLOCKED. Earlier preserved run process exit 0 meant collection completed, not acceptance passed. `reproduce.cjs` records three independent language reproductions, selected zero-stock and entirely SOLD_OUT behaviour, controlled loading and actual computed-font enlargement. Its successful process exit means evidence collection completed, not a product PASS.

`review_results.py` reconciles raw results with assertion meaning, supplementary execution and missing planned subchecks. It never treats unexecuted totals as passed. Raw results remain preserved. The extra focus-containment assertion is unresolved because modal semantics are not approved; raw FAIL stays visible and reviewed status is BLOCKED, not PASS. Root-font-only scaling was ineffective on pixel-sized text; computed-font injection is labelled separately, and native browser zoom is NOT_RUN.

## Evidence and sanitation

- `results.json`: main raw assertions; `reviewed-results.json`: authoritative reviewed case statuses; `supplementary.json`: targeted real-API and controlled-response checks.
- `IQA-*.png/json`, `MBQA-*.png`: screenshots, DOM and draft evidence. `fixtures.json` maps synthetic kitchen/meal/slot identifiers and expected content.
- `network.json`: public catalogue/zone/kitchen responses plus status-only anonymous profile failures. `console.json`: warnings/errors; no JavaScript page errors observed.
- `harness-attempt-1/` and `attempt-2/`: prior results and blocker evidence, clearly superseded. Do not count these as independent product defects or final PASS.
- `environment.json` and `setup-evidence.json`: observed browser/application/toolchain/startup information. `sha256.json`: evidence-file integrity hashes, excluding itself.

No cookies, auth/CSRF headers, passwords, contact numbers, private addresses, signed private URLs or identity-provider traces are retained. All catalogue IDs/text/images are synthetic. Do not upload fresh signed-in traces or logs without sanitizing. Existing Kane material was read only after independent execution; Kane was not installed or run for this assessment.
