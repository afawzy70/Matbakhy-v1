#!/bin/sh
# kcadm.sh inside the preview's Keycloak container, for deploy/web-preview/migrate-mfa.sh (KCADM_REMOTE=1).
#   kcadm-in-container.sh config credentials --config F   signs in as the operations client (OPS_CLIENT_SECRET) when it works, else with ADMIN_USER / ADMIN_PASSWORD
#   kcadm-in-container.sh config credentials-client --config F   operations client only (fails otherwise)
#   kcadm-in-container.sh cleanup F                        removes the kcadm config file
#   kcadm-in-container.sh <anything else>                  passed to kcadm.sh unchanged (standard input is forwarded)
# The admin password is read inside the container from the forwarded environment; it is never an argument on the host.
set -eu
cd "$(dirname "$0")"
ENV_FILE=${ENV_FILE:-.env}
exec_in() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} exec -T "$@"; }
KCADM_BIN=/opt/keycloak/bin/kcadm.sh
# .env calls the operations client secret KEYCLOAK_OPS_CLIENT_SECRET; it is forwarded into the container only as an environment variable.
OPS_CLIENT_SECRET="${OPS_CLIENT_SECRET:-${KEYCLOAK_OPS_CLIENT_SECRET:-}}"
export OPS_CLIENT_SECRET
ops_login() { # config file: the master operations client (service account); the secret is read inside the container from the environment
  exec_in -e OPS_CLIENT_SECRET identity sh -c 'exec "$0" config credentials --config "$1" --server http://localhost:8080 --realm master --client matbakhy-ops-cli --secret "$OPS_CLIENT_SECRET"' "$KCADM_BIN" "$1" </dev/null
}
if [ "${1:-}" = config ] && [ "${2:-}" = credentials-client ]; then
  shift 2
  [ "${1:-}" = --config ] || { echo "kcadm-in-container: --config is required" >&2; exit 2; }
  [ -n "${OPS_CLIENT_SECRET:-}" ] || { echo "kcadm-in-container: OPS_CLIENT_SECRET is not set" >&2; exit 2; }
  ops_login "$2"
elif [ "${1:-}" = config ] && [ "${2:-}" = credentials ]; then
  shift 2
  [ "${1:-}" = --config ] || { echo "kcadm-in-container: --config is required" >&2; exit 2; }
  # The operations client first (once the administrators need a one-time password it is the only way in); the bootstrap administrator's
  # password only while that client does not exist yet.
  if [ -n "${OPS_CLIENT_SECRET:-}" ] && ops_login "$2" >/dev/null 2>&1; then
    exit 0
  fi
  exec_in -e ADMIN_USER -e ADMIN_PASSWORD identity sh -c 'exec "$0" config credentials --config "$1" --server http://localhost:8080 --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD"' "$KCADM_BIN" "$2" </dev/null
elif [ "${1:-}" = cleanup ]; then
  exec_in identity rm -f "$2" </dev/null
else
  exec_in identity "$KCADM_BIN" "$@"
fi
