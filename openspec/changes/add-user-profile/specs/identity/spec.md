# Spec Delta

## ADDED Requirements

### Requirement: Gestión del perfil propio
`extension-backend` SHALL let an authenticated user view and edit their own profile: a display name and an email address. Both fields start empty for a newly created `User` and are never inferred from the Keycloak token — they stay empty until the user sets them.

#### Scenario: Nuevo usuario tiene el perfil vacío
- **WHEN** un usuario autenticado que nunca ha editado su perfil consulta `GET /api/v1/me`
- **THEN** el `displayName` y el `email` de la respuesta están vacíos

#### Scenario: Usuario actualiza su nombre para mostrar
- **WHEN** un usuario autenticado envía un `displayName` no vacío
- **THEN** su perfil queda con ese `displayName`
- **AND** su `email` no se modifica si no se incluyó en la petición

#### Scenario: Usuario actualiza su email
- **WHEN** un usuario autenticado envía un `email` con formato válido
- **THEN** su perfil queda con ese `email`
- **AND** su `displayName` no se modifica si no se incluyó en la petición

#### Scenario: Email con formato inválido es rechazado
- **WHEN** un usuario autenticado envía un `email` que no tiene formato de correo válido
- **THEN** el perfil no se modifica
- **AND** la petición es rechazada

#### Scenario: El perfil actualizado se refleja en la identidad expuesta
- **WHEN** un usuario autenticado que ya tiene `displayName` y/o `email` configurados consulta `GET /api/v1/me`
- **THEN** la respuesta incluye esos valores
