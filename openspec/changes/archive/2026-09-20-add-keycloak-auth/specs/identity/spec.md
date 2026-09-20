# Spec Delta

## Purpose

Identifica a los usuarios de ApollonCASE mediante Keycloak (OIDC) y los reconoce como `User` en `extension-backend`, sentando la base de identidad sobre la que se construyen proyectos y permisos.

## ADDED Requirements

### Requirement: Inicio de sesión con Keycloak
La webapp SHALL exigir un inicio de sesión OIDC contra Keycloak antes de permitir el uso de cualquier funcionalidad que dependa de `extension-backend`.

#### Scenario: Usuario no autenticado abre la webapp
- **WHEN** un usuario sin sesión activa abre la webapp
- **THEN** es redirigido al login de Keycloak
- **AND** tras introducir credenciales válidas, vuelve a la webapp con una sesión autenticada

#### Scenario: Credenciales inválidas
- **WHEN** un usuario introduce credenciales inválidas en Keycloak
- **THEN** Keycloak rechaza el login
- **AND** la webapp no obtiene una sesión autenticada

### Requirement: Cierre de sesión
La webapp SHALL permitir cerrar sesión, terminando tanto la sesión local como la sesión de Keycloak (logout SSO).

#### Scenario: Usuario cierra sesión
- **WHEN** un usuario autenticado cierra sesión desde la webapp
- **THEN** su sesión de Keycloak se termina
- **AND** peticiones posteriores de la webapp hacia `extension-backend` dejan de incluir un token válido

### Requirement: Renovación de sesión sin interrupción
Mientras la sesión de Keycloak del usuario siga viva, la webapp SHALL renovar el access token en segundo plano antes de que expire, sin interrumpir al usuario ni forzar una recarga de página.

#### Scenario: Access token próximo a expirar con sesión de Keycloak activa
- **WHEN** el access token de un usuario autenticado está a punto de expirar
- **AND** su sesión de Keycloak sigue activa
- **THEN** la webapp obtiene un nuevo access token en segundo plano
- **AND** el usuario continúa su trabajo sin verse interrumpido

#### Scenario: Renovación falla porque la sesión de Keycloak ya no es válida
- **WHEN** la webapp intenta renovar el access token en segundo plano
- **AND** la sesión de Keycloak ya no es válida (p. ej. expiró o fue revocada)
- **THEN** la renovación falla
- **AND** el usuario es redirigido al login de Keycloak

### Requirement: extension-backend exige autenticación
`extension-backend` SHALL rechazar cualquier petición que no incluya un JWT válido emitido por el realm de Keycloak configurado, salvo la documentación de la API.

#### Scenario: Petición sin token
- **WHEN** llega una petición a `extension-backend` sin cabecera `Authorization` (fuera de las rutas de documentación de la API)
- **THEN** `extension-backend` responde 401 y no concede ningún acceso

#### Scenario: Petición con token inválido o de otro emisor
- **WHEN** llega una petición con un JWT que no está firmado por el realm de Keycloak configurado, o que ha expirado
- **THEN** `extension-backend` responde 401

#### Scenario: Petición con token válido
- **WHEN** llega una petición con un JWT válido, no expirado y firmado por el realm de Keycloak configurado
- **THEN** `extension-backend` acepta la petición y la identifica con el `sub` del token

### Requirement: Reconocimiento del usuario en extension-backend
Al recibir una petición autenticada, `extension-backend` SHALL registrar o recuperar el `User` correspondiente al `sub` del JWT, de forma idempotente.

#### Scenario: Primera petición autenticada de un usuario
- **WHEN** `extension-backend` recibe una petición autenticada cuyo `sub` no corresponde a ningún `User` existente
- **THEN** crea un nuevo `User` asociado a ese `sub`

#### Scenario: Petición autenticada de un usuario ya conocido
- **WHEN** `extension-backend` recibe una petición autenticada cuyo `sub` ya corresponde a un `User` existente
- **THEN** reutiliza ese `User` sin crear un duplicado

### Requirement: Verificación end-to-end de la identidad
`extension-backend` SHALL exponer un endpoint autenticado que devuelva la identidad del usuario que hace la petición, permitiendo comprobar el flujo completo de autenticación.

#### Scenario: Usuario autenticado consulta su identidad
- **WHEN** un usuario autenticado hace una petición `GET /api/v1/me`
- **THEN** `extension-backend` responde con los datos del `User` asociado a su token

#### Scenario: Usuario no autenticado consulta su identidad
- **WHEN** una petición sin token llega a `GET /api/v1/me`
- **THEN** `extension-backend` responde 401
