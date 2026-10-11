# Claude Code start prompt

Copy everything in the fenced block into Claude Code from the cloned repository root. The instructions select implementation; they do not execute just by being stored here.

```text
MISSION: IMPLEMENT_MATBAKHY_MVP_FROM_REPOSITORY_SPECIFICATION

Repository: https://github.com/afawzy70/Matbakhy-v1

Act as the implementation engineer for the Matbakhy MVP. Deliver customer web, cook/platform backoffice, customer Android and customer iOS, backed by Spring Boot. Egypt is the target market. Work in this repository and preserve existing user work.

First read CLAUDE.md and AGENTS.md. Then read:
1. docs/product/01-context-and-decisions.md and planning/decisions.json
2. docs/product/02-product-specification.md
3. docs/product/03-business-rules.md and docs/product/04-state-machines.md
4. docs/product/05-screen-specification.md and docs/product/06-permissions.md
5. docs/engineering/01-architecture.md through 07-integrations.md
6. docs/delivery/01-roadmap.md and 02-execution-protocol.md
7. docs/quality/01-verification.md and 02-release-gates.md
8. planning/backlog.json, planning/scenarios.json, planning/gates.json and planning/execution-state.json
9. Applicable operations documents and contracts/endpoints.json.
10. docs/delivery/05-progress-reporting.md and docs/delivery/04-resume-and-recovery.md when present.

Inspect actual files, git status, remote branch/head, repository protections, installed tools and credentials availability without exposing secrets. Do not assume any application code exists. Reconcile the checkpoint with actual source and evidence. Run python3 scripts/validate_blueprint.py and python3 scripts/next_task.py.

Treat CONFIRMED requirements as owner direction. PROPOSED_DEFAULT items are the working local/staging implementation baseline for this mission unless I amend them. Do not represent them as historical approvals. OPEN financial/legal/provider/design decisions remain open and block only their dependent actions. Produce concrete reviewable design artifacts before asking for visual approval; continue independent backend/foundation work while waiting.

Start at MB-001 unless verified repository evidence shows it is already done. Follow story dependencies and phase gates. For each story: understand acceptance, implement, execute tests against the appropriate real stack, inspect evidence, review, repair defects, rerun affected tests, checkpoint, commit scoped changes and continue to the next eligible story. Do not stop after producing another plan or scaffold. Do not stop after each ordinary successful story to ask permission. If blocked, record the exact blocker and continue independent eligible work. No subagents are authorized by this prompt; use one agent unless I explicitly request delegation later.

Build the proposed Java 21/Spring Boot/PostgreSQL modular monolith, React/TypeScript customer and operations apps, and React Native/Expo customer mobile app. Verify supported version compatibility and pin it in P01. Use maintained OIDC integration; never invent password/token cryptography. Create validated OpenAPI and generated clients before dependent UI. Use real PostgreSQL for transactional/concurrency tests.

Implement cash-first ordering and cook-arranged delivery/pickup under explicitly synthetic development policies until live commercial decisions are resolved. Keep online-payment/courier/social extensions disabled unless selected through the decision process. Never activate real payments, send real customer communications, spend money, deploy publicly, submit to stores, perform destructive production changes or bypass branch protection without the applicable authorization. Prepare artifacts and evidence so approval is the final step for those actions. Create branches/commits and reviewable PRs where access permits; merging requires explicit authorization or an already applicable owner policy.

Protect these invariants: single cook/slot per order; server-owned totals and permissions; immutable snapshots; idempotent order submission; atomic stock reservation; legal versioned state transitions; cash separate from fulfillment; balanced immutable finance; outbox deduplication; scoped private data; Arabic RTL/English LTR; no secrets/PII in this public repo.

Configure actual verification commands as code appears. Missing checks are BLOCKED. Do not replace tests with echo-success scripts, remove checks, weaken assertions, turn failures into warnings or claim mock-only validation as production readiness. Every DONE story must have all mapped scenarios PASS, review evidence and source-SHA-bound results. Native Android and iOS need separate real build/device evidence. Missing signing/macOS/device access must remain a specific blocker, not a false PASS.

After three materially different unsuccessful repair attempts for a root cause, preserve logs, checkpoint the blocker and move to independent work. On pause/stop, stop new work and checkpoint safely. After interruption, re-inspect source and resume idempotently.

Use an existing documented implementation branch if one is active; otherwise create implementation/mvp from the verified latest main without discarding local changes. Keep related implementation on this integration branch so an unmerged PR does not stall the next story. Publish a draft PR at the first meaningful pushed checkpoint if access permits, describing scope and incomplete gates. Do not merge without applicable owner authorization. Never overwrite concurrent work: fetch, inspect and reconcile before pushing; never force-push. If GitHub credentials are unavailable, continue locally, record publication as BLOCKED and give one precise setup request without exposing credentials.

Follow docs/delivery/05-progress-reporting.md. Push scoped checkpoint commits after completed stories, before a deliberate pause, and at safe boundaries during long work (aim for 30–60 minutes while actively executing). A checkpoint may explicitly be WIP; it is not a passing gate. Publish tested source commits and their evidence without claiming that a later documentation-only evidence commit was itself the tested source. Do not create noisy empty heartbeat commits. Keep docs/status/latest.md and the cumulative Kuwait-date docs/status/daily/YYYY-MM-DD.md current. Review actual remote CI results when available; a successful push is not successful CI. Scheduled external reviews observe GitHub; they do not run, restart or supervise your local process.

Keep planning/execution-state.json, backlog/scenario status and verification/runs current. Report concrete progress regularly: current story, implemented behavior, checks actually run, defects/blockers and next action. Distinguish planning complete, implemented, verified, staged, submitted and released.

For ordinary reversible implementation choices, choose the simplest option consistent with the repository, document the reason and continue. Do not ask me to select libraries or repeat decisions already documented. Do not silently decide unresolved commercial/legal policies: use clearly labelled development fixtures and block only dependent live behavior. If two authoritative requirements conflict, record the conflict and work on independent tasks. Ask only for a decision or access that materially blocks all remaining eligible work, or for a required concrete approval; batch related questions and include your recommendation and exact affected task IDs. Never bypass your runtime's permission or security controls.

Before context limits or session exit, write a durable checkpoint including dirty/unpublished work, last tested source SHA, failures, remaining repair attempts, next command and exact resumption procedure. On resumption re-read repository instructions and validate actual Git/evidence state before proceeding; do not trust conversation memory alone. Do not claim a prompt can keep a closed or quota-limited session alive.

Begin now with the repository preflight and first eligible story. Continue until all authorized unblocked work is complete, I stop you, or every remaining task has a documented external/decision blocker. Return a self-contained checkpoint with exact resume action.
```

## Local starting commands

```bash
git clone https://github.com/afawzy70/Matbakhy-v1.git
cd Matbakhy-v1
python3 scripts/validate_blueprint.py
python3 scripts/next_task.py
claude
```

Install/authenticate Claude Code using its current official instructions if it is not available. On Windows use `python` if `python3` is unavailable. The repository's Python planning tools require Python 3.10+ and no third-party packages. Application toolchain/bootstrap commands will be created and tested in P01.
