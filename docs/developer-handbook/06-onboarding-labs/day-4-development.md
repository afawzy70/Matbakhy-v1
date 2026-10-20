# Day 4 - Development workflow

> **Owner role:** Testing and QA
> **Status:** REFERENCE — lab for building, testing, debugging a defect and preparing a PR locally.

## Objectives

- Run the same checks CI runs, locally.
- Write a unit test and an integration test and see them fail then pass.
- Reproduce, fix and regression-test a practice defect on a throwaway branch.
- Understand CI, the preview deployment and the DOC-001 gate.

## Reading list

[Test execution](../05-developer-workflow/test-execution.md), [run and debug](../05-developer-workflow/run-and-debug.md), [git and PR workflow](../05-developer-workflow/git-and-pr-workflow.md), [CI/CD](../05-developer-workflow/ci-cd.md), [preview environment](../05-developer-workflow/preview-environment.md), [documentation policy](../05-developer-workflow/documentation-policy.md), [documentation maintenance](../05-developer-workflow/documentation-maintenance.md), [shared libraries](../04-codebase/shared-libraries.md).

## Hands-on tasks

1. Branch: `git fetch origin && git switch -c fix/day4-practice origin/develop`. This branch is for practice and is never pushed for merge.
2. Run the CI-equivalent checks and record the result of each (PASS only if you saw it pass):
   ```bash
   pnpm install --frozen-lockfile
   pnpm typecheck && pnpm lint && pnpm test && pnpm build
   pnpm contract:check
   (cd backend && ./mvnw -B -ntp verify)
   python3 scripts/validate_blueprint.py
   python3 -m unittest discover -s scripts -p 'test_*.py'
   python3 scripts/doc001_check.py docs
   ```
   Use `scripts/check_workspace.sh` for a clean-clone run of the committed HEAD.
3. Unit test. In `packages/api-client/test/money.test.ts` find how money formatting is tested. Add one new case for a value you choose (for example zero piastres and a large amount), run `pnpm --filter @matbakhy/api-client test`, and confirm it passes. Then break the implementation on purpose, see the test fail, and revert the break.
4. Backend test. Pick one class from `backend/app/src/test/java/com/matbakhy/app/orders`. Run it alone with `scripts/check_backend_suite.sh <ClassName>`. Read the output "PASS: <class> (n tests)". Rename the class argument to something wrong and read the failure message.
5. Practice defect. Introduce a deliberate, tiny bug in a place that has tests (for example change a comparison in a web component under `apps/customer-web/src` that a `*.test.tsx` covers). Run the tests, find which test caught it, explain why, fix it. If no test catches it, write the missing test first and show it failing.
6. Browser E2E: read `apps/operations-web/e2e/money-reflow.e2e.ts` and, if Docker and Chromium are available, run `scripts/dev.sh e2e` (long; uses a throwaway stack). Otherwise record it as NOT RUN.
7. CI reading: open `.github/workflows/ci.yml` and `.github/workflows/deploy-preview.yml`. Answer in writing: which job proves the local stack boots? What must be true before images are built? What does the pipeline not roll back?
8. Preview: ask the owner for allow-listing if needed, then run `sh deploy/web-preview/smoke.sh <PREVIEW_DOMAIN>` against the preview (read-only). Note which checks it covers.
9. DOC-001 dry run: edit one handbook sentence on your practice branch, run `python3 scripts/doc001_check.py docs`, then write (do not submit) the PR declaration block from the policy page.
10. Discard the practice work: `git switch develop && git branch -D fix/day4-practice` (your own branch only).

## Deliverable

A results table: command, result (PASS / FAIL / NOT RUN), notes. Plus the failing-then-passing test you wrote, and a two-paragraph explanation of the practice defect (cause, detection, fix, regression test).

## Self-check

- [ ] I never reported PASS for something I did not run.
- [ ] I understand why integration tests use real PostgreSQL and why mocks would not be acceptable substitutes.
- [ ] I can explain why my local run might differ from CI (clean clone, pinned versions).
- [ ] I can read a CI log and find the first failing step.
- [ ] I know what DOC-001 requires in a PR and where the declaration goes.

Next: [Day 5](day-5-first-contribution.md).
