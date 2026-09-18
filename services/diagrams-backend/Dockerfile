# syntax=docker/dockerfile:1.9

ARG NODE_IMAGE=node:24.15.0-slim@sha256:24dc26ef1e3c3690f27ebc4136c9c186c3133b25563ae4d7f0692e4d1fe5db0e

# hadolint ignore=DL3006
FROM ${NODE_IMAGE} AS base
WORKDIR /app

FROM base AS builder

COPY package.json package-lock.json ./
COPY library/package.json ./library/
COPY packages/ui/package.json ./packages/ui/
COPY services/diagrams-backend/package.json ./services/diagrams-backend/

RUN --mount=type=cache,id=npm,target=/root/.npm \
    npm ci --ignore-scripts

COPY library ./library
COPY packages/ui ./packages/ui
COPY services/diagrams-backend ./services/diagrams-backend

RUN npm run build --workspace=@tumaet/apollon
RUN npm run build --workspace=@tumaet/server

# hadolint ignore=DL3006
FROM ${NODE_IMAGE} AS runtime

WORKDIR /app

LABEL org.opencontainers.image.title="Apollon Server" \
      org.opencontainers.image.description="Hono + Redis + WebSocket relay server for the Apollon UML modeling editor." \
      org.opencontainers.image.licenses="MIT"

RUN apt-get update && apt-get install -y --no-install-recommends tini && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --from=builder /app/library ./library
COPY --from=builder /app/packages/ui ./packages/ui
COPY --from=builder --chown=node:node /app/services/diagrams-backend ./services/diagrams-backend

WORKDIR /app/services/diagrams-backend

EXPOSE 8000 4444

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8000/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

USER node

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "dist/src/server.js"]
