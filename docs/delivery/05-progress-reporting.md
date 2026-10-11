# Published progress and external review contract

## Purpose and authority

Enable the owner and scheduled read-only reviews to inspect implementation without repeated chat prompts. This supplements the execution protocol; it never weakens acceptance criteria or gate evidence. All timestamps include an offset. Product scheduling uses Africa/Cairo; owner reporting days use Asia/Kuwait. The owner's current review schedule is approximately 08:00, 15:00 and 19:00 Kuwait time, with an evening daily summary. Scheduled review is external to this repo and does not control Claude, run tests or guarantee continuous execution.

## Branch and publication

1. Inspect actual Git status, remote heads, open PRs and the execution checkpoint. Reuse a documented active implementation branch. Otherwise create `implementation/mvp` from current main, preserving user work.
2. Record the branch in `planning/execution-state.json`. Keep one discoverable draft implementation PR where access permits. The default branch can lag; reviewers must inspect the active branch and PR head.
3. Commit scoped work after a completed story, before a deliberate pause, and at a safe boundary during long active work (aim for 30–60 minutes). Push when credentials/network permit. Do not create empty activity commits or leak secrets, private logs or customer data.
4. WIP publication is allowed on the implementation branch, labelled IN_PROGRESS/BLOCKED with incomplete checks. It must never be counted as DONE or merged as a passing release. Do not commit broken verification configuration to hide a failure.
5. Fetch before pushing. Reconcile concurrent changes without force-pushing or discarding work. Respect repository protections. An unmerged PR does not require waiting to continue authorized work on the same implementation branch. Merge only under explicit authorization or applicable repository policy.
6. If publication fails, preserve local commits and record the failure. Continue independent local work. Report publication status separately from implementation status; do not claim remote reviewers can see unpublished work.

## Checkpoint content

Update existing planning schemas without casually changing their meaning. Keep the execution state, story statuses, scenario statuses and gates consistent with observed evidence. Use the existing heartbeat field only for a real observed checkpoint, never a fabricated background activity signal.

Create `docs/status/latest.md` at the first implementation checkpoint and update it thereafter. Maintain `docs/status/daily/YYYY-MM-DD.md` cumulatively for the Kuwait calendar day; retain previous checkpoints and do not rewrite failures out of history. These reports are implementation-agent reports, not independent review certificates. The scheduled reviewer reports separately to the owner.

Each report must contain:

- Observation time and reporting period, active branch, PR link if available, current phase/story, and next eligible task.
- Full implementation source SHA and evidence run identifier/path. Evidence committed after the tested source must explicitly name that earlier source SHA. Never use a placeholder or circular claim that the evidence commit tested itself.
- Concrete behavior added or changed, separated into backend, customer web, cook/platform operations, Android and iOS. Mark untouched surfaces clearly.
- Commands actually executed, exit/result, mandatory checks NOT_RUN/BLOCKED/FAIL/PASS, useful redacted artifact links and actual CI run URLs/status when available. Distinguish queued/running CI from passed CI. Missing CI access is unknown/BLOCKED, not PASS.
- Scenarios passed or failed for the story, review method and findings, defects repaired, unresolved regressions, and phase gates remaining.
- Blockers with stable identifiers, affected task IDs, owner, first-seen time, concrete missing prerequisite, attempts made and next action. Batch owner decisions with recommendations and links to reviewable artifacts.
- Publication state, dirty or unpublished work if present, latest known remote head, and exact safe resume action/command.
- Counts of completed stories and remaining stories if useful, explicitly not a percentage of engineering effort or release readiness.

Use repository-relative links for files and full GitHub links for source commits, PRs and Actions runs. Confirm destinations exist. Do not put credentials, real personal information, signed private URLs or unredacted production payloads in reports.

## Evidence sequence

Implement and review a bounded change; create the source commit; execute required verification against that source; retain reproducible results under `verification/runs`; update planning and reporting with that source SHA; commit evidence/status separately and push both commits. If application/test/configuration files change after verification, create a new source commit and rerun affected verification. Use the repository's existing verification runner and evidence schema; do not invent a competing completion mechanism. Documentation-only evidence commits may refer to the tested source but must not imply a subsequent application change was tested.

DONE requires the repository's mapped scenarios, review and mandatory evidence, not merely a green documentation-integrity workflow. Check independently for Android and iOS native evidence. Missing macOS, signing, devices or provider credentials remain specific blockers. Self-review must be labelled self-review. A phase is gated until all its mandatory conditions hold.

## External review procedure

Read main for instructions, then discover the active branch from execution state, this branch convention and open implementation PRs. Inspect branch checkpoint files, current PR head, recent relevant commits, verification artifacts and actual CI results. Compare the same source revisions; distinguish current failures from earlier repaired runs. Report claimed completion separately from corroborated evidence. Do not equate story counts with effort, passing unit tests with release approval, or no recent commits with a stopped agent.

Morning/afternoon reports summarize changes since the last available review, checks, blockers and next work. The evening report covers the Kuwait calendar day with progress per surface, failed/repaired gates, unresolved risks and next priorities. When previous review context is unavailable, say so and use a stated observation window. If GitHub access fails, report the failed observation explicitly. No published changes means runtime state unknown; local unpushed work is invisible.

## Recovery and stopping

Before context loss or deliberate exit, persist the checkpoint and push if possible. Resume by inspecting current instructions, Git state and evidence; never repeat side effects blindly. A prompt cannot survive a closed host, exhausted quota or missing permissions by itself. If runtime interruption happens before publication, the next session must reconcile local work with the last remote checkpoint.

The implementation agent repairs failures under the execution protocol and continues independent eligible work. The scheduled reviewer is read-only: it never modifies code, posts repository comments, merges, deploys or restarts Claude. Raise urgent blockers in the active implementation session rather than waiting for the next scheduled report.
