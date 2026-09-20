# Design

## Context

`extension-backend` ya trae `spring-boot-starter-oauth2-resource-server` y una `SecurityConfig` que exige JWT en cualquier ruta salvo `/v3/api-docs/**` y `/swagger-ui/**`; solo falta que `issuer-uri` apunte a un emisor real (hoy es un placeholder). `OpenApiConfig` ya documenta el bearer auth y menciona explícitamente "JWT emitido por Keycloak (realm ApollonCASE)", así que el nombre del realm ya está decidido. `UsersService.trackKeycloakUser(keycloakId)` ya existe (upsert por `keycloakId`); `UsersController` está vacío. La webapp (React 19 + TanStack Router/Query) no tiene ninguna dependencia OIDC todavía. Keycloak se trata como servicio externo ya desplegado con un realm de pruebas — este cambio no despliega ni configura Keycloak en sí, solo apunta a él.

## Goals / Non-Goals

**Goals:**
- La webapp exige login OIDC contra el realm externo antes de exponer funcionalidad que dependa de `extension-backend`.
- La sesión se renueva sola mientras la sesión de Keycloak siga viva (sin recarga de página).
- `extension-backend` valida el JWT contra el realm externo y asocia cada petición autenticada a un `User`, de forma reutilizable por cualquier endpoint futuro (no solo `/me`).

**Non-Goals:**
- Desplegar, configurar o administrar Keycloak (realm, clients, usuarios) — es un servicio externo ya existente.
- Proxy de `extension-backend` hacia `diagrams-backend`, permisos por proyecto, o cambios en cómo la webapp llama hoy a `diagrams-backend` (sigue siendo directo y sin token).
- Acceso anónimo restringido — descartado por decisión de producto (la app no se comparte externamente).

## Decisions

### Cliente OIDC en la webapp: `oidc-client-ts` + `react-oidc-context`
Se usa una librería OIDC estándar (Authorization Code + PKCE, client público sin secreto) en lugar del adaptador `keycloak-js`. `keycloak-js` está marcado como deprecado por el propio proyecto Keycloak a favor de librerías OIDC genéricas; `oidc-client-ts` es la base recomendada y `react-oidc-context` da hooks/`AuthProvider` listos para integrarse en `AppProviders.tsx` sin más infraestructura. Alternativa considerada: `keycloak-js` — descartada por su deprecación upstream y por acoplar la webapp a la API específica de Keycloak en vez de a OIDC estándar.

### Renovación de sesión: refresh token, sin iframe
`automaticSilentRenew` se configura para renovar con el refresh token emitido en el login (flujo estándar, sin necesitar una página `silent-renew.html` en un iframe). Evita problemas de cookies de terceros/iframes bloqueados por el navegador, que sí afectan al enfoque de "check-sso" vía iframe. Requiere que el client de Keycloak emita refresh tokens (comportamiento por defecto de "Standard Flow"); es una restricción sobre la configuración del realm externo, no sobre este código. Si el refresh token expira o es inválido (sesión de Keycloak muerta), la renovación falla y el usuario es enviado a login, tal como describe el spec.

### La sesión se exige a nivel de shell de la app, no por-llamada
El gate de login vive en la raíz del árbol de rutas (antes de renderizar cualquier página), no repartido en cada componente que llame a `extension-backend`. Es el punto natural para colgar proyectos/permisos (roadmap #2/#3) y evita el estado intermedio de "app parcialmente usable sin sesión". Alternativa considerada: exigir login solo cuando se llama a `extension-backend` (p. ej. al abrir el perfil) — descartada porque este cambio existe precisamente para establecer identidad antes de construir sobre ella, y dejar el resto de la app "anónima primero" iría contra esa intención.

### Keycloak es configuración de entorno, no infraestructura de este repo
No se toca `docker/` ni los compose de producción. `extension-backend` recibe `issuer-uri` (y la webapp sus equivalentes `VITE_KEYCLOAK_*`: URL del realm y client id) vía variables de entorno por despliegue, igual que ya hace `application-local.properties` (gitignored) con el datasource. El client de la webapp es público (PKCE, sin secreto), así que no hay secretos de Keycloak que gestionar en este repo.

### Registrar el usuario en cada petición autenticada, no solo en `/me`
En vez de llamar a `trackKeycloakUser` dentro del controlador de `/me`, se resuelve una vez por petición (p. ej. en un filtro que corre después del filtro de OAuth2 resource server, leyendo el `Jwt` del `SecurityContext`) y se expone el `User` resultante a los controladores. Así cualquier endpoint autenticado que se añada después (proyectos, permisos) obtiene el `User` ya resuelto sin repetir esta lógica. Alternativa considerada: solo trackear en `/me` — descartada porque el spec exige el reconocimiento en "cualquier petición autenticada", no solo en el endpoint de verificación, y porque repetir la llamada en cada controlador futuro es el tipo de trabajo que conviene resolver una vez.

### CORS en `extension-backend`
El scaffold actual no declara CORS. Hace falta una configuración explícita de orígenes permitidos (equivalente al `CORS_ORIGIN` que ya usa `diagrams-backend`) para que la webapp pueda llamar a `extension-backend` desde otro origen/puerto; se resuelve por variable de entorno, igual que el resto de configuración por entorno de este cambio.

## Risks / Trade-offs

- **Refresh token en el navegador** → mitigado usando almacenamiento en memoria/`sessionStorage` (no `localStorage`), access tokens de vida corta y client público con PKCE (sin secreto que filtrar).
- **El realm/client de Keycloak vive fuera de este repo** → si su configuración cambia (redirect URIs, lifetimes) sin avisar, el login puede romperse sin que el cambio se vea en este repositorio; se documentan en tasks.md los valores exactos que `extension-backend`/webapp esperan del realm, para poder verificarlos contra el realm de pruebas.
- **CORS mal configurado bloquea todo** → cubierto explícitamente como parte de las tareas, no como detalle implícito de "añadir Spring Security".
- **Sin acceso anónimo** → toda la app pasa a depender de que el Keycloak externo esté disponible; aceptado porque la app no se comparte externamente (decisión de producto ya tomada).

## Migration Plan

No hay datos que migrar (funcionalidad nueva). Despliegue:
1. Confirmar en el realm de pruebas ("ApollonCASE") que existe un client público para la webapp con Standard Flow + PKCE y las redirect URIs de cada entorno.
2. Configurar `issuer-uri` en `extension-backend` (por entorno) y desplegarlo con el gate de autenticación activo.
3. Configurar las variables `VITE_KEYCLOAK_*` de la webapp y desplegarla con el gate de login activo.

Rollback: revertir el gate de login de la webapp y la dependencia de `issuer-uri` real en `extension-backend` — ninguna otra parte del sistema depende todavía de `extension-backend`, así que el rollback no afecta a diagramas ni a datos existentes.

## Open Questions

- Valores exactos del client de Keycloak en el realm de pruebas (client id, redirect URIs por entorno, tiempos de vida de token) — se confirman contra el realm existente al implementar; no cambian el enfoque ni las specs, solo la configuración.
