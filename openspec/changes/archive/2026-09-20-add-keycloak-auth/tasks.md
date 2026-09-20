# Tasks

## 1. Configuración de Keycloak (externo)

- [x] 1.1 Confirmar en el realm de pruebas "ApollonCASE" que existe un client público para la webapp (Standard Flow + PKCE, sin secreto) con las redirect URIs de cada entorno de trabajo, y verificar que `<issuer>/.well-known/openid-configuration` responde correctamente para ese realm.
- [x] 1.2 Documentar el issuer-uri y el client id en `application-local.properties.example` (backend) y en el `.env.example` de la webapp, sin incluir credenciales secretas.

## 2. extension-backend: validación de JWT y resolución de usuario

- [x] 2.1 Configurar `spring.security.oauth2.resourceserver.jwt.issuer-uri` real por entorno y verificar que la aplicación arranca sin errores de resolución de metadatos OIDC contra el realm externo.
- [x] 2.2 Añadir configuración CORS explícita para el origen de la webapp por entorno y verificar con una petición preflight (OPTIONS) desde el origen de desarrollo que la respuesta la permite.
- [x] 2.3 Implementar la resolución/registro del `User` en cada petición autenticada (tras el filtro del resource server, usando `JwtExtensions.requiredSubject` + `UsersService.trackKeycloakUser`) y verificar con un test de integración que una primera petición con JWT válido crea el `User` y una segunda petición con el mismo `sub` reutiliza el mismo `User` sin duplicarlo.
- [x] 2.4 Implementar `GET /api/v1/me` en `UsersController` devolviendo el `User` resuelto de la petición, y verificar con tests que responde 200 con los datos del usuario autenticado y 401 sin token o con token inválido.

## 3. Webapp: login, logout y sesión persistente

- [x] 3.1 Añadir `oidc-client-ts` y `react-oidc-context` como dependencias y verificar que `npm install` y el build de la webapp siguen funcionando.
- [x] 3.2 Añadir las variables `VITE_KEYCLOAK_*` (URL/realm, client id, redirect URI) siguiendo el patrón de `webapp/src/environment`, y verificar que se leen correctamente en dev y en build de producción.
- [x] 3.3 Integrar el `AuthProvider` en `AppProviders.tsx` con renovación automática basada en refresh token (`automaticSilentRenew`, sin iframe), y verificar manualmente que abrir la webapp sin sesión redirige al login de Keycloak.
- [x] 3.4 Añadir el gate de login en la raíz del árbol de rutas (bloquea el render de cualquier página hasta tener sesión autenticada) y verificar que ninguna ruta protegida renderiza contenido para un usuario no autenticado.
- [x] 3.5 Añadir una acción de logout (SSO) accesible desde la UI y verificar que, tras cerrar sesión, recargar la webapp vuelve a exigir login.
- [x] 3.6 Adjuntar el access token como header `Authorization` en las llamadas hacia `extension-backend` (no hacia `diagrams-backend`, que sigue sin tocarse) y verificar con una llamada real a `GET /api/v1/me` que la webapp muestra el usuario autenticado.

## 4. Verificación end-to-end

- [x] 4.1 Con `extension-backend` corriendo contra el realm de pruebas y la webapp apuntando a él, verificar manualmente el flujo completo: login → la webapp llama a `GET /api/v1/me` y muestra el usuario → forzar la expiración del access token y comprobar que la sesión se renueva sin interrumpir al usuario → logout → confirmar que las llamadas posteriores a `extension-backend` devuelven 401.
- [x] 4.2 Ejecutar `./gradlew test` en `extension-backend` y `npm run test --workspace=@tumaet/webapp`, y verificar que ambas suites pasan.
