import { createContext, use, type ReactNode } from "react"
import { RemoteDiagramGateway } from "@/services/diagramGateway/RemoteDiagramGateway"
import type { DiagramGateway } from "@/services/diagramGateway/types"

/**
 * Declares which backend `ApollonShared`'s diagram body + collaboration
 * session talk to. Defaults to `RemoteDiagramGateway` so every existing
 * mount (ad-hoc shared diagrams, and every test that renders `ApollonShared`
 * without wrapping it) keeps today's behavior with no explicit provider —
 * only the project-diagram route overrides it. Mirrors
 * `VersionRepositoryContext`'s per-route-declares-its-backend shape, except
 * this context holds the resolved gateway directly (a project gateway needs
 * its `projectId` baked in, so it can't be a static kind-keyed lookup).
 */
const DiagramGatewayContext = createContext<DiagramGateway>(
  RemoteDiagramGateway
)

export const DiagramGatewayProvider = ({
  gateway,
  children,
}: {
  gateway: DiagramGateway
  children: ReactNode
}) => (
  <DiagramGatewayContext value={gateway}>{children}</DiagramGatewayContext>
)

export function useDiagramGateway(): DiagramGateway {
  return use(DiagramGatewayContext)
}
