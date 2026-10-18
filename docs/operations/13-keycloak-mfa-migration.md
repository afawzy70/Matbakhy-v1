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
2. On the server, in `deploy/web-preview`: `./deploy.sh render` — adds `OIDC_ADMIN_CLIENT_SECRET` to `.env` if missing (mode 600 kept) and
   re-renders the (ignored-on-existing-realm) realm file; then `docker compose up -d api` (or the next deploy) so the API receives it.
3. `./migrate-mfa.sh status` — read-only; expect `flows=absent`, `browserFlow=browser`.
4. `./migrate-mfa.sh apply` — runs, in this order: verified database backup (`deploy.sh backup`), a Keycloak database dump and a realm
   export (no users) under `$BACKUP_DIR/keycloak/` (mode 600) plus a state file with the previous browser flow, the migration, and
   `verify`. It takes 3–5 minutes (kcadm starts a JVM per call).
5. `./migrate-mfa.sh status` again — expect `flows=complete`, `browserFlow=matbakhy browser`, `client.matbakhy-admin=present`.
6. `./migrate-mfa.sh verify` can be repeated at any time. It must print only `PASS` (and `SKIP mobile checks` until the beta client is registered; set
   `VERIFY_MOBILE=1` after `docs/mobile/ios-testflight-beta.md` step "register the mobile client").
7. Manual acceptance with synthetic accounts: a normal customer sign-in on the web app works; Account → Security → turn on two-step verification
   enrols an authenticator through Keycloak; with an administrator account holding `security.mfa_policy`, switching a category to REQUIRED is
   **not** done in this step (policies stay OPTIONAL).

## Idempotence and failure handling
* Re-running `apply` changes nothing when the tree is complete (it only re-sets the admin client secret to the value in `.env`).
* The new flow is bound only after it was built **and verified complete**; if `apply` stops half way the realm still uses the previous flow,
  users are unaffected, and the next run removes the incomplete attempt and builds again. If an incomplete flow is already bound the script refuses
  and asks for `rollback` first.

## Rollback
`./migrate-mfa.sh rollback` rebinds the previous browser flow (from the state file; `browser` by default) and deletes the `matbakhy` flows.
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
test double for `docker compose exec`. The Docker-specific parts (`docker compose exec`, `pg_dump`, `deploy.sh backup`) are exercised only on the server.
