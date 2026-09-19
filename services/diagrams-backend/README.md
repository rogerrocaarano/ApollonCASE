# @tumaet/server (`services/diagrams-backend`)

Servidor de [Apollon](../../README.md) para diagramas UML. Es el antiguo `standalone/server` del repositorio original, sin modificar. Ofrece:

- **API HTTP** (Hono, puerto `8000`): crear, leer, sobrescribir y borrar diagramas; historial de versiones; exportación a SVG, PNG y PDF; y páginas y vistas previas incrustables.
- **Relay WebSocket** (`ws`, puerto `4444`): retransmite las actualizaciones Yjs y el awareness entre los clientes de un mismo diagrama para la colaboración en tiempo real.
- **Redis con RedisJSON** como almacenamiento. Se requiere `redis/redis-stack-server` (el servidor se niega a arrancar sin el módulo ReJSON; `redis:alpine` no sirve).

Depende del workspace [`@tumaet/apollon`](../../library) (renderizado de exportaciones y protocolo de sincronización), que debe estar compilado (`npm run build --workspace=@tumaet/apollon`).

> **⚠️ Este servicio no tiene autenticación.** Conocer el `diagramId` (128 bits aleatorios) basta para leer, sobrescribir y borrar el diagrama (`GET/PUT/DELETE /api/diagrams/:diagramId`) y para unirse a su sala WebSocket (`?diagramId=…`). La cookie de propietario (`apollon_owner_<id>`, HMAC con `OWNER_SECRET`) es solo "fricción, no seguridad": nunca bloquea peticiones. El relay tampoco distingue lectores de editores. Por eso **no debe exponerse directamente a usuarios** cuando existan permisos; la arquitectura objetivo lo deja en una red privada detrás de [`extension-backend`](../extension-backend/README.md). Este servicio se mantiene sin modificaciones.

## Rutas

Todas bajo `/api` salvo indicación:

| Método | Ruta | Descripción |
| --- | --- | --- |
| `POST` | `/api/diagrams` | Crea un diagrama y emite la cookie de propietario. |
| `GET` `PUT` `DELETE` | `/api/diagrams/:diagramId` | Lee (renueva el TTL), sobrescribe (admite `If-Match` con `headRev`) o borra en cascada. |
| `GET` `POST` | `/api/diagrams/:diagramId/versions` | Lista o crea versiones (manuales y automáticas). |
| `GET` `PATCH` `DELETE` | `/api/diagrams/:diagramId/versions/:versionId` | Lee, renombra o elimina una versión. |
| `POST` | `/api/diagrams/:diagramId/versions/:versionId/restore` | Restaura una versión. |
| `POST` | `/api/converter/svg` `png` `pdf` | Convierte un modelo a SVG, PNG o PDF (`GET /api/converter/status` para comprobar). Costoso: no exponer sin límites. |
| `GET` `HEAD` | `/api/diagrams/:diagramId/preview.svg` | Vista previa SVG en caché. |
| `GET` `HEAD` | `/embed/:diagramId` | Página HTML renderizada en el servidor, apta para `<iframe>`. |
| `GET` | `/health`, `/health/ready` | Comprobaciones de salud. |

Las definiciones están en [`src/routes`](src/routes); el relay WebSocket, en [`src/ws.ts`](src/ws.ts).

## Configuración

Variables de entorno (se leen de `.env` si existe, sin pisar las ya definidas; ver [`.env.example`](.env.example) y [`src/config.ts`](src/config.ts)):

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `HOST` | `localhost` | Interfaz de escucha (usa `0.0.0.0` en contenedores). |
| `PORT` | `8000` | Puerto HTTP. |
| `WS_PORT` | `4444` | Puerto del relay WebSocket. |
| `CORS_ORIGIN` | — | Orígenes permitidos separados por comas. |
| `REDIS_URL` | `redis://localhost:6379` | Conexión a Redis Stack. |
| `OWNER_SECRET` | `development-only-replace-in-prod` | Secreto HMAC (≥ 32 caracteres) de la cookie de propietario. En `NODE_ENV=production` el servidor se niega a arrancar con el valor por defecto. |
| `MAX_VERSIONS_PER_DIAGRAM` | `50` | Versiones máximas por diagrama. |
| `MAX_SNAPSHOT_BYTES` | `5242880` | Tamaño máximo de una instantánea. |
| `MAX_DESCRIPTION_LENGTH` / `MAX_NAME_LENGTH` | `240` / `80` | Límites de texto de las versiones. |
| `DIAGRAM_TTL_SECONDS` | `10368000` (120 días) | Caducidad deslizante del diagrama en Redis; se renueva al leerlo. |
| `VERSION_TTL_SECONDS` | `10454400` (121 días) | Caducidad de las versiones. |
| `AUTO_VERSION_INTERVAL_SECONDS` | `1800` | Intervalo mínimo entre versiones automáticas. |

> Un diagrama que nadie abre durante 120 días **desaparece** de Redis. Tenlo en cuenta al diseñar la persistencia de proyectos (ver el README de `extension-backend`).

## Ejecución

Desde la raíz del repositorio:

```bash
./scripts/dev.sh                                   # Redis en Docker + backend y webapp con hot-reload (dev.ps1 en Windows)
npm run dev   --workspace=@tumaet/server           # solo el backend (tsx watch); requiere Redis en marcha
npm run build --workspace=@tumaet/server           # compila a dist/ (tsc + copia de assets)
npm run start --workspace=@tumaet/server           # ejecuta dist/src/server.js
npm run test  --workspace=@tumaet/server           # Vitest (las pruebas de integración usan testcontainers, por lo que necesitan Docker)
```

En Docker, la imagen se construye con [`Dockerfile`](Dockerfile) desde la raíz del repositorio como contexto; `scripts/docker-up` lo levanta junto con la webapp y Redis (ver [scripts/README.md](../../scripts/README.md)).
