# Content Security Policy rollout (SEC-004)

Status: **Report-Only deployed in the Caddyfile for `app.` and `ops.`; not enforced.** Rationale: an enforced policy was not validated against the real sign-in, checkout, staff and image flows, and a wrong policy would break the preview. Report-Only has no effect on behaviour.

Policy (see `deploy/web-preview/Caddyfile`, snippet `web_csp`): same-origin scripts/styles/fonts, `img-src` self + data: + media origin, `connect-src` self + API origin, no objects, `base-uri`/`form-action` self, `frame-ancestors 'none'`. Inline styles are allowed only as attributes (`style-src-attr`), which React needs; no `unsafe-inline` for scripts or style elements, no `unsafe-eval`.

Promotion steps (needs a browser run against the preview, i.e. the E2E suite plus a manual pass):
1. Run the browser E2E (customer web: browse, basket, checkout, deposit; ops web: cook and staff screens) and read the console for `Content-Security-Policy-Report-Only` violations.
2. Fix legitimate violations by tightening the app (preferred) or adding the narrow origin.
3. Rename the header to `Content-Security-Policy`; re-run the E2E and prove an injected inline script is blocked.

Until step 3 SEC-004 is **mitigated in part (Report-Only), not closed**.
