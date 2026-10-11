# Verification strategy and evidence

## Verification levels

| Level | Proves | Does not prove |
|---|---|---|
| Blueprint validation | References, IDs, dependencies and planning structure consistent | Product behavior, business approval or runtime security |
| Unit/domain | Money, state edges, cutoff and pure validation | Real database locks/provider integration |
| Integration | PostgreSQL constraints/transactions, identity/provider contracts, outbox | Browser/native usability |
| Contract | OpenAPI/schema and generated client compatibility | Full end-to-end journey |
| Browser E2E | Customer/cook/ops behavior with real API/database | Native iOS/Android correctness |
| Native E2E | Platform lifecycle, auth, checkout, push, accessibility | App-store approval or business readiness |
| Adversarial/reliability/load | Authorization, abuse, race/crash behavior and measured performance | Legal compliance or indefinite future availability |
| UAT/operational rehearsal | Owner/operator acceptance and real runbook usability | Untested scenarios or automatic permission to publish |

Use [scenarios.json](../../planning/scenarios.json) as acceptance coverage. Each scenario has Given/When/Then, owner story, phase, severity, level and execution status. Test implementation should reference its ID. Split a scenario into more tests as needed; retain original business outcome. Add explicit regression scenarios for new defects.

## Mandatory transaction cases

Run with real PostgreSQL and concurrent connections: last meal portion; cook-total capacity exhaustion across different meals; partial reservation rollback; accept vs expiry vs cancel; duplicated order after unknown response; same idempotency key/different payload; stale quote after price/address/zone/policy update; provider failure after commit; worker crash before/after send; duplicate ledger/refund/settlement source event; platform restart with pending jobs; slot cutoff around Cairo offset changes.

Use barriers/latches or deterministic fault injection, not sleeps that merely make races unlikely. Assert final database state: exactly one correct order/allocation, nonnegative counters within capacity, balanced entries and deduped outbox/inbox. A 200 response alone is insufficient.

## Cross-client and role matrix

Execute primary delivery and pickup journey as customer web, Android and iOS. Execute cook acceptance/preparation/handover and ops exception/finance via backoffice. Verify identical monetary snapshots and order timelines across clients. For each resource test anonymous, owner, other customer, owning cook, other cook and relevant/irrelevant staff grants.

Browser sizes: 360, 390, 768, 1440 CSS pixels; Arabic RTL and English LTR; long/empty/mixed-script content. Test current supported Chrome, Firefox and Safari/WebKit targets selected in P01, not browser-name assertions without actual execution. Native: emulator/simulator plus physical devices as specified in the mobile plan. Record exact version/device rather than claiming all devices supported.

## Fixtures

Synthetic identities: customer A/B, approved cook A/B, pending and suspended cooks, support/ops/finance/admin with distinct grants. Meals: published, draft, paused, archived, missing-allergen draft; stock: zero/one/plenty; zones: served/unserved/inactive; slots: open/cutoff/past/timezone boundary. Orders cover every state; finance covers due, collected, disputed, refund and remittance. Fixtures are versioned, resettable and cannot be loaded in production accidentally.

Synthetic profiles must not look like real approved businesses. Mail sink/fake SMS uses test recipients only. Test cleanup targets its own run namespace; never truncates shared/production data.

## Evidence and results

Allowed scenario/check results: NOT_RUN, PASS, FAIL, BLOCKED. SKIPPED is not a passing mandatory result. Evidence includes source SHA, environment/tool versions, start/end, command, exit code, result, sanitized log/artifact path and observed outcome. Screenshots/recordings accompany UI checks; machine outputs accompany tests. Human review includes reviewer, scope, date/reference and findings; label self-review honestly.

Use [evidence template](../../verification/templates/evidence.json). Store raw reports under ignored `verification/local/`; inspect/redact before committing selected evidence to `verification/runs/<run-id>/`. Never commit secrets, production data or private reviewer transcripts. Evidence referencing a source commit is not automatically valid for a later source change; rerun impacted tests and record the new SHA.

## Commands supplied now

```bash
python3 scripts/validate_blueprint.py
python3 scripts/next_task.py
python3 scripts/verify.py --phase P04
python3 scripts/validate_blueprint.py --phase P04 --commit <tested-source-sha>
```

The first validates planning structure. The second shows eligible tasks. The third executes configured checks and records a local report; initially all product phase checks are unconfigured and return BLOCKED (exit 2). The fourth validates promotion prerequisites and evidence for a completed phase at an exact source SHA; it cannot pass until the relevant implementation is complete.

`verify.py` is a bounded command/evidence runner, not an autonomous daemon or proof against a dishonest agent. CI and owner review remain necessary. It never edits backlog status or manufactures scenario results. In MB-008 configure real command arrays, bounded timeouts and actual evidence checks in `planning/gates.json`; never replace them with echo-success or empty wrappers.

## Defect severity and reruns

Critical: cross-account leak, financial corruption, overselling/duplicate charges/orders, credential exposure, inability to safely fulfill. High: broken primary journey, permission bypass, wrong totals/status, failed native target, unrecoverable data loss. Medium: significant recoverable secondary-flow/UX defect. Low: minor cosmetic/content defect.

Critical/high defects block affected release; medium/low exceptions need explicit owner acceptance with workaround, expiry and follow-up story. Fix root cause; rerun focused reproducer plus affected integration/role/client regressions. Broaden tests only for concrete affected risk. Capture initial failure and repaired result; do not rewrite history to make every run green.
