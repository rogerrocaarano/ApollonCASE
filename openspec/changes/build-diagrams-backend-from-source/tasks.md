# Tasks

## 1. Compose: build `server` from source

- [x] 1.1 In `docker/compose.production.yml`, change the `server` service from `image: "ghcr.io/ls1intum/apollon/server:${IMAGE_TAG:-latest}"` to `build: { context: .., dockerfile: ./services/diagrams-backend/Dockerfile }`, matching the pattern already used for `webapp`/`extension-backend` in that file and for `server` in `docker/compose.local.yml`. Verify `docker compose -f docker/compose.production.yml config` parses cleanly with no `image:`/`build:` conflict.
- [x] 1.2 Update `docker/.env.example`'s `IMAGE_TAG` comment to reflect that it no longer applies to `server` (only ever selected an upstream-published tag, which `server` no longer pulls). Removed entirely (not just annotated) since grepping `compose.production.yml` confirmed zero remaining `${IMAGE_TAG}` references - `webapp` had already switched to `build:` earlier, so nothing consumes it anymore.

## 2. Verification

- [x] 2.1 Bring up the full stack with `docker compose -f docker/compose.production.yml up -d --build` and verify all 5 services (`redis`, `postgres`, `server`, `extension-backend`, `webapp`) reach `healthy`, with `server`'s image now built locally (`docker image inspect docker-server:latest` shows no registry digest, or `docker compose images` shows it built rather than pulled). Verified: all 5 `healthy`; `docker image inspect docker-server:latest --format '{{.RepoDigests}}'` shows `docker-server@sha256:...` (a local image name), not `ghcr.io/ls1intum/apollon/server@sha256:...`.
- [x] 2.2 Verify `server`'s behavior is unchanged from the previous (pulled-image) deployment: `extension-backend` can still reach it (`docker exec extension-backend wget -qO- http://server:8000/health` returns `{"status":"ok"}`), confirming the source-built image is behaviorally equivalent. Verified: identical `{"status":"ok"}` response.
