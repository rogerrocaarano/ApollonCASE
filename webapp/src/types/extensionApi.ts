// Wire-compatible mirror of extension-backend's ProjectResponse/DiagramResponse
// (services/extension-backend/.../projects/ProjectDtos.kt, DiagramDtos.kt).

export interface Project {
  id: string
  name: string
  description: string
  myPermission: "OWNER" | "COLLABORATOR" | "VIEWER"
  createdAt: string
  updatedAt: string
}

/**
 * A `Diagram` row as extension-backend tracks it. Never carries the
 * underlying `diagrams-backend` id — the browser addresses a project diagram
 * only by this `id`, and extension-backend resolves it server-side. `status`
 * is `"failed"` when the body could no longer be resolved in
 * `diagrams-backend` (title/type/updatedAt are absent in that case).
 */
export interface ProjectDiagram {
  id: string
  projectId: string
  status: "ready" | "failed"
  title?: string
  type?: string
  updatedAt?: string
}
