#!/bin/sh
# MB-039 / TC-057: logical PostgreSQL backup with a checksum and a manifest of row counts.
#   scripts/db-backup.sh <output-dir>
# Connection comes from the standard libpq variables (PGHOST, PGPORT, PGUSER, PGDATABASE; password via PGPASSWORD
# or ~/.pgpass). Backups hold personal data: the output directory must be outside any Git working tree, and a
# backup is not accepted until db-restore.sh has restored and reconciled it.
set -eu

out="${1:?usage: db-backup.sh <output-dir>}"
db="${PGDATABASE:?set PGDATABASE}"
mkdir -p "$out"
if command -v git >/dev/null 2>&1 && git -C "$out" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "refusing: $out is inside a Git working tree; backups contain personal data" >&2
  exit 2
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
base="$out/$db-$stamp"
started="$(date -u +%s)"
pg_dump --format=custom --no-owner --no-privileges --file="$base.dump" "$db"
( cd "$out" && sha256sum "$(basename "$base.dump")" > "$(basename "$base.dump").sha256" )

{
  echo "database=$db"
  echo "taken_at=$stamp"
  echo "schema_version=$(psql -X -At -v ON_ERROR_STOP=1 -c "SELECT max(version::int) FROM flyway_schema_history WHERE success" "$db")"
  for t in customer_order order_item stock_allocation order_event cash_obligation ledger_group ledger_entry meal_slot cook_slot; do
    echo "rows.$t=$(psql -X -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM $t" "$db")"
  done
} > "$base.manifest"

echo "backup $base.dump written in $(( $(date -u +%s) - started ))s"
