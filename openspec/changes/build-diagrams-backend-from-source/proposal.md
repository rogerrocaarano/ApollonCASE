# Proposal

## Why

`docker/compose.production.yml`'s `server` service (diagrams-backend) still pulls `ghcr.io/ls1intum/apollon/server:${IMAGE_TAG:-latest}` - the unmodified upstream Apollon image - instead of building from `services/diagrams-backend/Dockerfile`, which already exists in this repo and is already what `docker/compose.local.yml` uses for local dev/testing. `webapp` and `extension-backend` were already switched to `build:` from this repo's own Dockerfiles earlier in `containerize-production-stack`; `server` is the one remaining service in the production compose file whose deployed image isn't guaranteed to be built from the exact source checked into this repository. No new Dockerfile is needed - `services/diagrams-backend/Dockerfile` already exists and needs no changes, since `diagrams-backend` itself is deliberately unmodified in this fork (see root `README.md`).

## What Changes

- Change `server` in `docker/compose.production.yml` from `image: ghcr.io/ls1intum/apollon/server:${IMAGE_TAG:-latest}` to `build: { context: .., dockerfile: ./services/diagrams-backend/Dockerfile }`, matching the pattern already used for `webapp`/`extension-backend` in that same file and for `server` in `docker/compose.local.yml`.
- `IMAGE_TAG` stops applying to `server` (it only ever selected an upstream-published tag); drop it from `docker/.env.example`'s description of that variable, or note it now only ever mattered before this change. No new variables are introduced - `services/diagrams-backend/Dockerfile` takes no build args.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

_None._ This is a build-source change to production deployment tooling with no behavior change: `services/diagrams-backend`'s own code and runtime behavior are unmodified (confirmed unmodified by design, per root `README.md`), so nothing observable to a user or another service changes - only which artifact produces the running container. Per the proposal instructions, this change sets `skip_specs: true` rather than inventing a requirement to satisfy validation.

## Impact

- **Affected files**: `docker/compose.production.yml` (the `server` service's `image:`/`build:` key), `docker/.env.example` (drop or annotate `IMAGE_TAG`'s now-narrower relevance).
- **Not affected**: `services/diagrams-backend/Dockerfile` (already exists, unchanged), `services/diagrams-backend/src` (unmodified per this fork's own architecture decision), `docker/compose.local.yml` (already builds from source).
- **Operational impact**: first deploy after this change pays a Docker build for `server` in addition to `webapp`/`extension-backend`, instead of a registry pull. No new environment variables to configure.
