# Synthetic demo accounts and test data (no credentials in the repository)

Actors are listed in `fixtures.json`; the cases reference them as `FX-*`. Seven already exist on the preview seed (`deploy/web-preview/seed-preview.sql`). The rest are provisioned by the steps below. **Nothing here has been applied**: provisioning changes a Keycloak realm and a database, so it needs the owner's go on the shared preview, and can be done freely on an isolated stack.

| Already seeded | Extra, to provision |
|---|---|
| FX-CUST-A/B (`preview-customer-1/2`), FX-COOK-A (`preview-cook-1`), FX-STAFF-ADMIN (`preview-ops-1`, only `staff.grant` + `audit.read`), FX-STAFF-NOGRANT (`preview-ops-2`) | FX-COOK-B (makes the seeded second kitchen loginable), FX-COOK-PENDING, FX-STAFF-SUPPORT / -OPS / -FINANCE / -ADMIN-B, FX-MULTIROLE, 4 × FX-CUST-NEW (sign-up is disabled on the preview, so these are pre-created Keycloak users with an unverified phone) |

## Steps (operator)
1. `python3 scripts/owner_e2e_fixtures.py check` — manifest, SQL seed and case files agree.
2. `python3 scripts/owner_e2e_fixtures.py render --out ~/matbakhy-private/owner-e2e-<run>` — writes `credentials.private.json` and `keycloak-users.json` (random password per user, directory 700, files 600). The command refuses a path inside the repository and never prints a password.
3. Create the Keycloak users from `keycloak-users.json` through the Keycloak container (`./kcadm-in-container.sh create users -r matbakhy -f - < one-user.json`, one JSON object per call; admin console stays unpublished).
4. Apply the application rows and grants: `deploy/web-preview/seed-owner-e2e.sql` with the same `psql ... -v issuer=https://id.<PREVIEW_DOMAIN>/realms/matbakhy` call that `./deploy.sh seed` uses. It is idempotent and contains no secrets.
5. Share the passwords with the testers over a private channel (a password manager); delete the private directory after the run. Never paste them into GitHub, tickets, screenshots or logs.
6. After the run, remove or disable the `owner-e2e-*` users (a synthetic account list stays in `fixtures.json`). Consumed fixtures (FX-COOK-PENDING after approval, FX-CUST-NEW after sign-in) are re-created by re-running steps 2–4 with a new `--out`.

## Not provided, by design
- TOTP seeds: testers enrol their own authenticator through Keycloak during the MFA cases; nothing stores an OTP secret.
- FX-ISOLATED: a disposable Docker Compose namespace made by the operator; destructive, race and fault cases run only there.
- FX-DEVICE-IOS / -ANDROID: signed physical builds (PR #11 for iOS needs the owner's Apple credentials; there is no Android distribution pipeline yet).
- Seeded meals have no photo, so price-change cases need a meal the cook publishes with a photo; seed slots expire three days after seeding (`./deploy.sh seed` again to refresh).

## Not run
`seed-owner-e2e.sql` and the render script were checked for consistency with each other and with the migrations' column names; the SQL has **not** been executed against PostgreSQL (NOT_RUN) — run it on the isolated stack first.
