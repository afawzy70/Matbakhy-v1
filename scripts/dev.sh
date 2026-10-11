#!/usr/bin/env bash
# Matbakhy local development stack (MB-008). Local and CI only.
#   scripts/dev.sh doctor   check prerequisites; fails with an actionable message
#   scripts/dev.sh up       build the API jar, start PostgreSQL, Keycloak, S3 emulator, mail sink and API
#   scripts/dev.sh smoke    verify every running service (health, OIDC discovery, migration, mail, buckets)
#   scripts/dev.sh down     stop containers (data volume kept)
#   scripts/dev.sh reset    stop and delete this local stack's data volume (refuses non-local env files)
#   scripts/dev.sh verify   doctor + up + smoke in an isolated, throwaway compose project, then clean up
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA="$ROOT/infra"
ENV_FILE="${MATBAKHY_ENV_FILE:-$INFRA/.env}"

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "-- $*"; }

doctor() {
  local missing=0
  check() { # name, command, hint
    if ! eval "$2" >/dev/null 2>&1; then echo "MISSING: $1. $3" >&2; missing=1; else echo "ok: $1"; fi
  }
  check "docker CLI" "command -v docker" "Install Docker Engine or Docker Desktop."
  check "docker daemon" "docker info" "Start the Docker daemon (Docker Desktop, or: sudo dockerd)."
  check "docker compose v2" "docker compose version" "Install the Docker Compose v2 plugin."
  check "JDK 21" "java -XshowSettings:properties -version 2>&1 | grep -q 'java.specification.version = 21'" "Install a JDK 21 and put it first on PATH."
  check "Node.js $(cat "$ROOT/.node-version")" "[ \"\$(node --version)\" = \"v$(cat "$ROOT/.node-version")\" ]" "Install the version in .node-version (e.g. nvm install)."
  check "pnpm (corepack)" "pnpm --version" "Run: corepack enable"
  check "curl" "command -v curl" "Install curl."
  check "openssl" "command -v openssl" "Install openssl (used to generate local passwords)."
  [ "$missing" -eq 0 ] || die "prerequisites missing (see above); nothing was started"
}

ensure_env() {
  if [ ! -f "$ENV_FILE" ]; then
    info "creating $ENV_FILE with random local passwords"
    sed -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 16)/" \
        -e "s/^KEYCLOAK_ADMIN_PASSWORD=.*/KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)/" \
        -e "s/^OIDC_WEB_CLIENT_SECRET=.*/OIDC_WEB_CLIENT_SECRET=$(openssl rand -hex 24)/" \
        -e "s/^DEV_USER_PASSWORD=.*/DEV_USER_PASSWORD=$(openssl rand -hex 12)/" \
        "$INFRA/.env.example" > "$ENV_FILE"
    chmod 600 "$ENV_FILE"
  fi
  grep -q '^MATBAKHY_ENV_MARKER=local$' "$ENV_FILE" || die "$ENV_FILE is not marked MATBAKHY_ENV_MARKER=local; refusing"
  set -a; . "$ENV_FILE"; set +a
}

compose() { docker compose --env-file "$ENV_FILE" -f "$INFRA/compose.yaml" "$@"; }

render_realm() {
  # Fill the realm template from the env file; the output stays out of Git (infra/.generated).
  mkdir -p "$INFRA/.generated/identity"
  sed -e "s|\${OIDC_WEB_CLIENT_SECRET}|${OIDC_WEB_CLIENT_SECRET}|" -e "s|\${API_PORT}|${API_PORT}|" \
      -e "s|\${DEV_USER_PASSWORD}|${DEV_USER_PASSWORD}|g" \
      "$INFRA/identity/matbakhy-realm.json" > "$INFRA/.generated/identity/matbakhy-realm.json"
  # Readable by the non-root Keycloak user in the container; holds generated local-only values.
  chmod 644 "$INFRA/.generated/identity/matbakhy-realm.json"
}

up() {
  ensure_env
  info "building API jar"
  (cd "$ROOT/backend" && ./mvnw -B -ntp -q -DskipTests package)
  render_realm
  info "starting stack (project ${COMPOSE_PROJECT_NAME})"
  compose up --build --detach --wait --wait-timeout 300
  compose ps
}

smoke() {
  ensure_env
  local api="http://127.0.0.1:${API_PORT}" idp="http://localhost:${IDENTITY_PORT}"
  info "API health"
  curl -fsS "$api/actuator/health" | grep -q '"status":"UP"' || die "API health is not UP"
  info "database migrated by Flyway"
  compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'" | grep -qx 1 || die "Flyway baseline not applied"
  info "OIDC discovery for realm matbakhy"
  curl -fsS "$idp/realms/matbakhy/.well-known/openid-configuration" | grep -q "\"issuer\":\"$idp/realms/matbakhy\"" \
    || die "Keycloak realm discovery failed"
  info "object storage buckets"
  local buckets; buckets="$(curl -fsS "http://127.0.0.1:${OBJECT_STORAGE_PORT}/")"
  echo "$buckets" | grep -q "matbakhy-private" && echo "$buckets" | grep -q "matbakhy-public" || die "S3 buckets missing"
  info "mail sink captures a synthetic message"
  local subject="matbakhy-smoke-$(date +%s)-$RANDOM"
  printf 'From: smoke@matbakhy.test\r\nTo: customer-a@matbakhy.test\r\nSubject: %s\r\n\r\nSynthetic smoke message.\r\n' "$subject" \
    | curl -fsS --url "smtp://127.0.0.1:${MAIL_SMTP_PORT}" --mail-from smoke@matbakhy.test --mail-rcpt customer-a@matbakhy.test --upload-file -
  curl -fsS "http://127.0.0.1:${MAIL_UI_PORT}/api/v1/search?query=subject:$subject" | grep -q "\"Subject\":\"$subject\"" \
    || die "mail sink did not capture the message"
  info "native client enforces PKCE (authorization without code_challenge is refused)"
  local pkce; pkce="$(curl -s -o /dev/null -w '%{redirect_url}' "$idp/realms/matbakhy/protocol/openid-connect/auth?response_type=code&scope=openid&client_id=matbakhy-mobile&redirect_uri=matbakhy-dev%3A%2F%2Fauth%2Fcallback&state=smoke")"
  echo "$pkce" | grep -q "error=invalid_request" || die "PKCE is not enforced for matbakhy-mobile"
  info "anonymous private API call is denied"
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$api/api/v1/me")" = "401" ] || die "anonymous /api/v1/me is not 401"
  info "web OIDC login through the API with a synthetic user"
  local jar; jar="$(mktemp)"
  local login_page action location
  location="$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' "http://localhost:${API_PORT}/oauth2/authorization/keycloak")"
  login_page="$(curl -s -c "$jar" -b "$jar" "$location")"
  action="$(echo "$login_page" | grep -o 'id="kc-form-login"[^>]*action="[^"]*"' | sed 's/.*action="//; s/"$//; s/&amp;/\&/g')"
  [ -n "$action" ] || die "Keycloak login form not found"
  location="$(curl -s -c "$jar" -b "$jar" -o /dev/null -w '%{redirect_url}' --data-urlencode "username=customer-a" \
    --data-urlencode "password=${DEV_USER_PASSWORD}" --data "credentialId=" "$action")"
  case "$location" in "http://localhost:${API_PORT}/login/oauth2/code/keycloak"*) ;; *) die "login did not return to the API callback";; esac
  curl -s -c "$jar" -b "$jar" -o /dev/null "$location"
  curl -s -c "$jar" -b "$jar" "http://localhost:${API_PORT}/api/v1/me" | grep -q '"emailVerified":true' || { rm -f "$jar"; die "authenticated /api/v1/me failed"; }
  rm -f "$jar"
  echo "PASS: local stack smoke checks"
}

down() { ensure_env; compose down; }

reset() {
  ensure_env
  info "removing containers and data volume of local project ${COMPOSE_PROJECT_NAME}"
  compose down --volumes
}

verify() {
  doctor
  VERIFY_TMP="$(mktemp -d)"
  export MATBAKHY_ENV_FILE="$VERIFY_TMP/.env" ENV_FILE="$VERIFY_TMP/.env"
  sed -e "s/^COMPOSE_PROJECT_NAME=.*/COMPOSE_PROJECT_NAME=matbakhy-verify-$$/" \
      -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 16)/" \
      -e "s/^KEYCLOAK_ADMIN_PASSWORD=.*/KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)/" \
      -e "s/^OIDC_WEB_CLIENT_SECRET=.*/OIDC_WEB_CLIENT_SECRET=$(openssl rand -hex 24)/" \
      -e "s/^DEV_USER_PASSWORD=.*/DEV_USER_PASSWORD=$(openssl rand -hex 12)/" \
      -e "s/^POSTGRES_PORT=.*/POSTGRES_PORT=15432/" -e "s/^IDENTITY_PORT=.*/IDENTITY_PORT=18180/" \
      -e "s/^OBJECT_STORAGE_PORT=.*/OBJECT_STORAGE_PORT=19090/" -e "s/^MAIL_UI_PORT=.*/MAIL_UI_PORT=18025/" \
      -e "s/^MAIL_SMTP_PORT=.*/MAIL_SMTP_PORT=11025/" -e "s/^API_PORT=.*/API_PORT=18080/" \
      "$INFRA/.env.example" > "$ENV_FILE"
  trap 'compose down --volumes >/dev/null 2>&1 || true; rm -rf "$VERIFY_TMP"' EXIT
  up
  smoke
}

case "${1:-}" in
  doctor) doctor ;;
  up) doctor; up ;;
  smoke) smoke ;;
  down) down ;;
  reset) reset ;;
  verify) verify ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
