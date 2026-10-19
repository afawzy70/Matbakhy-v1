# Public Keycloak admin console (Preview only)

Owner approval 2026-10-19: the Keycloak administration console is reachable from the public Internet at
`https://kc-admin.<preview domain>` (Preview: `https://kc-admin.167-86-90-174.sslip.io`) so the owner can administer the preview realm from a phone on
mobile data or a laptop. **Preview only. It is not approved for staging or production.** Customer sign-in stays on `https://id.<domain>` and is unchanged.

## What is exposed, and what is not

| Path on `kc-admin.*` | Served | Why |
|---|---|---|
| `/` | redirect to `/admin/master/console/` | convenience |
| `/admin`, `/admin/*` | Keycloak | the console and the Admin REST API (every REST call needs a bearer token; none gives 401) |
| `/realms/master`, `/realms/master/*` | Keycloak | the console's sign-in (master realm) |
| `/resources/*`, `/js/*` | Keycloak | console static assets |
| everything else (the `matbakhy` realm, `/health`, `/metrics`, `/welcome`, ...) | **404** | not needed; the customer realm stays on `id.*` |

Not published, ever: Keycloak's management port 9000 (health/metrics), Keycloak 8080, PostgreSQL, SeaweedFS, the API's management port, Grafana. Only
the proxy publishes ports (80/443). `id.*` still answers 404 for `/admin` and `/realms/master`.

## Security controls

1. **Password AND one-time password for every administrator.** The master realm's browser flow is `matbakhy master browser`: cookie, then username+password
   (REQUIRED), then OTP form (REQUIRED, not conditional). An administrator without an OTP is forced to enrol one at the first sign-in.
2. **No password-only path.** The password grant is switched off for every master client (including `admin-cli`), so the Internet cannot obtain a token with a
   password alone. Operator tooling (kcadm, `migrate-mfa.sh`, this script) signs in as the service account of the confidential master client
   `matbakhy-ops-cli`; its secret is `KEYCLOAK_OPS_CLIENT_SECRET` in the server's `.env` (mode 600, created by `./deploy.sh render`) and is never printed or committed.
3. **Brute-force protection** in the master realm: 5 failures, then a temporary lock that grows (60 s steps up to 15 min); no self-registration, no reset-password flow.
4. **Dedicated hostname.** `KC_HOSTNAME_ADMIN=https://kc-admin.<domain>` plus the master realm's frontend URL, so the master realm's issuer, sign-in forms,
   cookies and Admin REST all live on `kc-admin.*` (host-only `Secure` cookies; the customer host never sees them). CORS: only the console's own origin is allowed.
5. **Fail closed.** The proxy answers 404 for the whole host unless the file `generated/flags/admin-console-public` exists. `admin-console.sh enable` creates
   it only after hardening, and removes it again if the live verification does not pass. `./deploy.sh admin-console disable` removes it at once.
6. **Not tied to an IP address** (required so a phone on mobile data works); the controls above are the protection.

## Enabling, verifying, closing

The CD workflow runs `./deploy.sh admin-console enable` after every release (idempotent). By hand, on the server:

```
cd /srv/matbakhy-staging/deploy/web-preview
./deploy.sh admin-console status      # hardening, switch, how many users have an OTP
./deploy.sh admin-console enable      # backup, harden, open the switch, verify live (closes again on failure)
./deploy.sh admin-console verify      # live checks only
./deploy.sh admin-console disable     # kc-admin.* answers 404 again
```

`verify` creates a synthetic **read-only** administrator (`verify-admin-<random>`, random password and TOTP secret held in a mode-600 temporary file, rights:
view/query on the matbakhy realm and view-realm on master), signs in through the public URL and checks, with PASS/FAIL/NOT_RUN and exit 0/1/3: the console and
every asset load from the host; the master realm advertises only `kc-admin` URLs; Admin REST refuses no/forged tokens; a **password alone does not sign in** (the OTP
form is required); wrong password and wrong OTP issue no code; the right password plus OTP yields a token whose issuer is `kc-admin.*`; the `admin-cli`
password grant is refused; users, roles, clients and authentication flows of the matbakhy realm are listed through the Admin REST API; a foreign `Origin`
gets no CORS allowance; the read-only account cannot create users or read master users; paths outside the console answer 404; `id.*` is unchanged; internal ports are not
reachable on the public address. The synthetic administrator is deleted afterwards (a trap; `./admin-console.sh fixture-clean` removes leftovers).

## First sign-in (owner)

The first administrator is the bootstrap account (`KEYCLOAK_ADMIN_USER`, password `KEYCLOAK_ADMIN_PASSWORD` in the server's `.env`). Open the URL, sign in, and Keycloak
shows a QR code: scan it with an authenticator app (iPhone: Passwords or any TOTP app) and enter the code. **Do this immediately after the deploy:** until an OTP is
enrolled, whoever holds the password could enrol first (the password lives only in `.env`; never paste it in chats, tickets or logs). `status` prints
`otpEnrolledUsers`. Afterwards create a personal administrator (the console marks the bootstrap account as temporary) and give it its own OTP.

## Rollback

```
./deploy.sh admin-console rollback            # closes the host, rebinds the previous master browser flow, re-enables the admin-cli password grant
PURGE=1 ./deploy.sh admin-console rollback    # also removes the operations client and the master frontend URL
```

The previous flow name and a Keycloak database dump (`keycloak-db-<stamp>.dump`) are written under `$BACKUP_DIR/keycloak/` before any change
(`admin-console-state.env`, parsed as data, never sourced). The `matbakhy` realm, its users, credentials, clients and sessions are never modified by any of this.
To remove the exposure without touching Keycloak: `./deploy.sh admin-console disable`. To remove it from the code: revert the pull request (the host then no longer exists in the Caddyfile).

If an administrator loses the authenticator, the operations client can reset it: `kcadm` in the container (`./kcadm-in-container.sh`) removes the user's `otp` credential
through `users/<id>/credentials/<credential id>`; the user enrols a new one at the next sign-in. Lock-out of everything is a database-level recovery (restore the dump above).

## What this does not prove

Physical-device sign-in on a phone is the owner's step; the verification above drives the same browser flow over HTTP and is not a rendering test of the console in Safari.
