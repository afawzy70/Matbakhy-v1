#!/bin/sh
# Staging-only test setup channel for the browser E2E suite (owner decision 2026-10-14).
#
# The test runner never touches the database and never sends SQL. It sends the NAME of a checked-in fixture and its
# parameters on stdin; this script maps the name to e2e-fixtures/<name>.sql, validates the parameters and runs the file
# through psql with the parameters as psql variables (:'name' in the SQL, quoted by psql, never spliced into the text).
#
#   request (stdin):   line 1: fixture name        following lines: key=value
#   response (stdout): whatever the fixture selects (psql -At); non-zero exit on any refusal or SQL error
#
# Where it runs: ON THE SERVER, as the only command of an SSH key that is restricted to it, or locally against a
# throwaway stack. It is not a network service and nothing here is reachable from the Internet.
#   authorized_keys:   command="/srv/matbakhy-preview/e2e-fixture.sh",restrict ssh-ed25519 AAAA... e2e-runner
#
# Guards:
#   - refuses unless the marker file exists (E2E_ENABLED_MARKER, default .e2e-enabled next to this script). The owner
#     creates it deliberately for an E2E window on a PREVIEW/test database and removes it afterwards;
#   - only fixture names that match [a-z][a-z0-9_]* and exist in the fixture directory;
#   - only the parameters a fixture declares on its first line ("-- params: a b"), each exactly once, values of at most
#     512 characters without control characters;
#   - read-only fixtures are plain SELECTs; changing fixtures only write the synthetic rows the suite needs.
# The database reset that cleans up after a run is ./deploy.sh e2e-reset (also marker-guarded, backs up first).
#
# psql is reached through E2E_PSQL_PREFIX (space-separated words, no spaces inside a word). Default: the preview stack.
set -eu
set -f
HERE=$(cd "$(dirname "$0")" && pwd)
FIXTURES=${E2E_FIXTURE_DIR:-$HERE/e2e-fixtures}
MARKER=${E2E_ENABLED_MARKER:-$HERE/.e2e-enabled}
die() { echo "e2e-fixture: $*" >&2; exit "${2:-2}"; }

[ -f "$MARKER" ] || die "e2e fixtures are not enabled on this host (no marker file)" 3

IFS= read -r name || die "no fixture name on stdin"
case "$name" in
  ''|[!a-z]*|*[!a-z0-9_]*) die "invalid fixture name" ;;
esac
file="$FIXTURES/$name.sql"
[ -f "$file" ] || die "unknown fixture: $name"
header=$(head -n 1 "$file")
case "$header" in
  "-- params:"*) declared=${header#-- params:} ;;
  *) die "fixture $name has no params header" ;;
esac

provided=" "
args=""
nl='
'
while IFS= read -r line || [ -n "$line" ]; do
  [ -n "$line" ] || continue
  case "$line" in *=*) ;; *) die "bad parameter line" ;; esac
  key=${line%%=*}
  value=${line#*=}
  case "$key" in ''|[!a-z]*|*[!a-z0-9_]*) die "invalid parameter name" ;; esac
  case " $declared " in *" $key "*) ;; *) die "fixture $name does not take parameter $key" ;; esac
  case "$provided" in *" $key "*) die "parameter $key given twice" ;; esac
  [ "${#value}" -le 512 ] || die "parameter $key is too long"
  # No control characters (tab and everything below space, DEL, carriage return).
  case "$value" in *[[:cntrl:]]*) die "parameter $key has a control character" ;; esac
  provided="$provided$key "
  args="$args$nl$key=$value"
done
for key in $declared; do
  case "$provided" in *" $key "*) ;; *) die "fixture $name needs parameter $key" ;; esac
done

if [ -n "${E2E_PSQL_PREFIX:-}" ]; then
  # shellcheck disable=SC2086
  set -- $E2E_PSQL_PREFIX
else
  set -- docker compose --env-file "$HERE/.env" -f "$HERE/compose.yaml" exec -T postgres psql -U matbakhy -d matbakhy
fi
set -- "$@" -X -q -At -1 -v ON_ERROR_STOP=1
oldifs=$IFS
IFS=$nl
for pair in $args; do
  [ -n "$pair" ] || continue
  set -- "$@" -v "$pair"
done
IFS=$oldifs
exec "$@" -f - < "$file"
