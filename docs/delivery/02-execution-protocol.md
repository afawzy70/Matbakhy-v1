# Autonomous implementation, verification and recovery protocol

## What this repository provides

Persistent context, a bounded backlog, explicit business invariants, verification definitions, a state checkpoint, a next-task helper and a fail-closed verification runner. It does not itself install an always-running Claude session, restart a crashed agent or create a live portfolio supervisor. Claude operates while its host/session runs. Resume prompts plus Git/checkpoints recover progress; a later host supervisor can consume the same files.

## Session start

1. Read `CLAUDE.md`, decisions, execution state, backlog and applicable product/engineering specifications.
2. Inspect `git status`, branch, remote/head, recent commits and existing work. Never reset/clean another person's changes. Check actual repository rules and CI; do not assume merge rights from a document.
3. Run blueprint validation. Inspect recorded evidence and distinguish PLANNED, IN_PROGRESS, BLOCKED, DONE from real execution.
4. Reconcile stale state against source/tests; a checkpoint is a hint, not proof that its last operation committed.
5. Choose the earliest eligible story whose dependencies are DONE and whose specific required decision/environment is available. If a story is blocked, record its reason and choose independent eligible work.

## Bounded work loop

`SELECT → SPECIFY → IMPLEMENT → VERIFY → REVIEW → REPAIR (if needed) → CHECKPOINT → SELECT`

For each story:

- State the goal and acceptance scenarios. Read relevant files and contracts; do not rely only on a broad master prompt.
- Identify affected permissions, money/stock rules, migrations, localization, external adapters and failure paths.
- Implement one coherent vertical increment; avoid broad unrelated refactoring and invented framework infrastructure.
- Run focused domain/component checks, real integration checks for persistence/external contracts, and client/native checks where affected. Inspect output, not just process launch.
- Review correctness/security/UX/docs and actual diff. Self-review is allowed but labelled self-review. Never fabricate an independent reviewer.
- If a check fails, preserve reproduction/evidence, find cause, fix, rerun affected checks. A blocked tool/environment is not a code failure and not PASS.
- Capture tested source SHA, commands, environment, timestamps, exit codes, logs/artifact hashes, scenario results, review findings and residual risks. Remove sensitive data before committing evidence.
- Commit code, then store evidence referencing that source commit. Evidence-only follow-up commits do not imply the code changed; if source changes, rerun affected checks and reference the new source SHA.
- Mark story DONE only after mapped scenarios PASS, dependencies DONE, evidence validates and review is PASS. Update execution state with next actionable story. Continue automatically through available work.

## Checkpoint and heartbeat schema

`planning/execution-state.json` records current phase/story, last tested source commit, completed story/gate, branch, blockers, next action and update time. Story status lives in backlog; scenario status in scenarios; detailed run evidence in `verification/runs/`. Never mark a batch DONE merely because a phase command finished.

During a long active operation record heartbeat with run ID, PID/job reference, startedAt, lastProgressAt, bounded timeout and current step. A heartbeat means the process is alive; it does not establish useful progress. Use visible artifacts/test output/checkpoint changes to detect progress. If a host supervisor is later built, it must distinguish alive, progressing, stalled, blocked and finished.

No indefinite retry. Default three repair attempts per root cause, with a documented changed hypothesis each time. Long build/test timeout is explicit and scaled to the operation, not infinite. On timeout terminate only the owned task process, preserve logs and checkpoint before retry. Avoid concurrent writers to the same worktree. Optional future supervisor needs a lease, run ID and idempotent resume before launching an agent.

## Autonomy boundaries

Proceed without routine permission for code/tests/refactoring required by the selected story, local fixtures, documentation, bounded repairs and review. Do all reviewable preparation before a human checkpoint.

Pause only the affected action when actual owner input is required: unresolved material product policy, reviewable UX approval, real-money credentials/spend, public deployment/store release, destructive production migration, changed access policy or necessary external account. Record exact question, default/recommendation, affected tasks, evidence ready for review and independent work that can continue. Do not turn routine design/technical choices into a permission queue.

The current request authorizes creating the planning repository. Later implementation uses the start prompt's authorized scope. Public release and merge authorization are not implied by a green test. Follow actual owner instructions and branch protection; never bypass them.

## Stop, pause and resume

Owner `pause` means finish only the minimum safe in-flight boundary, checkpoint, report and await resume. `stop` means stop new work, preserve changes/logs and give exact state. `resume` re-inspects repository and evidence, then selects eligible work; never repeats a charge, deploy, submission or order because a prior response was lost.

## Status report format

```text
MISSION=MATBAKHY_MVP
PHASE=Pxx
STORY=MB-xxx
STATUS=IN_PROGRESS|DONE|BLOCKED|FAILED|PAUSED
SOURCE_COMMIT=<tested SHA or NOT_TESTED>
IMPLEMENTED=<concrete behavior>
CHECKS=<executed checks and results>
EVIDENCE=<paths>
OPEN_DEFECTS=<IDs and severity>
BLOCKERS=<specific dependency, owner, next action>
NEXT=<exact next story/action>
```

Give the owner short meaningful progress updates during work. Never report “all green” when mandatory checks are skipped or not run. Never call documentation validation product verification, prototype completion product delivery, or app upload store publication.
