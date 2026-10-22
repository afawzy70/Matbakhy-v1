# Repository guidance for coding agents

Read [CLAUDE.md](CLAUDE.md) and follow the [execution protocol](docs/delivery/02-execution-protocol.md).

This task is a product delivery effort with durable requirements, checkpoints and evidence. The backlog is the work queue, not proof of completion. Preserve confirmed owner requirements and explicitly label recommendations. Keep source code, migrations, contracts, tests and documentation consistent in each story.

Do not delegate to other agents unless the owner explicitly authorizes delegation. If delegation is authorized later, use isolated branches/worktrees and one owner per file set; merge and verify the combined result. A second agent is not a separate human approval or GitHub identity.

Use synthetic fixtures only. Never commit credentials, phone numbers, addresses, identity documents, payment details, private prior conversations or customer logs. The repository is public.

## DOC-001: documentation must stay synchronized (permanent owner rule)

Every change — feature, fix, hotfix, refactor, security, Keycloak, API, migration, business rule, pricing/financial logic, UI/UX, infrastructure, CI/CD, configuration, dependency, test or documentation-only — needs a documentation impact assessment **before implementation** and reconciled documentation **before the PR is submitted**. Update the Developer Handbook ([docs/developer-handbook/README.md](docs/developer-handbook/README.md)) and every other affected authoritative document, diagram, contract, test case and traceability entry in the same PR. Use the [change matrix](docs/developer-handbook/doc001-matrix.json) to find the pages. Fill the `Documentation Impact` block of the PR template; a "no change" claim needs a specific, reviewable justification (never "minor change", "bug fix", "internal only"). Run `python3 scripts/doc001_check.py docs` before pushing. An agent cannot approve its own change to the gate, this file, `CLAUDE.md`, the PR template or the policy. Policy: [documentation-policy.md](docs/developer-handbook/05-developer-workflow/documentation-policy.md). No documentation assessment, no merge; contradictory required documentation, no merge.
