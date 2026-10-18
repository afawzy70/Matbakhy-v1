# Staff MFA enforcement (CR-002 / BR-007) — evidence and rollout

Status: **config-as-code and automated IdP test done; live preview/staging realm NOT yet changed or verified** (see Blocker).
Owner decision 2026-10-18: Keycloak is responsible for MFA; no separate MFA mechanism in Spring Boot; Keycloak admin console is never publicly exposed.

## What was verified (commit in `fix-verification.md`)
1. **Before:** none of the realm definitions in the repo (`infra/identity/matbakhy-realm.json`, `deploy/web-preview/identity/matbakhy-realm.preview.json`, test realm) configured OTP, a conditional flow or an `amr` mapper. `docs/operations/06-web-preview.md` already said OTP is not configured in the preview realm. So MFA was **not enforced** by the repo-defined IdP. Documentation alone did not show enforcement.
2. **After (config as code):** all three realms carry the `matbakhy browser` flow: username/password, then — only for users holding the realm role `matbakhy-staff` — a REQUIRED OTP gate that runs **after** both the password form and the SSO-cookie path (`auth-otp-form`, so an existing password-only SSO session cannot skip it; a staff user with no OTP is forced into `CONFIGURE_TOTP` enrolment, never admitted). The `amr` protocol mapper is added to the web and mobile clients. Customers keep password-only login.
3. **Local replay on the pinned Keycloak 26.8.0** (distribution run on loopback only, no Docker, no exposed admin console) with the committed test realm: customer password-only → code issued, `amr=[]`; staff password-only → stopped at OTP; staff + valid TOTP → code issued, `amr` contains `otp` (`["pwd","otp"]` locally; `["otp"]` in the CI container — the OTP assertion is what matters); staff without enrolled OTP → forced to CONFIGURE_TOTP; wrong OTP → refused.
4. **Automated:** `StaffMfaIdpTest` (real Keycloak via Testcontainers, runs in Product CI) asserts the same five behaviours with per-run synthetic accounts/secrets. Unit tests cover claim handling (`StaffAssuranceTest`, `AccountMapperStaffMfaTest`).
5. **Claim compatibility:** the `amr` that Keycloak really emits (contains `otp`) is accepted by the existing `StaffAssurance` (high-risk step-up); `acr` stays `1`, so nothing relies on `acr`.

## API-side defense in depth (optional, default OFF)
`matbakhy.identity.require-staff-mfa` (`IDENTITY_REQUIRE_STAFF_MFA`): when true, an account receives staff permissions only if the token itself asserts MFA (acr in `mfa-acr-values` or amr `mfa|otp|hwk`). This closes "grant holder without the Keycloak role". It stays **off** until every grant holder in that environment authenticates through the MFA flow, otherwise it would remove existing staff access (the preview staff accounts `preview-ops-1/2` and dev `ops-a` are password-only by design, as dedicated MFA accounts do not yet exist there).

## Round-3 independent review (SECR-002 / CR3-003)
- SSO-cookie bypass closed: the OTP gate sits after the cookie/forms alternatives; `StaffMfaIdpTest.an_existing_sso_session_does_not_let_a_staff_account_skip_the_otp` proves a second authorization in the same browser asks for the OTP again; customers keep SSO.
- The documented variable is now really bound: `application.yaml` maps `IDENTITY_REQUIRE_STAFF_MFA` to `matbakhy.identity.require-staff-mfa` and the preview compose passes it through.
- Still open by decision: the flag defaults OFF, so a grant holder without the Keycloak role keeps staff permissions until it is enabled (SECR-001 / CR-002).

## Rollout (needs a person with Docker access on the Contabo host; the agent account has none)
1. `--import-realm` skips a realm that already exists, so editing the JSON does not change the live preview realm. Apply the `matbakhy browser` flow, the `matbakhy-staff` role and the `amr` mapper to the running realm via `kcadm.sh` inside the Keycloak container (internal network only), or recreate the preview realm from the updated file when no real data depends on it.
2. Create **dedicated synthetic staff accounts** (e.g. `preview-staff-mfa-1`) with the role, enrol TOTP out of band; keep `preview-ops-*` for the E2E suite (password-only, no role) until E2E supports OTP.
3. After applying, **terminate existing sessions of staff accounts** (kcadm `logout-all` / sessions delete), because sessions created before the change are password-only. Verify against the live realm: password-only staff login must not return a code; OTP login must show `amr` containing `otp` in the access token.
4. Assign `matbakhy-staff` to every account that holds a Matbakhy grant in production; then set `IDENTITY_REQUIRE_STAFF_MFA=true`.

## Blocker (explicit)
Live Keycloak (preview/staging) enforcement is **not** verified: the agent has no Docker/SSH access to the running stack, and no staff MFA test accounts exist there. CR-002 stays OPEN for deployment evidence; code and CI evidence above are complete.
