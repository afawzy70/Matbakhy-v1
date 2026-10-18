# Security review of the D18 MFA change (round 2, static)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `9ff290f732872c637b64132a3deeb52970148336`.

Summary (agent): Disposition: changes required. HEAD equals the requested SHA; working tree unchanged. All conclusions are static-only; no application, database, browser, Keycloak or integration tests were executed. Existing tests were inspected, not counted as execution evidence.

D18 risk disposition table:
Risk | Disposition | Evidence / limitation
SECR7-001: successful reset leaves application sessions authorized | closed | MfaAdminController.java:101-107 records reset epoch and deletes Spring sessions; MfaPolicyService.java:53-61 rejects earlier proofs under REQUIRED. Partial-reset and OPTIONAL staff assurance remain open separately.
SECR7-002: DISABLED enrolment/use | open | Authenticated application step-up is mitigated, but direct provider enrolment and anonymous step-up remain possible; SECR8-001.
SECR7-003: partial-reset audit | mitigated | Runtime failures now produce partial audit, but no durable attempt precedes destructive operations; SECR8-002.
REQUIRED category precedence / preference override | closed | MfaPolicyService.java:124-136 uses strongest mode and fails missing policy rows to REQUIRED.
REQUIRED endpoint bypass | mitigated | Both security chains apply enforcement; only public GETs, own profile GET, security settings, logout and anonymous activity are exempt. Current security mutation independently checks policy and assurance. No concrete protected mutation bypass identified; future routes under the broad security/** exemption need review.
Claims confusion | mitigated | Signature, issuer, audience and token timestamps validated; authorities originate from database. No demonstrated client-controlled ACR/AMR forgery. Actual provider claim issuance, cross-client SSO and account-console behavior require runtime verification.
Reset epoch / staff auth_time | open | REQUIRED rejects pre-reset proofs, but separate recent staff assurance ignores reset epoch under OPTIONAL/DISABLED; SECR8-003. Future auth_time lacks an upper bound, but no attacker-controlled signed-claim scenario established.
Downgrade / reset authorization | mitigated | Dedicated permissions, recent MFA, policy version checks, reasons, self-reset denial and audits exist. Reset-epoch exception remains SECR8-003.
Lock-out / recovery availability | open | Mobile lacks step-up handling; documented rollout guidance does not enforce category restrictions. DISABLED suppresses application step-up even for independent privileged assurance. Operational recovery and live realm migration remain unverified.
Admin service-account scope | mitigated | Application uses credential listing/deletion and logout only; realm manage-users remains broader than those operations. Preview proxy blocks /admin; local identity binds loopback. Stolen credentials retain broad realm-user management capability; network restriction is containment, not least privilege.
Secrets in local generated configuration | open | SECR8-004.
Preview compose/.env/deploy.sh/realm templates and logging | mitigated | Templates reference generated secrets; preview generated parent directory is mode 700; .env/generated paths ignored; admin client logs status/class rather than bodies or tokens. Bounded tracked-file scan found no private-key blocks or JWT-shaped strings. This is not exhaustive secret detection or live filesystem/log verification.
Provider-only factors / no application OTP storage | mitigated | Application stores policy, preference and reset time; admin client consumes credential identifiers/types. Actual provider response secrecy remains static-only.
PREVIEW defaults | closed | V31__mfa_policy.sql:14 seeds OPTIONAL for all categories.

Still static-only: successful and partial reset behavior against running Spring Session/Keycloak, session-save races, audit outage/crash recovery, token refresh/SSO ACR and auth_time semantics, unrelated-client escalation attempts, provider enrolment/removal controls, mobile recovery, deployed realm migration, actual service-account permissions, host file permissions and deployed log contents.

## Findings

### SECR8-001 [Medium] SECR7-002 remains open for provider enrolment and anonymous step-up
`backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java:263`
- Scenario: An account whose applicable categories are all DISABLED visits Keycloak account management directly, or signs out of the application and starts stepUp=true before the account is known.
- Actual: The resolver suppresses MFA only for a known DISABLED account. Anonymous step-up still requests MFA, and provider account-console enrolment has no category-policy guard.
- Evidence: SecurityConfiguration.java:263-277 permits anonymous MFA requests; MfaController.java:75-79 always returns the provider account-management URL. docs/security/mfa-policy.md explicitly records direct provider enrolment and anonymous step-up as residual limitations.
- Suggested fix: Enforce availability in provider enrolment/authentication entry points, preserving independent privileged step-up, or obtain an explicit owner-approved change to the DISABLED requirement.

### SECR8-002 [Medium] SECR7-003 is mitigated but destructive reset still precedes durable audit
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:101`
- Scenario: OTP deletion succeeds, then the process terminates before audit insertion, or the audit insert fails after provider changes.
- Actual: The reset timestamp is committed first, but it is an overwritten per-user marker without attempt reason or progress. Partial and successful audit records are inserted only after remote side effects; the catch-path audit can itself fail.
- Evidence: MfaAdminController.java:101-120 performs deletion before either audit record. MfaPolicyService.java:116-120 upserts only the latest reset time/admin. JdbcAuditLog.java:21-27 directly inserts the event. MfaResetAuditIntegrationTest.java:88-110 covers provider failure with a healthy audit store, not audit failure or process termination.
- Suggested fix: Persist a reset attempt and reason before remote mutation, then update completion/progress with durable reconciliation for interrupted operations.

### SECR8-003 [High] Pre-reset bearer proofs still authorize privileged actions under OPTIONAL policy
`backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java:39`
- Scenario: An attacker holds a recently stepped-up bearer token for a staff account with security.mfa_policy or security.mfa_reset. Another administrator resets that account. STAFF remains OPTIONAL and the victim's preference is off, as allowed by preview defaults. The attacker reuses the unexpired token to downgrade policy or reset another account.
- Actual: The policy filter checks the reset epoch only when decision.required() is true. StaffAssurance checks MFA claims and recency without consulting reset_at, so the token still satisfies privileged method authorization until token expiry or the recency window ends. A partial reset can also leave application sessions intact because session deletion follows provider logout.
- Evidence: MfaEnforcementFilter.java:42-44 gates epoch validation on required(). StaffAssurance.java:38-45 ignores reset state. MfaAdminController.java:73,80 uses that assurance for policy changes and resets; :102-107 can fail before application-session deletion. docs/security/mfa-policy.md acknowledges pre-reset tokens still count for separate staff assurance.
- Suggested fix: Make privileged assurance validate the account reset epoch regardless of category policy. Attempt local session invalidation independently of provider failure.

### SECR8-004 [Medium] Generated local realm exposes the new user-management client secret to other host users
`scripts/dev.sh:63`
- Scenario: On a shared development host with traversable workspace ancestors, another local user reads infra/.generated/identity/matbakhy-realm.json after dev.sh renders it, then uses the service-account credential against the loopback Keycloak endpoint.
- Actual: The generated directory and identity subdirectory are explicitly mode 755 and the realm is mode 644. The realm contains the rendered admin client secret, whose service account has manage-users. Initial .env creation also writes before chmod 600 without first establishing a restrictive umask.
- Evidence: scripts/dev.sh:38-44 writes .env before chmod; :59-64 renders secrets and applies publicly readable permissions. infra/identity/matbakhy-realm.json:182-187 grants view-users/manage-users. infra/compose.yaml:33-36 exposes Keycloak on host loopback and mounts the generated directory. Preview deploy.sh:64-79 has an owner-only parent safeguard absent locally.
- Suggested fix: Set umask 077 before secret creation and keep an owner-only generated parent directory, using the preview bind-mount pattern or an equivalent container-readable arrangement.
