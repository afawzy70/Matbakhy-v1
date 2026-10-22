# Approved Testing Workspace design comparison

Status: PARTIALLY IMPLEMENTED. This validates the testing website, never a live Matbakhy order.

## Reference and implementation

The owner supplied [matbakhy-full-scenario-keycloak.html](reference/approved-prototype.html). Its bytes are archived unchanged; [SHA256 provenance](reference/provenance.json). The original CSS after its remote-font import is retained, followed by scoped runtime additions. Cairo weights 400–800 are self-hosted with their SIL license. The approved green/ivory workspace direction supersedes the earlier inferred terracotta dashboard only for this testing tool.

Retained: 940px centered tutorial, ivory body, deep green rounded header, white cards, pale green actor cards, orange warnings, visible numbered step panels, role/account labels, direct links and checkbox progress. The source prototype's collapsed Developer Details and essential Keycloak/provisioning details are integrated into the chronological flow according to the owner's newer requirement. Optional source references alone remain collapsed.

E2E-ORDER-101 now has 19 visible controls: six setup steps, ten canonical execution steps, two added verification/recording steps and one original-outcomes verification. Canonical IDs and all original expectations remain unchanged. Extra steps are explicitly labelled. Three actor cards are included because the operations identity is required, even though the reference overview initially displayed only customer/cook cards. These content expansions intentionally change height, so pixel-identical full-page rendering is not claimed.

## Screenshots

| View | Before | Approved reference | Updated |
|---|---|---|---|
| Desktop 1440px | [before](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/before-desktop.png) | [reference](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/reference-desktop.png) | [updated](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/after-desktop.png) |
| Mobile 390px | [before](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/before-mobile.png) | [reference](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/reference-mobile.png) | [updated](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/after-mobile.png) |

Execution panel: [desktop checkout](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/after-desktop-checkout.png) and [mobile checkout](../../../verification/runs/20261022-WORKSPACE-REDESIGN/screenshots/after-mobile-checkout.png). [Measured comparison](../../../verification/runs/20261022-WORKSPACE-REDESIGN/visual-comparison.json).

## Measured validation and review

Chromium compared actual HTML and implementation at 1440px, 390px and 320px: main width, body background/font, header background/radius/padding, heading size, card background/radius, step radius/background and RTL all match. Both views use identical Cairo files via controlled font routing while reference bytes stay unchanged. No live application requests were made. Every essential flow body is visible, ten canonical steps remain, and optional references are the only details element.

Independent AI source review (audit_commerce) supports source-grounded completeness for ORDER-101 after correcting checkout routing, first-use API helpers in each browser profile, executable read-only SQL instructions and all eight prerequisite mappings. Independent security review (audit_identity) closed export/step-invalidations and per-case release issues; Node regressions passed. Independent usability review (guidance_discovery) inspected actual before/reference/after desktop screenshots: strong qualitative match, no blocking desktop defect; dense Arabic/English technical strings are a remaining minor readability concern. The same usability reviewer also inspected the actual mobile reference/after/checkout captures: no blocking visual defect; content wraps and stacks while long mixed-language text remains a minor readability concern. No human owner design approval is claimed.

## Coverage and limitations

200/200 cases and 1005/1005 canonical steps are preserved. 1/200 guides is reviewed complete (ORDER-101), and 199 are drafts with incomplete case-specific Arabic setup review. All share the approved visible-step layout. See [coverage matrix](coverage-matrix.md). Guide completeness is distinct from runtime readiness: additional owner-e2e fixtures, authorized Keycloak access, capacity and read-only DB evidence may still block ORDER-101 execution. Physical-device, destructive and isolated cases retain readiness warnings. No real order, live configuration, fixture provisioning, deployment or product scenario PASS occurred.
