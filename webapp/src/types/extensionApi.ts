// Wire-compatible mirror of extension-backend's ProjectResponse/DiagramResponse
// (services/extension-backend/.../projects/ProjectDtos.kt, DiagramDtos.kt).

export interface Project {
  id: string
  name: string
  description: string
  ownerId: string
  createdAt: string
  updatedAt: string
}

/** A `Diagram` row as extension-backend tracks it: project ownership only, no body. */
export interface ProjectDiagram {
  id: string
  redisId: string
  projectId: string
}
