# Security review round 4 (independent, static)

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `d3e22ca081dc0ede9ee02f496642de4d1a86d63a`.

Summary (agent): HEAD matched the requested SHA at start and end; worktree clean. Reviewed the full 164a831..HEAD diff and related authorization/media paths offline. SECR-002 is closed in source: all three realm flow definitions are identical. Cookie and forms are ALTERNATIVE only inside the REQUIRED login subflow; successful cookie authentication completes that subflow, not the outer browser flow. The subsequent CONDITIONAL staff subflow becomes required when the role condition matches, enforcing REQUIRED OTP; customers skip it. This agrees with the maintainer-reported Keycloak 26.8.0 replay, which was not independently executed. CR3-002 is closed in source by scheduling sealed-upload cleanup after ticket expiry plus one minute. The fingerprint delimiter collision is also corrected. SECR-003 is only partially fixed: document rollback deletion is removed, public-image rollback deletion remains. SECR-004 protects files after successful rendering but leaves a first-render exposure window. SECR-001 remains open by decision because enforcement defaults off. With the flag ON, no inspected path gives a password-only token lacking an accepted MFA assertion staff authority: bearer and web authentication use AccountMapper, and scoped authorization checks its authorities. The environment alias is now wired through application.yaml and preview compose. All conclusions from this review are static-only, including concurrency, file-permission timing, cleanup scheduling and authorization behavior. Tests were inspected, not executed. Live realm rollout, deployed configuration, runtime claim issuance, storage behavior and upstream dependency advisories were not verified. No additional concrete vulnerability was established in the diff.

## Commands
| Command | Exit | Note |
|---|---|---|
| `git rev-parse HEAD` | 0 | Repeated before and after inspection; matched d3e22ca081dc0ede9ee02f496642de4d1a86d63a. |
| `git status --short` | 0 | Clean; no files modified. |
| `git diff --stat 164a831..HEAD; git diff --name-only 164a831..HEAD` | 0 | Enumerated all 21 changed files; inspected diff and related source/tests using read-only commands. |
| `python3 -B inline JSON inspection of the three realm files` | 0 | Confirmed identical authentication flows, matching browserFlow bindings and staff-role conditional configurati |
| `git diff --check 164a831..HEAD` | 0 | No whitespace errors; not runtime security verification. |

## Findings

### SECR4-001 [High] Default-off MFA enforcement still permits password-only staff grant holders
`backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java:55`
- Scenario: An active account has an application staff grant but lacks the Keycloak matbakhy-staff role. With the default configuration, password-only authentication skips the realm's role-conditioned OTP and receives staff authorities.
- Actual: The default-off gate issues database-backed permissions without checking MFA. This is the still-open SECR-001 decision, not a newly introduced bypass.
- Evidence: AccountMapper.java:33 defaults false and :55-58 conditionally adds permissions. application.yaml:71 binds IDENTITY_REQUIRE_STAFF_MFA with default false; deploy/web-preview/compose.yaml:150 also defaults false. With the flag true, both bearer and web paths use this mapper and cannot obtain these authorities without StaffAssurance.assertsMfa succeeding.
- Suggested fix: Reconcile staff grant holders with the provider MFA policy and enable enforcement in the target environment.

### SECR4-002 [Medium] Public-image rollback callback can still delete a concurrent retry's approved object
`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaService.java:203`
- Scenario: Completion A writes media/<id>.jpg and rolls back. After its database lock releases but before its rollback callback deletes the object, completion B writes the same key and commits approval. A's delayed callback then deletes B's approved image.
- Actual: The document branch no longer registers rollback deletion, but the public branch retains unconditional deletion of its shared deterministic key.
- Evidence: MediaService.java:198-203 writes the deterministic public key and registers deletion. The callback executes after rollback at :305-312, outside the reference-aware cleanup worker. SECR-003 therefore remains open for public variants.
- Suggested fix: Use attempt-specific keys with durable reference-aware cleanup, or otherwise remove unconditional rollback deletion of the shared public key.

### SECR4-003 [Medium] Credential files are written before their parent directory becomes private
`deploy/web-preview/deploy.sh:61`
- Scenario: On a fresh render with a traversable deployment parent and a normal 022 umask, mkdir creates traversable generated directories. An unrelated local account opens or copies the rendered credential files before chmod 700 runs. A rendering failure before chmod can leave the exposure persistent.
- Actual: The script writes both files at lines 62-65 and restricts GENERATED_DIR only at line 68. Successful completion protects subsequent pathname access but cannot revoke a file descriptor already opened during rendering.
- Evidence: deploy.sh:59-70 has mkdir, credential rendering, then chmod 700 in that order. render establishes no restrictive umask; umask 077 at :30 belongs only to init_secrets. This leaves SECR-004 partially unresolved.
- Suggested fix: Create and restrict GENERATED_DIR before writing secrets, and establish restrictive creation permissions before rendering. Apply container-readable file permissions only beneath the already-private parent.
