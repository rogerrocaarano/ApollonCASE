# Spec Delta

## ADDED Requirements

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
