# Code review round 3

Agent: independent Codex CLI 0.163.0, `codex exec -s read-only`, stdin </dev/null. Commit inspected: `164a831fe3f38236801103582cf0c5b76a6c470d` (source identical to the fixed tree except the round-3 fixes in d3e22ca).

Summary (agent): Three actionable findings. Reviewed the requested stabilization diff and related code. HEAD matches the requested SHA; working tree remains clean. TLS helper tests passed. Full backend/Keycloak integration tests were not run. PDF probing did not establish a vulnerability. Report files were not written because the sandbox is read-only.

## Commands
| Command | Exit | Note |
|---|---|---|
| `git rev-parse HEAD` | 0 | Confirmed requested SHA at start and end. |
| `git diff --check b7ec0e9..HEAD -- backend apps deploy infra scripts .github` | 0 | No whitespace errors. |
| `python3 -B scripts/test_rehearsal_tls.py` | 0 | Three tests passed. |
| `python3 -B scripts/validate_blueprint.py` | 0 | Blueprint structure passed; this does not verify product behavior. |
| `Inline Python fingerprint and realm validation` | 0 | Confirmed distinct requests produce identical fingerprints. All three realm JSON files have identical custom f |
| `Bounded PDFBox 3.0.8 nested-array probe using local JDK and in-memory source` | 0 | PDFBox rejected a roughly 40 KB nested-array input with IOException. No PDF vulnerability established. An init |
| `git status --short` | 0 | Working tree clean. |

## Findings

### CR3-001 [Medium] Unescaped field delimiters allow different placement requests to share a fingerprint
`backend/modules/orders/src/main/java/com/matbakhy/orders/internal/OrderService.java:139`
- Scenario: Place an order with cancellation version 'cancel-v1' and fulfillment note 'leave\nat door'. Retry the same quoteId/clientRequestId with a new Idempotency-Key, cancellation version 'cancel-v1\nleave', and note 'at door'. Both policy strings satisfy the controller's length and nonblank constraints.
- Expected: The changed cancellation version and fulfillment note produce IDEMPOTENCY_CONFLICT.
- Actual: Both requests produce the same canonical string and SHA-256. The existing-order branch returns the original order before validating the changed policy version.
- Evidence: An executable Python reproduction of the exact concatenation confirmed equal fingerprints. Lines 158-160 accept the matching fingerprint; policy validation occurs later at line 166. A new Idempotency-Key avoids the temporary idempotency record's distinct JSON hash.
- Suggested fix: Hash an unambiguous versioned encoding, such as a JSON array or length-prefixed fields, after applying the intended normalization.

### CR3-002 [Medium] Cleanup can finish before the upload ticket loses write access
`backend/modules/catalog/src/main/java/com/matbakhy/catalog/internal/media/MediaService.java:212`
- Scenario: Complete a document shortly after obtaining its ten-minute upload ticket. The cleanup job deletes uploads/<id> and marks the cleanup DONE. Before the ticket expires, reuse the original signed PUT URL to upload the same-sized bytes again.
- Expected: The obsolete upload object is eventually removed after no valid ticket can recreate it.
- Actual: The object is recreated after successful cleanup and retained indefinitely by this cleanup mechanism. DONE rows are never selected again, enqueue ignores the existing row, and the approved asset now stores only documents/<id>.
- Evidence: createTicket issues a ten-minute reusable signed PUT. Line 212 enqueues immediate cleanup without ticket expiry. MediaCleanup.runDue selects only PENDING rows and marks successful deletion DONE at line 76. The approved-object reference guard cannot detect a later PUT to the abandoned upload key.
- Suggested fix: Schedule final deletion after ticket_expires_at plus an appropriate margin. If immediate deletion is desired, retain a pending final sweep until the upload capability expires.

### CR3-003 [High] Documented MFA rollout variable does not enable the API enforcement
`backend/app/src/main/java/com/matbakhy/app/security/AccountMapper.java:33`
- Scenario: Follow docs/security/staff-mfa-enforcement.md and set IDENTITY_REQUIRE_STAFF_MFA=true for the backend. A server-side grant holder without the Keycloak matbakhy-staff role signs in using a password-only token.
- Expected: The documented toggle enables enforcement and prevents that token from receiving staff permissions.
- Actual: The injected property remains false: IDENTITY_REQUIRE_STAFF_MFA is not mapped to matbakhy.identity.require-staff-mfa. AccountMapper therefore continues granting staff authorities without checking the token's MFA assertion.
- Evidence: The constructor reads only ${matbakhy.identity.require-staff-mfa:false}. application.yaml maps other identity environment aliases explicitly but contains no mapping for IDENTITY_REQUIRE_STAFF_MFA. Repository search finds that alias only in documentation. Lines 55-58 grant permissions when the flag is false.
- Suggested fix: Add an explicit application.yaml mapping from IDENTITY_REQUIRE_STAFF_MFA to matbakhy.identity.require-staff-mfa, propagate it through the deployment configuration, and verify the documented rollout path.
