# Code review round 4

Agent: independent Codex CLI 0.163.0 read-only, stdin </dev/null. Commit inspected: `d3e22ca081dc0ede9ee02f496642de4d1a86d63a`.

Summary (agent): HEAD verified. Two findings remain. Length-prefixed fingerprinting, transactional approval enqueue, sealed-key rollback change, MFA property binding and test-clock changes are consistent with the claimed fixes. All three realms parse, resolve execution references and use requirements consistent with Keycloak 26 flow processing ([source](https://raw.githubusercontent.com/keycloak/keycloak/26.0.0/services/src/main/java/org/keycloak/authentication/DefaultAuthenticationFlow.java)). Actual realm import and Java integration tests remain unverified: Docker access is denied and Java/Maven are unavailable. Read-only filesystem prevented writing review artifacts.

## Commands
| Command | Exit | Note |
|---|---|---|
| `git rev-parse HEAD` | 0 | Equals the required source SHA. |
| `git diff 164a831..HEAD` | 0 | Reviewed relevant source, configuration and test changes. |
| `Python JSON parsing and execution-reference/requirement checks for all three realms` | 0 | All referenced flowAlias and authenticatorConfig aliases exist; requirement values pass static checks. Actual  |
| `sh -n deploy/web-preview/deploy.sh` | 0 | Shell syntax valid. |
| `Python SQLite model of repeated enqueue ON CONFLICT DO NOTHING` | 0 | Confirmed later notBefore is ignored; SQL-model evidence only, not PostgreSQL integration. |
| `docker info --format '{{.ServerVersion}}'` | 1 | Docker socket permission denied. |

## Findings

### CR4-001 [Medium] Repeated enqueue silently discards a later deletion deadline
`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaCleanup.java:55`
- Scenario: Enqueue a private object for immediate cleanup, then enqueue the same bucket/key with notBefore set to upload-ticket expiry plus one minute. The unique constraint preserves the earlier deadline.
- Actual: ON CONFLICT DO NOTHING leaves next_attempt_at unchanged. Once the object is unreferenced, runDue can delete it before the later notBefore.
- Evidence: The four-argument enqueue computes due but its INSERT ignores conflicting rows. V30__media_cleanup.sql defines UNIQUE(bucket, object_key). A two-insert SQL model reproduced retention of the earlier deadline. Existing tests repeat only identical immediate requests.
- Suggested fix: For pending conflicts, atomically retain the greater existing/requested deadline. Explicitly define terminal-row re-enqueue behavior.

### CR4-002 [Medium] Generated directory is secured only after secrets are written
`deploy/web-preview/deploy.sh:61`
- Scenario: Run render for a new GENERATED_DIR under a traversable parent with the common umask 022, or rerender a directory left at mode 755 by the previous script.
- Actual: mkdir creates traversable directories and sed writes secret-bearing files before chmod 700 runs. Another local user can read them during rendering; a failure before chmod leaves the directory exposed.
- Evidence: Directory creation occurs at line 61, secret rendering at lines 62–65, and the first owner-only chmod at line 68. render does not set a restrictive umask before these operations.
- Suggested fix: Create and chmod GENERATED_DIR to 700 before creating children or opening output files, and use a restrictive umask during rendering.
