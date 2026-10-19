## Problem and intended behavior

What requirement/defect does this change address, and why does it matter?

## Changes

Story IDs, changed behavior, contracts/migrations and affected surfaces.

## Verification

Tested source commit, actual commands/results and evidence paths. List NOT_RUN/BLOCKED checks explicitly. Include authorization and stock/finance regressions when affected.

## Review and risks

Review findings, repairs, residual risks, required owner decisions and deployment/rollback implications. Do not claim independent review or approval unless it occurred.

## Documentation Impact (DOC-001 — mandatory; "NO DOCUMENTATION ASSESSMENT — NO MERGE")

Every PR (features, fixes, refactors, security, Keycloak, API, migrations, UI, CI/CD, config, dependencies, tests and documentation-only changes) states how documentation is affected. Policy: [documentation policy](../docs/developer-handbook/05-developer-workflow/documentation-policy.md); which docs belong to which code: [change matrix](../docs/developer-handbook/doc001-matrix.json). The required check `Documentation Consistency / DOC-001` parses the block below; a missing, malformed or empty block fails it. The check proves structure and reference integrity only — a reviewer must compare the documentation with the code, tests and requirements (never accept "no docs needed", "minor change", "internal only", "bug fix", "no user impact").

Fill **exactly one** block. Lists are comma-separated repo-relative paths that must exist and be changed in this PR. `impact: updated` needs `summary`, `consistency` and at least one of `handbook_pages` / `other_docs`; `impact: none` needs a specific `justification` (≥ 80 characters) citing the existing page that stays accurate and why. Changing the gate, the policy, `AGENTS.md`, `CLAUDE.md` or this template also needs `governance_review` and an approving review by someone other than the author.

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
