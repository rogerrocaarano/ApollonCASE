import { useState, type MouseEvent as ReactMouseEvent } from "react"
import { MoreVertical } from "lucide-react"
import { Link } from "@tanstack/react-router"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@tumaet/ui/components/dropdown-menu"
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
import { Button } from "@tumaet/ui/components/button"
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from "@tumaet/ui/components/card"
import { cn } from "@tumaet/ui/lib/utils"
import type { Project } from "@/types"

const formatUpdatedAt = (isoDate: string) => {
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

export type ProjectCardProps = {
  project: Project
  onRename?: (project: Project) => void
  onDelete?: (project: Project) => void
}

/**
 * Project tile: name, description, last-modified date, rename and delete
 * actions. Deliberately lighter than `DiagramCard` — thumbnails, favorites,
 * and the local/shared distinction are diagram-specific concerns that don't
 * apply to a project.
 */
export function ProjectCard({ project, onRename, onDelete }: ProjectCardProps) {
  const title = project.name.trim() || "Untitled project"
  const description = project.description.trim()
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const stopPropagation = (
    event: ReactMouseEvent<HTMLElement>
  ) => {
    event.stopPropagation()
    event.preventDefault()
  }

  return (
    <Card
      role="listitem"
      className="home-diagram-card group relative flex min-h-40 flex-col gap-0 overflow-hidden rounded-[var(--apollon-chrome-radius-lg)] border border-[var(--apollon-chrome-border)] bg-[var(--home-card-surface)] py-0 shadow-[var(--apollon-chrome-shadow-floating)] transition-all duration-[280ms] ease-[cubic-bezier(0.16,1,0.3,1)] hover:bg-accent-hover hover:shadow-[0_6px_16px_var(--home-shadow-card-hover)]"
    >
      <Link
        to="/projects/$id"
        params={{ id: project.id }}
        aria-label={`Open ${title}`}
        className="flex h-full w-full flex-col rounded-[inherit] p-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
      >
        <CardHeader className="w-full px-0 pt-0 pb-2">
          <p
            className="line-clamp-2 text-sm leading-snug font-medium text-[var(--home-text-strong)]"
            title={title}
          >
            {title}
          </p>
        </CardHeader>

        <CardContent className="mt-auto w-full px-0 text-left">
          <p
            className={cn(
              "line-clamp-3 text-xs leading-snug",
              description ? "text-muted-foreground" : "text-muted-foreground italic"
            )}
          >
            {description || "No description"}
          </p>
        </CardContent>

        <CardFooter className="w-full px-0 pt-2.5">
          <time
            dateTime={project.updatedAt}
            title={new Date(project.updatedAt).toLocaleString()}
            className="truncate text-xs leading-tight font-medium text-muted-foreground"
          >
            Updated {formatUpdatedAt(project.updatedAt)}
          </time>
        </CardFooter>
      </Link>

      {onRename || onDelete ? (
        <div
          className="pointer-events-none absolute inset-x-3 top-3 z-20 flex justify-end"
          onClick={stopPropagation}
          onMouseDown={stopPropagation}
        >
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-lg"
                  aria-label="Project actions"
                  className="pointer-events-auto text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 focus-visible:opacity-100 aria-expanded:opacity-100"
                />
              }
            >
              <MoreVertical className="size-5" aria-hidden="true" />
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="end"
              aria-label="Project actions"
              sideOffset={8}
            >
              {onRename ? (
                <DropdownMenuItem onClick={() => onRename(project)}>
                  Rename
                </DropdownMenuItem>
              ) : null}
              {onRename && onDelete ? <DropdownMenuSeparator /> : null}
              {onDelete ? (
                <DropdownMenuItem
                  variant="destructive"
                  closeOnClick={false}
                  onClick={() => setConfirmingDelete(true)}
                >
                  Delete
                </DropdownMenuItem>
              ) : null}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ) : null}

      {onDelete ? (
        <AlertDialog
          open={confirmingDelete}
          onOpenChange={setConfirmingDelete}
        >
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Delete this project?</AlertDialogTitle>
              <AlertDialogDescription>
                This permanently deletes &quot;{title}&quot; and all of its
                diagrams. This action cannot be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction
                variant="destructive"
                onClick={() => onDelete(project)}
              >
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      ) : null}
    </Card>
  )
}
