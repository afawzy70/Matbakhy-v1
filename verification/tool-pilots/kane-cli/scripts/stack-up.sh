#!/usr/bin/env bash
# QA-only: bring up an isolated throwaway Matbakhy stack from a checked-out application worktree.
# usage: stack-up.sh <app-worktree> <state-dir>   (Node from .node-version must be first on PATH)
# Writes <state-dir>/env (chmod 600, never committed). Synthetic local credentials only.
set -euo pipefail
APP="$1"; STATE="$2"; mkdir -p "$STATE"
INFRA="$APP/infra"; ENV_FILE="$STATE/.env"
sed -e "s/^COMPOSE_PROJECT_NAME=.*/COMPOSE_PROJECT_NAME=matbakhy-kane/" \
    -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 16)/" \
    -e "s/^KEYCLOAK_ADMIN_PASSWORD=.*/KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)/" \
    -e "s/^OIDC_WEB_CLIENT_SECRET=.*/OIDC_WEB_CLIENT_SECRET=$(openssl rand -hex 24)/" \
    -e "s/^DEV_USER_PASSWORD=.*/DEV_USER_PASSWORD=$(openssl rand -hex 12)/" \
    -e "s/^POSTGRES_PORT=.*/POSTGRES_PORT=15432/" -e "s/^IDENTITY_PORT=.*/IDENTITY_PORT=18180/" \
    -e "s/^OBJECT_STORAGE_PORT=.*/OBJECT_STORAGE_PORT=19090/" -e "s/^MAIL_UI_PORT=.*/MAIL_UI_PORT=18025/" \
    -e "s/^MAIL_SMTP_PORT=.*/MAIL_SMTP_PORT=11025/" -e "s/^API_PORT=.*/API_PORT=18080/" \
    "$INFRA/.env.example" > "$ENV_FILE"
chmod 600 "$ENV_FILE"
export MATBAKHY_ENV_FILE="$ENV_FILE" MATBAKHY_GENERATED_DIR="$STATE/generated"
"$APP/scripts/dev.sh" up
