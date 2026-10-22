# SAST results

Source: `b7ec0e9052a612f7a8aab38f00519107b1dce213`. Semgrep 1.181.0, Gitleaks 8.30.1. Commands, timestamps, exit codes and redacted machine output: [evidence](evidence/). Scanner output is not a vulnerability verdict.

## Executed coverage

Semgrep completed (exit 0): 418 rules, 471 tracked targets, approximately 99.2% parsed lines. Rulesets: `p/security-audit`, `p/owasp-top-ten`; telemetry/version checks disabled. Excluded docs, verification, planning and pnpm lockfile from code SAST. Registry rules were not pinned by digest; result includes rule IDs and metadata. [Raw JSON](evidence/semgrep.json), [execution](evidence/semgrep.execution.json).

Coverage is partial: three rule timeouts in `apps/customer-web/src/App.test.tsx`; partial Bash parsing in preview-images workflow snippets, Android/iOS test scripts, mvnw, deploy/e2e/smoke helpers, db-restore and dev.sh. These were not silently suppressed. Production controls were also reviewed manually. Exact warning paths/spans remain in JSON. No unexecuted rules are represented as PASS.

| Warning | Triage |
| --- | --- |
| .npmrc missing minimum release age | Supply-chain hardening suggestion; repository uses pnpm rather than npm for installs; not an exploit |
| pnpm blockExoticSubdeps, minimumReleaseAge, trustPolicy | Three separate configuration suggestions; lockfile/engine pinning exists, but these protections are absent. No malicious dependency resolution demonstrated |
| scripts/record_evidence.py subprocess from spec | Trusted local verification command runner, argv list with no shell=True. Operator-controlled command specification is its purpose; no remotely exposed injection path found |
| scripts/rehearsal/load.py dynamic urllib URL | URL built from operator-supplied domain with fixed HTTPS prefix; arbitrary file-scheme attack not established. Manual inspection did confirm disabled TLS checks (SEC-003) |

Gitleaks redacted directory scan completed (exit 0), no detected secrets, [JSON](evidence/gitleaks.json). This initially covers current checked-out files, not a guarantee that no sensitive data exists or that every historical commit is clean. Do not infer clean Git history from this result. Any separate history scan is identified by its own execution evidence.

## Manual security findings

SEC-001: compiled unchanged MediaInspector/MediaPolicy/MediaLibrary on temporary checksum-verified JDK 21; benign local fixture reproduced escaped-name PDF acceptance. See [reproduction](evidence/pdf-inspector-reproduction.json) and [harness](evidence/PdfAudit.java). This is an executed component test, not an HTTP upload or browser-code-execution test.

SEC-002: baseline private approved bytes reuse the outstanding upload key; independently inspected CR-003 patch `2be5a953319a5fef51efdf7b97f3fd6b9b6528eb` seals bytes to a distinct key. Runtime replay retest NOT TESTED. SEC-003: source-confirmed TLS verification disabled in rehearsal helper. SEC-004: missing CSP verified through live headers and proxy source.

Manual SQL review found parameter binding in reviewed services; concatenated order scope, state filters and sort fragments inspected were server constants/validated choices. No verified SQL injection, Java native deserialization, unsafe polymorphic request typing or production frontend HTML injection sink was found in the examined paths. This is bounded review evidence, not proof of absence.

## Publication follow-up

A separate redacted Git-history scan of the baseline and ancestors completed: **336 commits**, approximately 44.25 MB, no detected secrets. See [history JSON](evidence/gitleaks-history.json) and [scanner summary](evidence/gitleaks-history.stderr). A separate directory scan of the proposed audit publication also found no detected secrets. Neither result is a proof that all historical sensitive data classes are absent.
