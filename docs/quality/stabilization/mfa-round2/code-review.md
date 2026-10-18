# Code review of the D18 MFA change (round 2)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `9ff290f732872c637b64132a3deeb52970148336`.

Summary (agent): Round 2: changes required. Source inspection confirms CR7-001 and CR7-005 fixes. CR7-002 and CR7-004 remain incomplete; CR7-003 fixes the authenticated resolver path, including pre-mapping OIDC authentication, but introduces a staff step-up regression. QA7-001/002 fixes are present; QA7-003 adds real refresh rejection for reset but still lacks equivalent provider-backed disable coverage. Runtime tests NOT_RUN in the read-only environment; report files could not be written.

## Findings

### CR8-001 [High] Pre-reset MFA tokens still authorize high-risk staff actions
`backend/app/src/main/java/com/matbakhy/app/security/StaffAssurance.java:39`
- Scenario: A staff account with OPTIONAL policy and preference off has a recently issued MFA bearer token. Another administrator resets that account. Before the token expires, its holder submits a policy change or staff grant using the old token.
- Actual: The enforcement filter skips satisfiedBy when decision.required() is false. StaffAssurance checks only MFA claims and recency, so the pre-reset token still authorizes the command.
- Evidence: MfaEnforcementFilter.java:43 gates epoch validation on decision.required(). StaffAssurance.java:38-42 never reads the reset epoch. MfaAdminController.java:73 and :80 use this assurance check for policy changes and resets; StaffController uses it for grants. Provider logout does not revoke already issued bearer tokens.
- Suggested fix: Apply the account reset epoch to every MFA assurance decision, independently of the effective policy.

### CR8-002 [High] Reset still performs destructive work before persisting audit evidence
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:101`
- Scenario: The reset epoch commits and Keycloak credential removal succeeds, but the final audit insertion fails. Alternatively, provider work fails partially and the failure-audit insertion also fails.
- Actual: The operation exits with an error after destructive work, without any reset audit event. Recording the epoch first preserves invalidation but does not preserve the reason, request ID or reset attempt audit.
- Evidence: Lines 101-107 commit the epoch and perform provider/session mutations before audit.record. The partial-failure audit is at lines 113-114; the success audit is outside the try block at lines 120-121. JdbcAuditLog.record directly inserts into audit_event; no initial intent is recorded.
- Suggested fix: Commit a reset intent audit before provider work, atomically with the epoch where practical, then append completion or partial-failure evidence.

### CR8-003 [Medium] DISABLED policy prevents required staff step-up
`backend/app/src/main/java/com/matbakhy/app/security/SecurityConfiguration.java:270`
- Scenario: All applicable categories for a policy administrator are DISABLED. After password login, or after an existing MFA proof becomes stale, the administrator attempts a policy update and follows the displayed step-up link.
- Actual: The resolver removes both the MFA request and fresh-authentication request. The normal provider flow returns password-level authentication, and the protected action again returns STEP_UP_REQUIRED.
- Evidence: SecurityConfiguration.java:270-271 returns the default authorization request whenever MFA is unavailable. MfaAdminController.java:73 still requires recentMfa regardless of policy. apps/operations-web/src/ops/Security.tsx uses the same stepUpUrl for policy/reset errors. docs/product/06-permissions.md requires recent step-up for high-risk changes independently of category policy.
- Suggested fix: Provide a server-controlled step-up flow for authorized high-risk staff actions that preserves their independent assurance requirement under DISABLED, while retaining the ordinary policy-controlled behavior.
