#!/bin/sh
# MB-039 / TC-057: restore a backup into a NEW, EMPTY database and reconcile it. A backup counts as good only after
# this passes (BR-057).
#   RESTORE_CONFIRM_TARGET=<target-db> scripts/db-restore.sh <backup.dump> <target-db> [reconcile.sql]
# Connection comes from the standard libpq variables; the target is created when it does not exist. Never restores
# over a database that already has tables. Exit codes: 0 reconciled, 1 reconciliation failed, 2 refused.
set -eu

dump="${1:?usage: db-restore.sh <backup.dump> <target-db> [reconcile.sql]}"
target="${2:?usage: db-restore.sh <backup.dump> <target-db> [reconcile.sql]}"
here="$(cd "$(dirname "$0")" && pwd)"
reconcile="${3:-$here/../infra/db/reconcile.sql}"
manifest="${dump%.dump}.manifest"

case "$target" in
  *[!A-Za-z0-9_]*|'') echo "refusing: target name must be [A-Za-z0-9_]" >&2; exit 2 ;;
esac
if [ "${RESTORE_CONFIRM_TARGET:-}" != "$target" ]; then
  echo "refusing: set RESTORE_CONFIRM_TARGET=$target to confirm the restore target" >&2
  exit 2
fi
( cd "$(dirname "$dump")" && sha256sum -c "$(basename "$dump").sha256" >/dev/null ) || { echo "refusing: checksum mismatch for $dump" >&2; exit 2; }

started="$(date -u +%s)"
exists="$(psql -X -At -v ON_ERROR_STOP=1 -d postgres -c "SELECT count(*) FROM pg_database WHERE datname = '$target'")"
if [ "$exists" = "0" ]; then
  createdb "$target"
else
  tables="$(psql -X -At -v ON_ERROR_STOP=1 -d "$target" -c "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')")"
  if [ "$tables" != "0" ]; then
    echo "refusing: $target already has $tables tables; restore only into a new, empty database" >&2
    exit 2
  fi
fi
pg_restore --exit-on-error --no-owner --no-privileges --dbname="$target" "$dump"
restored="$(date -u +%s)"

status=0
if [ -f "$manifest" ]; then
  for line in $(grep '^rows\.' "$manifest"); do
    t="${line#rows.}"; t="${t%%=*}"; want="${line#*=}"
    got="$(psql -X -At -v ON_ERROR_STOP=1 -d "$target" -c "SELECT count(*) FROM $t")"
    if [ "$got" != "$want" ]; then
      echo "MISMATCH rows.$t: manifest $want, restored $got"
      status=1
    fi
  done
else
  echo "WARNING: no manifest next to $dump; row counts not compared"
fi

psql -X -At -F ' ' -v ON_ERROR_STOP=1 -d "$target" -f "$reconcile" > "/tmp/reconcile-$target.out"
while read -r name violations; do
  if [ "$violations" = "0" ]; then echo "OK $name"; else echo "FAIL $name: $violations"; status=1; fi
done < "/tmp/reconcile-$target.out"
rm -f "/tmp/reconcile-$target.out"

echo "restore_seconds=$(( restored - started )) total_seconds=$(( $(date -u +%s) - started ))"
if [ "$status" = 0 ]; then echo "RECONCILED $target"; else echo "NOT RECONCILED $target"; fi
exit "$status"
