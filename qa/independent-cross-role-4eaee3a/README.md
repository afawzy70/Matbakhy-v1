# Independent cross-role QA campaign

Application fixed at `4eaee3a1f53b929b9e7685fd30da689d470d095c`; QA branch `qa/independent-cross-role-4eaee3a-20261012`. Product code is unchanged. Reports/evidence are English. Owner summary is delivered in Egyptian Arabic. No release gate is approved.

Read [report.md](report.md), [eligibility.md](eligibility.md), [cases.md](cases.md), and [evidence/reviewed-results.json](evidence/reviewed-results.json). The frozen eligibility/cases were committed before runtime. Raw attempts are retained, including incorrect QA locators. `review-results.py` combines actually executed remaining segments without counting attempts twice; it cannot turn a failed assertion into a pass.

## Local isolated setup

Requires documented Docker services, Node 24.21.0/pnpm 10.34.6, full JDK 21/Maven, Python 3, Chromium, and the repository's pinned dependencies. These scripts assume this campaign worktree is `/workspace/matbakhy-cross-role-qa` and private state is `/workspace/qa-tools/cross-role`. Use a different project/state/port set when running concurrently; update the QA harness constants, not product code. Do not run this against production or an existing developer database.

Executed setup, with no credentials printed:

```bash
cd /workspace/matbakhy-cross-role-qa
export PATH=/workspace/qa-tools/node-v24.21.0-linux-x64/bin:/workspace/qa-tools/pnpm/node_modules/.bin:/workspace/qa-tools/jdk-21.0.12.1+1/bin:$PATH
export JAVA_HOME=/workspace/qa-tools/jdk-21.0.12.1+1
export MAVEN_OPTS=-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
pnpm install --frozen-lockfile
(cd backend && ./mvnw -B -ntp -s /workspace/qa-tools/maven-settings.xml -Dmaven.repo.local=/workspace/qa-tools/m2 -DskipTests package)
chmod 644 backend/app/target/app-*.jar
python3 qa/independent-cross-role-4eaee3a/setup.py
export MATBAKHY_GENERATED_DIR=/workspace/qa-tools/cross-role/stack/generated

docker compose --env-file /workspace/qa-tools/cross-role/stack/.env -f infra/compose.yaml -f /workspace/qa-tools/cross-role/stack/override.yaml up --build -d --wait --wait-timeout 300
python3 qa/independent-cross-role-4eaee3a/seed.py
```

Run Vite independently in customer-web and operations-web with `VITE_API_BASE_URL=http://localhost:38080`, ports `35173` and `35174`, `--strictPort`. The environment uses DB35432, IdP38180, S339090, mail38025/31025. Compose project/volumes are `matbakhy-cross-role-4eaee3a` only. `scripts/dev.sh` is the documented startup inspected; its normal realm renderer overwrites the QA OTP realm, so this campaign used the same build/compose services with its private QA override directly.

`setup.py` generates random local passwords/OTP credentials outside Git, real Keycloak password + conditional OTP executions, and a mapper for actual completed authentication methods. `auth.cjs` authenticates through the real UI, saves private session cookies outside Git, and handles Keycloak's raw UTF-8 OTP key representation. No forged MFA claims, disabled authentication, or business-rule edits.

`seed.py` is intentionally one-shot and refuses duplicate database IDs. It directly seeds approved synthetic baseline kitchens, published safe solid-colour media/content, slots, verified contact and initial granular grants; these are prerequisites, not onboarding/contact verification PASS evidence. Onboarding approval, new media upload, slot creation, orders, lifecycle, cash reporting, grant/revocation, and configuration changes were then exercised through real authorized UI/API actions. Do not repeat business actions blindly: completed orders cannot be recreated by a terminal-state resume script. Reset only this QA project for a clean run.

Fake/substituted dependencies: local Keycloak realm instead of production IdP; S3Mock instead of cloud storage; development media scanner and notification/SMS adapters; Mailpit; synthetic commerce policy (tax0, commission10%, HALF_UP, deliveryfee30 EGP). Verified phones are fixture SQL because the fake OTP sender has no retrieval endpoint. Response/lookup failure injection in C-RECOVERY alters transport delivery only; actual quote/order/idempotency/database/authentication remain real. No SMS/provider/payment/store acceptance is implied.

Environment-specific corrections are QA-only: non-proxied container healthcheck, Java/NO_PROXY settings for local S3, readable built jar, complete JDK/trusted session CA. An initial healthcheck and media upload failure were resolved as environment failures, not product defects.

## Execution

```bash
node qa/independent-cross-role-4eaee3a/auth.cjs
IQA_EXPANDED=1 node qa/independent-cross-role-4eaee3a/run-web.cjs
node qa/independent-cross-role-4eaee3a/expanded.cjs
node qa/independent-cross-role-4eaee3a/more.cjs
node qa/independent-cross-role-4eaee3a/final-segments.cjs
node qa/independent-cross-role-4eaee3a/finance-final.cjs
python3 qa/independent-cross-role-4eaee3a/review-results.py
```

Run scripts sequentially; all append the same result files. `IQA_CASES=comma,separated,IDs` permits a bounded resume in the expanded/more/final-segments runners. `*-FINAL` continues the SAME order/cash flow after a QA locator correction; it is not a separate fresh full journey. Inspect dependencies/state before resuming. Historical harness attempts include wrong wrapped-select exact labels, querying a204 response as JSON, invalid expected error name, and transient response timing; report adjudication identifies them. No assertion is weakened to accommodate a product failure.

Axe4.10.3 is already in the pinned dependency tree. W-A11Y executes 36 combinations of six actual screens × three widths × two languages, plus keyboard/statement semantics. Its PASS is practical sampled coverage only. A screen reader is unavailable and MB-029 remains blocked.

## Independent native execution

`.github/workflows/independent-qa-native.yml` checks out the application SHA literally, then the QA controls separately. It never follows later implementation/mvp changes, changes product sources, cancels Claude's native workflows, uses paid testing credits, or uploads a store build. Existing documented adb/XCUITest scripts are invoked independently and their assertion meanings inspected. QA controls add rendered basket/lifecycle/language checks and a read-only database collector. Android and iOS run separately against fresh real services and their documented native seed. See native evidence/report for exact run/job/control SHAs, statuses and limitations. A compile/launch/CI inspection is not a journey PASS.

## Evidence/privacy

Screenshots mask inputs and handover/contact displays. JSON recursively removes credentials, signed URLs, contact/address fields, and private locations/codes. Only synthetic identifiers, business state, prices and counters remain. Private authentication state, full logs, realm, keys and generated SQL are outside Git. Selected sanitized native log lines and independently checked database correlation are preserved; raw CI temporary screenshots not uploaded are not claimed as durable screenshots.

Cleanup uses only the dedicated compose files/project: `docker compose ... down --volumes`; terminate only Vite processes whose cwd belongs to this worktree. Never use Docker system prune or a global process kill.

Publication hygiene review: the repository scanner's history mode could not resolve the shared worktree git directory inside its container and scanned0commits, so that output is not called a history PASS. Its working-tree scan flagged20 generic-api-key matches, all traced to UUID command idempotency keys in RPC evidence/case prose, not login credentials. Current evidence now masks these keys as well; the digest-pinned scanner independently scanned the QA directory and found0leaks. Historical QA checkpoint retains authenticated command nonce values, not reusable access credentials; no force-push/history rewrite was performed.

Native harness amendments: Android extra journey now starts a new real foreground process after offline recovery, retaining session/storage. iOS attempt2 progressed beyond launch but met an OS Save Password? sheet; QA-only `ios-auth-controls.py` dismisses only that observed prompt with Not Now, keeping the same element/timeouts/assertions and never changing the product binary. Android-only and iOS-only commits run separate non-cancelling QA concurrency groups; they do not rerun the other surface or cancel its active session. Final outcomes must come from execution, not these harness changes.
