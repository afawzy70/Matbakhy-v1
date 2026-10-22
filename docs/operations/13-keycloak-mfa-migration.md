# Keycloak MFA migration of the existing preview realm (D18)

**Status: rehearsed against Keycloak 26.8.0 on a throwaway local instance; NOT yet run on the Contabo preview.** CI success proves the
repository realm files and the application, not that this server's realm was changed. The live run needs a person with Docker access
on the host (`deploy/web-preview`), and its result must be recorded below (see "Evidence of the live run").

Why a migration: the preview deployment imports `identity/matbakhy-realm.preview.json` with `--import-realm`, which **skips a realm that
already exists**. The new MFA flow, authentication levels, `amr` mapper and admin service account therefore have to be applied to the
running realm. Files: `deploy/web-preview/migrate-mfa.sh` (operator entry), `identity/mfa-migration.kcadm.sh` (idempotent kcadm
logic), `kcadm-in-container.sh` (runs kcadm inside the Keycloak container; the admin password and the new client secret never appear
as host command-line arguments), `identity/verify-mfa-flow.py` (checks from outside, through the public proxy).

## What it changes (and what it never touches)
Creates the browser flow `matbakhy browser` (cookie / `matbakhy browser forms` → level 1: username+password, level 2: OTP form, each level
behind a *Level of Authentication* condition; amr references `pwd` / `otp`), sets `acr.loa.map={"mfa":"2"}` on the realm and the web (and
mobile, when present) clients, adds the `oidc-amr-mapper` to those clients, creates the `matbakhy-admin` service-account client with the
realm-management roles `view-users` and `manage-users` only, and **binds the new flow last**. A normal sign-in never asks for OTP; only an
authorization that asks for `acr_values=mfa` does. Users, credentials, sessions, other clients and the built-in `browser` flow are not
modified or deleted. No downtime; the identity container is not restarted.

## Order
1. Deploy the release that contains migration V31 (the CD workflow does it). Until the secret below exists the API starts and everything
   works, but the MFA reset and "end sessions" return 503 / are skipped.
2. The same deploy does this: `deploy.sh up` runs `render` first, which adds `OIDC_ADMIN_CLIENT_SECRET` to `.env` if missing (mode 600 kept) and
   re-renders the (ignored-on-existing-realm) realm file, and compose passes it to the API. Check with `./migrate-mfa.sh status` that `.env` has the secret before step 4.
3. `./migrate-mfa.sh status` — read-only; expect `flows=absent`, `browserFlow=browser`.
4. `./migrate-mfa.sh apply` — runs, in this order: verified database backup (`deploy.sh backup`), a Keycloak database dump and a realm
   export (no users) under `$BACKUP_DIR/keycloak/` (mode 600) plus a state file with the previous browser flow, the migration, and
   `verify`. It takes 3–5 minutes (kcadm starts a JVM per call).
5. `./migrate-mfa.sh status` again — expect `flows=complete`, `browserFlow=matbakhy browser`, `client.matbakhy-admin=present`.
6. `./migrate-mfa.sh verify` can be repeated at any time. It creates a **synthetic user** (`verify-mfa-<random>`, random password and TOTP secret held in a
   mode-600 temp file) through kcadm, signs in with real Keycloak flows, exchanges the codes for tokens and calls the real API through the public proxy:
   a level-1 token must carry the API audience and **no** MFA evidence; a step-up answered with the user's real OTP must carry `acr` in the API's MFA values
   and `amr` `otp`; the API must accept that token when the user turns two-step verification on, **reject** the level-1 token with `STEP_UP_REQUIRED`
   afterwards, and accept the elevated token for the same call; the fixture's preference is switched back off and the user is deleted (only names starting
   `verify-mfa-` are ever removed; `./migrate-mfa.sh fixture-clean` removes leftovers). The final line is `RESULT: PASS`, `RESULT: FAIL` (exit 1) or
   `RESULT: INCOMPLETE` (exit 3, assurance NOT_RUN, never a PASS). `apply` treats exit 3 as "applied, not proven" and prints a warning. The mobile checks stay
   `SKIP` until the beta client is registered (`VERIFY_MOBILE=1` after `docs/mobile/ios-testflight-beta.md` step "register the mobile client"). A first API call by the
   fixture provisions an application account for it; it is a synthetic, inactive-by-use customer record in the preview database (no orders).
7. Manual acceptance with synthetic accounts: a normal customer sign-in on the web app works; Account → Security → turn on two-step verification
   enrols an authenticator through Keycloak; with an administrator account holding `security.mfa_policy`, switching a category to REQUIRED is
   **not** done in this step (policies stay OPTIONAL).

## Idempotence and failure handling
* Re-running `apply` changes nothing when the tree is complete (it only re-sets the admin client secret to the value in `.env`).
* The new flow is bound only after it was built **and verified complete**; if `apply` stops half way the realm still uses the previous flow,
  users are unaffected, and the next run removes the incomplete attempt and builds again. If an incomplete flow is already bound the script refuses
  and asks for `rollback` first.

## Rollback
`./migrate-mfa.sh rollback` rebinds the previous browser flow (read from the state file, which is parsed as validated data and never sourced; the flow name is stored base64-encoded; an unreadable file is refused and you pass `PREVIOUS_FLOW='<name>'` explicitly) and deletes the `matbakhy` flows.
`PURGE=1 ./migrate-mfa.sh rollback` also removes the `matbakhy-admin` client, the `amr` mappers and the `acr.loa.map` attributes.
Users and credentials are untouched either way (an authenticator someone enrolled stays in Keycloak but is unused by the previous flow).
If the identity store itself ever has to be restored: stop `identity`, restore `keycloak-db-<stamp>.dump` from the state file into an empty `keycloak`
database with `pg_restore`, start `identity`. This loses sign-ins and enrolments made after the backup.

## Evidence of the live run (fill in; do not claim before it exists)
| Item | Value |
|---|---|
| Date / operator | |
| Release commit running | |
| `status` before / after | |
| `verify` output (PASS lines only, no secrets) | |
| Backup files (names only) | |
| Result | NOT RUN |

## Rehearsal evidence (local, not the live server)
Apply on a realm imported from the pre-D18 preview file, `status` → `flows=complete`, second `apply` → "already complete", normal sign-in of
`preview-customer-1` and `preview-ops-1` still issue a code, a step-up request leads to enrolment, `verify` all PASS, `PURGE=1 rollback`
restores `browserFlow=browser` and removes the admin client and mappers; also run through the `kcadm-in-container.sh` wrapper with a
test double for `docker compose exec`. After the review of `ff64f32`: the assurance verification was run against the same throwaway Keycloak 26.8.0 with a stand-in for the API's MFA contract (the real API needs PostgreSQL and could not be started without Docker), giving `RESULT: PASS`; with no fixture `RESULT: INCOMPLETE` (exit 3); with the `amr` mapper deleted `RESULT: FAIL` on the OTP step-up token. The real API acceptance is exercised only by the live run. The Docker-specific parts (`docker compose exec`, `pg_dump`, `deploy.sh backup`) are exercised only on the server.
