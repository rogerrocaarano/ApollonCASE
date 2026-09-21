import { useEffect, useState } from "react"
import { getRouteApi, useNavigate } from "@tanstack/react-router"
import { Plus } from "lucide-react"
import { toast } from "react-toastify"
import type { UMLDiagramType } from "@tumaet/apollon"
import { Button } from "@tumaet/ui/components/button"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@tumaet/ui/components/alert-dialog"
import { PageShell } from "@/components/PageShell"
import { BackNav } from "@/components/navbar/BackNav"
import {
  ProjectDiagramCard,
  type ResolvedProjectDiagram,
} from "@/components/projects/ProjectDiagramCard"
import { ProjectGallerySkeleton } from "@/components/projects/ProjectGallerySkeleton"
import { useModalContext } from "@/contexts"
import { ProjectsApiClient } from "@/services/ExtensionApiClient"
import type { Project } from "@/types"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import { log } from "@/logger"

const route = getRouteApi("/projects/$id")

/**
 * A project's diagrams. `extension-backend` resolves each row's
 * title/type/updatedAt from `diagrams-backend` server-side and returns them
 * already enriched — the browser never learns a project diagram's
 * `diagrams-backend` id (see gate-project-diagrams).
 */
export const ProjectDetailPage = () => {
  const { id } = route.useParams()
  const { openModal } = useModalContext()
  const navigate = useNavigate()

  const [project, setProject] = useState<Project | null>(null)
  const [diagrams, setDiagrams] = useState<ResolvedProjectDiagram[] | null>(
    null
  )
  const [error, setError] = useState(false)
  const [confirmingDeleteProject, setConfirmingDeleteProject] =
    useState(false)

  useDocumentTitle(project?.name || "Project")

  const loadProject = async () => {
    try {
      const result = await ProjectsApiClient.get(id)
      setProject(result)
    } catch (err) {
      log.error("Failed to load project", err as Error)
      setError(true)
    }
  }

  const loadDiagrams = async () => {
    try {
      const rows = await ProjectsApiClient.listDiagrams(id)
      const resolved: ResolvedProjectDiagram[] = rows.map((row) =>
        row.status === "ready" && row.title !== undefined && row.type
          ? {
              diagramId: row.id,
              projectId: row.projectId,
              status: "ready",
              title: row.title,
              type: row.type as UMLDiagramType,
              lastModifiedAt: row.updatedAt ?? "",
            }
          : {
              diagramId: row.id,
              projectId: row.projectId,
              status: "failed",
            }
      )
      setDiagrams(resolved)
    } catch (err) {
      log.error("Failed to load project diagrams", err as Error)
      setError(true)
    }
  }

  useEffect(() => {
    void loadProject()
    void loadDiagrams()
    // Re-run only when the route param changes, not on every re-render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const handleNewDiagram = () => {
    openModal("NEW_DIAGRAM", { projectId: id })
  }

  const handleRename = () => {
    if (!project) return
    openModal("RENAME_PROJECT", {
      dialogVariant: "home",
      project,
      onRenamed: (updated: Project) => setProject(updated),
    })
  }

  const handleDeleteProject = async () => {
    try {
      await ProjectsApiClient.deleteProject(id)
      navigate({ to: "/" })
    } catch (err) {
      log.error("Failed to delete project", err as Error)
      toast.error("Could not delete the project. Please try again.")
    }
  }

  const handleDeleteDiagram = async (diagram: ResolvedProjectDiagram) => {
    try {
      await ProjectsApiClient.deleteDiagram(id, diagram.diagramId)
      setDiagrams((current) =>
        (current ?? []).filter((existing) => existing.diagramId !== diagram.diagramId)
      )
    } catch (err) {
      log.error("Failed to delete diagram", err as Error)
      toast.error("Could not delete the diagram. Please try again.")
    }
  }

  return (
    <PageShell
      header={
        <div className="sticky top-[calc(var(--safe-area-inset-top,0px)_+_0.75rem)] z-20 flex flex-col gap-2 pb-2 md:top-[calc(var(--safe-area-inset-top,0px)_+_1rem)]">
          <BackNav to="/" label="Projects" />
          <div className="flex items-center justify-between gap-4">
            <header role="banner" className="min-w-0">
              <h1 className="truncate text-lg font-semibold text-foreground">
                {project?.name.trim() || "Project"}
              </h1>
              {project?.description.trim() ? (
                <p className="truncate text-sm text-muted-foreground">
                  {project.description}
                </p>
              ) : null}
            </header>
            <div className="flex shrink-0 items-center gap-2">
              {project ? (
                <>
                  <Button type="button" variant="outline" onClick={handleRename}>
                    Rename
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setConfirmingDeleteProject(true)}
                  >
                    Delete
                  </Button>
                </>
              ) : null}
              <Button type="button" onClick={handleNewDiagram}>
                <Plus className="size-4" aria-hidden />
                New diagram
              </Button>
            </div>
          </div>
        </div>
      }
    >
      <div className="mt-4">
        {error ? (
          <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 text-center">
            <p className="text-lg font-semibold text-foreground">
              Server unavailable
            </p>
            <p className="max-w-xs text-sm text-muted-foreground">
              Could not reach the server. Check your connection and try again.
            </p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setError(false)
                void loadProject()
                void loadDiagrams()
              }}
            >
              Try again
            </Button>
          </div>
        ) : diagrams === null ? (
          <ProjectGallerySkeleton />
        ) : diagrams.length === 0 ? (
          <div className="flex min-h-[320px] flex-col items-center justify-center gap-2 text-center">
            <p className="text-lg font-semibold text-foreground">
              No diagrams yet
            </p>
            <p className="max-w-xs text-sm text-muted-foreground">
              Use &quot;New diagram&quot; to add one to this project.
            </p>
          </div>
        ) : (
          <div
            role="list"
            className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,240px),1fr))] justify-start gap-4 md:grid-cols-[repeat(auto-fill,minmax(min(100%,260px),1fr))] md:gap-6 xl:grid-cols-[repeat(auto-fill,minmax(min(100%,280px),1fr))]"
          >
            {diagrams.map((diagram) => (
              <ProjectDiagramCard
                key={diagram.diagramId}
                diagram={diagram}
                onDelete={(d) => void handleDeleteDiagram(d)}
              />
            ))}
          </div>
        )}
      </div>

      {project ? (
        <AlertDialog
          open={confirmingDeleteProject}
          onOpenChange={setConfirmingDeleteProject}
        >
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Delete this project?</AlertDialogTitle>
              <AlertDialogDescription>
                This permanently deletes &quot;{project.name.trim() || "Project"}
                &quot; and all of its diagrams. This action cannot be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction
                variant="destructive"
                onClick={() => void handleDeleteProject()}
              >
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      ) : null}
    </PageShell>
  )
}
