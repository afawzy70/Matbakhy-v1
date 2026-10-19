# Security review of the D18 MFA change (round 4, final, static)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `240cf0c08fef1fa92bb65f2886a938cf9401d04a`.

Summary (agent): Final disposition: CR9-001 and CR9-002 are closed in source. No new actionable security findings identified in git diff 7e61db7..HEAD. All conclusions below are static-only; tests were inspected, not executed. No network access or repository changes.

| D18 review item | Disposition | Static-only evidence |
|---|---|---|
| CR9-001: provider failure skips local session termination | closed | MfaAdminController.java:104-111 commits reset metadata and deletes application sessions before credential listing, deletion or provider logout. ApplicationSessions.java:20-23 performs transactional deletion. MfaResetAuditIntegrationTest.java:109-118 now asserts session deletion after provider failure. |
| CR9-002: environment-file creation permission window | closed | scripts/dev.sh:38-43 applies umask 077 before shell redirection creates the credential file; chmod 600 remains at line 44. |
| Reset audit and invalidation of earlier MFA proofs | closed | MfaAdminController.java:99-118 records the attempt before destructive work and audits partial failure. MfaPolicyService.java:53-61 and StaffAssurance.java:53-59 reject pre-reset proofs. |
| REQUIRED precedence and preference override | closed | MfaPolicyService.java:124-136 selects the strongest applicable mode and defaults missing policy rows to REQUIRED. |
| REQUIRED enforcement and settings exceptions | mitigated | SecurityConfiguration.java:166,216 installs enforcement on both chains; MfaEnforcementFilter checks current policy; MfaController.java:103-121 independently guards settings mutations. No concrete protected-mutation bypass identified. |
| Administrative downgrade/reset controls | mitigated | MfaAdminController.java:72-95 requires dedicated permission, recent MFA, validated reason and no self-reset; MfaPolicyService.java:148-167 checks versions and audits policy changes. |
| Provider claim trust | mitigated | SecurityConfiguration.java:66-78 validates JWT issuer, audience and timestamps; StaffAssurance.java:39-44 consumes provider MFA assertions. |
| PREVIEW defaults | closed | V31__mfa_policy.sql:13-14 seeds OPTIONAL for all categories. |
| Remaining documented D18 decisions | open by documented decision | docs/security/mfa-policy.md, Residual risks; incorporated by reference without repeating them as findings. |

This is source-review closure, not runtime, deployment or full D18 acceptance verification. No SECR10 finding IDs assigned.

Findings: 0
