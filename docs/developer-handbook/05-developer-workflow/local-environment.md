# Local environment

> **Owner role:** Infrastructure and deployment
> **Status:** PARTIALLY IMPLEMENTED — the Docker-based local stack and Vite dev servers are IMPLEMENTED and exercised by CI on Linux; the Windows/macOS procedures and running the API outside Docker are NOT VERIFIED.

Install the tools first ([prerequisites.md](prerequisites.md)). Everything here uses synthetic data and local-only secrets.

## What `scripts/dev.sh` provides

`scripts/dev.sh` (bash) wraps `infra/compose.yaml`. Services, from the compose file:

| Service | Image (pinned by digest) | Host port (default, 127.0.0.1 only) | Purpose |
|---|---|---|---|
| `postgres` | `postgres:16.15-alpine` | 5432 | application database, Flyway migrations on API start |
| `identity` | `keycloak/keycloak:26.8.0` in `start-dev` mode | 8180 | local OIDC provider, realm imported from `infra/identity/matbakhy-realm.json` |
| `object-storage` | `adobe/s3mock:5.2.3` | 9090 | S3 emulator with buckets `matbakhy-private` and `matbakhy-public` |
| `mail` | `axllent/mailpit:v1.31.5` | 8025 (UI), 1025 (SMTP) | mail sink; nothing leaves the machine |
| `api` | built from `backend/Dockerfile` | 8080 | Spring Boot API, `APP_ENV=LOCAL`, profile `dev` |

Commands (all from the repository root):

| Command | Effect |
|---|---|
| `scripts/dev.sh doctor` | verify prerequisites; starts nothing |
| `scripts/dev.sh up` | `doctor`, create the env file if missing, build the API jar (`./mvnw -DskipTests package`), render the realm, `docker compose up --build --detach --wait` |
| `scripts/dev.sh smoke` | health, Flyway baseline, dev zones, OIDC discovery, S3 buckets, mail capture, PKCE enforcement, anonymous 401, web OIDC login with a synthetic user, signed private upload round trip |
| `scripts/dev.sh down` | stop containers, keep the data volume |
| `scripts/dev.sh reset` | stop and delete this stack's volume (refuses env files not marked local) |
| `scripts/dev.sh verify` | `doctor` + `up` + `smoke` in a throwaway compose project on alternate ports (15432, 18180, 18080 ...), removed on exit |
| `scripts/dev.sh e2e` | throwaway stack, both web apps, then Playwright browser workflows; see [test-execution.md](test-execution.md) |

First run:

```bash
scripts/dev.sh up
scripts/dev.sh smoke      # prints PASS only if every check passed
```

`up` creates the local env file (infra/.env, git-ignored) from `infra/.env.example` with random passwords (mode 600). The file is git-ignored (`.gitignore` ignores `.env` and `.env.*`) and must carry `MATBAKHY_ENV_MARKER=local`; the script refuses any other file. The rendered realm goes to infra/.generated/ (git-ignored). To keep several stacks apart set `MATBAKHY_ENV_FILE` and `COMPOSE_PROJECT_NAME`.

## Synthetic users

The local realm (`infra/identity/matbakhy-realm.json`) defines `customer-a`, `customer-b`, `cook-a` and `ops-a`, plus the service account `service-account-matbakhy-admin`. All users share one password, `DEV_USER_PASSWORD` in your local env file. Read it from your own file when you need it; do not paste it anywhere. Local MFA and SMS are not available; browser tests simulate phone verification with SQL (see `apps/operations-web/e2e/fixtures.ts`).

Development data comes from Flyway repeatable scripts in `backend/app/src/main/resources/db/devdata` (service zones named `test-zone-*`, synthetic commerce policy). `EnvironmentGuard` (`backend/app/src/main/java/com/matbakhy/app/config/EnvironmentGuard.java`) refuses the `dev` profile or devdata locations when `APP_ENV` is STAGING or PRODUCTION; LOCAL, CI and PREVIEW count as synthetic.

## Frontends

```bash
corepack enable
pnpm install --frozen-lockfile
VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/customer-web dev      # http://localhost:5173
VITE_API_BASE_URL=http://localhost:8080 pnpm --filter @matbakhy/operations-web dev    # http://localhost:5174
```

Both servers use `strictPort`: if 5173 or 5174 is taken they fail instead of moving, because the API allows exactly those origins (`WEB_ALLOWED_ORIGINS` default in `backend/app/src/main/resources/application.yaml`) and the compose file sets the post-login redirect to `http://localhost:5173/`. `VITE_DEMO_NOTICE` controls the demo-environment banner (see `apps/customer-web/src`). Sign-in uses the API's session cookie flow (`/oauth2/authorization/keycloak`), so open the apps at `localhost`, not `127.0.0.1` — the OIDC issuer is `http://localhost:8180/realms/matbakhy`.

## Mobile (customer app)

`apps/customer-mobile` is an Expo SDK 57 / React Native 0.86 app. Configuration comes from `EXPO_PUBLIC_*` variables (`EXPO_PUBLIC_API_URL`, `EXPO_PUBLIC_OIDC_ISSUER`, `EXPO_PUBLIC_ENVIRONMENT`, `EXPO_PUBLIC_OIDC_CLIENT_ID`, `EXPO_PUBLIC_NOTIFICATION_PROMPT`) with defaults in `apps/customer-mobile/app.json` and validation in `apps/customer-mobile/src/config.ts`.

```bash
pnpm --filter @matbakhy/customer-mobile start     # Expo dev server
(cd apps/customer-mobile && pnpm bundle:native)    # Android + iOS JS bundles, no native toolchain needed
```

Running on an emulator or simulator needs the native toolchains and a development build; the `android/` and `ios/` directories are generated by `expo prebuild` and are not committed. Expo Go, a JS bundle or a web preview never counts as native verification (project rule, see [toolchain.md](../../engineering/toolchain.md)). The scripted native flows live in `apps/customer-mobile/scripts/` and run in CI ([ci-cd.md](ci-cd.md)). On an emulator the app reaches the host stack through the emulator's loopback alias; the exact values CI uses are in `apps/customer-mobile/scripts/auth-roundtrip-android.sh`. A local interactive emulator session by hand: NOT VERIFIED.

## Running the API outside Docker

`./mvnw spring-boot:run` from `backend/app` is plausible (the Spring Boot plugin is configured in `backend/app/pom.xml`) but is NOT VERIFIED here. Requirements read from `application.yaml`: `APP_ENV` has no default and must be set (`LOCAL` for development); `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` default to `localhost:5432/matbakhy`; `OIDC_ISSUER` defaults to `http://localhost:8180/realms/matbakhy`; `OIDC_WEB_CLIENT_SECRET` and the realm must match what `scripts/dev.sh` rendered. The simplest supported way to iterate on the backend is `scripts/dev.sh up` and re-run it after changes (it rebuilds the jar and image).

## Database and Keycloak access (local)

```bash
# psql inside the container (variables come from your env file)
set -a; . infra/.env; set +a
docker compose --env-file infra/.env -f infra/compose.yaml exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
# migrations applied
docker compose --env-file infra/.env -f infra/compose.yaml exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"
```

Keycloak admin console: `http://localhost:8180`, user `KEYCLOAK_ADMIN_USER` (default `local-admin`), password `KEYCLOAK_ADMIN_PASSWORD` from your env file. Mail sink UI: `http://localhost:8025`. More in [run-and-debug.md](run-and-debug.md).

## Cleaning up

`scripts/dev.sh down` keeps data; `scripts/dev.sh reset` deletes the local volume. Both are safe for local stacks only: never point `MATBAKHY_ENV_FILE` at a non-local file.

## Known limitations and documentation gaps

- [toolchain.md](../../engineering/toolchain.md) says "only Linux has been exercised"; this is still true for every procedure on this page.
- No documented procedure for pointing a physical phone at a laptop-hosted stack.
