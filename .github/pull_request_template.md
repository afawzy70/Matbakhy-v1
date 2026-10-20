## Problem and intended behavior

What requirement/defect does this change address, and why does it matter?

## Changes

Story IDs, changed behavior, contracts/migrations and affected surfaces.

## Verification

Tested source commit, actual commands/results and evidence paths. List NOT_RUN/BLOCKED checks explicitly. Include authorization and stock/finance regressions when affected.

## Review and risks

Review findings, repairs, residual risks, required owner decisions and deployment/rollback implications. Do not claim independent review or approval unless it occurred.

## Roles and review evidence

Record the **actual** roles. The project currently shares one GitHub account, so GitHub cannot verify who is who; state it anyway.

- Author (owner / Mohamed / AI agent and session):
- Reviewer (owner / Mohamed / none yet) and what they compared:
- Codex code review: (link or NOT_RUN) — supporting evidence only, never equivalent to a separate human approval:
- Codex security review: (link or NOT_RUN, required for auth, Keycloak, payments, workflows and governance changes):
- Owner authorization needed? (governance, branch protection, workflow enforcement, agent instructions → yes; link the owner's `OWNER-AUTHORIZES-GOVERNANCE` comment):

## Documentation Impact (DOC-001 — mandatory; "NO DOCUMENTATION ASSESSMENT — NO MERGE")

Every PR (features, fixes, refactors, security, Keycloak, API, migrations, UI, CI/CD, config, dependencies, tests and documentation-only changes) states how documentation is affected. Policy: [documentation policy](../docs/developer-handbook/05-developer-workflow/documentation-policy.md); which docs belong to which code: [change matrix](../docs/developer-handbook/doc001-matrix.json). The required check `Documentation Consistency / DOC-001` parses the block below; a missing, malformed or empty block fails it. The check proves structure and reference integrity only — a reviewer must compare the documentation with the code, tests and requirements (never accept "no docs needed", "minor change", "internal only", "bug fix", "no user impact").

Fill **exactly one** block. Lists are comma-separated repo-relative paths that must exist and be changed in this PR. `impact: updated` needs `summary`, `consistency` and at least one of `handbook_pages` / `other_docs`; `impact: none` needs a specific `justification` (≥ 80 characters) citing the existing page that stays accurate and why. Changing the gate, the verifier, the policy, `AGENTS.md`, `CLAUDE.md` or this template (a governance change) also needs `governance_review` (actual author and reviewer roles, Codex code/security review evidence or NOT_RUN) and the repository owner's comment consisting only of `OWNER-AUTHORIZES-GOVERNANCE <first 12 characters of the head commit>`; no separate GitHub approval is required while one account is shared, and Codex output is never an approval.

<!-- DOC-001:BEGIN -->
impact:
summary:
handbook_pages:
other_docs:
diagrams:
contracts:
tests:
traceability:
consistency:
justification:
governance_review:
<!-- DOC-001:END -->

Also answer in prose: changed behaviour and why; affected applications/modules and business rules; handbook pages, other authoritative documents, diagrams, contracts and test cases updated; traceability references updated; documentation that needs no change and why it stays accurate; confirmation that implementation and documentation agree (what you compared).
