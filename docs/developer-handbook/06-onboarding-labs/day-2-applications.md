# Day 2 - Applications and user journeys

> **Owner role:** Frontend and mobile
> **Status:** REFERENCE — lab for running the applications and walking each user journey.

## Objectives

- Run the full local stack and both web apps.
- Walk the customer journey and the cook and back-office journeys with synthetic users.
- Know what the mobile app is, where its code lives and how it is verified natively.

## Reading list

1. [Customer web](../02-user-journeys/customer-web.md), [customer mobile](../02-user-journeys/customer-mobile.md), [cook operations](../02-user-journeys/cook-operations.md), [back-office admin](../02-user-journeys/backoffice-admin.md), [finance and support](../02-user-journeys/finance-and-support.md), [authentication and MFA](../02-user-journeys/authentication-and-mfa.md).
2. [Frontend architecture](../03-architecture/frontend-architecture.md), [mobile architecture](../03-architecture/mobile-architecture.md), [frontend code guide](../04-codebase/frontend-code-guide.md), [mobile code guide](../04-codebase/mobile-code-guide.md).
3. [Prerequisites](../05-developer-workflow/prerequisites.md) and [local environment](../05-developer-workflow/local-environment.md).

## Hands-on tasks

1. Verify tools: `scripts/dev.sh doctor`. Fix anything MISSING before continuing.
2. Start the stack and prove it works:
   ```bash
   scripts/dev.sh up
   scripts/dev.sh smoke      # must end with: PASS: local stack smoke checks
   ```
   If it fails, use [troubleshooting](../05-developer-workflow/troubleshooting.md) and keep notes of what you hit.
3. Start the web apps (two terminals):
   ```bash
   pnpm install --frozen-lockfile
   VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/customer-web dev
   VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/operations-web dev
   ```
4. Create the first logins (this is what makes the synthetic users exist in the database). Read the local password from your own generated, git-ignored env file in the infra directory (variable `DEV_USER_PASSWORD`; never copy it elsewhere). Sign in once at `http://localhost:5173` as `customer-a` and once at `http://localhost:5174` as `cook-a` and as `ops-a` (use separate browser profiles or private windows). Expected: each sign-in lands on the app; `cook-a` sees "no kitchen" and "no application". The `app_user` rows are created at first sign-in, which the fixture steps below need.
5. Local fixture state. A freshly started stack has zones and a commerce policy (`backend/app/src/main/resources/db/devdata/R__dev_service_zones.sql`, `R__dev_commerce_policy.sql`) but no approved kitchen, no meal, no slot, no verified phone and no staff grants. The browser E2E suite builds that state through checked-in, guarded fixtures (`deploy/web-preview/e2e-fixture.sh` runs `deploy/web-preview/e2e-fixtures/<name>.sql`; it refuses to run unless a marker file exists). You can run the same fixtures by hand against your own LOCAL stack only (never a shared one). From the repository root:
   ```bash
   set -a; . infra/.env; set +a
   export E2E_ENABLED_MARKER="${TMPDIR:-/tmp}/matbakhy-local-e2e-enabled"   # the guard marker outside the repository; create it deliberately, delete it afterwards
   export E2E_PSQL_PREFIX="docker compose --env-file infra/.env -f infra/compose.yaml exec -T postgres psql -U $POSTGRES_USER -d $POSTGRES_DB"
   touch "$E2E_ENABLED_MARKER"
   fx() { printf '%s\n' "$@" | sh deploy/web-preview/e2e-fixture.sh; }
   fx cook_approved_with_kitchen cook_subject=00000000-0000-4000-8000-0000000000c1
   fx customer_phone_verified subject=00000000-0000-4000-8000-0000000000a1 phone=+201001112223
   fx staff_review_grants subject=00000000-0000-4000-8000-0000000000d2
   ```
   The subjects are the fixed synthetic ones of `apps/operations-web/e2e/fixtures.ts` (`LOCAL_ACCOUNTS`) and the realm in `infra/identity/matbakhy-realm.json`. Expected: no error; psql prints nothing for these inserts. `cook_approved_with_kitchen` uses fixed ids and is meant for an empty database, so run it once (a second run fails on duplicate keys; `scripts/dev.sh reset` gives a clean start). Reload the cook workspace: you should now see the kitchen "مطبخ تجريبي". Remove the marker when done: `rm "$E2E_ENABLED_MARKER"`. NOT VERIFIED: these exact commands were written from the scripts and not executed when this page was written; if one fails, record the error and the file it points to, and fix this page (policy in [documentation maintenance](../05-developer-workflow/documentation-maintenance.md)).
6. Create the catalogue through the UI, as the E2E suite does (`apps/operations-web/e2e/cook-workspace.e2e.ts`, steps "meal with an uploaded photo" and "slot tomorrow"): as `cook-a` open the Meals tab, fill the Arabic title, price and portion fields, attach a photo of at least 640 x 480 pixels (any synthetic image), create, then publish; open the Slots tab, create a slot for tomorrow with a lead time that allows it, give the meal a capacity, and open the slot. Expected: the meal card shows PUBLISHED and the slot card shows OPEN. NOT VERIFIED manually; the field labels live in the app's i18n files.
7. Customer journey at `http://localhost:5173` as `customer-a`: browse the published meal, add to basket, request a quote and watch the totals (a quote needs a verified contact, which step 5 provided; a delivery quote also needs a saved address in a seeded zone). Switch Arabic and English and confirm the direction flips (RTL/LTR).
8. Cook and back-office journeys at `http://localhost:5174`: as `cook-a` find the cook dashboard and order list; as `ops-a` (now holding `content.moderate`, `cook.review`, `support.case`, `order.ops`, `order.exception_cancel`) open the staff screens. Note which menu items appear for each role and what happens when `cook-a` opens a staff-only URL. Staff actions that need recent MFA (for example approving a cook) require the realm's second-factor step (`infra/identity/matbakhy-realm.json` defines the custom browser flow with an OTP form, and `SecurityConfiguration` requests it with `max_age=0` for web step-up; see [authentication and MFA](../02-user-journeys/authentication-and-mfa.md)). Enrolling and stepping up interactively in the local realm is NOT VERIFIED here; step 5 approves the kitchen by fixture only to make the setup repeatable without interactive enrolment.
9. Compare what you did with the automated version: read `apps/operations-web/e2e/customer-journey.e2e.ts` and `apps/operations-web/e2e/cook-workspace.e2e.ts`. List the steps they automate.
10. Mailpit at `http://localhost:8025`: trigger something that sends mail, if any, and find it. Note that nothing leaves your machine.
11. Mobile: open `apps/customer-mobile/src/App.tsx` and `apps/customer-mobile/src/config.ts`; list the screens under `apps/customer-mobile/src/screens`; read `apps/customer-mobile/scripts/order-roundtrip-android.sh` to see which native behaviours CI proves. Run `pnpm --filter @matbakhy/customer-mobile test`. If you have no emulator, state that native verification was NOT RUN by you.
12. Shut down: `scripts/dev.sh down` (keeps data) or `scripts/dev.sh reset` (wipes the local volume).

## Deliverable

A short written walkthrough (screenshots only of synthetic data, no passwords) listing, per surface, the three most important screens, the API calls each makes (from the browser Network tab) and which role may see it.

## Self-check

- [ ] `scripts/dev.sh smoke` printed PASS on my machine, or I can state exactly which step failed and why.
- [ ] I can explain why the web apps must run on ports 5173 and 5174.
- [ ] I know the difference between the customer web, the operations web (cook workspace plus back office) and the mobile app, and that there are no separate cook or driver native apps in the MVP.
- [ ] I can say why a JS bundle or Expo Go does not count as native verification.
- [ ] I can state which prices and totals are computed by the server.

Next: [Day 3](day-3-backend-security.md).
