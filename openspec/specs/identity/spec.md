# identity

## Purpose

Identifica a los usuarios de ApollonCASE mediante Keycloak (OIDC) y los reconoce como `User` en `extension-backend`, sentando la base de identidad sobre la que se construyen proyectos y permisos.

## Requirements

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

#### Scenario: El control de cierre de sesión está disponible en toda vista autenticada
- **WHEN** un usuario autenticado está en cualquier vista de la webapp que requiere sesión — incluidas la lista de proyectos, el detalle de un proyecto, y el editor
- **THEN** esa vista ofrece un control alcanzable para cerrar sesión

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

### Requirement: extension-backend sincroniza el email del usuario desde el token
`extension-backend` SHALL keep each `User`'s `email` synced with the `email` claim of their current Keycloak token, updated on every authenticated request. `email` is system-managed: it is derived entirely from the token and is never a value the user sets independently of it. `extension-backend` does not store or serve any other user-editable profile field — a user's display name is not part of this `User` record (see the collaboration-identity requirement below).

#### Scenario: Nuevo usuario obtiene su email del token
- **WHEN** un usuario se autentica con `extension-backend` por primera vez
- **THEN** su `User` se crea con `email` igual al claim `email` de su token de Keycloak

#### Scenario: El email se mantiene sincronizado con el token
- **WHEN** el claim `email` del token de Keycloak de un usuario autenticado difiere del `email` almacenado
- **THEN** esa petición autenticada actualiza el `email` almacenado para que coincida con el token
- **AND** esto ocurre automáticamente, sin que el usuario envíe un email en el body de ninguna petición

### Requirement: La identidad de colaboración se deriva del token de Keycloak
Al entrar a una sesión de diagrama colaborativa, la webapp SHALL identificar al usuario ante otros participantes (nombre y color de cursor) derivando su nombre directamente de los claims `given_name` y `family_name` de su token de Keycloak vigente, concatenados. La webapp SHALL NOT pedir un nombre por sesión, SHALL NOT almacenar ese nombre, y SHALL NOT bloquear la entrada a la sesión colaborativa a la espera de que el usuario complete un perfil.

#### Scenario: Usuario entra a colaborar inmediatamente
- **WHEN** un usuario autenticado abre un diagrama en modo colaborativo
- **THEN** entra directamente a la sesión colaborativa, sin ningún aviso o modal previo
- **AND** otros participantes lo ven identificado con el nombre derivado de `given_name` y `family_name` de su token

#### Scenario: El nombre de colaboración refleja el token vigente
- **WHEN** el `given_name` o `family_name` del token de Keycloak de un usuario cambia entre dos sesiones colaborativas
- **THEN** en la sesión nueva ese usuario aparece identificado con el nombre actualizado, sin acción adicional de su parte

### Requirement: Webapp does not let a user edit their own identity
Since a user's `email` and display name are both derived entirely from their Keycloak token, the webapp SHALL NOT present any editable field for either, and SHALL NOT submit either value to `extension-backend` when identifying the user. The webapp MAY display the current email and the computed display name as read-only information.

#### Scenario: No hay pantalla de edición de perfil
- **WHEN** an authenticated user looks for a way to change their display name or email within the webapp
- **THEN** the webapp offers no such editable control

#### Scenario: El nombre y el email mostrados reflejan la sesión vigente
- **WHEN** an authenticated user views their own account information
- **THEN** the display name shown matches `given_name` + `family_name` from their current Keycloak token
- **AND** the email shown matches the `email` claim of that same token
