# Tasks

## 1. `extension-backend` health endpoint

- [ ] 1.1 Add `spring-boot-starter-actuator` to `services/extension-backend/build.gradle.kts` and verify `./gradlew build` succeeds.
- [ ] 1.2 Set `management.endpoints.web.exposure.include=health` and `management.endpoint.health.show-details=never` in `services/extension-backend/src/main/resources/application.properties`, and verify `GET /actuator/health` (unauthenticated, once 1.3 is done) returns `{"status":"UP"}` with no extra detail against a running Postgres.
- [ ] 1.3 Add `/actuator/health` to the `permitAll` list in `services/extension-backend/src/main/kotlin/com/rocayasociados/extensionbackend/common/SecurityConfig.kt`, alongside `/v3/api-docs/**` and `/swagger-ui/**`, and verify an existing `SecurityConfig`/controller test (or a new one) confirms the endpoint is reachable without a bearer token while `/api/v1/**` still requires one.

## 2. `extension-backend` Dockerfile

- [ ] 2.1 Create `services/extension-backend/Dockerfile`: multi-stage build using `eclipse-temurin:25-jdk` (builder, `./gradlew bootJar -x test --no-daemon` with a `/root/.gradle` cache mount) and `eclipse-temurin:25-jre-alpine` (runtime), following the `LABEL`/non-root-`USER`/`HEALTHCHECK`/`EXPOSE` conventions already used in `webapp/Dockerfile` and `services/diagrams-backend/Dockerfile`. Verify `docker build -f services/extension-backend/Dockerfile .` (context: repo root, matching the other two Dockerfiles) succeeds and produces a runnable image.
- [ ] 2.2 Verify the built image starts and reports healthy standalone: `docker run` it with a reachable Postgres and `issuer-uri` set, then confirm `docker inspect --format='{{.State.Health.Status}}'` reaches `healthy`.

## 3. Compose: private network and Postgres

- [ ] 3.1 In `docker/compose.db.yml`, rename the Redis service `db` → `redis` (keep image/command/healthcheck/volume unchanged) and add the new `apollon-internal` network definition (`internal: true`, owned here like `compose.proxy.yml` owns `apollon-network`). Verify `docker compose -f docker/compose.db.yml config` parses cleanly.
- [ ] 3.2 Add a `postgres` service to `docker/compose.db.yml`: `postgres:17-alpine`, named volume (e.g. `pgdata:/var/lib/postgresql/data`), `pg_isready` healthcheck, `restart: unless-stopped`, `security_opt: [no-new-privileges:true]`, same `logging` block as `redis`, environment for `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` sourced from compose variables (no hardcoded credentials). Both `redis` and `postgres` join `apollon-internal` only (not `apollon-network`). Verify `docker compose -f docker/compose.db.yml up -d postgres` starts and reaches `healthy`.

## 4. Compose: `extension-backend` service and re-routing

- [ ] 4.1 In `docker/compose.app.yml`, declare `apollon-internal` as `external: true` (alongside the existing `apollon-network` external declaration).
- [ ] 4.2 Add the `extension-backend` service to `docker/compose.app.yml`: built from `services/extension-backend/Dockerfile`, joined to both `apollon-network` and `apollon-internal`, environment for `ISSUER_URI` (required, `${ISSUER_URI:?ISSUER_URI must be set}` style matching `ACME_EMAIL` in `compose.proxy.yml`), `app.cors.allowed-origins`, `app.diagrams-backend.base-url`/`ws-base-url` pointed at the internal `diagrams-backend` DNS name, and the Postgres datasource pointed at the internal `postgres` service. Give it the `traefik.*` labels currently on `server` for `PathPrefix(/api)`/`PathPrefix(/ws)` (renamed to `extension-backend`'s router/service names), plus the same `cap_drop`/`security_opt`/`logging`/`healthcheck` (using `/actuator/health`) conventions as `webapp`/`server`.
- [ ] 4.3 In `docker/compose.app.yml`, remove every `traefik.*` label from `diagrams-backend` (`server`), move it from `apollon-network` to `apollon-internal` only, and update its `REDIS_URL` to `redis://redis:6379`. Verify `docker compose -f docker/compose.proxy.yml -f docker/compose.db.yml -f docker/compose.app.yml config` parses cleanly with no duplicate/dangling network references.
- [ ] 4.4 Bring the full stack up locally (`docker compose -f docker/compose.proxy.yml -f docker/compose.db.yml -f docker/compose.app.yml up -d --build` with a self-signed/staging ACME setup or `APP_HOSTNAME` pointed at `localhost`) and verify: `extension-backend`'s `/api/v1/me` and `/ws/diagrams` are reachable through Traefik at the app hostname, `diagrams-backend` is NOT reachable from outside the Docker host or from the `webapp` container (e.g. `docker compose exec webapp wget -qO- http://diagrams-backend:8000/health` fails because `webapp` isn't on `apollon-internal`), and `extension-backend` can still reach `diagrams-backend` and `postgres` internally.

## 5. Environment documentation

- [ ] 5.1 Create `docker/.env.example` documenting every variable the three production compose files reference: `APP_HOSTNAME`, `ACME_EMAIL`, `OWNER_SECRET`, `IMAGE_TAG`, `CORS_ORIGIN`, `LEGAL_PROFILE`, `LIVE_UPDATE_DIR`, `ISSUER_URI`, `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`, and the webapp's `VITE_KEYCLOAK_AUTHORITY`/`VITE_KEYCLOAK_CLIENT_ID`/`VITE_EXTENSION_SERVER_URL` (documented as build-time webapp variables, not consumed by these compose files). Verify every `${VAR...}` reference across `docker/compose.*.yml` has a corresponding documented entry.
- [ ] 5.2 Update the comment in `webapp/src/constants/urls.ts` above `extensionServerURL` and the `docker` row description in `webapp/.env.example` that currently assume `diagrams-backend` is Traefik-routed at the webapp's origin in production — it no longer is.

## 6. Documentation

- [ ] 6.1 Update the root `README.md` architecture section (containers) and the `docker/` row of its file table to describe the new topology: `webapp` + `extension-backend` public via Traefik, `diagrams-backend`/Redis/Postgres on the private `apollon-internal` network, Keycloak external/decoupled (no compose entry). Remove the now-stale "Los compose actuales aún no aíslan el backend" note.
- [ ] 6.2 Update `services/extension-backend/README.md`: drop the "Estado: por construir... aún no hay código, `pom.xml` ni imagen Docker" banner (code and a Dockerfile now exist) and reflect that the gateway pattern (option A) is what's deployed.
- [ ] 6.3 Add a note (in `application-local.properties.example` or the design's referenced location) flagging `spring.jpa.hibernate.ddl-auto=update` as unreviewed for production, so a future change addresses schema migrations deliberately rather than by omission.
