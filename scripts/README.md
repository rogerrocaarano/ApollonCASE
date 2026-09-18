# Scripts

Cada script tiene versión bash (`.sh`, Linux/macOS/Git Bash) y PowerShell (`.ps1`, Windows). Se ejecutan desde cualquier directorio.

- **`docker-up`** — build + levanta el stack completo en Docker (webapp, server, redis). Proyecto Compose `apolloncase`. Ctrl+C detiene los contenedores; `docker-down` los elimina junto con la red.
- **`docker-down`** — detiene y elimina el stack de `docker-up`.
- **`dev`** — desarrollo local con hot-reload: levanta (o reutiliza) Redis en Docker (proyecto `apolloncase-dev-db`), corre `npm install` si falta `node_modules`, y arranca backend (`@tumaet/server`, puerto 8000/ws 4444) y webapp (`@tumaet/webapp`, puerto 5173) en la misma terminal. Ctrl+C detiene solo backend y webapp — Redis se queda corriendo; deténlo manualmente con `docker compose -f docker/compose.local.db.yml down` cuando termines de trabajar.

```bash
# Linux/macOS
./scripts/docker-up.sh
./scripts/dev.sh
```

```powershell
# Windows
.\scripts\docker-up.ps1
.\scripts\dev.ps1
```
