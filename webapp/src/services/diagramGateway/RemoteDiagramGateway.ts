import { DiagramApiClient } from "@/services/DiagramApiClient"
import { WebSocketManager } from "@/services/WebSocketManager"
import type { DiagramGateway } from "./types"

/**
 * Today's ad-hoc shared-diagram behavior, unchanged: talk to
 * `diagrams-backend` directly. The default gateway everywhere `ApollonShared`
 * is used without an explicit provider (see `DiagramGatewayContext`).
 */
export const RemoteDiagramGateway: DiagramGateway = {
  kind: "remote",
  client: DiagramApiClient,
  async connect(diagramId, instance, onError) {
    const manager = new WebSocketManager(diagramId, instance, onError)
    manager.startConnection()
    return manager
  },
}
