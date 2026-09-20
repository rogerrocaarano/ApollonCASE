# Proposal

## Why

Los roadmap #2 (proyectos) y #3 (permisos) necesitan saber quién es cada usuario, y hoy no existe forma de averiguarlo: la webapp no tiene login y `extension-backend`, aunque ya declara la dependencia `oauth2-resource-server` y su `SecurityConfig` exige un JWT válido en cualquier ruta salvo Swagger, no tiene ningún emisor de tokens configurado (`issuer-uri` es un placeholder) ni ningún flujo real para obtener uno. Sin esto, `extension-backend` es inalcanzable y no hay identidad sobre la que construir proyectos o permisos.

## What Changes

- Tratar Keycloak como servicio externo: ya existe un realm de pruebas, así que este cambio no añade contenedores de Keycloak al stack, solo la configuración (issuer-uri, client id de la webapp, redirect URIs) para apuntar a ese realm por entorno.
- Webapp: login/logout OIDC contra Keycloak, con renovación de sesión en segundo plano (silent refresh) mientras la sesión de Keycloak siga viva, y sin exigir recarga de página al expirar el access token.
- Webapp: adjuntar el access token en las peticiones hacia `extension-backend`.
- `extension-backend`: reemplazar el placeholder de `issuer-uri` por configuración real de Keycloak por entorno, y exponer un endpoint autenticado (`GET /api/v1/me`) que registra/recupera el `User` a partir del `sub` del token, usando `UsersService.trackKeycloakUser` (ya implementado) — esto valida el flujo end-to-end.
- Fuera de alcance de este cambio: el proxy de `extension-backend` hacia `diagrams-backend`, los permisos por proyecto y el aislamiento de red de `diagrams-backend` (roadmap #3, que construye sobre esta identidad). El acceso anónimo queda descartado explícitamente: la aplicación no se comparte externamente, así que todo acceso a `extension-backend` exige login.

## Capabilities

### New Capabilities
- `identity`: autenticación de usuarios de ApollonCASE contra Keycloak (OIDC) y su reconocimiento como `User` en `extension-backend`.

### Modified Capabilities
(ninguna — no hay specs existentes en el proyecto)

## Impact

- Ningún cambio en `docker/`: Keycloak es externo (realm de pruebas ya existente) y `extension-backend` no está hoy conectado a ningún script ni compose de este repo (se ejecuta aparte, p. ej. desde el IDE) — este cambio no lo añade a `scripts/dev.*` ni `scripts/docker-up.*`.
- `services/extension-backend`: `application.properties` / `application-local.properties(.example)` (issuer-uri real del realm externo, client id esperado), `UsersController` (hoy vacío; nuevo endpoint `/me`), y el mecanismo que conecta el JWT autenticado con `UsersService.trackKeycloakUser`.
- `webapp`: nueva dependencia de cliente OIDC, nuevo provider de auth integrado en `AppProviders.tsx`, nuevas variables `VITE_*` con los datos del realm/client de Keycloak, y cabecera `Authorization` en las llamadas hacia `extension-backend` (no hacia `diagrams-backend`, que la webapp sigue llamando directamente en este cambio).
