#!/bin/sh
# First start only: a separate database and role for Keycloak in the same PostgreSQL instance.
set -eu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v kcpass="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE keycloak LOGIN PASSWORD :'kcpass';
CREATE DATABASE keycloak OWNER keycloak;
SQL
