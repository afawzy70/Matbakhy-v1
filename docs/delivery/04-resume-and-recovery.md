# Resume and recovery prompts

## Resume existing work

```text
Resume MATBAKHY_MVP in this repository. Read CLAUDE.md, the execution protocol, planning/execution-state.json, backlog, decisions, gates and latest verification evidence. Inspect actual Git state and CI before trusting the checkpoint. Identify the last tested source commit and any uncommitted work; preserve it. Revalidate stale affected checks. Select the next eligible incomplete story, implement → verify → review → repair → checkpoint and continue automatically within existing authorization. Do not restart completed work, reapprove known decisions, repeat external side effects, or mark missing checks PASS. Report exact current state and then continue.
```

## Repair a failed verification gate

```text
MISSION=MATBAKHY_GATE_RECOVERY
Read the latest failed/BLOCKED gate report and source commit. Preserve the failing reproduction and evidence. Distinguish code defect from missing environment/credential/decision. List affected rules/scenarios and identify the root cause. Make the smallest complete fix; retain acceptance criteria and security controls. Rerun failed checks and affected regression/concurrency/authorization tests. Inspect real output, update evidence at the new tested SHA, and re-evaluate the gate. Never remove a test, waive a required platform, suppress an error or use mock success to pass. After at most three materially different unsuccessful fixes, checkpoint the blocker and continue independent eligible work. On success resume the next dependent story automatically.
```

## Apply owner feedback or new requirements

```text
Incorporate the owner feedback below into Matbakhy. Record exact requirement/decision changes, distinguish scope change from defect, and assess affected rules, data, API, screens, mobile, finance/security and tests. Preserve previously confirmed requirements unless explicitly superseded. Create/update backlog and scenarios with dependency links; do not silently discard old acceptance or mark new work DONE. Implement authorized changes, verify affected regressions and checkpoint. Ask only for material unresolved choices after preparing concrete alternatives.

OWNER_FEEDBACK:
<paste feedback>
```

## Missing native/provider access

```text
Record the unavailable device/runner/account/provider as a specific BLOCKED check with required setup, owner and dependent stories. Do not invent credentials, disable certificate/security checks, spend money or fake platform verification. Complete all unaffected implementation, tests, documentation and reviewable artifacts, then give the exact remaining setup and resume command/action.
```

## Incorporate 2026-10-13 owner decisions without restarting execution

```text
Continue MATBAKHY_MVP from the existing working branch and verified checkpoint. Safely fetch/reconcile the latest main branch without discarding uncommitted work. Read docs/delivery/07-owner-decisions-implementation-sequence.md, docs/strategy/mvp-direct-deposit-payment.md, docs/strategy/free-core-sponsored-discovery.md and docs/product/01-context-and-decisions.md. Preserve current in-flight work; record a requirements-to-stories-to-tests change matrix and align machine-readable decision/backlog/scenario entries at the next safe boundary. MVP: 0% platform commission and no platform service fee or ads; eligible COD plus manual direct-to-kitchen deposit, kitchen-reported acknowledgement, separate kitchen acceptance, immutable quote, dispute/refund paths; privacy-safe organic growth metrics. No billing/paid boosts, AI Concierge implementation or automated payment custody/gateway. Continue dependency-ordered stories, verify and checkpoint each. Block only live monetary features pending required Egyptian legal/provider/food licensing review. Do not mark not-run tests PASS. Report concise Egyptian Arabic progress to owner.
```
