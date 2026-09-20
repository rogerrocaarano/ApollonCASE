import { useState, type MouseEvent as ReactMouseEvent } from "react"
import type { UMLDiagramType } from "@tumaet/apollon"
import { Link } from "@tanstack/react-router"
import { AlertTriangle, Trash2 } from "lucide-react"
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from "@tumaet/ui/components/card"
import { Badge } from "@tumaet/ui/components/badge"
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
import { cn } from "@tumaet/ui/lib/utils"
import {
  getDiagramTypeIcon,
  getDiagramTypeShortLabel,
} from "@/components/home/diagramTypeMeta"
import { sharedDiagramRoute } from "@/utils/sharedDiagramLinks"

export type ResolvedProjectDiagram =
  | {
      /** `diagrams-backend` id — used to open the diagram in the editor. */
      id: string
      /** `extension-backend` `Diagram` row id — used to delete it from the project. */
      diagramId: string
      status: "ready"
      title: string
      type: UMLDiagramType
      lastModifiedAt: string
    }
  | {
      id: string
      diagramId: string
      status: "failed"
    }

const formatLastModified = (isoDate: string) => {
  const date = new Date(isoDate)
  if (Number.isNaN(date.getTime())) {
    return "Unknown date"
  }
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  })
}

/**
 * A project's diagram tile. No thumbnail: unlike `DiagramCard`, a project
 * diagram may never have been opened locally, so there is no thumbnail-cache
 * entry to draw from (see design.md's "diagram cards require a round trip"
 * trade-off in the `add-projects` change) — the diagram-type glyph stands in.
 */
export function ProjectDiagramCard({
  diagram,
  onDelete,
}: {
  diagram: ResolvedProjectDiagram
  onDelete?: (diagram: ResolvedProjectDiagram) => void
}) {
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const stopPropagation = (event: ReactMouseEvent<HTMLElement>) => {
    event.stopPropagation()
    event.preventDefault()
  }

  const deleteButton = onDelete ? (
    <div
      className="pointer-events-none absolute inset-x-3 top-3 z-20 flex justify-end"
      onClick={stopPropagation}
      onMouseDown={stopPropagation}
    >
      <Button
        type="button"
        variant="ghost"
        size="icon-lg"
        aria-label="Delete diagram"
        className="pointer-events-auto text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 focus-visible:opacity-100"
        onClick={() => setConfirmingDelete(true)}
      >
        <Trash2 className="size-4" aria-hidden />
      </Button>
    </div>
  ) : null

  const confirmDialog = onDelete ? (
    <AlertDialog open={confirmingDelete} onOpenChange={setConfirmingDelete}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete this diagram?</AlertDialogTitle>
          <AlertDialogDescription>
            This permanently deletes the diagram. This action cannot be
            undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={() => onDelete(diagram)}
          >
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  ) : null

  if (diagram.status === "failed") {
    return (
      <Card
        role="listitem"
        className="home-diagram-card group relative flex min-h-40 flex-col items-center justify-center gap-2 overflow-hidden rounded-[var(--apollon-chrome-radius-lg)] border border-[var(--apollon-chrome-border)] bg-[var(--home-card-surface)] p-4 text-center opacity-70"
      >
        <AlertTriangle className="size-6 text-muted-foreground" aria-hidden />
        <p className="text-xs text-muted-foreground">
          Could not load this diagram
        </p>
        {deleteButton}
        {confirmDialog}
      </Card>
    )
  }

  const title = diagram.title.trim() || "Untitled diagram"

  return (
    <Card
      role="listitem"
      className="home-diagram-card group relative flex min-h-40 flex-col gap-0 overflow-hidden rounded-[var(--apollon-chrome-radius-lg)] border border-[var(--apollon-chrome-border)] bg-[var(--home-card-surface)] py-0 shadow-[var(--apollon-chrome-shadow-floating)] transition-all duration-[280ms] ease-[cubic-bezier(0.16,1,0.3,1)] hover:bg-accent-hover hover:shadow-[0_6px_16px_var(--home-shadow-card-hover)]"
    >
      <Link
        {...sharedDiagramRoute(diagram.id)}
        aria-label={`Open ${title}`}
        className="flex h-full w-full flex-col rounded-[inherit] p-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
      >
        <CardHeader className="flex w-full items-center gap-2 px-0 pt-0 pb-2">
          {getDiagramTypeIcon(diagram.type, "size-6")}
        </CardHeader>

        <CardContent className="mt-auto w-full px-0 text-left">
          <p
            className={cn(
              "line-clamp-2 text-sm leading-snug font-medium text-[var(--home-text-strong)]"
            )}
            title={title}
          >
            {title}
          </p>
        </CardContent>

        <CardFooter className="flex w-full items-center justify-between gap-2 px-0 pt-2.5">
          <time
            dateTime={diagram.lastModifiedAt}
            className="truncate text-xs leading-tight font-medium text-muted-foreground"
          >
            {formatLastModified(diagram.lastModifiedAt)}
          </time>
          <Badge className="h-auto rounded border-0 px-2 py-0.5 text-xs leading-tight">
            {getDiagramTypeShortLabel(diagram.type)}
          </Badge>
        </CardFooter>
      </Link>
      {deleteButton}
      {confirmDialog}
    </Card>
  )
}
