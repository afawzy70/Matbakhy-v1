# Reusable controls and rerun commands

Product binaries/services must be fromd877a1576cd942200fe0a53b5a19fee76b996025. This QA branch also carries definitions and reports; integrate controls selectively, never merge historical evidence into implementation or product code. Original reusable harness came from independent evidencef76664c; known bad old quote oracle has been removed and replaced under D13. All changes here are QA setup/scripts/docs/CI and inventory metadata.

Prerequisites: Docker28.4/Compose2.40.3 local daemon; Node24.21.0/pnpm10.34.6; full JDK21.0.12.1/Maven wrapper; trusted session CA; Chromium154; Python3. No local adb/emulator or Xcode/simulator; use authorized public GitHub-hosted native workflow. Do not use paid credits.

```bash
cd /workspace/matbakhy-regression-qa
export PATH=/workspace/qa-tools/node-v24.21.0-linux-x64/bin:/workspace/qa-tools/pnpm/node_modules/.bin:/workspace/qa-tools/jdk-21.0.12.1+1/bin:$PATH
export JAVA_HOME=/workspace/qa-tools/jdk-21.0.12.1+1
export MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
pnpm install --frozen-lockfile
(cd backend && ./mvnw -B -ntp -s /workspace/qa-tools/maven-settings.xml -Dmaven.repo.local=/workspace/qa-tools/m2 -DskipTests package)
chmod 644 backend/app/target/matbakhy-app-*.jar
python3 qa/regression-d877a15/setup.py
export MATBAKHY_GENERATED_DIR=/workspace/qa-tools/regression-d877a15/stack/generated
docker compose --env-file /workspace/qa-tools/regression-d877a15/stack/.env -f infra/compose.yaml -f /workspace/qa-tools/regression-d877a15/stack/override.yaml up --build -d --wait --wait-timeout 300
python3 qa/regression-d877a15/seed.py
```

Start each Vite server withVITE_API_BASE_URL=http://localhost:39080, customer36173/staff36174 `--strictPort --host127.0.0.1`. Run `bash qa/regression-d877a15/run-full.sh`. After an attempt use`IQA_ATTEMPT=<next-unused-number> bash qa/regression-d877a15/reset-full.sh`; it asserts the exact own Compose project, archives private auth state and sanitized run results, creates new volumes/credentials/seed, reruns all39 automated web/API case groups, and fails missing/duplicate/zero cases. Independent connected order chain is explicitly shared within one fresh campaign. Do not reuse terminal states or unresolved request keys blindly. Targeted retries are labelled TARGETED.

Pricing race barriers: a QA-only after-update trigger waits on a held advisory lock, without changing business decisions/data; genuine authorized kitchen PATCH/placement transactions execute and their PostgreSQL lock waits are observed. The trigger is removed in finally. Placement-first uses a held stock row. Policy reductions modify only the synthetic policy row as a test prerequisite. No forged MFA/authentication or price input to order creation.

Native CI: `.github/workflows/independent-qa-regression-native.yml` checks out literal application SHA and separate suite ref. Binary built before instrumentation; `native/instrument.py` edits only UI-test scripts/proxy, adds pending observations, same-process lower-limit/edit/remove/background/cold/favorites/locale/support checks. `fixture-control.py` is loopback on9099, synthetic policy/support-message seed only. It does not grant app access or change server rules. Recovery assertions require a genuinely committed request whose proxy response is still held before termination. No component/build/other-agent PASS is native coverage.

Privacy: generated passwords/OTP/session cookies, realm and full startup logs remain outsideGit. Evidence masks contact/address/handover/token/signed URLs and idempotency nonce aliases. Initial Gitleaks working-tree failure identified two synthetic idkey UUID nonces in uncommitted attempt files; both masked, no scanner exclusions added. Full-history scan counted224commits (nonzero), retained separately from directory scan. Final scan must include all intended deliverables and fail if zero commits scanned.
