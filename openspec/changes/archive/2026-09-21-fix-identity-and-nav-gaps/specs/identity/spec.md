# Spec Delta

## MODIFIED Requirements

### Requirement: Cierre de sesión
La webapp SHALL permitir cerrar sesión, terminando tanto la sesión local como la sesión de Keycloak (logout SSO).

#### Scenario: Usuario cierra sesión
- **WHEN** un usuario autenticado cierra sesión desde la webapp
- **THEN** su sesión de Keycloak se termina
- **AND** peticiones posteriores de la webapp hacia `extension-backend` dejan de incluir un token válido

#### Scenario: El control de cierre de sesión está disponible en toda vista autenticada
- **WHEN** un usuario autenticado está en cualquier vista de la webapp que requiere sesión — incluidas la lista de proyectos, el detalle de un proyecto, y el editor
- **THEN** esa vista ofrece un control alcanzable para cerrar sesión

## REMOVED Requirements

### Requirement: Gestión del perfil propio
**Reason**: `displayName` stops being a stored or user-editable field — it's now derived on the fly from the Keycloak token's `given_name`/`family_name` claims and never round-tripped through `extension-backend`. The email-sync behavior this requirement also described is preserved under the new "extension-backend sincroniza el email del usuario desde el token" requirement.
**Migration**: None. `PATCH /api/v1/me` is removed and `GET /api/v1/me`'s response drops `displayName`; the only consumer (the webapp) is updated in this same change.

### Requirement: La identidad de colaboración proviene del perfil del usuario
**Reason**: The stored-profile-plus-gate model (prompt to complete a profile before collaborating) is replaced by deriving the name directly from the current Keycloak token, which is always available. See the new "La identidad de colaboración se deriva del token de Keycloak" requirement.
**Migration**: None — internal webapp behavior, no external contract change.

### Requirement: Webapp does not let a user edit their own email
**Reason**: Superseded by a broader requirement covering both email and display name, since neither is editable anymore. See the new "Webapp does not let a user edit their own identity" requirement.
**Migration**: None.

## ADDED Requirements

### Requirement: extension-backend sincroniza el email del usuario desde el token
`extension-backend` SHALL keep each `User`'s `email` synced with the `email` claim of their current Keycloak token, updated on every authenticated request. `email` is system-managed: it is derived entirely from the token and is never a value the user sets independently of it. `extension-backend` does not store or serve any other user-editable profile field.

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
