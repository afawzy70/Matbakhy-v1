# MB-023 review (self-review, not independent)

Reviewer: Claude Code implementation agent (self-review).

## Scope
V16 meal moderation, API-071/072, kitchen active-order impact (API-068/070), web step-up login start, operations web reviewer and moderator sections, browser workflow with a synthetic `ops-a` staff user.

## Findings
1. Moderation is enforced where food is shown or sold: discovery query, public meal detail and the quote catalogue port. Mutations removing the discovery filter or the quote filter each failed ModerationIntegrationTest.
2. A hide cannot be lifted by the cook (pause and republish keep it hidden); the cook sees the state and reason. Content and publication status are untouched; audit records hide and restore.
3. Impact summary comes from the orders module through a `cooks.api` port, keeping the module graph acyclic (ModuleBoundaryTest passes).
4. Step-up: the API keeps deciding from token claims; the new login parameter only requests MFA. Covered by an identity test against real Keycloak (acr_values, max_age=0, PKCE kept).
5. Staff UI sections appear only when the server's protected list endpoint answers 200; a cook gets 403 on the catalogue in the browser workflow.

## Not done / residual
- Review moderation (API-073): customer reviews do not exist before MB-028; recorded there as a carried step. Not claimed here.
- Application decisions and operating-state changes need MFA step-up, which the local Keycloak realm cannot provide; in the browser workflow the UI shows the step-up prompt and the kitchen stays ACTIVE. The decisions themselves are covered by backend tests with MFA test logins (CookWorkflowIntegrationTest, ModerationIntegrationTest).
- Signed document reads are exercised in component tests and backend media tests, not in the browser workflow (no documents in the synthetic application).
- Staff grants in the browser workflow are synthetic SQL (granting needs an administrator with MFA).
