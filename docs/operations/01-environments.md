# Development, staging and production

## Environment contract

| Environment | Data/providers | Purpose |
|---|---|---|
| local | Synthetic seed, PostgreSQL, local IdP, private/public storage emulator, mail sink/fake SMS/push | Reproducible development and integration testing |
| CI | Isolated short-lived synthetic database/storage and test adapters | Deterministic build/test evidence; no real notifications |
| staging | Synthetic/approved test users, provider sandboxes, separate keys/domains/app IDs | End-to-end integration, operator rehearsal, native beta |
| production | Approved live providers/policies, real users, restricted access | Owner-authorized controlled pilot |

P01 creates tested Bash and PowerShell bootstrap/build/test scripts and documents prerequisites, ports, commands, reset scope and troubleshooting. Expected developer operations: start dependencies, migrate, seed synthetic data, run API/web/mobile dev mode, execute tests, stop. Do not publish commands as working until tested. Reset scripts refuse production and require an explicit local environment marker.

Configuration includes APP_ENV, allowed origins, API/public base URLs, DATABASE_URL credentials reference, OIDC issuer/client registrations, storage bucket/region, provider adapter modes/secret references, TIMEZONE=Africa/Cairo, CURRENCY=EGP, feature flags and approved policy versions. Keep secrets out of client bundles/config APIs/logs. `.env.example` contains placeholders. Validate configuration at startup; missing live provider or approval is a clear blocking error.

## Hosting baseline

Choose owner-approved hosting/budget/domain in O07. Default design can run as containers with a reverse proxy, backend/worker process, managed or properly backed-up PostgreSQL and object storage. No default Kubernetes requirement. Database and IdP must not be public without appropriate access controls. TLS, restricted admin access, secrets management, persistent storage, backup schedule and alerts are required for live use.

Deploy immutable versioned artifacts. Pipeline: install from locks → lint/typecheck/build → domain/integration/contract → E2E/security → publish artifact → stage migration/deploy → smoke/UAT → approved production promotion. Never deploy PR code with production secrets before trusted review. Restrict deployment concurrency to one per environment.

## Database release and rollback

Validate migration against empty and previous-release schemas. Take/check a recoverable backup; apply forward-compatible migration, deploy, smoke and reconcile. Application rollback is safe only if schema remains compatible. Otherwise execute tested forward repair or restore procedure with explicit data-loss window review; do not blindly apply down migrations. Maintain outbox/finance idempotency through restore/replay.

## Monitoring

Structured JSON logs: timestamp, level, component, request/trace ID, safe order/event ID, duration, error code. Metrics: request latency/error rate, order submission success, stock conflict, acceptance expiry, outbox age/dead-letter, DB pool/lock latency, failed auth/OTP limit, cash variance, unmatched finance events, backup age and deployment version. Avoid high-cardinality PII labels.

Proposed initial alerts: any ledger imbalance/oversell → critical; sustained valid-order failure >1% over 10 minutes → investigate; oldest outbox pending >5 minutes → warning; repeated failed jobs/dead letters → ops; no successful backup in 24 hours → incident. Tune noise with actual pilot data; do not weaken invariant alerts. Route alerts only to approved operator channels.

## Pre-preview audit and log readiness

Owner direction (2026-10-12): complete the [audit and log readiness checklist](05-observability-readiness.md) under MB-039, with deployed verification under MB-040, before external tester access to an early web preview. Continue current mobile recovery and independent QA; prepare the checklist at the next eligible observability checkpoint. An early preview does not close MB-040 or waive its existing dependencies.
