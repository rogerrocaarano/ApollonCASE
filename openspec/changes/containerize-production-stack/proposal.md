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

## Post-Implementation Revision

After the above was implemented and verified (see design.md's Migration Plan and tasks.md), the user changed direction on how the stack is exposed: no Traefik/reverse-proxy in this repo at all — deployment target is Dokploy (or an equivalent platform), which provides its own reverse proxy and domain routing. This does not change the core decisions above (webapp + extension-backend are the only services meant to receive a public domain; diagrams-backend/Redis/Postgres stay on a private network; Keycloak stays fully decoupled) - it only changes *how* that public exposure happens:

- Removed entirely: the Traefik `reverse-proxy`/`maintenance` services, all `traefik.*` labels, `ACME_EMAIL`/`APP_HOSTNAME`/`APP_HOSTNAME_ALIASES_RULE`.
- Consolidated `compose.proxy.yml` + `compose.db.yml` + `compose.app.yml` into a single `docker/compose.production.yml`. This also incidentally fixed a real problem found during verification: those three files could not be brought up together in one `docker compose up` (Compose's network-merge rules force a network `external: true` project-wide the moment any passed file says so, even the file that owns it) - a single file has no such split, so `apollon-network`/`apollon-internal` are just owned there directly and the whole stack comes up with one command.
- `webapp` and `extension-backend` now only `expose` port 8080 (no `ports:`); the deploying platform (Dokploy) is expected to attach its own proxy to those two services by name/port.

Verified the same way as before (real `docker compose up`, not just `config`): all 5 services (`redis`, `postgres`, `server`, `extension-backend`, `webapp`) reach `healthy` from one command; `docker port docker-server-1` shows nothing published; `webapp` cannot resolve `server` (different network); `extension-backend` reaches `server` and `postgres` internally and serves `/api/v1/me` with a 401 (auth still enforced) on its own exposed port.
