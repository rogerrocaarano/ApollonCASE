import { useLocation } from "@tanstack/react-router"
import { ALL_DIAGRAMS_LABEL } from "@/lib/navProvenance"

export type EditorBackTarget =
  | { to: "/projects/$id"; params: { id: string }; label: string }
  | { to: "/"; label: string }

/**
 * Resolves the editor's own back-to-dashboard control. Parses the current
 * pathname directly rather than `useParams()`/`useMatches()` — the navbar
 * renders above the matched route (same constraint `useDiagramIdFromPath`
 * documents), so route-scoped param hooks return nothing useful here.
 *
 * A project diagram (`/projects/:projectId/diagrams/:diagramId`) goes back to
 * that project's diagram list; every other editor surface (local, shared,
 * playground) keeps today's behavior of going back to `/`.
 */
export function useEditorBackTarget(): EditorBackTarget {
  const location = useLocation()
  const segments = location.pathname.split("/").filter(Boolean)

  if (segments[0] === "projects" && segments[1] && segments[2] === "diagrams") {
    return {
      to: "/projects/$id",
      params: { id: segments[1] },
      label: "Project diagrams",
    }
  }

  return { to: "/", label: ALL_DIAGRAMS_LABEL }
}
