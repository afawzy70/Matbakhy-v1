# Matbakhy Testing Workspace

Status: PARTIALLY IMPLEMENTED. All 200 canonical IDs are represented; guidance completeness is tracked separately in the [coverage matrix](../../docs/quality/testing-workspace/coverage-matrix.md). Draft companions do not count as complete, reviewed execution guidance. No live acceptance results are claimed.

From the repository root:

```bash
python3 scripts/testing_workspace.py
python3 -m http.server 8765 --bind 127.0.0.1 --directory scripts/testing-workspace
```

Open http://127.0.0.1:8765/ and choose **ابدأ الاختبار · J0**. Start with CFG-001, record the deployed RELEASE_COMMIT and confirm the preview access requirements. Source links are pinned to the develop baseline; generated input hashes identify canonical and supplemental content. Live URLs are documented references, not availability checks.

The site is static and does not call Matbakhy's backend, provision fixtures, change Keycloak or run SQL. The application links open separately. The default environment is the documented shared preview; isolated/native cases remain blocked until their environment is provided. Never substitute local realm/accounts for preview fixtures without a reviewed mapping.

Step/check IDs and result statuses persist in this browser's local storage. Passwords, notes, evidence, defect text and environment/release entries are held only in tab memory. Reloading loses them. A resumed result is explicitly marked as missing evidence in exports unless a tester supplies evidence and the tested release again. Export requires a separate preview and confirmation. Redaction removes entered credential history and common token forms, but manual inspection is still required.

To regenerate after catalog/guidance edits, rerun the generator. Do not edit catalog.json directly. The generator fails on missing/duplicate canonical IDs and produces a matrix that preserves pending review and blockers. Review approval is explicit per case and requires source references; field presence never grants completeness.

Checks:

```bash
python3 scripts/testing_workspace.py --check
python3 scripts/test_testing_workspace.py
node --test scripts/testing-workspace/core.test.mjs
node --check scripts/testing-workspace/app.js
python3 scripts/doc001_check.py docs
python3 scripts/test_doc001_gate.py
```

Browser QA is a separate local-site check; see browser_check.py. Passing website tests does not mean any of the 200 product scenarios passed.
