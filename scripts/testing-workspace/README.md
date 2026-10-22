# Matbakhy Testing Workspace

Status: PARTIALLY IMPLEMENTED. All 200 canonical IDs and 1005 canonical steps are represented; 1/200 guides (E2E-ORDER-101) is independently reviewed for source-grounded instruction completeness, while 199 remain drafts; guidance completeness is tracked separately in the [coverage matrix](../../docs/quality/testing-workspace/coverage-matrix.md). Draft companions do not count as complete, reviewed execution guidance. No live acceptance results are claimed.

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

## Approved tutorial design

The [owner-supplied HTML](../../docs/quality/testing-workspace/reference/approved-prototype.html) is the visual authority for this workspace, replacing the earlier inferred dashboard. Its source bytes and SHA256 are archived. The implementation retains the 940px centered canvas, ivory/green palette, Cairo, rounded cards and fully visible step controls. Cairo is self-hosted under fonts with its SIL license.

Open http://127.0.0.1:8765/#/case/E2E-ORDER-101 for the reference scenario: 18 chronological preparation/execution/verification steps plus a final mandatory original-outcomes check (19 visible controls). Ten canonical steps retain their original IDs, and each of the eight original prerequisites maps explicitly into that flow. Setup steps do not count toward the 1005 canonical steps. Keycloak, private credential sources, fixture provisioning, kitchen capacity, API/SQL verification and troubleshooting are adjacent to the relevant action. Only optional source references are collapsed; there is no Developer Details section.

All 200 scenarios use this visible-step presentation. The other 199 retain source-specific original prerequisites and outcomes, but their generic Arabic setup summaries still require case-specific review and are not counted complete. Runtime readiness and guide completeness are separate: even the complete ORDER-101 guide remains NOT_RUN or BLOCKED until its actual environment/fixtures/evidence are available.

Release/environment metadata is per scenario in tab memory. Both the PASS button and export independently validate current guide completeness, all setup/canonical/verification keys, evidence and the case's release. A resumed/stale PASS without those conditions exports BLOCKED while preserving recorded_status. Refresh preserves safe checks/status only, never secrets or narrative evidence.

Browser checks (Python Playwright with Chromium installed):

```bash
python3 scripts/testing-workspace/browser_check.py
python3 scripts/testing-workspace/reference_check.py
```

The second script compares computed geometry and tokens against the archived HTML at desktop/mobile widths, using the same self-hosted Cairo font in both views. It writes screenshots and visual-comparison.json under verification/runs/20261022-WORKSPACE-REDESIGN. This is not a claim of pixel-identical content or a successful live order. See the [comparison and limitations](../../docs/quality/testing-workspace/design-comparison.md).
