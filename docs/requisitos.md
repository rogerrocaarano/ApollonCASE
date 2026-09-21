# Especificación de requisitos de ApollonCASE

## 1. Introducción

ApollonCASE es una herramienta CASE para modelar sistemas de software con diagramas UML y generar a partir de ellos artefactos de implementación. Este documento recoge los requisitos del sistema en fase de análisis: describe qué debe hacer, sin prescribir cómo se diseña o despliega. El estado de cumplimiento de cada requisito se cubrirá en un anexo.

**Convenciones**

- **ID**: identificador secuencial (`RF001…` funcionales, `RNF001…` no funcionales).
- **Prioridad**: *Crítico* (sin esto el sistema no cumple su objetivo), *Importante*, *Secundario*.
- *(por definir)*: el requisito tiene decisiones abiertas (ver [sección 4](#4-decisiones-pendientes)).
- *(por confirmar)*: el requisito es una propuesta que aún no está aprobada.

## 2. Requisitos funcionales

| ID | Categoría | Nombre del requisito | Prioridad |
| --- | --- | --- | --- |
| RF001 | Modelos UML | Edición de diagramas de clase UML (y demás tipos de diagrama soportados por el editor) | Crítico |
| RF002 | Modelos UML | Gestión de proyectos como contenedor de diagramas (crear, renombrar, archivar, eliminar) | Importante |
| RF003 | Modelos UML | Creación y modificación de diagramas mediante lenguaje natural (texto y voz) con revisión y confirmación previa *(por definir)* | Importante |
| RF004 | Modelos UML | Importación de modelos UML desde Enterprise Architect *(por definir)* | Secundario |
| RF005 | Generación de artefactos | Generación de un proyecto Spring Boot (entidades JPA, repositorios, migraciones, configuración) a partir de diagramas de clase | Crítico |
| RF006 | Generación de artefactos | Generación de una API REST (servicios, controladores, DTOs, validaciones) con documentación OpenAPI | Crítico |
| RF007 | Colaboración | Edición colaborativa de diagramas en tiempo real | Importante |
| RF008 | Colaboración | Gestión de permisos por usuario y por proyecto (propietario, colaborador, lector), con herencia a los diagramas, invitaciones y revocación | Importante |
| RF009 | Colaboración | Almacenamiento y versionado de diagramas | Secundario |
| RF010 | Adicionales | Auditoría e historial de cambios por usuario y por proyecto *(por confirmar)* | Secundario |
| RF011 | Adicionales | Exportación de un proyecto completo en ZIP, con diagramas y código generado *(por confirmar)* | Secundario |
| RF012 | Adicionales | Plantillas de proyecto *(por confirmar)* | Secundario |
| RF013 | Adicionales | Roles a nivel de organización o equipo *(por confirmar)* | Secundario |

## 3. Requisitos no funcionales

| ID | Categoría (ISO/IEC 25010) | Nombre del requisito | Prioridad |
| --- | --- | --- | --- |
| RNF001 | Seguridad | Un usuario solo puede leer o modificar los proyectos y diagramas que sus permisos le permiten; al revocar un permiso, el acceso se corta también en las sesiones de edición colaborativa en curso | Crítico |
| RNF002 | Usabilidad | Ningún cambio propuesto por IA se aplica al diagrama sin confirmación previa del usuario | Importante |
| RNF003 | Fiabilidad | El proyecto generado debe compilar y ejecutarse sin intervención manual *(por confirmar)* | Importante |

## 4. Decisiones pendientes

- Proveedor de IA y de transcripción de voz (RF003).
- Formato de intercambio y alcance de la importación desde Enterprise Architect (RF004).
- Si los diagramas existentes fuera de un proyecto (locales o compartidos por enlace) se migran a proyectos o se mantienen como "sueltos" (RF002).
