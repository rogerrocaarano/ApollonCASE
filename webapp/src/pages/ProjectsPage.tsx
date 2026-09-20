import { useCallback, useEffect, useState } from "react"
import { Plus } from "lucide-react"
import { toast } from "react-toastify"
import { Button } from "@tumaet/ui/components/button"
import { PageShell } from "@/components/PageShell"
import { ProjectGallery } from "@/components/projects/ProjectGallery"
import { ProjectGallerySkeleton } from "@/components/projects/ProjectGallerySkeleton"
import { useModalContext } from "@/contexts"
import { ProjectsApiClient } from "@/services/ExtensionApiClient"
import type { Project } from "@/types"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { log } from "@/logger"

/**
 * The webapp's landing page after login. Replaces the old flat diagram
 * gallery (`HomePage`): diagrams are now always organized inside a project
 * (see the `add-projects` change), so the entry point lists projects instead.
 */
export const ProjectsPage = () => {
  useDocumentTitle("Your projects")
  const { openModal } = useModalContext()
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [loadError, setLoadError] = useState(false)

  const load = useCallback(async () => {
    try {
      const result = await ProjectsApiClient.list()
      setProjects(result)
      setLoadError(false)
    } catch (err) {
      log.error("Failed to load projects", err as Error)
      setLoadError(true)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const handleNewProject = () => {
    openModal("NEW_PROJECT", {
      dialogVariant: "home",
      onCreated: (project: Project) => {
        setProjects((current) => [project, ...(current ?? [])])
      },
    })
  }

  const handleRename = (project: Project) => {
    openModal("RENAME_PROJECT", {
      dialogVariant: "home",
      project,
      onRenamed: (updated: Project) => {
        setProjects((current) =>
          (current ?? []).map((existing) =>
            existing.id === updated.id ? updated : existing
          )
        )
      },
    })
  }

  const handleDelete = async (project: Project) => {
    try {
      await ProjectsApiClient.deleteProject(project.id)
      setProjects((current) =>
        (current ?? []).filter((existing) => existing.id !== project.id)
      )
    } catch (err) {
      log.error("Failed to delete project", err as Error)
      toast.error("Could not delete the project. Please try again.")
    }
  }

  return (
    <PageShell
      header={
        <div className="sticky top-[calc(var(--safe-area-inset-top,0px)_+_0.75rem)] z-20 flex items-center justify-between gap-4 pb-2 md:top-[calc(var(--safe-area-inset-top,0px)_+_1rem)]">
          <header role="banner">
            <h1 className="text-lg font-semibold text-foreground">
              Your projects
            </h1>
          </header>
          <Button type="button" onClick={handleNewProject}>
            <Plus className="size-4" aria-hidden />
            New project
          </Button>
        </div>
      }
    >
      <div className="mt-4">
        {projects === null && !loadError ? (
          <ProjectGallerySkeleton />
        ) : loadError ? (
          <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 text-center">
            <p className="text-lg font-semibold text-foreground">
              Server unavailable
            </p>
            <p className="max-w-xs text-sm text-muted-foreground">
              Could not reach the server. Check your connection and try again.
            </p>
            <Button variant="outline" size="sm" onClick={() => void load()}>
              Try again
            </Button>
          </div>
        ) : (
          <ProjectGallery
            projects={projects ?? []}
            onRename={handleRename}
            onDelete={(project) => void handleDelete(project)}
          />
        )}
      </div>
    </PageShell>
  )
}
