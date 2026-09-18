# syntax=docker/dockerfile:1.9

ARG NODE_IMAGE=node:24.15.0-slim@sha256:24dc26ef1e3c3690f27ebc4136c9c186c3133b25563ae4d7f0692e4d1fe5db0e

# hadolint ignore=DL3006
FROM ${NODE_IMAGE} AS builder
WORKDIR /app

COPY package.json package-lock.json ./
COPY library/package.json ./library/
COPY packages/ui/package.json ./packages/ui/
COPY webapp/package.json ./webapp/

RUN --mount=type=cache,id=npm,target=/root/.npm \
    npm ci --ignore-scripts

COPY library ./library
COPY packages/ui ./packages/ui
COPY webapp ./webapp

# The webapp's `tsc -b` resolves `@tumaet/apollon` via the workspace link in
# node_modules, which requires the library's compiled .d.ts in dist/.
RUN npm run build --workspace=@tumaet/apollon \
 && npm run build --workspace=@tumaet/webapp

FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10 AS runtime

LABEL org.opencontainers.image.title="Apollon Webapp" \
      org.opencontainers.image.description="Browser-hosted web app for the Apollon UML modeling editor." \
      org.opencontainers.image.licenses="MIT"

RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/webapp/dist /usr/share/nginx/html
COPY webapp/docker/entrypoint.sh /docker-entrypoint.d/40-apollon-env.sh

RUN chmod +x /docker-entrypoint.d/40-apollon-env.sh && \
    chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1

USER nginx

CMD ["nginx", "-g", "daemon off;"]
