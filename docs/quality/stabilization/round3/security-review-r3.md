# Security review (independent, static)

Agent: independent Codex CLI 0.163.0, `codex exec -s read-only`, stdin </dev/null. Commit inspected: `164a831fe3f38236801103582cf0c5b76a6c470d` (source identical to the fixed tree except the round-3 fixes in d3e22ca).

Summary (agent): Static-only review; HEAD matched the requested SHA before and after inspection. Five findings remain. SEC-002/CR-003 seals approved documents under a key unavailable to upload tickets, closing the direct overwrite replay. SEC-001 structurally inspects decoded PDF names and compressed objects; no concrete bypass of the targeted checks was identified. QA-002 has a canonicalization collision. Durable cleanup has bounded retries and safe class-only logging, but rollback deletion can remove a concurrently approved object. Staff MFA is incomplete through grant/role mismatch and existing SSO sessions. Inspected order, payment, cash, media and support paths enforce customer/kitchen ownership or staff permissions; no additional concrete object-authorization, CSRF, mass-assignment, storage-key traversal or SSRF bypass was identified. RedactedThrowable removes exception messages and suppressed exceptions; inspected MediaCleanup logs contain identifiers and exception class names rather than secret values. Workflow controls include pinned actions, exact-commit CI gating, verified SSH hosts and digest-pinned deployment images; rendered secret-file permissions remain unsafe. Tomcat 11.0.26, Jackson 3.1.7 and PDFBox 3.0.8 exist in the local Maven cache; the override property names match the cached Boot BOM, and PDFBox aligns its internal dependencies. Could not verify upstream release authenticity/current advisories, resolved packaged dependency versions, compatibility, runtime authorization, PDF parser behavior, concurrency outcomes, actual CI results, GitHub environment protections, deployed realm state, storage permissions, or live log contents. No network, runtime tests or DAST were used; repository tests were inspected, not executed.

## Commands
| Command | Exit | Note |
|---|---|---|
| `git rev-parse HEAD` | 0 | Executed before and after review; both returned the requested source SHA. |
| `git status --short` | 0 | Worktree clean; no files changed. |
| `Static source inspection using rg, cat, sed, nl and Python JSON parsing` | 0 | Inspected security configuration, scoped services/controllers, media lifecycle, realm flows, workflows, deploy |
| `Python synthetic fingerprint collision check` | 0 | Confirmed distinct policy/note field tuples produce identical canonical bytes and SHA-256. |
| `Read cached Maven artifact metadata and Spring Boot dependency BOM` | 0 | Confirmed requested versions are cached and override properties are recognized by the parent BOM; no dependenc |

## Findings

### SECR-001 [High] Password-only grant holders retain staff permissions by default
`backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java:55`
- Scenario: An active account receives support.case or finance.read through the application grant lifecycle but has no Keycloak matbakhy-staff role. Its password-only login skips conditional OTP, yet AccountMapper grants its staff authorities. The preview compose configuration does not enable the optional mapper gate.
- Expected: Every staff grant holder must authenticate with MFA before receiving staff permissions.
- Actual: The IdP condition uses a separate realm role, while application authorization uses database grants. require-staff-mfa defaults to false, allowing password-only access to staff endpoints without an additional step-up requirement.
- Evidence: AccountMapper.java:33 defaults the gate off; lines 55-57 issue permissions without MFA when disabled. GrantService grants database permissions without synchronizing the IdP role. docs/security/staff-mfa-enforcement.md explicitly documents the mismatch and password-only preview staff.
- Suggested fix: Require provider-asserted MFA for staff authorities in deployed environments and reconcile every grant holder with the IdP MFA policy before activation.

### SECR-002 [High] Existing password-only SSO sessions bypass the staff OTP subflow
`infra/identity/matbakhy-realm.json:177`
- Scenario: A customer signs in with a password, then is assigned the matbakhy-staff role and an application staff grant while its Keycloak SSO session remains active. A subsequent normal authorization request authenticates through auth-cookie.
- Expected: The newly privileged account must complete OTP before obtaining staff access, including when an SSO session already exists.
- Actual: auth-cookie and the forms flow are ALTERNATIVE siblings. OTP is nested exclusively inside forms, so successful cookie authentication avoids the role-conditioned OTP step. The default-off API MFA gate does not compensate.
- Evidence: infra realm lines 175-188 place auth-cookie before the alternative forms flow. The same structure appears at deploy/web-preview/identity/matbakhy-realm.preview.json:152 and backend/app/src/test/resources/keycloak/test-realm.json:190. Inspected IdP tests cover fresh password/OTP flows, not privilege elevation with an existing cookie.
- Suggested fix: Enforce staff assurance after all primary authentication paths, including cookie authentication, and invalidate or step up existing sessions on staff elevation. Retain API enforcement of the MFA assertion.

### SECR-003 [Medium] Rollback cleanup can delete a concurrent retry's approved document
`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaService.java:209`
- Scenario: Completion A writes documents/<mediaId> and later rolls back. After the database releases A's row lock, completion B retries, writes the same deterministic key and commits APPROVED. A's delayed afterCompletion rollback callback then deletes that key.
- Expected: Cleanup must never delete an object referenced by a committed approved asset.
- Actual: The rollback callback deletes the shared deterministic key directly, without the durable cleanup worker's reference check. The public variant uses the same pattern at line 203.
- Evidence: MediaService.java:207-210 writes a deterministic sealed key and registers unconditional deletion. onRollback at lines 301-310 invokes that deletion after transaction completion, when a waiting retry can proceed. This bypasses MediaCleanup.runDue's active-reference guard.
- Suggested fix: Use unique object keys per completion attempt and enqueue failed-attempt objects for durable reference-aware cleanup instead of deleting shared keys in rollback callbacks.

### SECR-004 [Medium] Rendered identity and storage credentials are world-readable
`deploy/web-preview/deploy.sh:67`
- Scenario: After deploy.sh render or up, an unrelated local host account traverses the generated configuration directories and reads the rendered realm or storage configuration.
- Expected: Only the deployment account and required container identities can read credential-bearing files.
- Actual: The script explicitly makes generated directories mode 755 and both credential-bearing files mode 644.
- Evidence: Lines 62-65 render the OIDC client secret, preview account credentials and storage credentials into files; lines 66-67 grant directory traversal and file readability to all local users. deploy-preview.yml invokes deploy.sh up, which runs render.
- Suggested fix: Use restrictive directory permissions and narrowly scoped ownership/group permissions for the container UIDs, or mount secrets through an appropriate secret mechanism.

### SECR-005 [Low] Unescaped delimiters allow distinct requests to share a fingerprint
`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:139`
- Scenario: Place an order with cancellation version 'cancel-v1' and fulfillment note 'x\ny'. Retry the same quoteId/clientRequestId using a fresh Idempotency-Key, cancellation version 'cancel-v1\nx' and note 'y'. Both strings meet the controller's length constraints.
- Expected: Changed policy acceptance or fulfillment notes must cause IDEMPOTENCY_CONFLICT.
- Actual: Joining unescaped fields with newlines produces identical canonical bytes for these distinct requests. The permanent-request recovery branch accepts the matching fingerprint before validating policy versions.
- Evidence: OrderService.java:139-140 joins variable-length strings without escaping or length prefixes; lines 153-160 return the existing order on a fingerprint match. A synthetic Python check confirmed identical canonical bytes and digests. This bypasses conflict detection but does not create another order or modify the original.
- Suggested fix: Hash an unambiguous versioned representation, such as a JSON array of normalized fields or length-prefixed values.
