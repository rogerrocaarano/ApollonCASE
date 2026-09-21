import { useMemo } from "react"
import { createFileRoute } from "@tanstack/react-router"
import { ApollonShared } from "@/pages/ApollonShared"
import { VersionRepositoryProvider } from "@/contexts/VersionRepositoryContext"
import { DiagramGatewayProvider } from "@/contexts/DiagramGatewayContext"
import { createProjectDiagramGateway } from "@/services/diagramGateway/ProjectDiagramGateway"
import { toProjectVersionDiagramId } from "@/services/versionRepository"
import { DiagramView } from "@/types"

/**
 * A project diagram's editor. Unlike `/shared/$diagramId`, there is no
 * `?view=` mode: a project diagram has exactly one possible accessor (the
 * project owner), so the per-link access-mode concept `?view=` exists to
 * encode doesn't apply — it always opens live, editable, with collaboration
 * on (see `gate-project-diagrams`'s design.md, decision 5). `?version=<id>`
 * still previews a saved version, same as every other editor route.
 */
type ProjectDiagramSearch = { version?: string }

function ProjectDiagramRouteComponent() {
  const { projectId, diagramId } = Route.useParams()
  const { version } = Route.useSearch()

  // Memoized on projectId: the gateway's identity must stay stable across
  // renders (ApollonShared's editor-mount effect depends on it, and a fresh
  // gateway every render would reconnect the editor continuously — the same
  // pitfall a stray object literal caused for `collaborationUser` before).
  const gateway = useMemo(
    () => createProjectDiagramGateway(projectId),
    [projectId]
  )
  const versionDiagramId = useMemo(
    () => toProjectVersionDiagramId(projectId, diagramId),
    [projectId, diagramId]
  )

  return (
    <VersionRepositoryProvider kind="project">
      <DiagramGatewayProvider gateway={gateway}>
        <ApollonShared
          diagramId={diagramId}
          viewType={DiagramView.COLLABORATE}
          previewFromUrl={version}
          versionDiagramId={versionDiagramId}
          isAdHocShare={false}
        />
      </DiagramGatewayProvider>
    </VersionRepositoryProvider>
  )
}

export const Route = createFileRoute(
  "/projects/$projectId/diagrams/$diagramId"
)({
  validateSearch: (search: Record<string, unknown>): ProjectDiagramSearch => ({
    version: typeof search.version === "string" ? search.version : undefined,
  }),
  component: ProjectDiagramRouteComponent,
})
