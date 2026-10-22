# Authentication and authorization review

Baseline `b7ec0e9052a612f7a8aab38f00519107b1dce213`. Static review plus sampled anonymous DAST; authenticated scenarios **NOT TESTED** pending dedicated account access. No possession of implementation-owner credentials is assumed.

## Backend boundaries

SecurityConfiguration defines a stateless bearer chain selected by Authorization: Bearer and a separate web OIDC/session chain. JWT decoder validates signature through configured JWKS, timestamps, issuer and API audience. Token role claims are not used: AccountMapper resolves the issuer/subject account and current server-side grants. Account status and grants are reread per request, including WebAccountFilter for OIDC sessions. Bootstrapping requires a configured exact subject/issuer and no active administrator. Review actual bootstrap subject lifecycle before real rollout; no bootstrap configuration was read from secrets.

Web mutations retain Spring CSRF protection and strict first-party CORS; only the anonymous capped activity endpoint bypasses CSRF because it acts for nobody and ignores identity. Bearer requests do not use ambient cookie credentials. Secure/HttpOnly/Lax session cookie settings were both source-reviewed and observed during login initiation. WebReturnTo validates exact origins, rejects userinfo and double-slash paths, and removes an invalid saved target. Live initiation included state, nonce and PKCE S256. Successful login/callback, fixation defense and logout revocation were NOT TESTED.

Staff controllers use server-side @PreAuthorize permissions; grant mutations, cook decisions, configuration writes, payment-recipient/finance decisions and handover override additionally require recent MFA where declared. StaffAssurance trusts signed provider assurance claims and checks age; token-role or frontend flags do not grant roles. GrantService rejects self grants, audits grant/revoke and protects the last administrator. Synthetic preview realm registration is disabled, direct password grants disabled, brute-force protection enabled and callback exact in template. Actual deployed realm/client configuration drift is NOT VERIFIED.

## Object-level review

| Resource | Static enforcement found | Runtime result |
| --- | --- | --- |
| Customer order reads/events/cancel | customer_id predicate under actor principal; cancellation locks owned row | Anonymous 401; customer A/B NOT TESTED |
| Quote/place | quote owned by customer, address owned by customer, actor not supplied in body | Authenticated NOT TESTED |
| Cook orders/slots/meals/settings | kitchen resolved from authenticated owner; kitchen predicates and scoped row locks | Cook A/B and customer-as-cook NOT TESTED |
| Payment reports | lockForCustomer / lockForKitchen scope payment through order ownership | Authenticated NOT TESTED |
| Staff operations | method permissions, selected MFA/step-up and server grants | Anonymous denied; customer/staff/MFA matrix NOT TESTED |
| Upload status/private document | owner or scoped cook-review permission, reviewable attachment, signed attachment download | Anonymous storage denied; authenticated attachment/reviewer scope NOT TESTED |
| Notifications/addresses/favorites | principal-scoped service predicates reviewed | Cross-account matrix NOT TESTED |

No verified BOLA or permission escalation was demonstrated in reviewed paths. This is not an authorization PASS: valid accounts, owned fixtures, revoked roles, wrong-role requests and recent/stale MFA must be independently exercised.

## Web and mobile

React clients use credentials:include and echo XSRF-TOKEN; session credentials are not stored in localStorage/sessionStorage. Customer basket and anonymous activity identifiers use browser storage; they are not authority. Reviewed rendering uses React interpolation; no confirmed production dangerouslySetInnerHTML/eval sink found. Dependencies require separate triage. CSP absence is SEC-004.

Expo AuthSession uses code+PKCE in the system browser, exact callback/state validation and no public client secret. TokenStore uses Expo SecureStore with WHEN_UNLOCKED_THIS_DEVICE_ONLY; validates stored shape, deletes invalid state and keeps tokens out of logs. Subject decoding is explicitly unverified client identity selection; API validates tokens. Deep links name screens and filter auth callbacks, while server authorization remains necessary. API/issuer config requires HTTPS outside development. Android cleartext plugin permits localhost/emulator-host only for development configuration; app identifiers remain development identifiers. Native secure-store/backup, Android manifest, iOS ATS/Keychain, universal links, signed release build and physical-device behavior were **NOT TESTED** in this audit. Do not infer native assurance from TypeScript alone.
