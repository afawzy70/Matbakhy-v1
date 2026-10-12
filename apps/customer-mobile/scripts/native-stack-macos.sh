#!/usr/bin/env bash
# MB-031 (CI only, macOS runner): the local identity provider and API for the iOS sign-in round trip. GitHub's macOS
# runners have no Docker, so the same pinned versions run as native processes: PostgreSQL 16 (Homebrew), Keycloak
# 26.8.0 (release archive, checksum checked against the release's own .sha1) in dev mode with the repository realm,
# and the API jar with the LOCAL/dev profile. Synthetic users only; passwords are random per run.
# Writes the synthetic user password to $RUNNER_TEMP/stack.env.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="${RUNNER_TEMP:?}"
KC_VERSION=26.8.0
export JAVA_HOME="${JAVA_HOME_21_arm64:-${JAVA_HOME_21_X64:?JDK 21}}"
export PATH="$JAVA_HOME/bin:$PATH"
USER_PASSWORD="$(openssl rand -hex 12)"
WEB_SECRET="$(openssl rand -hex 24)"
ADMIN_PASSWORD="$(openssl rand -hex 16)"
echo "::add-mask::$USER_PASSWORD"
echo "MB031_USER_PASSWORD=$USER_PASSWORD" > "$TMP/stack.env"

echo "-- PostgreSQL 16"
brew install postgresql@16 >/dev/null
PG="$(brew --prefix postgresql@16)/bin"
"$PG/initdb" -D "$TMP/pg" -U matbakhy --auth=trust >/dev/null
"$PG/pg_ctl" -D "$TMP/pg" -l "$TMP/pg.log" -o "-p 5432 -k $TMP" start
"$PG/createdb" -h localhost -U matbakhy matbakhy

echo "-- Keycloak $KC_VERSION"
curl -fsSL -o "$TMP/kc.tar.gz" "https://github.com/keycloak/keycloak/releases/download/$KC_VERSION/keycloak-$KC_VERSION.tar.gz"
curl -fsSL -o "$TMP/kc.sha1" "https://github.com/keycloak/keycloak/releases/download/$KC_VERSION/keycloak-$KC_VERSION.tar.gz.sha1"
echo "$(cut -d' ' -f1 < "$TMP/kc.sha1")  $TMP/kc.tar.gz" | shasum -a 1 -c -
tar xzf "$TMP/kc.tar.gz" -C "$TMP"
KC="$TMP/keycloak-$KC_VERSION"
mkdir -p "$KC/data/import"
sed -e "s|\${OIDC_WEB_CLIENT_SECRET}|$WEB_SECRET|" -e "s|\${API_PORT}|8080|" -e "s|\${DEV_USER_PASSWORD}|$USER_PASSWORD|g" \
  "$ROOT/infra/identity/matbakhy-realm.json" > "$KC/data/import/matbakhy-realm.json"
KC_BOOTSTRAP_ADMIN_USERNAME=local-admin KC_BOOTSTRAP_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  nohup "$KC/bin/kc.sh" start-dev --import-realm --http-port=8180 --hostname=http://localhost:8180 > "$TMP/keycloak.log" 2>&1 &

echo "-- API"
(cd "$ROOT/backend" && ./mvnw -B -ntp -q -DskipTests package)
JAR=$(ls "$ROOT"/backend/app/target/*.jar | grep -v plain | head -1)
for _ in $(seq 1 90); do curl -fs -o /dev/null http://localhost:8180/realms/matbakhy/.well-known/openid-configuration && break; sleep 2; done
curl -fsS -o /dev/null http://localhost:8180/realms/matbakhy/.well-known/openid-configuration || { tail -40 "$TMP/keycloak.log"; exit 1; }
APP_ENV=LOCAL SPRING_PROFILES_ACTIVE=dev DATABASE_URL=jdbc:postgresql://localhost:5432/matbakhy DATABASE_USERNAME=matbakhy \
  OIDC_ISSUER=http://localhost:8180/realms/matbakhy OIDC_WEB_CLIENT_SECRET="$WEB_SECRET" WEB_POST_LOGIN_REDIRECT=http://localhost:5173/ \
  OBJECT_STORAGE_ENDPOINT=http://localhost:9090 OBJECT_STORAGE_CLIENT_ENDPOINT=http://localhost:9090 \
  nohup java -jar "$JAR" > "$TMP/api.log" 2>&1 &
for _ in $(seq 1 90); do curl -fs http://localhost:8080/actuator/health | grep -q '"status":"UP"' && break; sleep 2; done
curl -fs http://localhost:8080/actuator/health | grep -q '"status":"UP"' || { tail -60 "$TMP/api.log"; exit 1; }
echo "stack ready: identity http://localhost:8180, API http://localhost:8080"
