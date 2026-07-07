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
