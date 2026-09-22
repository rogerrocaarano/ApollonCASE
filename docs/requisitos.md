# Especificación de requisitos de ApollonCASE

## 1. Introducción

ApollonCASE es una herramienta CASE colaborativa para modelar sistemas de software con diagramas UML y generar a partir de ellos artefactos de implementación. Cubre el modelado de datos en su totalidad: modelo conceptual (diagramas de clases), modelo lógico normalizado y modelo físico en PostgreSQL.

**Convenciones**

- **ID**: identificador secuencial (`RF001…` funcionales, `RNF001…` no funcionales).
- **Prioridad**: *Crítico* (sin esto el sistema no cumple su objetivo), *Importante*, *Secundario*.
- *(por definir)*: el requisito tiene decisiones abiertas (ver [sección 4](#4-decisiones-pendientes)).
- *(por confirmar)*: el requisito es una propuesta que aún no está aprobada.

## 2. Requisitos funcionales

| ID | Categoría | Nombre del requisito | Prioridad |
| --- | --- | --- | --- |
| RF001 | Modelado de datos | Modelado conceptual mediante diagramas de clases UML (y demás tipos de diagrama soportados por el editor) | Crítico |
| RF002 | Modelado de datos | Derivación del modelo lógico (tablas, claves y relaciones) a partir del modelo conceptual | Crítico |
| RF003 | Modelado de datos | Normalización del modelo lógico: detección de incumplimientos de las formas normales y propuesta o aplicación de la corrección | Importante |
| RF004 | Modelado de datos | Gestión de proyectos como contenedor de diagramas (crear, renombrar, archivar, eliminar) | Importante |
| RF005 | Modelado de datos | Creación y modificación de diagramas mediante lenguaje natural (texto y voz) con revisión y confirmación previa *(por definir)* | Importante |
| RF006 | Interoperabilidad | Importación de modelos y diagramas desde otras herramientas, empezando por Enterprise Architect *(por definir)* | Importante |
| RF007 | Interoperabilidad | Exportación de modelos y diagramas para su uso en otras herramientas *(por definir)* | Importante |
| RF008 | Generación de artefactos | Generación de la base de datos PostgreSQL (scripts DDL y migraciones) a partir del modelo | Crítico |
| RF009 | Generación de artefactos | Generación de un backend Spring Boot (entidades JPA, repositorios, servicios, controladores REST, DTOs, validaciones y configuración) a partir de diagramas de clases | Crítico |
| RF010 | Generación de artefactos | Generación de la especificación OpenAPI de la API generada | Crítico |
| RF011 | Colaboración | Edición colaborativa de diagramas en tiempo real | Crítico |
| RF012 | Colaboración | Gestión de permisos por usuario y por proyecto (propietario, colaborador, lector), con herencia a los diagramas, invitaciones y revocación | Importante |
| RF013 | Colaboración | Congelamiento de clases y diagramas: impedir su modificación hasta que se descongelen *(por definir)* | Importante |
| RF014 | Colaboración | Almacenamiento y versionado de diagramas | Secundario |
| RF015 | Adicionales | Auditoría e historial de cambios por usuario y por proyecto *(por confirmar)* | Secundario |
| RF016 | Adicionales | Exportación de un proyecto completo en ZIP, con diagramas y código generado *(por confirmar)* | Secundario |
| RF017 | Adicionales | Plantillas de proyecto *(por confirmar)* | Secundario |
| RF018 | Adicionales | Roles a nivel de organización o equipo *(por confirmar)* | Secundario |

## 3. Requisitos no funcionales

| ID | Categoría (ISO/IEC 25010) | Nombre del requisito | Prioridad |
| --- | --- | --- | --- |
| RNF001 | Seguridad | Un usuario solo puede leer o modificar los proyectos y diagramas que sus permisos le permiten; al revocar un permiso, el acceso se corta también en las sesiones de edición colaborativa en curso | Crítico |
| RNF002 | Usabilidad | Ningún cambio propuesto por IA se aplica al diagrama sin confirmación previa del usuario | Importante |
| RNF003 | Compatibilidad | El proyecto generado debe compilar sin errores ni intervención manual en un entorno Java compatible con la versión de Java y de Spring Boot declaradas en el propio proyecto | Importante |
| RNF004 | Privacidad | Las funciones de IA deben ejecutarse con un modelo local, sin enviar los modelos ni los mensajes del usuario a servicios externos | Importante |
