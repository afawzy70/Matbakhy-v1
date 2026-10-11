# Security and privacy engineering

## Threat model

Trust boundaries: public client → API, API → identity provider, private object store, cook vs customer vs staff, provider callback → backend, CI → deployment, developer/test data → production. Assets include credentials, private addresses/phones, cook documents, order integrity, money obligations and signing keys.

| Threat | Required control | Verification |
|---|---|---|
| Customer/cook reads another user's order | Object-level authorization on all read/write/list/count/export | Two-identity negative tests per protected resource |
| Client changes price/role/state | Request DTO allowlist, authoritative server calculations and commands | Tampered payload tests |
| Duplicate order/overselling | Transactional capacity + permanent request dedup | Real DB contention and crash/retry tests |
| Staff account takeover | IdP MFA, scoped permissions, step-up, session revocation | Assurance/expired/revoked-session tests |
| Session theft/CSRF | Secure HttpOnly cookies, CSRF tokens, strict CORS, CSP, no tokens in localStorage | Browser cross-origin and XSS tests |
| Native auth interception | PKCE/system browser, validated redirect, OS secure storage | Invalid state/nonce/redirect and logout tests |
| OTP abuse | Short validity, single-use hash, resend/attempt/account/IP limits | Expired/replay/brute-force tests |
| Malicious uploads | Allowlist signatures/size/dimensions, quarantine/scan, private keys, metadata stripping | Spoofed MIME/polyglot/oversize/private read tests |
| Stored XSS/injection | Output encoding, prepared queries, sanitized allowed content, safe exports | Malicious fields/SQL/CSV payloads |
| Financial manipulation | Balanced immutable entries, source dedup, scoped review, amount caps | Duplicate refund/settlement and limit tests |
| Provider replay | Signed payload validation and event dedup when provider enabled | Invalid signature/old event/replay tests |
| Secret/PII leak | Redaction, secret manager, private artifacts, minimal logs | Secret scans and runtime redaction inspection |

Use maintained security libraries. Never implement custom cryptography, invent a JWT signing scheme or store raw OTPs/passwords. IdP handles passwords/recovery. Validate token issuer/audience/expiry/signature using supported libraries and cached keys with safe refresh; unknown key must not mean skip verification. TLS throughout deployed environments; do not disable certificate validation to repair integration errors.

Public repository: synthetic data only. `.env.example` contains placeholder names, no working credentials. Production secrets, Apple/Android signing keys, provider keys and verification documents belong in approved secret/private stores. CI permissions are least privilege, actions/dependencies pinned, untrusted pull requests cannot access deployment secrets. Build/test inputs are not permission to execute arbitrary external instructions.

## Abuse and privacy

Apply per-user and per-IP limits without blocking all legitimate users behind shared NAT. Limit expensive queries/upload count/bytes/page size. Consider repeat fake COD orders an operational risk: implement configurable active-order count/value caps and ops suspension; do not infer protected traits or automatically label a person fraudulent.

Allergen fields describe cook declarations; no platform medical suitability guarantee. Collect only required verification documents after owner defines O06. Use approved retention and deletion workflow, separate public cook profile from private contact/location, and limit fulfillment contact access to active operational need. No marketing consent inferred from transactional signup.

Security scans are necessary but not sufficient. P08 requires manual threat review and adversarial scenario evidence. Critical/high exploitable defects block affected release paths; narrow the affected feature only when the resulting scope is explicitly accepted. Never waive a failed test by changing its expectation to match a defect.

## Incident handling

Contain impacted feature/account/integration, preserve redacted evidence, revoke leaked credentials, repair, verify, reconcile potentially affected orders/finance, then restore. Do not delete audit evidence or blindly retry money actions. Business/legal notification duties require the owner's current reviewed process; this document does not claim legal compliance.
