# Front image for the web preview: both web builds plus the Caddy reverse proxy. Built in CI at one commit, never
# on the server. The API address is fixed at build time (Vite), so the image is specific to one preview domain.
#   docker build -f deploy/web-preview/front.Dockerfile --build-arg PREVIEW_DOMAIN=example.org .
FROM node:24.21.0-alpine AS build
ARG PREVIEW_DOMAIN
# The demo/test notice is part of this build (it also shows when the API cannot be reached); the API reports it too.
ARG DEMO_NOTICE=true
RUN test -n "$PREVIEW_DOMAIN"
WORKDIR /src
COPY . .
RUN corepack enable && pnpm install --frozen-lockfile \
 && VITE_DEMO_NOTICE="${DEMO_NOTICE}" VITE_API_BASE_URL="https://api.${PREVIEW_DOMAIN}" pnpm --filter @matbakhy/customer-web build \
 && VITE_DEMO_NOTICE="${DEMO_NOTICE}" VITE_API_BASE_URL="https://api.${PREVIEW_DOMAIN}" pnpm --filter @matbakhy/operations-web build

FROM caddy:2.10.2-alpine
COPY deploy/web-preview/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/apps/customer-web/dist /srv/customer-web
COPY --from=build /src/apps/operations-web/dist /srv/operations-web
