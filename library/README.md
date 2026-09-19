<div align="center">

# @tumaet/apollon

[![npm version](https://img.shields.io/npm/v/@tumaet/apollon)](https://www.npmjs.com/package/@tumaet/apollon)
[![npm downloads](https://img.shields.io/npm/dm/@tumaet/apollon)](https://www.npmjs.com/package/@tumaet/apollon)
[![license](https://img.shields.io/npm/l/@tumaet/apollon)](https://github.com/ls1intum/Apollon/blob/main/LICENSE)
[![types included](https://img.shields.io/npm/types/@tumaet/apollon)](https://www.npmjs.com/package/@tumaet/apollon)

**Editor UML embebible para la web.** Se monta en cualquier nodo del DOM y funciona dentro de Angular, Vue, Svelte, JS puro o React.

<!-- npm-safe header widgets: npmjs.com strips GitHub's <picture> theme swap
     and width/height attributes, so these are plain markdown images at their
     natural (1x) size, with absolute URLs (repo-relative paths do not resolve
     on npmjs.com). All PNGs are generated from the live editor by the
     readme-assets Playwright project. -->

[![Prueba la demo en vivo](https://raw.githubusercontent.com/ls1intum/Apollon/main/docs/static/img/apollon-btn-demo-light-1x.png)](https://apollon.aet.cit.tum.de) [![Documentación](https://raw.githubusercontent.com/ls1intum/Apollon/main/docs/static/img/apollon-btn-docs-light-1x.png)](https://ls1intum.github.io/Apollon/library/)

[Referencia de la API](https://ls1intum.github.io/Apollon/library/api) · [Ejemplos](https://ls1intum.github.io/Apollon/library/embedding/react) · [GitHub](https://github.com/ls1intum/Apollon)

[![El editor Apollon mostrando un diagrama de clases UML, con la paleta de elementos a la izquierda](https://raw.githubusercontent.com/ls1intum/Apollon/main/docs/static/img/apollon-editor-light.png)](https://apollon.aet.cit.tum.de)

</div>

---

Apollon es el editor de modelado detrás de [Artemis](https://artemis.tum.de/), la plataforma de aprendizaje interactivo de TUM. La API es imperativa: se llama a `new ApollonEditor(container, options)` y el editor renderiza su propio árbol de React dentro de ese nodo, así que tu código nunca toca React. En una aplicación React, renderiza en su lugar el [componente `<Apollon>`](#react).

## Uso dentro de ApollonCASE

En este monorepo la librería se consume como el workspace `@tumaet/apollon` (ver el [README raíz](../README.md)); no hace falta instalarla desde npm.

- **Desarrollo:** la webapp resuelve `@tumaet/apollon` directamente desde `library/lib` mediante alias de Vite, así que los cambios se recargan en caliente al ejecutar `scripts/dev`.
- **Compilación:** `npm run build --workspace=@tumaet/apollon` genera `library/dist/` (también lo necesitan `tsc -b` de la webapp y el backend `@tumaet/server`, que dependen del paquete compilado).
- **Pruebas:** `npm run test --workspace=@tumaet/apollon`.

El resto de este documento es el README original del paquete (traducido), pensado para quien lo instala desde npm.

## Características

- **13 tipos de diagrama**: clases, objetos, actividad, casos de uso, comunicación, componentes, despliegue, redes de Petri, grafo de alcanzabilidad, árbol sintáctico, diagrama de flujo, BPMN y SFC.
- **Independiente del framework**: una sola API imperativa para Angular, Vue, Svelte y JS puro, más un componente React, hooks y provider.
- **Colaboración en tiempo real**: edición multiusuario opcional sobre [Yjs](https://yjs.dev/), con el transporte que prefieras (WebSocket, WebRTC, BroadcastChannel).
- **Exportación**: SVG y JSON incluidos. PNG y PDF se generan a partir del SVG (ver [Exportación](#exportación)).
- **Superposiciones en el lienzo**: inyecta tus propias barras de herramientas, avisos y rieles en el lienzo del editor; Apollon mide los controles que reservan espacio y los coloca junto a la interfaz integrada, con `<ApollonControl>` (React) o `addControl` / `getRegionElement` (cualquier framework). Ver [Overlay controls](https://ls1intum.github.io/Apollon/library/api/overlay-controls).
- **Internacionalización**: sustituye los textos de la interfaz del editor expuestos en `ApollonLabels` (tooltips, aria-labels, popovers de edición y evaluación) con `labels` / `setLabels` / `useLabels`. Ver [i18n](https://ls1intum.github.io/Apollon/library/api/overlay-controls#i18n).
- **Modo de evaluación**: adjunta puntuaciones y comentarios a los elementos. Es el flujo de calificación que usa Artemis.
- **TypeScript**: incluye definiciones de tipos.

## Instalación

```sh
npm install @tumaet/apollon
```

npm 7+, pnpm 8+ y Bun resuelven automáticamente las dependencias peer necesarias.
Yarn nunca instala peers: en ese caso, enuméralas explícitamente:

```sh
npm install @tumaet/apollon react react-dom @xyflow/react yjs y-protocols
```

```ts
import { ApollonEditor } from "@tumaet/apollon"
import "@tumaet/apollon/style.css"
```

Apollon publica **una única** compilación con todas las dependencias de ejecución externas: la familia React (`react`, `react-dom`, `@xyflow/react`), los singletons CRDT (`yjs`, `y-protocols`) y las dependencias de interfaz propias de Apollon (`@base-ui/react`, `lucide-react`, `@dnd-kit`, `zustand`, `@chenglou/pretext`), que llegan de forma transitiva al instalar el paquete. Tu bundler resuelve y deduplica cada una contra el `node_modules` de tu aplicación, y tus herramientas de análisis de bundle o SBOM las ven como los paquetes reales que son, nunca como una copia incrustada de forma invisible en un chunk. Esto funciona desde cualquier framework con bundler (Angular, Vue, Svelte, React).

Las peers necesarias y para qué sirve cada una:

| Peer            | Rango     | Para qué                                                     |
| --------------- | --------- | ------------------------------------------------------------ |
| `react`         | `^19.0.0` | el renderizado del editor                                    |
| `react-dom`     | `^19.0.0` | el renderizado del editor                                    |
| `@xyflow/react` | `^12.9.0` | el lienzo del diagrama                                       |
| `yjs`           | `^13.6.0` | el modelo de documento, deshacer/rehacer y la colaboración en vivo |
| `y-protocols`   | `^1.0.6`  | sincronización y awareness de la colaboración                |

Mantenerlas externas significa que un host que ya usa React o Yjs comparte una única instancia con el editor en lugar de cargar una copia privada y posiblemente incompatible: sin carga duplicada y sin errores de "Invalid hook call" ni de documentos de instancias distintas.

### Hosts que no usan React (Angular, Vue, Svelte, JS puro)

La API es imperativa (`new ApollonEditor(container, options)`) y el editor renderiza su propio árbol de React dentro del contenedor, así que tu código nunca importa ni toca React. Aun así debes instalar las peers de React (el editor las usa internamente), pero Apollon es lo único en la página que las usa.

### Hosts React

Importa el componente `<Apollon>`, los hooks y el provider desde la misma entrada: `import { Apollon } from "@tumaet/apollon"`. Se renderizan sobre el React que ya tienes. Como el paquete no tiene efectos secundarios salvo el CSS, los hosts que no usan React eliminan el componente y los hooks por tree-shaking de forma automática.

> **⚠️ Dale al contenedor una altura explícita y distinta de cero** (`600px`, `80vh` o un hijo de flex/grid con tamaño). El lienzo se ajusta al tamaño de su padre, así que sin una altura resoluble colapsa a cero píxeles y se ve en blanco. Es el error de integración más habitual. Ver [Troubleshooting](https://ls1intum.github.io/Apollon/library/troubleshooting).

## Inicio rápido

```ts
import { ApollonEditor, UMLDiagramType } from "@tumaet/apollon"
import "@tumaet/apollon/style.css"

const container = document.getElementById("apollon")
if (!container) throw new Error("#apollon container missing")

const editor = new ApollonEditor(container, {
  type: UMLDiagramType.ClassDiagram,
})

const subscriptionId = editor.subscribeToModelChange((model) => {
  // persist / broadcast the latest diagram JSON
})

const { svg } = await editor.exportAsSVG()

editor.unsubscribe(subscriptionId)
editor.destroy()
```

El editor es solo de cliente. En frameworks SSR (Next.js, Remix, SvelteKit, Nuxt), constrúyelo desde un efecto del lado del cliente, nunca durante el render. Llama siempre a `editor.destroy()` antes de volver a montar sobre el mismo contenedor.

## Ejemplos de integración

### React

Renderiza el componente `<Apollon>` de `@tumaet/apollon`. Gestiona el ciclo de vida del editor: lo construye al montarse y lo destruye al desmontarse.

```tsx
import { Apollon } from "@tumaet/apollon"
import type { UMLModel } from "@tumaet/apollon"
import "@tumaet/apollon/style.css"

export function DiagramEditor({ initialModel }: { initialModel?: UMLModel }) {
  return (
    <Apollon
      style={{ height: 600 }}
      defaultModel={initialModel}
      onMount={(editor) => {
        const id = editor.subscribeToModelChange((model) => {
          localStorage.setItem("diagram", JSON.stringify(model))
        })
        return () => editor.unsubscribe(id)
      }}
    />
  )
}
```

Accede a la instancia mediante `ref`, el callback `onMount(editor)` o el hook `useApollonEditor()`. Consulta la [guía de integración con React](https://ls1intum.github.io/Apollon/library/embedding/react) para hooks, provider y SSR.

### Angular (17.3+ basado en signals)

```ts no-check
import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  input,
  viewChild,
} from "@angular/core"
import { ApollonEditor, type UMLModel } from "@tumaet/apollon"
import "@tumaet/apollon/style.css"

@Component({
  selector: "app-diagram-editor",
  template: `<div #host style="width: 100%; height: 100%"></div>`,
})
export class DiagramEditorComponent {
  readonly initialModel = input<UMLModel>()
  private host = viewChild.required<ElementRef<HTMLDivElement>>("host")

  constructor() {
    const destroyRef = inject(DestroyRef)
    afterNextRender(() => {
      const editor = new ApollonEditor(this.host().nativeElement, {
        model: this.initialModel(),
      })
      const subId = editor.subscribeToModelChange((model) => {
        localStorage.setItem("diagram", JSON.stringify(model))
      })
      destroyRef.onDestroy(() => {
        editor.unsubscribe(subId)
        editor.destroy()
      })
    })
  }
}
```

`afterNextRender` se ejecuta solo en el navegador, por lo que es seguro con SSR.

### JS puro / CDN

`yjs` y `y-protocols` son peers obligatorias, pero en la vía CDN esm.sh las resuelve y sirve automáticamente desde la URL de importación: no hay nada extra que cargar. (Con un bundler, instalas las peers tú mismo.)

```html
<link rel="stylesheet" href="https://esm.sh/@tumaet/apollon@5.3.0/style.css" />
<div id="apollon" style="width: 100%; height: 600px"></div>

<script type="module">
  import { ApollonEditor } from "https://esm.sh/@tumaet/apollon@5.3.0"

  const saved = localStorage.getItem("diagram")
  const editor = new ApollonEditor(document.getElementById("apollon"), {
    model: saved ? JSON.parse(saved) : undefined,
  })

  editor.subscribeToModelChange((model) => {
    localStorage.setItem("diagram", JSON.stringify(model))
  })
</script>
```

> **⚠️ Fija una versión exacta**, como hacen las URLs anteriores. Una URL de CDN sin fijar se resuelve a `latest`, de modo que una nueva versión mayor puede llegar en la siguiente recarga de la página y romper tu integración.

## Diagramas soportados

Clases, Objetos, Actividad, Casos de uso, Comunicación, Componentes, Despliegue, Redes de Petri, Grafo de alcanzabilidad, Árbol sintáctico, Diagrama de flujo, BPMN, SFC. El enum `UMLDiagramType` contiene los valores de cadena exactos.

## Colaboración en tiempo real

La colaboración es opcional e independiente del transporte. Establece `collaborationEnabled: true` y conecta tu transporte:

```ts no-check
const editor = new ApollonEditor(container, { collaborationEnabled: true })

// Outbound: the editor calls back when it has bytes to send.
editor.sendBroadcastMessage((base64) => transport.send(base64))

// Inbound: forward every received frame back to the editor.
transport.onMessage((base64) => editor.receiveBroadcastedMessage(base64))
```

Para que la librería dibuje la presencia de los participantes, los cursores en vivo y el resaltado remoto de la selección de nodos y aristas, pasa la configuración opcional de interfaz `collaboration`:

```ts no-check
const editor = new ApollonEditor(container, {
  collaborationEnabled: true,
  collaboration: {
    enabled: true,
    user: { name: "Ada", color: "#1c7ed6" },
  },
})
```

Sirve cualquier transporte compatible con Yjs: `y-websocket`, `y-webrtc`, BroadcastChannel o tu propio relay. El cursor y el awareness de la selección viajan por el mismo canal. Ver [Collaboration](https://ls1intum.github.io/Apollon/library/api/collaboration).

## Exportación

- **SVG**: `await editor.exportAsSVG(options)` devuelve `{ svg, clip }`. `svgMode: "web"` (el valor por defecto) conserva las variables CSS para una salida que se adapta al tema; `"compat"` las incrusta para PDF e Inkscape.
- **JSON**: `editor.model` devuelve el `UMLModel`, y volver a asignarlo es seguro en ambos sentidos. Usa `importDiagram(json)` para normalizar antes los modelos antiguos v2/v3.
- **Sin interfaz (headless)**: `ApollonEditor.exportModelAsSvg(model, options)` renderiza un modelo sin un editor montado.
- **PNG / PDF**: no vienen integrados, pero la librería incluye los renderizadores `svgToPng` / `svgToPdf` en [`@tumaet/apollon/export`](https://ls1intum.github.io/Apollon/library/api/export) (PNG con `@resvg/resvg-wasm`, PDF con `svg2pdf.js` + `jspdf`, dependencias opcionales que se instalan automáticamente con el paquete). El servidor de este repositorio ([`services/diagrams-backend`](../services/diagrams-backend/README.md)) renderiza en el servidor, con `@napi-rs/canvas` (PNG) y `pdfmake` (PDF).

Consulta [Export](https://ls1intum.github.io/Apollon/library/api/export) para ver todas las `ExportOptions`.

## Temas

Personaliza el editor mediante las propiedades CSS personalizadas `--apollon-*` (tipadas con `createApollonTheme`) más un selector `data-theme` claro/oscuro; es independiente del framework y no requiere Tailwind. Un primer cambio de marca son tres tokens: `primary`, `background` y `foreground`.

```tsx
import { Apollon, createApollonTheme } from "@tumaet/apollon"

declare const dark: boolean // your app's light/dark state
;<Apollon
  style={{ height: "80vh" }}
  theme={createApollonTheme({
    primary: "#ff5722",
    background: "#fff",
    foreground: "#1a1a1a",
  })}
  dataTheme={dark ? "dark" : undefined}
/>
```

Consulta [Theming](https://ls1intum.github.io/Apollon/library/theming) (o [`THEMING.md`](./THEMING.md)) para el contrato completo, el modo oscuro y los patrones de integración en el host.

## Documentación

- [Resumen de la librería](https://ls1intum.github.io/Apollon/library/): instalación, inicio rápido, integración
- [Theming](https://ls1intum.github.io/Apollon/library/theming): el contrato `--apollon-*`, `createApollonTheme`, claro/oscuro
- [Referencia de la API](https://ls1intum.github.io/Apollon/library/api): toda la superficie de `ApollonEditor` y `<Apollon>`
- [Troubleshooting](https://ls1intum.github.io/Apollon/library/troubleshooting): lienzo en blanco, SSR, React duplicado y otros problemas habituales

El protocolo de comunicación del lado servidor se expone mediante el subpath `@tumaet/apollon/internals`. Es inestable y no está cubierto por SemVer.

## Relacionado

- Código fuente e incidencias: <https://github.com/ls1intum/Apollon>
- Editor en vivo: <https://apollon.aet.cit.tum.de>
- La webapp standalone y el servidor de colaboración están en este monorepo ([`webapp/`](../webapp/README.md) y [`services/diagrams-backend/`](../services/diagrams-backend/README.md)). La [extensión de VS Code](https://marketplace.visualstudio.com/items?itemName=aet-tum.apollon-extension) y la carpeta `docs/` del repositorio original de Apollon no se importaron a ApollonCASE, por lo que los enlaces a `docs/static` de arriba apuntan al repositorio upstream.

## Licencia

MIT. Ver [LICENSE](https://github.com/ls1intum/Apollon/blob/main/LICENSE).
