# ---- build each MFE (remotes under their /remotes/<name>/ base) ----
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend-customer ./frontend-customer
COPY frontend-rider ./frontend-rider
COPY frontend-admin ./frontend-admin
COPY frontend-b2b ./frontend-b2b
COPY frontend-host ./frontend-host
COPY shared-ui ./shared-ui

# shared-ui is consumed by each app via "file:../shared-ui" (not an npm
# workspace), so it needs its own node_modules installed in the build
# context before any app that imports it can resolve the dependency.
RUN cd shared-ui && npm ci --no-audit --no-fund

# Same-origin API base for the tunnel/nginx deployment: with these empty, every
# MFE's REST base resolves to a relative "/api/..." which the demo nginx proxies
# to the backend on the same origin. This is what makes the app work behind the
# Cloudflare tunnel (a remote browser must NOT be told to call "localhost").
# These are build-time only and scoped to THIS image — the local run_demo.sh
# preview builds separately and keeps its absolute localhost defaults (it has no
# proxy, so it needs them). Covers host (VITE_API_BASE_URL), admin governance
# console (VITE_API_BASE), and the customer surface (VITE_API_URL).
ENV VITE_API_BASE_URL="" \
    VITE_API_BASE="" \
    VITE_API_URL=""

RUN cd frontend-customer && npm ci --no-audit --no-fund && npm run build -- --base=/remotes/customer/
RUN cd frontend-rider    && npm ci --no-audit --no-fund && npm run build -- --base=/remotes/rider/
RUN cd frontend-admin    && npm ci --no-audit --no-fund && npm run build -- --base=/remotes/admin/
RUN cd frontend-b2b      && npm ci --no-audit --no-fund && npm run build -- --base=/remotes/b2b/
# host references remotes at same-origin paths
ENV VITE_REMOTE_CUSTOMER=/remotes/customer/assets/remoteEntry.js \
    VITE_REMOTE_RIDER=/remotes/rider/assets/remoteEntry.js \
    VITE_REMOTE_ADMIN=/remotes/admin/assets/remoteEntry.js \
    VITE_REMOTE_B2B=/remotes/b2b/assets/remoteEntry.js
RUN cd frontend-host && npm ci --no-audit --no-fund && npm run build

# ---- single nginx serving everything same-origin ----
FROM nginx:alpine-slim
COPY --from=build /app/frontend-host/dist /usr/share/nginx/html
COPY --from=build /app/frontend-customer/dist /usr/share/nginx/html/remotes/customer
COPY --from=build /app/frontend-rider/dist    /usr/share/nginx/html/remotes/rider
COPY --from=build /app/frontend-admin/dist    /usr/share/nginx/html/remotes/admin
COPY --from=build /app/frontend-b2b/dist      /usr/share/nginx/html/remotes/b2b
COPY demo/nginx.demo.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
