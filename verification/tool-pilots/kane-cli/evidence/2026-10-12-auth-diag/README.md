# Kane CLI authentication diagnostic — 2026-10-12T09:32Z

Scope: authentication only. App not started, no test run, no credits used. No credentials, cookies, tokens or authorization headers recorded.

- CLI: `@testmuai/kane-cli` 0.8.22 (bundled Node runtime). Commands confirmed in `--help`: `login --profile/--username/--access-key`, `whoami --profile`, `balance --profile`, `profiles list|switch|delete`.
- New profile: `kane-diag-093227` (no existing profiles were present; none deleted).
- Input shape (values never printed): `LT_USERNAME` 12 chars, no whitespace; `LT_ACCESS_KEY` 52 chars with one leading space, the same shape as the earlier session. The leading space was removed before login.
- Capture method: a Node `--require` hook that logged only method, host, path (no query), status, request-ID headers and the error body of responses ≥ 400 (`network.ndjson`).

| Step | CLI output | Network |
|---|---|---|
| `kane-cli login --profile kane-diag-093227 --username … --access-key …` | `Credentials not valid. Please use valid credentials.` exit 1 | `GET kaneai-api.lambdatest.com/v16-controller/v1/auth/tms-credentials` → **401** `{"error":"authentication failed"}`; `x-request-id: 9c822882-66ee-4d5b-94b3-7548b38a3cad`, `cf-ray: a49518f629e70b74-ORD` (09:32:28.666Z) |
| `kane-cli whoami --profile kane-diag-093227` | Not logged in, Method none, Token missing; exit 1 | none |
| `kane-cli balance --profile kane-diag-093227` | `Not authenticated [prod]. Run "kane-cli login" first.` exit 2 | none (refused locally, no balance request sent) |

Conclusion: the server rejected the explicit login itself. Login did not succeed, and the balance check never reached the server. A 401 alone does not show whether the cause is the values, the account state or the endpoint; the request ID above lets TestMu support look up the server-side reason.
