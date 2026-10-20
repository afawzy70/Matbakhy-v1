# Independent review record (handbook and DOC-001)

> **Owner role:** Handbook navigation
> **Status:** REFERENCE — record of the independent Codex reviews of this work; Codex is an AI reviewer, not human approval

Reviews ran read-only against the branch docs/developer-handbook-doc001 (branch name) with the Codex CLI, separate from the agents that wrote the pages. Reviewers verified claims against code (each report lists 25–30 verified claims) and could not run the Java/Docker stack. Nothing here is a human approval; the owner's explicit merge approval is still required.

## Round 1 (commit `6c94a7c`)

| Review | Result | Resolution |
|---|---|---|
| Technical accuracy | 0 High, 8 Medium: preview fixtures/providers boundary, idempotency 24 h not enforced, module diagram direction, V21 erasure exception, deposit and ER diagram errors, MFA enable/disable wording, 503 claim | all fixed after checking the code; pages and diagrams corrected |
| Business accuracy | 3 High (admin console exposure and master-admin MFA; finance wording reversed who holds money; support-case linkage unenforced), 7 Medium (cash NOT_DUE exception, paused kitchen, revision vs price change, deposit diagram, COMMISSION_REVERSAL, discovery without slot, browser coverage overstated) | all fixed; the unenforced support-case reference is documented as a gap |
| Developer experience | 2 High (bootstrap executes PR code; Day-2 lab not runnable), 7 Medium (review events, `cd backend` blocks, request-id log search, E2E artifacts deleted, handbook check failing, fake-provider guard, PR template statement) | all fixed or documented |
| Governance and CI | 4 High (workflow sourcing, bootstrap execution, deleted/whitespace docs counted, approvals not bound to commit), 8 Medium (comment cancels approval, matrix gaps, non-doc citations, hidden declaration, shallow fetch, Markdown fidelity, broken baseline, invalid example) | gate hardened (fail-closed base script, commit-bound approvals, substantive-change test, comment/fence-proof declaration, GitHub-compatible anchors, 100 % tracked-file matrix coverage test); workflow sourcing documented as NOT VERIFIED |

A fresh-developer simulation (a context-free agent on a clean clone) ran in parallel: [fresh-developer-simulation](fresh-developer-simulation.md).

## Round 2 (commit `5238465`)

| Review | Result | Resolution |
|---|---|---|
| Governance | 1 High (dismissal after a green run is not re-evaluated), 2 Medium (nested fences hide a declaration; repository-root links break navigation) | fences now close only with a matching delimiter; navigation resolves root links like validation (regression tests added); the dismissal gap is documented as a known limitation with the Code Owners ruleset as the authoritative control, and the workflow comment no longer asserts unverified sourcing |
| Content | 0 High, 5 Medium (lab said local MFA is impossible; handover override overstated; two diagrams not synchronised; owner case wording) | all fixed; owner cases CFG-001 and CFG-121 reworded and the generated catalog re-rendered (`owner_e2e_catalog.py check` passes) |

Environmental note: in the governance reviewer's sandbox the temporary directory was not writable, so its run of `scripts/test_doc001_gate.py` errored for that reason; the same tests pass here (31 tests).

## Round 3 (commit `2f2e05b`)

| Review | Result | Resolution |
|---|---|---|
| Governance and content, all round-two findings re-checked | Every round-two finding confirmed fixed (nested fences, root-link navigation, lab MFA wording, handover override, both diagrams, owner case wording); 1 new Medium: declaration fields could be hidden in an HTML comment inside the marker block | fixed in the gate (any HTML comment inside the block is rejected) with a regression test; **this last one-line fix was verified by the test suite only and was not re-reviewed by Codex** |

The dismissal-after-green limitation is accepted as documented: the authoritative control is the Code Owners ruleset requirement, which is **not installed yet** (no ruleset exists on develop; see [documentation maintenance](../05-developer-workflow/documentation-maintenance.md#branch-protection-status)).

## What the reviews do not establish

No human review; no execution of the GitHub workflow on a real pull request; no live verification of branch-protection behaviour; no Java, Docker, Playwright or native runs.
