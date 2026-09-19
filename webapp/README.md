# @tumaet/webapp

Aplicación web de [Apollon](../README.md) para el navegador. Envuelve la librería [`@tumaet/apollon`](../library) con enrutamiento, persistencia de diagramas, compartición e interfaz de evaluación.

Forma parte del monorepo ApollonCASE (npm workspaces). Se levanta desde la raíz del repositorio con `scripts/dev` (ver [scripts/README.md](../scripts/README.md)); no se ejecuta `npm install` dentro de esta carpeta.

## Stack

React 19, TypeScript, Vite, el design system estilo shadcn [`@tumaet/ui`](../packages/ui) (primitivas Base UI + Tailwind v4), [TanStack Query](https://tanstack.com/query) para el estado de servidor del historial de versiones, Storybook, Vitest y Playwright (visual + e2e).

## Configuración

Por defecto la webapp usa URLs relativas a su origen para hablar con el backend. Para apuntar a otro servidor, copia `.env.example` a `.env` y define:

```sh
VITE_SERVER_URL=http://localhost:8000
VITE_SERVER_URL_WSS=ws://localhost:4444/ws
```

En desarrollo, Vite hace de proxy de `/api`, `/embed` y `/ws` hacia el backend en `localhost:8000`/`4444` (puertos configurables con `APOLLON_SERVER_PORT` y `APOLLON_WS_PORT`; el de la webapp con `APOLLON_WEBAPP_PORT`, 5173 por defecto). Las rutas de `@tumaet/apollon` y `@tumaet/ui` se resuelven desde el código fuente (alias en `vite.config.ts`), de modo que los cambios en `library/` y `packages/ui/` se recargan en caliente.

## Depuración del estado de servidor

El historial de versiones (la lista, los cuerpos inmutables de las instantáneas y las mutaciones de crear, renombrar, borrar y restaurar) pasa por TanStack Query (ver [`src/queries`](src/queries) y la nota de frontera en [`src/queryClient.ts`](src/queryClient.ts)). El cuerpo inicial del diagrama del editor **no** es una query a propósito: es una semilla de una sola vez que pasa a ser propiedad de Yjs tras el montaje, así que nunca debe recargarse ni servirse desde caché; véase [`src/hooks/useDiagramSeed.ts`](src/hooks/useDiagramSeed.ts).

Las Query Devtools están **desactivadas por defecto**: su botón flotante queda abajo a la derecha, sobre el minimapa del editor, y el resto de esquinas las ocupa la interfaz del propio editor. Actívalas por navegador desde la consola y recarga:

```js
localStorage.setItem("apollon:query-devtools", "1")
```

Se eliminan de las compilaciones de producción en cualquier caso.

## Scripts

Ejecuta los comandos desde la raíz del repositorio para que los workspaces se resuelvan correctamente:

```sh
scripts/dev.sh                                          # backend + webapp con hot-reload (dev.ps1 en Windows)
npm run start --workspace=@tumaet/webapp                # solo la webapp (espera el backend en marcha)
npm run storybook --workspace=@tumaet/webapp            # Storybook (editor + stories de @tumaet/ui) en :6006
npm run test --workspace=@tumaet/webapp                 # pruebas unitarias (Vitest)
npm run test:e2e --workspace=@tumaet/webapp             # suite e2e de Playwright
npm run build --workspace=@tumaet/webapp                # bundle de producción en dist/
```

Consulta el [README raíz](../README.md) para la puesta en marcha completa, la estructura del repositorio y el roadmap de extensión.
