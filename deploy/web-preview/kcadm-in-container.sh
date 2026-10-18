#!/bin/sh
# kcadm.sh inside the preview's Keycloak container, for deploy/web-preview/migrate-mfa.sh (KCADM_REMOTE=1).
#   kcadm-in-container.sh config credentials --config F   signs in with ADMIN_USER / ADMIN_PASSWORD from the environment
#   kcadm-in-container.sh cleanup F                        removes the kcadm config file
#   kcadm-in-container.sh <anything else>                  passed to kcadm.sh unchanged (standard input is forwarded)
# The admin password is read inside the container from the forwarded environment; it is never an argument on the host.
set -eu
cd "$(dirname "$0")"
ENV_FILE=${ENV_FILE:-.env}
exec_in() { docker compose --env-file "$ENV_FILE" -f compose.yaml ${COMPOSE_OVERRIDE:+-f "$COMPOSE_OVERRIDE"} exec -T "$@"; }
KCADM_BIN=/opt/keycloak/bin/kcadm.sh
if [ "${1:-}" = config ] && [ "${2:-}" = credentials ]; then
  shift 2
  [ "${1:-}" = --config ] || { echo "kcadm-in-container: --config is required" >&2; exit 2; }
  exec_in -e ADMIN_USER -e ADMIN_PASSWORD identity sh -c 'exec "$0" config credentials --config "$1" --server http://localhost:8080 --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD"' "$KCADM_BIN" "$2"
elif [ "${1:-}" = cleanup ]; then
  exec_in identity rm -f "$2"
else
  exec_in identity "$KCADM_BIN" "$@"
fi
