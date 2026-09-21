import { getRouteApi } from "@tanstack/react-router"
import { ApollonShared } from "@/pages/ApollonShared"

/**
 * Test-only stand-in for `routes/shared.$diagramId.tsx`'s route component:
 * `ApollonShared` reads its diagram id / view / preview version as props
 * now (so the same component can serve a project-diagram route too), and
 * the real route file supplies them via `Route.useParams()`/`useSearch()`.
 * `renderWithRouter` mounts `ApollonShared` directly as a synthetic route's
 * component (see its `component: () => <>{ui}</>`), so tests need this
 * same little adapter to read from that synthetic route instead.
 */
const route = getRouteApi("/shared/$diagramId")

export function ApollonSharedAtSharedRoute() {
  const { diagramId } = route.useParams()
  const { view, version } = route.useSearch()
  return (
    <ApollonShared diagramId={diagramId} viewType={view} previewFromUrl={version} />
  )
}
