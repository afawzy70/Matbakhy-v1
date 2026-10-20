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

The dismissal-after-green limitation is accepted as documented. (An earlier version of this record called Code Owners the authoritative control; the final review showed that is not demonstrated — see round 4 and [documentation maintenance](../05-developer-workflow/documentation-maintenance.md#branch-protection-status).)

## Round 4 — final focused review of the activation (after PR #26, the ruleset and the disposable PRs)

| Finding | Result | State |
|---|---|---|
| **High** — with the `pull_request` event a PR can edit `.github/workflows/doc001.yml` and neutralise its own DOC-001 check | **Confirmed on GitHub** with disposable PR #33 (the check passed in 2 s with a no-op job). `pull_request_target` would fix it but runs from `main` (not modified here); rulesets' "required workflows" are unavailable for personal-account repositories (API rejected it) | **OPEN — owner decision** (install a `pull_request_target` workflow on `main`, or accept and review every workflow diff). Documented in the maintenance page |
| **Medium** — governance approval accepted from any non-author login | fix written (owner/member/collaborator only, with test) in PR #34 | **OPEN until PR #34 is applied by the owner** (governance PRs cannot merge without a second identity or a deliberate ruleset relaxation) |
| **Medium** — gate tests do not prove the workflow-level boundary | tests relabelled script-level in PR #34; the real boundary was exercised with the disposable PRs | **OPEN until PR #34 is applied** |
| Low — branch audit reads at most 500 PRs and only `*.yml` workflow files | not fixed; report-only tool, ancestor check still protects unique work | recorded |

The final Codex verdict was "unresolved High/Medium issues remain", which is accurate; this record does not claim otherwise.

## Round 5 — trusted enforcement and single-account governance (branch `governance/doc001-single-account-model`)

Codex (read-only, supporting evidence — not a human approval, and GitHub cannot show who ran it) reviewed the first version of the verifier and the owner-authorization rule. Result: 1 High, 5 Medium, 2 Low and test gaps.

| Finding | State |
|---|---|
| **High** — installation token echoed in the exception of a failed `git fetch` (argument filter matched the wrong prefix) | fixed: credentials travel in the git child's environment, errors name only the subcommand, tokens masked; test added |
| **Medium** — an error kept a previous green and aborted the scan | fixed: every error posts a failure and the scan continues; test added |
| **Medium** — two PRs with one head commit overwrote each other's verdict | fixed: success only if all PRs on that head pass; test added |
| **Medium** — quoted or negated authorization text counted | fixed: marker must be a line of its own, at least 12 hex characters, association required; tests added |
| **Medium** — description or comments could change between judging and posting | fixed for the judging window (final snapshot, fail closed); the revocation window until the next poll is documented |
| **Medium** — no pagination of PRs and comments | fixed; test added |
| Low — 7-character prefix accepted, missing association defaulted to OWNER, branch-protection documentation not in the governance path list | fixed / documentation corrected |

These fixes were tested locally only (`scripts/test_doc001_gate.py`, `scripts/test_doc001_trusted_verifier.py`). A second Codex pass over the fixed code and the live App behaviour are NOT_RUN. The workflow-bypass hole remains open until the App is installed and a copy of PR #33 is observed blocked.

## What the reviews do not establish

No human review; no execution of the GitHub workflow on a real pull request; no live verification of branch-protection behaviour; no Java, Docker, Playwright or native runs.
