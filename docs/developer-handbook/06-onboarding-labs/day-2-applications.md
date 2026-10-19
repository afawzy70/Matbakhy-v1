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
4. Customer journey at `http://localhost:5173`: read the local password for the synthetic user `customer-a` from your own local env file (`DEV_USER_PASSWORD`; never copy it elsewhere), sign in, browse meals, add to basket, request a quote and watch the totals. Try switching Arabic and English and confirm direction flips (RTL/LTR).
5. Cook and back-office journeys at `http://localhost:5174` with `cook-a` and `ops-a`: find the cook dashboard and the order list. Note which menu items appear for each role and what happens when you open a staff-only URL as a cook.
6. Compare what you did with the automated version: read `apps/operations-web/e2e/customer-journey.e2e.ts` and `apps/operations-web/e2e/cook-workspace.e2e.ts`. List the steps they automate.
7. Mailpit at `http://localhost:8025`: trigger something that sends mail, if any, and find it. Note that nothing leaves your machine.
8. Mobile: open `apps/customer-mobile/src/App.tsx` and `apps/customer-mobile/src/config.ts`; list the screens under `apps/customer-mobile/src/screens`; read `apps/customer-mobile/scripts/order-roundtrip-android.sh` to see which native behaviours CI proves. Run `pnpm --filter @matbakhy/customer-mobile test`. If you have no emulator, state that native verification was NOT RUN by you.
9. Shut down: `scripts/dev.sh down` (keeps data) or `scripts/dev.sh reset` (wipes the local volume).

## Deliverable

A short written walkthrough (screenshots only of synthetic data, no passwords) listing, per surface, the three most important screens, the API calls each makes (from the browser Network tab) and which role may see it.

## Self-check

- [ ] `scripts/dev.sh smoke` printed PASS on my machine, or I can state exactly which step failed and why.
- [ ] I can explain why the web apps must run on ports 5173 and 5174.
- [ ] I know the difference between the customer web, the operations web (cook workspace plus back office) and the mobile app, and that there are no separate cook or driver native apps in the MVP.
- [ ] I can say why a JS bundle or Expo Go does not count as native verification.
- [ ] I can state which prices and totals are computed by the server.

Next: [Day 3](day-3-backend-security.md).
