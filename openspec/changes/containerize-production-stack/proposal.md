# Proposal

## Why

`docker/compose.app.yml`, `compose.db.yml`, and `compose.proxy.yml` are unmodified leftovers from upstream Apollon: they only deploy `webapp` + `diagrams-backend` + Redis, with `diagrams-backend` publicly routed through Traefik. They predate `extension-backend`, which now has real code (Spring Boot, Postgres via JPA, the `/api/v1/**` REST gateway and `/ws/diagrams` relay from `gate-project-diagrams`) but no `Dockerfile` and no place in any compose file. Production containerization needs to catch up to the target architecture the README already describes: the webapp and `extension-backend` are the only publicly reachable services, `diagrams-backend` sits behind `extension-backend` on a private network, and Keycloak stays fully decoupled — configured by URL, never deployed by this repo.

## What Changes

- Add a multi-stage `Dockerfile` for `extension-backend` (Gradle build, JDK 25 toolchain runtime image), following the existing non-root/minimal-runtime conventions of `webapp/Dockerfile` and `services/diagrams-backend/Dockerfile`.
- Add a containerized PostgreSQL service to the production compose files (image, named volume, healthcheck, restart policy), matching the existing Redis `db` service's pattern in `docker/compose.db.yml`.
- Add an `extension-backend` service to the production compose files: built from the new Dockerfile, wired to Postgres and to `diagrams-backend` over an internal network, receiving `issuer-uri` and CORS origins by environment variable (no Keycloak container or config baked into any compose file).
- **BREAKING**: Remove `diagrams-backend`'s public Traefik routing (`/api`, `/ws` on the app hostname) and move it, Redis, and Postgres onto a private compose network reachable only from `extension-backend`. Give `extension-backend` the `/api`/`/ws` Traefik routes instead, matching its actual `/api/v1/**` and `/ws/diagrams` paths.
- Update `webapp`'s production environment guidance/comments: `VITE_SERVER_URL` no longer resolves to a same-host, Traefik-routed `diagrams-backend`; `VITE_EXTENSION_SERVER_URL`/`VITE_KEYCLOAK_*` become the only externally-facing backend URLs needed in production.
- Update `docker/` and root `README.md` documentation to reflect the new services, the private network split, and that Keycloak remains external infrastructure configured purely through environment variables.

## Capabilities

### New Capabilities

_None._ This change ships deployment tooling (Dockerfile, Compose services, network topology, docs) with no new or modified application-level requirement — the authorization and gateway behavior it deploys was already specified by `gate-project-diagrams`. Per the proposal instructions, this change sets `skip_specs: true` rather than inventing a requirement to satisfy validation.

### Modified Capabilities

_None._

## Impact

- **Affected files**: `docker/compose.app.yml`, `docker/compose.db.yml`, `docker/compose.proxy.yml`, a new `services/extension-backend/Dockerfile`, `README.md`, `services/extension-backend/README.md`, `webapp/src/constants/urls.ts` (comment only), `webapp/.env.example`.
- **Not affected**: `docker/compose.local.yml`, `docker/compose.local.db.yml`, and the `scripts/dev*`/`scripts/docker-up*` local-development workflow — this change is scoped to the production compose files only.
- **Dependencies**: assumes `gate-project-diagrams` is far enough along that the webapp no longer needs direct, publicly-reachable access to `diagrams-backend` for project diagrams; loose/shared-by-link diagrams and `/embed` losing direct public access is an accepted, explicit consequence of this change (confirmed during exploration), not a gap to fix here.
- **Operational impact**: deployers must now provide `issuer-uri` (extension-backend) and `VITE_KEYCLOAK_*` (webapp) pointing at an externally-managed Keycloak, plus Postgres credentials, as environment variables at deploy time — no new secrets are introduced by this repo.
