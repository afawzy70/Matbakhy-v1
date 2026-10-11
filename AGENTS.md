# Repository guidance for coding agents

Read [CLAUDE.md](CLAUDE.md) and follow the [execution protocol](docs/delivery/02-execution-protocol.md).

This task is a product delivery effort with durable requirements, checkpoints and evidence. The backlog is the work queue, not proof of completion. Preserve confirmed owner requirements and explicitly label recommendations. Keep source code, migrations, contracts, tests and documentation consistent in each story.

Do not delegate to other agents unless the owner explicitly authorizes delegation. If delegation is authorized later, use isolated branches/worktrees and one owner per file set; merge and verify the combined result. A second agent is not a separate human approval or GitHub identity.

Use synthetic fixtures only. Never commit credentials, phone numbers, addresses, identity documents, payment details, private prior conversations or customer logs. The repository is public.
