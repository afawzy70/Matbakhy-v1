# Phase promotion and release gates

All initial phase gates are NOT_RUN. `planning/gates.json` defines mandatory checks and decision prerequisites. Implemented code, command success, scenario evidence, review and required approvals must agree.

| Gate | Required evidence |
|---|---|
| P00 | Current scope/provenance, traceable backlog, editable brand and reviewable UX artifacts; unanswered decisions remain honest |
| P01 | Clean workspace/toolchain, PostgreSQL migration, module boundary tests, validated contract/client generation, reproducible local stack/CI |
| P02 | OIDC/session/PKCE contract, OTP/address tests, negative role/object access, private cook approval workflow |
| P03 | Safe media, publication validation, stock/cutoff/timezone, correct serviceable discovery/pagination |
| P04 | Real transactional happy/failure path; concurrent capacity/transition tests; cash/ledger/outbox correctness |
| P05 | Cook and ops queues, support privacy, finance/refund/settlement examples, config/access controls with actual UI/API |
| P06 | Owner visual decision; real browser E2E, RTL/accessibility/responsive evidence across customer and operations |
| P07 | Native Android and iOS evidence separately, account switching/offline/deep link/push; signing/device dependencies resolved |
| P08 | Threat/adversarial tests, race/crash recovery, measured performance, redaction/deletion, restore/migration evidence |
| P09 | Approved launch decisions, isolated staging, operator rehearsal, owner UAT, exact candidate release review |
| P10 | Explicit production/store authorization, deployed pilot smoke/monitoring, store outcomes, observed closure and guides |

## Exact candidate gate

Release report names source commit, backend/container digests, web artifact hashes, Android/iOS version/build IDs and schema migration version. Checks for another commit/build cannot be silently reused. When only evidence files change, report the tested source commit separately from the evidence commit; any runtime/config/contract change requires appropriate rerun.

Required phase stories DONE; all mapped scenarios PASS with evidence; dependencies complete; code/security review PASS; zero unresolved critical/high defects; all required decisions ACCEPTED with actual reference; valid staging/restore/native evidence; approved release scope. A skipped/missing/failed mandatory check yields BLOCKED/FAIL. A passed blueprint validator cannot satisfy any product gate by itself.

P09/P10 promotion rechecks runtime-phase scenarios and mandatory checks from P01 onward at the candidate source SHA. Earlier design review references may remain valid if the design scope is unchanged, but old runtime tests cannot establish a new release candidate. Rebuild/retest native artifacts after relevant candidate changes.

## Human checkpoints

1. Reviewable brand/UX before final visual lock (O05).
2. Concrete unresolved commercial, privacy, verification, provider, region and operating policies before live pilot.
3. Owner UAT on exact candidate and scoped release authorization before public deployment/store submission.

Routine code, test, architecture and repair work continues without repeated approvals. If an approval already exists, reference it rather than ask again. A different release scope/build may need renewed review; do not invent an approval based on silence.

## No-go conditions

Any stock or finance inconsistency, unauthorized data access, leaked credential, missing native target, fake production provider, unapproved live commercial policy, unrehearsed restore, broken critical journey, misleading allergen/verification claim or unavailable support/escalation route. Fix or explicitly remove the affected optional feature through owner scope change; do not lower the gate.
