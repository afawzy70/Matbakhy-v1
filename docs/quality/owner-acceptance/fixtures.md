# Synthetic demo accounts and test data (no credentials in the repository)

Actors are listed in `fixtures.json`; the cases reference them as `FX-*`. Seven already exist on the preview seed (`deploy/web-preview/seed-preview.sql`). The rest are provisioned by the steps below. **Nothing here has been applied**: provisioning changes a Keycloak realm and a database, so it needs the owner's go on the shared preview, and can be done freely on an isolated stack.

| Already seeded | Extra, to provision |
|---|---|
| FX-CUST-A/B (`preview-customer-1/2`), FX-COOK-A (`preview-cook-1`), FX-STAFF-ADMIN (`preview-ops-1`, only `staff.grant` + `audit.read`), FX-STAFF-NOGRANT (`preview-ops-2`) | FX-COOK-B (makes the seeded second kitchen loginable), FX-COOK-PENDING, FX-STAFF-SUPPORT / -OPS / -FINANCE / -ADMIN-B, FX-MULTIROLE, 4 × FX-CUST-NEW (sign-up is disabled on the preview, so these are pre-created Keycloak users with an unverified phone) |

## Steps (operator)
1. `python3 scripts/owner_e2e_fixtures.py check` — manifest, SQL seed and case files agree.
2. `python3 scripts/owner_e2e_fixtures.py render --out ~/matbakhy-private/owner-e2e-<run>` — writes `credentials.private.json` and `partial-import.json` (random password per user, directory 700, files 600). It refuses a path inside the repository and never prints a password.
3. Create the Keycloak users with a **partial import** (it keeps the fixed user ids that the SQL seed refers to; a plain `create users` call would assign new ids and the grants would not match). On the server, in `deploy/web-preview`:
   ```
   set -a; . ./.env; set +a; export ADMIN_USER="$KEYCLOAK_ADMIN_USER" ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD"
   ./kcadm-in-container.sh config credentials --config /tmp/kcadm-e2e.cfg
   ./kcadm-in-container.sh create partialImport -r matbakhy -f - --config /tmp/kcadm-e2e.cfg < ~/matbakhy-private/owner-e2e-<run>/partial-import.json
   ./kcadm-in-container.sh cleanup /tmp/kcadm-e2e.cfg
   ```
   Users that already exist are skipped (their password is not changed).
4. Apply the application rows and grants: `deploy/web-preview/seed-owner-e2e.sql` with the same `psql ... -v issuer=https://id.<PREVIEW_DOMAIN>/realms/matbakhy` call that `./deploy.sh seed` uses. It contains no secrets, does not change a row twice and uses only permission codes that exist on the deployed baseline. After the release with migration V31 is deployed, also apply `deploy/web-preview/seed-owner-e2e-mfa.sql` (grants `security.mfa_policy` and `security.mfa_reset` to FX-STAFF-ADMIN-B); a pre-V31 database rejects those codes.
5. Share the passwords over a private channel (a password manager); delete the private directory after the run. Never paste them into GitHub, tickets, screenshots or logs.
6. Slots: seeded slots exist for three days after seeding. Run `deploy/web-preview/refresh-slots.sql` (additive, repeatable) before a run; do **not** re-run `./deploy.sh seed` (it is not repeatable, and after step 4 its first insert collides).

## Single-use fixtures
On the shared preview the provisioning is one-shot: a consumed fixture (FX-COOK-PENDING once approved, an FX-CUST-NEW once it has signed in) is not reset by re-rendering. Rotating a password needs the Keycloak account console or `kcadm set-password`. Cases that consume fixtures, and all destructive cases, belong on a fresh isolated stack, which is recreated per run. To run on the shared preview again, disable the old users and create new run-specific ones (a new `--out` plus matching rows), or accept that those cases are NOT_RUN.

## Not provided, by design
- TOTP seeds: testers enrol their own authenticator through Keycloak during the MFA cases; nothing stores an OTP secret.
- FX-ISOLATED: a disposable Docker Compose namespace made by the operator; destructive, race and fault cases run only there.
- FX-DEVICE-IOS / -ANDROID: signed physical builds (PR #11 for iOS needs the owner's Apple credentials; there is no Android distribution pipeline yet).
- Seeded meals have no photo, so price-change cases need a meal the cook publishes with a photo.

## Not run
`seed-owner-e2e.sql`, `seed-owner-e2e-mfa.sql`, `refresh-slots.sql` and the render script were checked for consistency with each other and with the migrations' column names; the SQL has **not** been executed against PostgreSQL (NOT_RUN) — run it on the isolated stack first.
