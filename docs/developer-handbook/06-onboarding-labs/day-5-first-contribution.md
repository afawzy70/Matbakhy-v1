# Day 5 - First contribution

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — lab for delivering one small real change end to end under the repository's rules.
> **Language / اللغة:** English · [العربية](day-5-first-contribution-ar.md)

## Objectives

Deliver a bounded, real improvement: find a small GitHub issue, change code or documentation, add tests, update the handbook per DOC-001, open a compliant PR and respond to review.

## Before you start

Re-read [git and PR workflow](../05-developer-workflow/git-and-pr-workflow.md) and [documentation policy](../05-developer-workflow/documentation-policy.md). Rules that apply today: base on `develop`; never touch `main`; never merge yourself; no secrets or real data; do not weaken tests or security.

## Steps

1. **Choose a small real issue.** List what exists; do not assume numbers:
   ```bash
   gh issue list --state open --limit 50
   gh label list
   gh issue list --state open --label <label-from-the-list-above>
   ```
   Prefer an issue that is reproducible, confined to one module or surface, fixable in a few hours, and has no pending owner decision. Labels vary: use whatever the label list shows for small, documentation, bug or "good first" style work. If there are none that fit, ask the owner to point you at one; a defect in the handbook itself found during Days 1-4 also qualifies (open the issue first). Avoid anything touching money movement, authorization rules, migrations on shared tables or deployment scripts for a first change.
2. **Understand it.** Reproduce the problem locally (stack from [local environment](../05-developer-workflow/local-environment.md)). Find the code with the Day-3 technique. Locate the business rule or scenario it concerns (`planning/rules.json`, `planning/scenarios.json`) and the handbook pages that describe the area (use `docs/developer-handbook/doc001-matrix.json` to see which pages the area maps to).
3. **Plan in the issue.** Comment with: reproduction, intended behaviour, the files you expect to change, the tests you will add, the handbook pages affected. Wait for agreement if the scope is unclear.
4. **Branch.** `git fetch origin && git switch -c <kind>/<short-description> origin/develop`.
5. **Test first where feasible.** Write a failing test that captures the defect or the new behaviour (unit, component, or `@IntegrationTest` as appropriate). Run it and keep the failing output.
6. **Implement.** Smallest change that passes. Keep server-owned rules on the server; keep money in integer piastres; do not duplicate business logic in a client.
7. **Verify.** Run the affected suites and their neighbours ([test execution](../05-developer-workflow/test-execution.md)), plus `pnpm contract:check` if contracts changed and `python3 scripts/validate_blueprint.py` if planning changed. Record exact commands and results; list anything you could not run as NOT RUN.
8. **Update the handbook (DOC-001).** Change every handbook page the matrix names for the area you touched, and any other page that now says something untrue. Keep the page header (owner role, status) correct. Run:
   ```bash
   python3 scripts/doc001_check.py docs
   ```
   If your change truly needs no documentation change, you must say so with a specific justification; that is judged by a reviewer, not by the script.
9. **Commit.** Scoped commits with clear messages; no unrelated edits.
10. **Open the PR** against `develop`: `gh pr create --base develop`. Fill every section of the template (Problem; Changes; Verification with real commands and results; Review and risks) and the **Documentation Impact declaration** exactly as specified in [documentation-policy.md](../05-developer-workflow/documentation-policy.md) between `<!-- DOC-001:BEGIN -->` and `<!-- DOC-001:END -->`. Link the issue. State honestly what you did not run. Do not claim independent review.
11. **Check CI.** `gh pr checks` until required jobs finish; read failures with `gh run view <run-id> --log-failed` and fix them with new commits (no force-push).
12. **Respond to review.** Answer every comment: fix it, or explain with evidence why not. Re-run affected checks after changes and update the PR's Verification section. If a reviewer finds a documentation inaccuracy, fix the page in the same PR. Do not merge; the owner or an authorized maintainer decides.

## Deliverable

An open PR (or a draft PR if the owner prefers) that has: a linked real issue; at least one new or changed automated test; handbook updates or a reviewer-credible justification; a completed DOC-001 declaration; green required checks or an honest list of failing ones; and your replies to review comments.

## Self-check

- [ ] The issue is real and I did not invent numbers or acceptance criteria.
- [ ] My test fails without the change and passes with it.
- [ ] `python3 scripts/doc001_check.py docs` passes and the pages say what the code does.
- [ ] The PR lists exactly what I ran, and what I did not.
- [ ] No secrets, real data or unrelated edits are in the diff (`git diff origin/develop --stat`).
- [ ] I did not touch `main`, and I did not merge.

Then take the [final assessment](final-assessment.md).
