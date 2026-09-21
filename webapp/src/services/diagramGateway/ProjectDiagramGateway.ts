import { extensionWssURL } from "@/constants"
import { ProjectsApiClient } from "@/services/ExtensionApiClient"
import { WebSocketManager } from "@/services/WebSocketManager"
import type { Diagram } from "@/types"
import { projectExtensionRequest } from "./projectExtensionRequest"
import type { DiagramGateway } from "./types"

const bodyPath = (projectId: string, diagramId: string) =>
  `/api/v1/projects/${projectId}/diagrams/${diagramId}/body`

/**
 * "project" gateway: reaches a project diagram's body and live collaboration
 * session only through `extension-backend`, which checks project ownership
 * before proxying to `diagrams-backend` — the raw `diagrams-backend` id never
 * reaches the browser. `projectId` is baked in by this factory since a
 * project diagram's URLs are scoped to its project; call it once per route
 * mount (memoized on `projectId`) so the returned object has a stable
 * identity across renders — `ApollonShared`'s editor-mount effect depends on
 * it, and a fresh gateway every render would reconnect the editor
 * continuously.
 */
export function createProjectDiagramGateway(projectId: string): DiagramGateway {
  return {
    kind: "project",
    client: {
      fetchDiagram: (diagramId, opts) =>
        projectExtensionRequest<Diagram>(bodyPath(projectId, diagramId), {
          signal: opts?.signal,
        }),
      sendDiagramUpdate: (diagramId, model, opts) => {
        const headers: Record<string, string> = {}
        if (opts?.ifMatch !== undefined) {
          headers["If-Match"] = String(opts.ifMatch)
        }
        return projectExtensionRequest<{ headRev: number; updatedAt: string }>(
          bodyPath(projectId, diagramId),
          { method: "PUT", body: model, headers }
        )
      },
    },
    async connect(diagramId, instance, onError) {
      // A ticket is single-use and short-lived, so every (re)connect attempt
      // — including reconnects after a drop — must mint a fresh one. Passing
      // an async URL-builder to WebSocketManager (rather than a static URL)
      // is exactly what that's for.
      const manager = new WebSocketManager(
        diagramId,
        instance,
        onError,
        async (id) => {
          const { ticket } = await ProjectsApiClient.issueWsTicket(
            projectId,
            id
          )
          return `${extensionWssURL}/ws/diagrams?ticket=${encodeURIComponent(ticket)}`
        }
      )
      manager.startConnection()
      return manager
    },
  }
}
