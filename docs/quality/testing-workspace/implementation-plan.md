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
