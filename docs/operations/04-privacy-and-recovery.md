# Privacy, logging and recovery (MB-039)

Status on 2026-10-12: implemented and tested locally on PostgreSQL. The retention period is a **proposed default pending O06** (owner's professional privacy/retention review). The restore rehearsal ran on a local throwaway database, not on staging; the staging rehearsal is blocked on O07 (no staging environment exists).

## Account deletion (BR-055, TC-055)

- A customer confirms deletion in the app. Access ends at once: the account can no longer use the API, and a later sign-in with the same provider identity creates a new, empty account.
- After a grace period (`matbakhy.privacy.deletion-grace`, proposed default 14 days, pending O06) the erasure job (`AccountErasureJob`, every 15 minutes) runs each due request in its own transaction:
  - Deferred while the customer has an active order (retried later). Refused for an account that owns a kitchen (cook accounts need staff review).
  - Erased: name, phone, provider subject, saved addresses, phone challenges, web sessions, favorites, push devices (pending pushes skipped), support case text, review text, and the contact, address and note snapshots on the customer's finished orders.
  - Kept: order amounts, items, states and events, cash obligations, ledger entries and settlements. They hold no personal data after erasure, and finance must stay balanced and auditable.
  - Audited as `account.erasure` with per-area counts only.
- The database enforces this. A finished order can change only by receiving the erasure markers; its other columns stay immutable.

## Logs (BR-056, TC-056)

- Every log line carries the request ID. In plain-text logs it appears in brackets after the level; in structured logs it is a JSON field. Deployed environments set `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`.
- Order events in the outbox store the request ID that caused them. Dispatch and provider failures are logged under that same request ID, so one ID connects the request, the order and a later delivery failure.
- Logs contain IDs, states and error codes only. They never contain:
  - names, emails, phone numbers or addresses
  - OTP or handover codes
  - push tokens or bearer tokens
- Unexpected errors and background-job errors are logged with exception types and stack frames, without messages, because database messages can quote values.
- Provider error text is stored and logged only if it is a plain code. Anything else becomes `PROVIDER_ERROR`.
- `LogRedactionIntegrationTest` runs OTP, address, push device, order, handover, provider outage and unexpected-error flows with distinctive synthetic data. It scans the captured JSON logs for that data and checks the request-ID trace.

## Backup and restore (BR-057, TC-057)

| Step | Command |
|---|---|
| Backup | `PGHOST=… PGUSER=… PGDATABASE=… scripts/db-backup.sh <dir outside any Git checkout>` |
| Restore and reconcile | `RESTORE_CONFIRM_TARGET=<new_db> scripts/db-restore.sh <file.dump> <new_db>` |
| Reconcile only | `psql -X -At -F ' ' -f infra/db/reconcile.sql <db>` (every count must be 0) |

**Backup output.** `db-backup.sh` writes three files:
- a custom-format dump
- its SHA-256 checksum
- a manifest with the schema version and row counts

**Restore rules.** `db-restore.sh` refuses to run in these cases:
- the target is not explicitly confirmed
- the checksum does not match
- the target database already has tables

It then compares row counts with the manifest and runs `infra/db/reconcile.sql`.

**What `reconcile.sql` checks:**
- stock counters against allocations, per meal slot and per cook slot
- no live stock held by cancelled or rejected orders
- each order's item lines against its subtotal
- each order's state against its latest event
- exactly one cash obligation per order, for the total
- every ledger group balanced and posted only for completed orders
- every migration successful

**Acceptance.** A backup is accepted only when the restore exits 0 (`RECONCILED`).

**Local rehearsal.** `BackupRestoreIntegrationTest` covers:
- orders in pending, completed (with posted commission) and rejected states
- backup, restore into a new database, reconciliation
- the application starting on the restored database without re-applying migrations, and serving the orders
- a write made after the backup being absent, which is the RPO window
- damaged stock and ledger data being detected
- the refusals above

Measured on the CI-sized dataset: backup about 0.4 s; restore, reconciliation and application start about 2.3 s. These numbers do not predict production RTO.

**RPO.** With logical dumps alone, RPO equals the backup interval. Managed point-in-time recovery would shorten it; that is an O07 hosting choice.

**Still required before live use (O07):**
- scheduled backups on the chosen host, with encrypted storage and restricted access
- a staging restore drill measured on realistic data volume
- a backup-age alert ("no successful backup in 24 hours")
- recorded RPO and RTO targets agreed by the owner
