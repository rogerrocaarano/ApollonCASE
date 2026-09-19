# @tumaet/ui

Design system compartido de Apollon, al estilo [shadcn/ui](https://ui.shadcn.com/): componentes construidos sobre primitivas de [Base UI](https://base-ui.com/) y estilizados con Tailwind v4. Paquete privado del monorepo ApollonCASE (ver el [README raíz](../../README.md)); lo consumen [`@tumaet/apollon`](../../library) y [`@tumaet/webapp`](../../webapp).

## Contenido

- [`src/components`](src/components): componentes (`button`, `dialog`, `dropdown-menu`, `select`, `tabs`, `sheet`, `color-picker`, etc.) junto a sus `*.stories.tsx`.
- [`src/styles`](src/styles): tokens de diseño (`tokens.css`), tema (`theme.css`) y la hoja de componentes (`components.css`) que se precompila.
- [`src/lib`](src/lib): utilidades (`utils`, `color-swatch-tokens`) y [`src/theme.ts`](src/theme.ts).
- [`src/stories`](src/stories): documentación MDX de tokens y temas.

## Exports

Se definen en el `package.json` (`exports`); apuntan al código fuente, no a un `dist/` empaquetado:

| Import | Descripción |
| --- | --- |
| `@tumaet/ui/components/*` | Componentes, p. ej. `@tumaet/ui/components/button`. |
| `@tumaet/ui/styles/theme.css`, `@tumaet/ui/styles/tokens.css` | Tema y tokens CSS. |
| `@tumaet/ui/lib/utils` | Utilidades (`cn`, etc.). |
| `@tumaet/ui/lib/color-swatch-tokens` | Tokens de muestras de color. |
| `@tumaet/ui/theme` | Definición del tema. |

Los tipos se emiten en `dist/` con `build:types`; la hoja de estilos precompilada es `dist/components.css`.

## Scripts

Desde la raíz del repositorio:

```bash
npm run build:css   --workspace=@tumaet/ui   # compila src/styles/components.css -> dist/components.css
npm run build:types --workspace=@tumaet/ui   # emite las declaraciones (.d.ts) en dist/
npm run build       --workspace=@tumaet/ui   # ambos
npm run typecheck   --workspace=@tumaet/ui
npm run test        --workspace=@tumaet/ui   # Vitest
npm run lint        --workspace=@tumaet/ui
```

`dist/components.css` **debe existir** antes de arrancar o compilar la webapp (`src/webapp.css` lo importa por ruta relativa) y antes de las pruebas de la librería. Los scripts `start`, `build`, `storybook` y `test` de `webapp` y `library` ya lo generan con `npm run build:css --workspace=@tumaet/ui`, por lo que normalmente no hace falta ejecutarlo a mano.

## Storybook

Las stories de este paquete se sirven desde el Storybook de la webapp (que también incluye las stories del editor):

```bash
npm run storybook --workspace=@tumaet/webapp   # http://localhost:6006
```
