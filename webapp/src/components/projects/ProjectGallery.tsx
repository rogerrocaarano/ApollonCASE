import type { Project } from "@/types"
import { ProjectCard } from "./ProjectCard"

export type ProjectGalleryProps = {
  projects: Project[]
  onRename: (project: Project) => void
  onDelete: (project: Project) => void
}

export function ProjectGallery({
  projects,
  onRename,
  onDelete,
}: ProjectGalleryProps) {
  if (projects.length === 0) {
    return (
      <div className="flex min-h-[320px] flex-col items-center justify-center gap-2 text-center transition-colors duration-200">
        <p className="text-lg font-semibold text-foreground">No projects yet</p>
        <p className="max-w-xs text-sm text-muted-foreground">
          Create a project to start organizing your diagrams.
        </p>
      </div>
    )
  }

  return (
    <div
      role="list"
      className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,240px),1fr))] justify-start gap-4 md:grid-cols-[repeat(auto-fill,minmax(min(100%,260px),1fr))] md:gap-6 xl:grid-cols-[repeat(auto-fill,minmax(min(100%,280px),1fr))]"
    >
      {projects.map((project) => (
        <ProjectCard
          key={project.id}
          project={project}
          onRename={onRename}
          onDelete={onDelete}
        />
      ))}
    </div>
  )
}
