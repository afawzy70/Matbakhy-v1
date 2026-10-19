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
   (REQUIRED), then OTP form (REQUIRED, not conditional).
   **There is no public enrolment.** The `CONFIGURE_TOTP` required action is disabled in the master realm, so an account without an OTP simply cannot sign in:
   knowing a password never lets anyone enrol their own authenticator first. Administrators receive their password *and* OTP key from the server
   (`./deploy.sh admin-console create-admin <name>`, below).
2. **No password-only path.** The password grant is switched off for every master client (including `admin-cli`), no master client may override the browser or
   direct-grant flow, and sessions/tokens that predate the hardening are revoked, so the Internet cannot obtain a token or reuse an old cookie with a password alone. Operator tooling (`migrate-mfa.sh`'s kcadm, and `identity/admin_console.py`, which talks to the Admin REST API on the identity container's Docker address) signs in as the service account of the confidential master client
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
reachable on the public address; an account **without** an OTP cannot sign in and is offered no enrolment. The synthetic administrators are deleted afterwards (a trap; `./admin-console.sh fixture-clean` removes leftovers).

## First sign-in (owner)

The existing bootstrap account (`KEYCLOAK_ADMIN_USER`) has no OTP, so after hardening it **cannot sign in to the console** (that is intended). The first
`enable` therefore creates the owner's administrator `owner-admin` by itself when nobody can sign in yet (name: `ADMIN_CONSOLE_OWNER`); more can be created on the server:

```
./deploy.sh admin-console create-admin <name>      # e.g. a personal account
```

It writes `generated/admin-console/<name>.private` (mode 600, directory 700): the generated password, the one-time-password key (base32) and an `otpauth://` URI.
Read the file on the server (`sudo -u devops cat <GENERATED_DIR>/admin-console/<name>.private`, e.g. `/srv/matbakhy-staging/generated/admin-console/owner-admin.private` where `GENERATED_DIR` is the directory in the server's `.env`), add the account in an authenticator app (iPhone: Passwords or any TOTP app, "enter a setup key" with the base32 key, 6 digits, 30 s), sign in
at the console URL, then **delete the file**. Never paste its contents into chats, tickets or logs. `./deploy.sh admin-console status` lists every master account
as `otp` or `no-otp(cannot-sign-in)`. To replace an administrator (lost phone): `REPLACE=1 ./deploy.sh admin-console create-admin <name>`.

## Rollback

```
./deploy.sh admin-console rollback            # closes the host, rebinds the previous master browser flow, re-enables the admin-cli password grant
PURGE=1 ./deploy.sh admin-console rollback    # also removes the operations client and the master frontend URL
```

The previous flow name and a Keycloak database dump (`keycloak-db-<stamp>.dump`) are written under `$BACKUP_DIR/keycloak/` before any change
(`admin-console-state.env`, parsed as data, never sourced). The `matbakhy` realm, its users, credentials, clients and sessions are never modified by any of this.
To remove the exposure without touching Keycloak: `./deploy.sh admin-console disable`. To remove it from the code: revert the pull request (the host then no longer exists in the Caddyfile).

A lost authenticator is handled with `REPLACE=1 ... create-admin <name>`. Lock-out of everything is a database-level recovery (restore the dump above).
The operations client's secret never appears on a command line (the Admin REST client reads it from the environment).

## What this does not prove

Physical-device sign-in on a phone is the owner's step; the verification above drives the same browser flow over HTTP and is not a rendering test of the console in Safari.
