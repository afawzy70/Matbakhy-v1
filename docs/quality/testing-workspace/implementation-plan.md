# Testing Workspace delivery checkpoint

Status: IN_PROGRESS / NOT_RUN for product acceptance. Owner direction: 2026-10-22.

## Documentation impact assessed before implementation

Add an isolated, buildless testing website under scripts/testing-workspace. It reads generated data from the canonical owner-acceptance catalog; it does not replace that catalog or the Developer Handbook. Update handbook test-execution English/Arabic pages, owner guide, coverage matrix and this checkpoint. Backend, API, permissions, payment rules and deployment configuration do not change. No diagram or contract change is required: the website makes no application API requests and has no server component.

## Delivery batches

1. Foundation, catalog generator, J0–J8 navigation and execution flow.
2. Discovery/basket Egyptian Arabic guidance.
3. Orders/finance Egyptian Arabic guidance.
4. Cook/operations guidance.
5. Identity/configuration guidance and source corrections.
6. Coverage audit, security/UX checks and independent review.

Completeness is separate from case representation, prerequisites and execution results. Arabic draft guidance is not reviewed guidance. Do not promote a case to complete solely because its source JSON has fields. Preserve gaps and NOT VERIFIED labels. No product cases have been executed by this delivery effort.

## Safe integration

Use Python standard library for generation and local HTTP serving, native browser modules and CSS. Keep private passwords only in a JavaScript Map. Persist only step IDs, readiness acknowledgements and result status; evidence and defect text remain in memory and need deliberate sanitized export. No auto-provisioning, live SQL, Keycloak changes or deployment.

## Owner prototype correction — 2026-10-22

The attached approved prototype replaces the earlier inferred terracotta dashboard design for this workspace. Preserve its 940px centered single-column tutorial, ivory background, dark-green header, Cairo typography, white cards and visible numbered steps. Archive the supplied bytes and SHA256. Remove the prototype's separate developer details and collapsed essential configuration: the owner's newer execution-flow requirement overrides those elements. Keep original canonical catalog bytes unchanged. Add distinct setup/verification steps around canonical steps, never renumber canonical identity.

Documentation impact before implementation: update this plan, workspace README, handbook test-execution English/Arabic, coverage matrix and status/evidence; add independent review and reference-comparison record. No backend/deployment/permission/contract change. Tests cover all 200 cases / 1005 steps, visibility, adjacent actor/configuration/URL, progress migration/resume, outcome guards, credentials and desktop/mobile visual tokens. Guidance completion needs explicit case review of the rich chronological tutorial; draft projections remain incomplete.

## Owner-approved scanner findings resolution

The owner explicitly approved the four exact fingerprints documented in security-review.md. Documentation impact assessed before applying: update .gitleaksignore, security-review.md, CI/CD handbook English/Arabic and status/evidence in the same PR. The scanner, all rules, all other findings, canonical cases and runtime code remain unchanged. Re-run the verified Gitleaks binary over full history and the committable tree, DOC-001 and handbook parity. Keep PR #43 Draft; approval is limited to these exceptions, not merge or deployment.

Post-approval CI review also found two redundant browser-global declarations in the testing workspace header comment under the pinned globals package. Remove only those declarations; no runtime behavior or visual change. Validate syntax, focused checks and the new remote workspace run.
