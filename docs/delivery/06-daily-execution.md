# Daily execution and model selection

## Owner direction — 2026-10-11

This policy supersedes the earlier one-time, open-ended execution instruction. Use one fresh owner-facing Claude Code prompt per workday, grounded in latest repository progress and verification. Within each daily session, continue autonomously through eligible work without asking permission after ordinary tasks.

The work window is 12:00 noon to 00:00 midnight in Asia/Kuwait. Identify a workday by its starting date. Stop starting new stories around 23:30; finish or safely checkpoint current operations, record remaining checks and push by midnight when possible. Do not terminate migrations or destructive operations abruptly. If started outside this window, perform a safe checkpoint/preflight and await the next authorized window rather than assuming overnight implementation is authorized. Runtime interruptions and quota limits may end work earlier. Do not spend tokens idle-waiting until noon or manufacture busywork to fill twelve hours.

A prompt and scheduled review are not an installed process supervisor. They cannot guarantee automatic start/stop, recovery or twelve hours of continuous execution.

## Model policy

Start the FIRST implementation session with the latest available Opus selected by the owner. Use it for repository reconciliation, foundation decisions and initial implementation/verification. This is a recommendation for a strong start, not proof that every task needs Opus or a blanket instruction to consume the highest reasoning budget.

At each daily review, recommend the model for the next bounded work based on actual upcoming complexity, delivered verified behavior, review findings, regression rate, repair attempts and available usage telemetry:
- Keep/use Opus for unresolved architecture, permissions/security, transactional correctness/concurrency or difficult root-cause investigations.
- Move to Sonnet when foundational decisions are stable and the next stories are well specified routine implementation, tests, UI/API integration or documentation.
- Escalate a bounded difficult task back to Opus when observed failures warrant it; state the specific task, required outcome and return-to-Sonnet condition.
- Do not change models merely because another day passed. Do not assume an agent can change its own running model. The daily prompt names the recommended model and the user selects it in Claude Code.
- Missing usage telemetry does not block implementation. Report actual tokens, cost and remaining subscription quota as UNKNOWN when unavailable. Never infer cost from commits, code volume or elapsed hours.

Record the actual model/version and effort if observable, work scope, verified outputs, repair attempts and supplied redacted usage totals in status reports. Otherwise explicitly record unknown values. Do not publish billing/account identifiers or sensitive screenshots. Twelve hours is an availability window, not a token/spend budget or a throughput guarantee.

## Reviews

External read-only reviews target approximately 12:00, 18:00 and 00:15 Kuwait time; delivery can vary.
- Noon: inspect latest evidence; provide one copyable daily execution prompt and model recommendation.
- 18:00: inspect progress, failures/blockers and any evidence-based need to change priorities or model. Avoid needless replacement prompts.
- 00:15: summarize the PREVIOUS starting-date workday, noon through midnight, and recommend next priorities/model provisionally. Refresh those recommendations at noon.

The first session should start on Opus. Later decisions can move up or down according to evidence. Reviews do not execute model changes, edit code, merge or deploy.

## Required daily prompt

Include the dated work window and timezone; selected model and selection command; observed branch/source SHA and instruction to reconcile newer changes; current phase and concrete eligible task IDs; priority defects; bounded goals and acceptance evidence; existing repository reading/authority rules; implement/test/review/repair/continue loop; checkpoint/publication rules; precise blockers and approval boundaries; and the midnight stop/resume procedure.

Use confirmed current evidence, not an assumed empty repository. If access fails, clearly state the limitation and provide conditional preflight instructions without inventing current tasks or completion. Reuse the master start prompt's execution and safety rules but replace its unlimited duration with the daily cutoff. Earlier unfinished work carries forward based on dependencies and evidence.
