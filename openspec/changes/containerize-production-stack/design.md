# Design

## Context

See `proposal.md` - Why for motivation. Current state, confirmed by reading the code and configs directly:

- `docker/compose.proxy.yml` creates `apollon-network` (external Traefik-facing bridge network) and runs Traefik + a maintenance page. `compose.db.yml` and `compose.app.yml` both declare `apollon-network` as `external: true` and join it.
- `compose.app.yml` runs `webapp` and `server` (`diagrams-backend`), both on `apollon-network`, both carrying Traefik labels. `server` is routed at the app hostname under `PathPrefix(/api)` and `PathPrefix(/ws)`.
- `compose.db.yml` runs one service, `db` (Redis Stack), also on `apollon-network` - there is currently no network separation between "publicly routed" and "backing store" services.
- `extension-backend` (Spring Boot + Kotlin + Gradle 9.7.1 wrapper, JDK 25 toolchain in `build.gradle.kts`) has no `Dockerfile` and no compose entry. It already exposes `/api/v1/**` (REST) and `/ws/diagrams` (`WebSocketRelayConfig`), reads `spring.security.oauth2.resourceserver.jwt.issuer-uri` and `app.diagrams-backend.{base-url,ws-base-url}` from properties/env, and its `SecurityConfig` permits only `/v3/api-docs/**`, `/swagger-ui/**`, and `/ws/diagrams` (ticket-authenticated) without a JWT.
- `extension-backend` has no health endpoint and no Actuator dependency today.
- `webapp/src/constants/urls.ts` documents, in a comment, that `VITE_SERVER_URL` defaults to a same-origin path because `diagrams-backend` "is routed by Traefik at the same host" in production - that assumption becomes false once `diagrams-backend` is moved off the public network.
- No production `.env.example` exists for the `docker/` compose files today (`compose.app.yml`/`compose.proxy.yml` reference `APP_HOSTNAME`, `ACME_EMAIL`, `OWNER_SECRET`, `IMAGE_TAG`, `CORS_ORIGIN`, `LEGAL_PROFILE` with no documented example).
- Confirmed during exploration: `diagrams-backend` moves off the public network now, even though `gate-project-diagrams` (in progress) has not yet resolved what happens to loose/shared-by-link diagrams and `/embed`. That gap is accepted, not solved here.

## Goals / Non-Goals

**Goals:**
- `extension-backend` and `webapp` are the only services reachable from the public `apollon-network`/Traefik; `diagrams-backend`, Redis, and Postgres live on a private network only `extension-backend` can reach.
- `extension-backend` builds and runs as a container with the same hardening conventions already used by `webapp`/`diagrams-backend` (non-root user, pinned base image, healthcheck, minimal runtime).
- Postgres is containerized alongside Redis, following the existing `db` service's pattern (named volume, healthcheck, `unless-stopped`).
- Keycloak has zero footprint in any compose file - only environment variables (`issuer-uri`, `VITE_KEYCLOAK_*`) reference it, exactly as `add-keycloak-auth` already decided for the webapp.

**Non-Goals:**
- Changing `extension-backend`'s schema-management strategy. `application-local.properties.example` uses `spring.jpa.hibernate.ddl-auto=update`, which is not a safe production migration strategy (no rollback, no audit trail), but introducing Flyway/Liquibase is an application-level change independent of packaging. Flagged here so it isn't silently carried into production; not addressed by this change.
- Resolving `gate-project-diagrams`'s open questions about loose diagrams, `/embed`, or diagram TTL/snapshotting. This change assumes that work lands separately; isolating `diagrams-backend`'s network now is a deliberate, confirmed choice, not a signal that those questions are resolved.
- Changing `docker/compose.local.yml` / `compose.local.db.yml` or the `scripts/dev*`/`scripts/docker-up*` local workflow.
- Deploying, configuring, or documenting Keycloak itself (realm, clients) - unchanged non-goal from `add-keycloak-auth`.

## Decisions

### Two compose networks: public `apollon-network`, private `apollon-internal`
`apollon-internal` is a new `internal: true` bridge network (no outbound/inbound beyond the Docker host), created in `compose.db.yml` and declared `external: true` from `compose.app.yml`, mirroring the existing cross-file pattern for `apollon-network`. `diagrams-backend`, Redis, and the new Postgres service join only `apollon-internal`. `extension-backend` is dual-homed (both networks) since it is the sole bridge between the public gateway and the private stores. `webapp` stays on `apollon-network` only - it never talks to Postgres, Redis, or `diagrams-backend` directly.
Alternative considered: a single flat network relying only on the absence of Traefik labels to keep services "private". Rejected because any container attached to `apollon-network` (including a compromised `webapp`) could still reach `diagrams-backend`/Postgres directly over the Docker network; a separate `internal: true` network makes the isolation structural, not just a labeling convention.

### Retarget Traefik routing from `diagrams-backend` to `extension-backend`
`extension-backend`'s real paths (`/api/v1/**`, `/ws/diagrams`) both fall under the existing `PathPrefix(/api)` / `PathPrefix(/ws)` Traefik rules already defined for `server` in `compose.app.yml`. Those labels move to a new `extension-backend` service definition; `diagrams-backend` keeps its `expose` ports (for other containers on `apollon-internal`) but loses every `traefik.*` label and its `apollon-network` membership.
Alternative considered: a new subdomain/path for `extension-backend` (e.g. `api.<host>`). Rejected - no code or infra reason requires it, it would need a second TLS certificate/DNS record per deployment, and the existing path prefixes already fit without collision now that `diagrams-backend` no longer claims them.

### `extension-backend` Dockerfile: multi-stage Gradle build, Temurin JRE runtime
Builder stage: `eclipse-temurin:25-jdk` (matches the `JavaLanguageVersion.of(25)` toolchain in `build.gradle.kts` exactly, avoiding Gradle's toolchain auto-provisioning inside the build). Copy `gradlew`, `gradle/`, `settings.gradle.kts`, `build.gradle.kts` first and run a dependency-only step before copying `src/`, to reuse the pattern of build-file-first copying already used in `webapp/Dockerfile`/`services/diagrams-backend/Dockerfile` for cache efficiency; use a `--mount=type=cache,id=gradle,target=/root/.gradle` cache mount (same technique those two Dockerfiles use for `/root/.npm`). Build with `./gradlew bootJar -x test --no-daemon`.
Runtime stage: `eclipse-temurin:25-jre-alpine` (small, and Alpine's busybox `wget` supports the same `HEALTHCHECK ... wget --spider` style already used in `webapp/Dockerfile`). Run as a non-root user, `COPY --from=builder` the built jar as `/app/app.jar`, `EXPOSE 8080`, `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.
Alternative considered: Jib or Buildpacks (no Dockerfile at all). Rejected to keep the same authoring style (explicit multi-stage `Dockerfile`) as the other two services in this repo, so all three follow one convention.

### Health check needs a minimal Spring Boot Actuator, scoped to `/actuator/health` only
`extension-backend` has no endpoint a container/Traefik healthcheck can call without a JWT. Add `spring-boot-starter-actuator`, set `management.endpoints.web.exposure.include=health` (nothing else exposed) and `management.endpoint.health.show-details=never`, and add `/actuator/health` to `SecurityConfig`'s existing `permitAll` list alongside `/v3/api-docs/**`/`/swagger-ui/**`. Used both by the Dockerfile's `HEALTHCHECK` and implicitly by Traefik's Docker provider (which already excludes unhealthy containers from routing for `webapp`/`server` today, based on the same Docker healthcheck mechanism - no new Traefik configuration needed).
Alternative considered: a raw unauthenticated `GET /health` controller endpoint (matching `diagrams-backend`'s own `/health`, avoiding a new dependency). Rejected because Actuator's health endpoint is idiomatic for Spring Boot, already wires in the datasource health indicator (Postgres reachability) for free, and is a one-line addition given the starter is a single Gradle dependency.

### Postgres: `postgres:17-alpine`, containerized like the existing `db` (Redis) service
New service in `compose.db.yml`, same shape as the existing Redis service: named volume (`pgdata`), `healthcheck` via `pg_isready`, `restart: unless-stopped`, `security_opt: [no-new-privileges:true]` (matching Redis's own hardening in that file - no `cap_drop` there either, since both images need root to `chown`/init their data directory on first run), same `logging` block. Renaming the existing Redis service from `db` to `redis` in the same pass, now that two stores exist in one file, and updating `diagrams-backend`'s `REDIS_URL` to `redis://redis:6379` accordingly - `db` was only ever an internal Docker DNS name, never externally visible.
Alternative considered: keep Redis named `db` and call the new service `postgres`/`db2`. Rejected as needlessly confusing once two stores coexist in the same file.

### Keycloak stays entirely out of every compose file
No service, no network entry, no volume. `extension-backend` receives `issuer-uri` and `app.diagrams-backend.*` by environment variable in `compose.app.yml` (`${ISSUER_URI:?...}` style, matching the existing `${ACME_EMAIL:?...}` required-var pattern already used in `compose.proxy.yml`); the webapp continues to receive `VITE_KEYCLOAK_*` at build/deploy time as it already does per `add-keycloak-auth`. A new `docker/.env.example` documents every variable a deployer must set (`APP_HOSTNAME`, `ACME_EMAIL`, `OWNER_SECRET`, `ISSUER_URI`, `POSTGRES_*`, `CORS_ORIGIN`) - none of it Keycloak infrastructure, all of it configuration pointing at Keycloak.
This is not a new decision - it's `add-keycloak-auth`'s already-recorded "Keycloak es configuración de entorno, no infraestructura de este repo" applied consistently to the new service.

## Risks / Trade-offs

- **BREAKING**: any deployment relying on directly reaching `diagrams-backend` at `<host>/api` or `<host>/ws` (loose diagrams, shared-by-link, `/embed`, previews) loses that access the moment this ships → accepted per the exploration decision; not mitigated here. Document it plainly in the migration plan and in `README.md` so it isn't a silent regression.
- **`ddl-auto=update` in production** → out of scope (see Non-Goals), but called out in `tasks.md` as a documentation note so it isn't mistaken for a deliberate, reviewed production migration strategy.
- **New Actuator dependency widens `extension-backend`'s attack surface slightly** → mitigated by exposing only `health` (`management.endpoints.web.exposure.include=health`) and hiding details (`show-details=never`), so no environment/config/beans data is ever served.
- **`internal: true` network typo or omission silently re-exposes the private services** → mitigated by keeping the network topology in version control (compose files), reviewable the same way as any other code change; `tasks.md` should include verifying (e.g. via `docker compose exec webapp curl diagrams-backend:8000` failing, or from outside the Docker host) that `diagrams-backend`/Postgres are unreachable from outside `apollon-internal` before considering this done.
- **Renaming the Redis service (`db` → `redis`) touches `diagrams-backend`'s `REDIS_URL` env var** → low risk: it's an internal Docker Compose DNS name with no external consumers, but `tasks.md` must update every reference in `compose.app.yml`/`compose.db.yml` together to avoid a broken connection string.

## Migration Plan

1. Add `extension-backend`'s `Dockerfile`, Actuator dependency/config, and `SecurityConfig` allow-list entry for `/actuator/health`.
2. Update `compose.db.yml`: rename `db` → `redis`, add the `postgres` service, add the `apollon-internal` network (owning it, `internal: true`).
3. Update `compose.app.yml`: add `extension-backend`, move its Traefik labels off `diagrams-backend` onto it, move `diagrams-backend` to `apollon-internal`-only (drop its `apollon-network` membership and Traefik labels), update `REDIS_URL`, declare `apollon-internal` as `external: true`.
4. Add `docker/.env.example` documenting every required/optional variable across the three compose files, including `ISSUER_URI` and the webapp's `VITE_KEYCLOAK_*` (documented for context even though the webapp is built separately, not run via this compose).
5. Update `README.md` and `services/extension-backend/README.md` to describe the new topology (diagram: webapp + extension-backend public, diagrams-backend/Redis/Postgres private, Keycloak external) and drop language that's now stale (e.g. `extension-backend`'s "por construir" framing, `diagrams-backend`'s implied public reachability).
6. Update the comment in `webapp/src/constants/urls.ts` (and `.env.example`) that currently assumes `diagrams-backend` is Traefik-routed at the same host in production.

Rollback: revert the compose/Dockerfile/doc changes. Since `diagrams-backend` and Redis are otherwise unmodified (only their network membership and labels changed) and Postgres is new (no prior production data to lose), rollback is a plain `git revert` with no data migration to undo.

## Open Questions

None - the two decisions that would have changed this design's approach (diagrams-backend's public exposure, and whether Postgres is containerized here) were resolved with the user before writing this document.
