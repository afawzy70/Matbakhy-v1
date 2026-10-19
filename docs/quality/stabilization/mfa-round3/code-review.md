# Code review of the D18 MFA change (round 3)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `7e61db71e8a089e52d3bae9ffacdf7022907b2cd`.

Summary (agent): Changes required. Static inspection confirms CR8-001/002/003 fixes: Spring selects the annotated JDBC constructor; no other production StaffAssurance constructors were found; reset attempt auditing precedes destructive work; STAFF retains step-up under DISABLED. Two remaining issues follow. Runtime tests NOT_RUN and report files not written because the sandbox is read-only. HEAD verified at start and end.

## Findings

### CR9-001 [High] Provider failure prevents local session termination
`backend/app/src/main/java/com/matbakhy/app/mfa/MfaAdminController.java:110`
- Scenario: Support resets a compromised account whose policy is OPTIONAL and preference is off. Credential listing, deletion or provider logout throws while the attacker holds an application web session.
- Actual: The exception skips sessions.endAllFor. The reset epoch blocks earlier MFA proofs, but ordinary protected API calls remain accessible through the surviving web session when MFA is not required.
- Evidence: Lines 105–110 place all provider calls before local deletion inside one try block. MfaEnforcementFilter.java checks the reset epoch only when decision.required() is true. MfaResetAuditIntegrationTest exercises second-deletion failure but checks session deletion only after a subsequent successful retry. This concerns surviving web sessions, not the documented five-minute bearer-token lifetime.
- Suggested fix: Attempt local session termination independently after recording the reset epoch, preserving and auditing failures from both local and provider operations.

### CR9-002 [Medium] Initial environment file is readable before chmod
`scripts/dev.sh:43`
- Scenario: On a shared host with traversable workspace directories and umask 022, another local user watches for initial infra/.env creation and opens it before chmod runs.
- Actual: Shell redirection creates the file with permissive inherited permissions, writes generated credentials, then chmod restricts it. The new umask 077 occurs later in render_realm and does not protect ensure_env.
- Evidence: Lines 38–44 write the environment file before chmod 600; up calls ensure_env before render_realm, where umask 077 is set at line 60. An already-open file descriptor remains readable after chmod.
- Suggested fix: Set umask 077 before any environment-file creation, preferably at script startup.
