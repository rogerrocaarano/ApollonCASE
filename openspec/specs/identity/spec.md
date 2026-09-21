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

### Requirement: Gestión del perfil propio
`extension-backend` SHALL let an authenticated user view their own profile: a display name and an email address. `displayName` SHALL start empty for a newly created `User` and stay empty until the user sets it — it is never inferred from the Keycloak token. `email` SHALL always reflect the `email` claim of the user's current Keycloak token, synced on every authenticated request; it is system-managed and not a value the user sets independently of their token.

#### Scenario: Nuevo usuario tiene displayName vacío y email desde el token
- **WHEN** un usuario se autentica con `extension-backend` por primera vez
- **THEN** su `User` se crea con `email` igual al claim `email` de su token de Keycloak
- **AND** su `displayName` queda vacío

#### Scenario: Usuario actualiza su nombre para mostrar
- **WHEN** un usuario autenticado envía un `displayName` no vacío
- **THEN** su perfil queda con ese `displayName`
- **AND** su `email` no se ve afectado por esta petición

#### Scenario: El email se mantiene sincronizado con el token
- **WHEN** el claim `email` del token de Keycloak de un usuario autenticado difiere del `email` almacenado
- **THEN** esa petición autenticada actualiza el `email` almacenado para que coincida con el token
- **AND** esto ocurre automáticamente, sin que el usuario envíe un email en el body de la petición

#### Scenario: El perfil ya no acepta un email enviado manualmente
- **WHEN** un usuario autenticado incluye un campo `email` en una petición de actualización de perfil
- **THEN** ese campo se ignora o la petición lo rechaza
- **AND** el `email` almacenado sigue siendo el que provino del token de Keycloak, sin validarse contra lo enviado

#### Scenario: El perfil actualizado se refleja en la identidad expuesta
- **WHEN** un usuario autenticado que ya tiene `displayName` configurado consulta `GET /api/v1/me`
- **THEN** la respuesta incluye ese `displayName`
- **AND** incluye el `email` sincronizado desde su token de Keycloak

### Requirement: La identidad de colaboración proviene del perfil del usuario
Al entrar a una sesión de diagrama colaborativa, la webapp SHALL identificar al usuario ante otros participantes (nombre y color de cursor) usando el `displayName` de su propio perfil, sin pedirle un nombre por sesión. Si el usuario no tiene `displayName` configurado, la webapp SHALL requerírselo antes de continuar, en vez de generarle uno o dejarlo sin identificar.

#### Scenario: Usuario con nombre configurado entra a colaborar
- **WHEN** un usuario autenticado con `displayName` ya configurado abre un diagrama en modo colaborativo
- **THEN** entra directamente a la sesión colaborativa
- **AND** otros participantes lo ven identificado con ese `displayName`

#### Scenario: Usuario sin nombre configurado debe completarlo antes de entrar
- **WHEN** un usuario autenticado sin `displayName` configurado abre un diagrama en modo colaborativo
- **THEN** la webapp le pide configurar un nombre para mostrar antes de continuar
- **AND** no puede confirmar sin proporcionar un nombre no vacío

#### Scenario: Usuario completa su nombre y continúa
- **WHEN** un usuario sin `displayName` configurado, al que se le pidió completarlo, guarda un nombre no vacío
- **THEN** entra a la sesión colaborativa identificado con ese nombre

#### Scenario: Usuario cierra el aviso sin completar su nombre
- **WHEN** un usuario sin `displayName` configurado cierra el aviso sin guardar un nombre
- **THEN** no entra a la sesión colaborativa

### Requirement: Webapp does not let a user edit their own email
Since `email` is system-managed and synced from the user's Keycloak token, the webapp SHALL NOT present an editable email field or submit an email value when updating the user's own profile. The webapp MAY display the current email as read-only information.

#### Scenario: Profile editor has no editable email field
- **WHEN** an authenticated user opens their profile editor
- **THEN** the webapp does not show an input the user can type into to change their email
- **AND** saving the profile does not send an email value to the server

#### Scenario: Email shown reflects the current token
- **WHEN** an authenticated user opens their profile editor
- **THEN** any email shown matches the `email` claim of their current session
