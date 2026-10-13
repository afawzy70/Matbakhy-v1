#!/bin/sh
# First start only: the API's runtime role (MB-039). It owns nothing; the migration owner (POSTGRES_USER)
# owns the tables, and Flyway's afterMigrate callback grants this role only what it needs
# (no DDL, no UPDATE/DELETE on append-only tables, no trigger changes).
set -eu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v apppass="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE matbakhy_app LOGIN PASSWORD :'apppass' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
