# Spec Delta

## MODIFIED Requirements

### Requirement: Gestión del perfil propio
`extension-backend` SHALL let an authenticated user view their own profile: a display name and an email address. `displayName` SHALL start empty for a newly created `User` and stay empty until the user sets it — it is never inferred from the Keycloak token. `email` SHALL always reflect the `email` claim of the user's current Keycloak token, synced on every authenticated request; it is system-managed and not a value the user sets independently of their token.

#### Scenario: Nuevo usuario tiene el perfil vacío
- **WHEN** un usuario se autentica con `extension-backend` por primera vez
- **THEN** su `User` se crea con `email` igual al claim `email` de su token de Keycloak
- **AND** su `displayName` queda vacío

#### Scenario: Usuario actualiza su nombre para mostrar
- **WHEN** un usuario autenticado envía un `displayName` no vacío
- **THEN** su perfil queda con ese `displayName`
- **AND** su `email` no se ve afectado por esta petición

#### Scenario: Usuario actualiza su email
- **WHEN** el claim `email` del token de Keycloak de un usuario autenticado difiere del `email` almacenado
- **THEN** esa petición autenticada actualiza el `email` almacenado para que coincida con el token
- **AND** esto ocurre automáticamente, sin que el usuario envíe un email en el body de la petición

#### Scenario: Email con formato inválido es rechazado
- **WHEN** un usuario autenticado incluye un campo `email` en una petición de actualización de perfil
- **THEN** ese campo se ignora o la petición lo rechaza
- **AND** el `email` almacenado sigue siendo el que provino del token de Keycloak, sin validarse contra lo enviado

#### Scenario: El perfil actualizado se refleja en la identidad expuesta
- **WHEN** un usuario autenticado que ya tiene `displayName` configurado consulta `GET /api/v1/me`
- **THEN** la respuesta incluye ese `displayName`
- **AND** incluye el `email` sincronizado desde su token de Keycloak
