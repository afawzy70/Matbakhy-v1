# Non-destructive DAST

Run on 2026-10-18 from `vmi3673442`; [29 initial results](evidence/dast-anonymous.json), [5 follow-up results](evidence/dast-followup.json). Sequential, 0.5s spacing, 15s timeout; redirects not automatically followed. TLS certificate/hostname verification remained enabled. No credentials, access tokens, cookie values or response bodies are stored. No brute-force/load/concurrency/real-data mutation performed. Deployed commit SHA **NOT VERIFIED**; source review baseline `b7ec0e9052a612f7a8aab38f00519107b1dce213` is not asserted as the deployed image.

| Test | Observed | Verdict / limit |
| --- | --- | --- |
| Customer and operations / | 200 | Both HTML shells accessible from this server; this does not prove public staff allowlist failure because server-origin may be trusted |
| Public configuration, zones, meals?limit=1 | 200 | Sampled safe public reads available |
| me, orders, all-zero order ID, cook orders, ops orders/staff | 401 | PASS anonymous boundary for sampled paths; authenticated role/BOLA NOT TESTED |
| Malformed bearer on orders | 401 | PASS malformed-token rejection; wrong issuer/audience/signature/expiry separately NOT TESTED with signed fixtures |
| Malicious-origin preflight | 403 | PASS sampled CORS rejection |
| First-party preflight | 200 | PASS permitted origin, response allow-origin/credentials recorded |
| Empty POST orders without CSRF/session | 403 | PASS unauthenticated CSRF boundary; authenticated browser CSRF/session lifecycle NOT TESTED |
| meals limit -1 / 101 and invalid cursor | 422 | PASS sampled validation; no stack trace/body payload retained |
| Actuator health/env/prometheus, API docs/Swagger | 404 | PASS sampled public exposure boundary |
| Keycloak /admin and master discovery | 404 | PASS sampled proxy boundary; no bypass claim based solely on these routes |
| Matbakhy OIDC discovery | 200 | Public issuer/PKCE metadata captured |
| OIDC authorization with foreign callback | 400 | PASS invalid redirect rejection |
| Web login initiation with foreign returnTo | 302 to configured IdP | State, nonce, S256 and PKCE challenge present; does not prove post-login return target behavior without completing login |
| No-PKCE authorization | 302 to configured callback | Error query value not retained in initial metadata; **NOT VERIFIED** as a successful/failed PKCE flow from status alone; no code/token exchange |
| Media /, private bucket and all-zero guessed private object | 403 | PASS sampled anonymous denials; no enumeration or object data retrieved |
| Session cookie at login initiation | Secure + HttpOnly + SameSite=Lax | PASS sampled flags; cookie rotation, authenticated expiry/logout and revocation NOT TESTED |
| CSRF cookie at login initiation | Secure, readable; SameSite absent | Expected JS-readable CSRF cookie; cross-subdomain scope source-reviewed. No session credential in this cookie |
| HSTS/nosniff/frame protections | Present on sampled HTML | PASS sampled headers; CSP absent (SEC-004) |

Keycloak emitted KC_STATE_CHECKER without Secure/HttpOnly in the invalid-request response; this is not the authenticated identity session cookie. No authentication/session compromise is claimed from that observation; inspect authenticated Keycloak cookies with dedicated accounts before signoff.

## Not tested / intentionally skipped

Dedicated customer A/B, cook A/B and staff accounts were not supplied. Thus ownership, cook/private data isolation, valid-role elevation attempts, MFA enforcement, session fixation/logout/expiry, valid JWT negative matrix and authenticated sensitive-field tests are NOT TESTED. No signup/account fabrication or existing demo passwords were guessed.

Pricing tampering, updated quote acknowledgement, preserved committed prices, capacity races, repeated payment/order operations and upload replay are NOT TESTED live; they need isolated synthetic fixtures/accounts and would create state. DoS advisories, high-volume rate tests, load, brute force, password reset, database/Keycloak resets, deletion and destructive tests were skipped under the owner’s policy. Conservative public requests cannot establish rate-limit enforcement. Stored/reflected XSS and browser DOM execution were not exercised with authenticated content.

Operations/kitchen access from an independent external IP and firewall reachability were NOT TESTED; local host listening metadata is not equivalent to remote exposure. Full TLS cipher/protocol scans and raw parser/request-smuggling tests were not run. This DAST checkpoint is incomplete, not a PASS for the whole application.
