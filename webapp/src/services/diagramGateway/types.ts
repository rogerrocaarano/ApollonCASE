import type { ApollonEditor, UMLModel } from "@tumaet/apollon"
import type { ControlEvent, Diagram } from "@/types"

/**
 * The subset of `DiagramApiClient` the editor lifecycle needs to read/write a
 * diagram's body. Narrowed to a port so a diagram reached through a project
 * (via `extension-backend`) can satisfy it without depending on
 * `diagrams-backend`'s direct REST shape.
 */
export interface DiagramContentClient {
  fetchDiagram(
    diagramId: string,
    opts?: { signal?: AbortSignal }
  ): Promise<Diagram>
  sendDiagramUpdate(
    diagramId: string,
    model: UMLModel,
    opts?: { ifMatch?: number }
  ): Promise<{ headRev: number; updatedAt: string }>
}

/** What `ApollonShared` needs from a live collaboration connection. */
export interface CollaborationConnection {
  onControl(listener: (event: ControlEvent) => void): () => void
  publishControl(event: ControlEvent): void
  cleanup(): void
}

/**
 * Where a diagram's content and live collaboration session come from.
 * `"remote"` talks to `diagrams-backend` directly (today's ad-hoc shared
 * diagrams); `"project"` talks to `extension-backend`, which proxies to
 * `diagrams-backend` after an ownership check and never exposes the
 * underlying `diagrams-backend` id to the browser.
 */
export interface DiagramGateway {
  readonly kind: "remote" | "project"
  readonly client: DiagramContentClient
  /**
   * Opens (and starts) a collaboration connection for `diagramId`. Async
   * because a `"project"` gateway must fetch a one-time ticket before it can
   * connect at all.
   */
  connect(
    diagramId: string,
    instance: ApollonEditor,
    onError: (e: Event) => void
  ): Promise<CollaborationConnection>
}
