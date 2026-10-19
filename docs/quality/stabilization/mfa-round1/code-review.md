# Code review of the D18 MFA change (round 1)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `c139bdf3c03705f042388ad5d8bd0c1ccf945a1d`.

Summary (agent): Release blocked by MFA enforcement, session invalidation, DISABLED-mode and recovery-audit gaps. Reviewed git diff 1df6b0d..HEAD; HEAD matches the required SHA. Static checks passed; runtime tests were not run because the workspace is read-only and build dependencies are unavailable. Report files could not be written under the filesystem restriction.

## Commands
| Command | Exit | Note |
|---|---|---|
| `git rev-parse HEAD` | 0 | Matches c139bdf3c03705f042388ad5d8bd0c1ccf945a1d. |
| `python3 scripts/validate_blueprint.py` | 0 | Blueprint structure passed; does not establish product correctness. |
| `git diff --check 1df6b0d..HEAD` | 0 | No whitespace errors. |
| `python3 static inspection of realm JSON, endpoint registry and profile exemption` | 0 | Realm JSON parses; authentication flows/configurations match across development, preview and test. Confirmed P |
| `command -v mvn` | 1 | Maven unavailable; no installed node_modules or backend/app/target directory found. |

## Findings

### CR7-001 [High] Profile writes bypass REQUIRED MFA on both chains
`backend/app/src/main/java/com/matbakhy/app/security/MfaEnforcementFilter.java:55`
- Scenario: An active user under REQUIRED authenticates with password only and sends PATCH /api/v1/me with a valid expectedVersion and changed displayName or locale.
- Actual: OPEN is matched without checking the HTTP method, so PATCH reaches ProfileController.update without an MFA assertion.
- Evidence: OPEN includes /api/v1/me at line 27; lines 55–57 exempt every method. ProfileController maps @PatchMapping under /api/v1/me and performs profiles.update. Both security chains install this filter.
- Suggested fix: Make exemptions explicit method/path pairs and limit the profile exemption to GET.

### CR7-002 [High] Recovery reset leaves authenticated application sessions usable
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:101`
- Scenario: A user has an MFA-authenticated Spring web session. An administrator resets that user's factors after suspected compromise. The old browser continues calling protected APIs.
- Actual: Reset ends Keycloak sessions only. The Spring session retains its OIDC claims, including the MFA assertion, and continues satisfying the enforcement filter.
- Evidence: Reset calls keycloak.logout and returns sessionsEnded=true without invalidating Spring sessions. WebAccountFilter remaps stored OidcUser claims on each request without checking provider-session validity or token expiry. Neither realm client configures backchannel logout. The same omission affects MfaController.endSessions.
- Suggested fix: Invalidate all Spring sessions belonging to the target account during reset and disabling, or enforce a server-side session-revocation epoch on every request.

### CR7-003 [High] DISABLED does not prevent MFA enrolment or use
`backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java:258`
- Scenario: Set all applicable categories to DISABLED. The signed-in user directly opens /oauth2/authorization/keycloak?stepUp=true.
- Actual: The resolver unconditionally requests MFA. The realm flow still performs OTP or CONFIGURE_TOTP enrolment regardless of the application policy. Hiding controls and rejecting preference enablement does not block these flows.
- Evidence: Lines 258–265 inspect only stepUp=true and attach acr_values/max_age. MfaController.view also returns stepUpPath and accountManagementUrl when unavailable. The identical realm flows have no policy condition; MfaIdpTest explicitly demonstrates enrolment through an MFA authorization request.
- Suggested fix: Enforce availability across supported Keycloak enrolment and authentication entry points, including direct account-console access, while preserving factors and independent required assurance controls.

### CR7-004 [High] Destructive reset can complete partially without any audit record
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:97`
- Scenario: Keycloak successfully deletes an OTP credential, but the following logout request fails; alternatively, the final audit insertion fails.
- Actual: The credential is already removed, but execution exits before audit.record. There is no initial attempt record or failure record, and external deletions cannot be rolled back.
- Evidence: Credential deletion occurs at lines 97–99 and logout at line 101. The sole success audit is written afterward at lines 105–106. KeycloakAdmin throws on failed logout, and reset has no failure-audit handling.
- Suggested fix: Persist an audited reset intent before external mutations, then record completion or partial failure with safe operation details. Refuse destructive work if the initial audit cannot be persisted.

### CR7-005 [Medium] Reset UI visibility uses unrelated permissions
`apps/operations-web/src/App.tsx:257`
- Scenario: A staff member holds security.mfa_reset alone, or holds staff.grant/security.mfa_policy without security.mfa_reset.
- Actual: A reset-only administrator can receive no Security tab, while policy administrators and staff-grant administrators see a reset control that the server rejects.
- Evidence: Session tracks mfaPolicy and staff but never reset permission. allowedTabs derives Security visibility from those and other sections; line 257 sets resetAllowed={session.mfaPolicy || session.staff}. MfaAdminController independently requires PERM_security.mfa_reset.
- Suggested fix: Expose and consume the caller's actual reset capability; use it for Security-tab visibility and reset controls.
