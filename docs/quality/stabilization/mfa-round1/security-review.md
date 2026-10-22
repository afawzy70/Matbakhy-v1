# Security review of the D18 MFA change (round 1, static)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `c139bdf3c03705f042388ad5d8bd0c1ccf945a1d`.

Summary (agent): Static-only, read-only review of 1df6b0d..HEAD; HEAD matches the requested SHA. Three findings. No network, runtime tests, or live Keycloak verification performed. Server-side precedence prevents preferences overriding REQUIRED; mutation endpoints retain browser CSRF protection; reset checks permission, recent MFA and self-reset. No application response or logging path exposing OTP secrets was identified. /me/security returns own-account policy/preference and session assurance, not actual factor inventory. Service-account manage-users scope is broad. Realm configurations match, but ACR/AMR issuance, unrelated-client step-up, account-console removal safeguards and live rollout semantics remain unverified; existing tests do not establish those cases. No confirmed client-parameter ACR forgery or persistent policy-change race bypass is claimed.

## Commands
| Command | Exit | Note |
|---|---|---|
| `git rev-parse HEAD; git status --short` | 0 | Requested SHA confirmed; working tree clean. |
| `git diff --stat 1df6b0d..HEAD; targeted local source, configuration, contract and test inspection` | 0 | Static inspection only; no secret values printed. |
| `python3 -B: verify SHA, compare three realm flow configurations, assert reset operation ordering` | 0 | Local, preview and test browser flows/authenticator configurations match; removal precedes logout and success  |
| `bash -n scripts/dev.sh; sh -n deploy/web-preview/deploy.sh` | 0 | Shell syntax checks passed; scripts were not executed. |

## Findings

### SECR7-001 [High] MFA reset leaves existing application web sessions authorized
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:101`
- Scenario: An attacker holds a previously stepped-up Spring web session. An administrator resets the victim's MFA, removing OTP credentials and ending Keycloak sessions. The attacker continues calling protected APIs with the existing application cookie under REQUIRED.
- Actual: Reset only calls Keycloak logout. Spring sessions are neither deleted nor checked against a revocation generation. WebAccountFilter reuses stored OIDC claims, and REQUIRED accepts their old MFA assertion without checking token expiry or reset time. Activity can keep the application session alive beyond the documented access-token lifetime.
- Evidence: MfaAdminController.java:97-107 removes credentials, logs out at Keycloak and returns sessionsEnded=true. WebAccountFilter.java:31-35 maps user.getClaims() from the stored login on each request. MfaEnforcementFilter.java:42-44 checks only policy and assertsMfa. No application-session invalidation or back-channel logout integration was found.
- Suggested fix: Invalidate all affected Spring sessions on reset and disabling, or enforce a server-side authentication generation on every request. Integrate provider logout/revocation events where applicable and report session termination accurately.

### SECR7-002 [Medium] DISABLED blocks preference enablement but not MFA enrolment or use
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaController.java:95`
- Scenario: All categories applicable to a user are DISABLED. The user directly visits /oauth2/authorization/keycloak?stepUp=true instead of using the hidden UI controls.
- Actual: Only preference enablement checks availability. The authorization resolver unconditionally requests MFA for stepUp=true, and the shared Keycloak flow runs OTP or enrolment without consulting category policy. Security settings also return the step-up and account-console URLs while disabled.
- Evidence: MfaController.java:71-75 always returns both URLs; :94-97 restricts only preference enablement. SecurityConfiguration.java stepUpResolver checks only the stepUp parameter. infra/identity/matbakhy-realm.json:269-283 makes level-2 OTP REQUIRED with no application-policy condition; the enrolment test documents the no-factor path.
- Suggested fix: Enforce availability in supported provider enrolment/use flows as well as application entry points, while retaining separate mandatory assurance for privileged operations. Do not rely on hiding UI controls.

### SECR7-003 [Medium] Partial reset failures can remove factors without a durable reset audit
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:97`
- Scenario: Credential deletion succeeds, then another deletion or Keycloak logout fails. Alternatively, the final audit write fails after provider changes have completed.
- Actual: The only success audit occurs after all remote operations. There is no attempt record or failure handler. A dependency exception can therefore leave OTP removed with no reset audit, and potentially leave provider sessions active.
- Evidence: MfaAdminController.java:97-101 performs irreversible remote operations before audit.record at :105-106. KeycloakAdmin.deleteCredential/logout throw on unsuccessful responses. The shared denial audit handles AccessDeniedException, not these dependency failures.
- Suggested fix: Persist a reset attempt before destructive calls, then record completion or partial failure with removed-factor count and logout outcome. Use a durable retry/reconciliation mechanism for incomplete session termination.
