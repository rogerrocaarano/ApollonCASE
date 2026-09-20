import { Skeleton } from "@tumaet/ui/components/skeleton"

type ProjectGallerySkeletonProps = {
  count?: number
}

export const ProjectGallerySkeleton = ({
  count = 6,
}: ProjectGallerySkeletonProps) => {
  const cards = Array.from({ length: count }, (_, i) => i)

  return (
    <div
      role="list"
      aria-hidden="true"
      aria-label="Loading projects"
      className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,240px),1fr))] justify-start gap-4 md:grid-cols-[repeat(auto-fill,minmax(min(100%,260px),1fr))] md:gap-6 xl:grid-cols-[repeat(auto-fill,minmax(min(100%,280px),1fr))]"
    >
      {cards.map((index) => (
        <div
          key={index}
          className="home-diagram-card flex min-h-40 w-full flex-col gap-2 overflow-hidden rounded-[var(--apollon-chrome-radius-lg)] border border-[var(--apollon-chrome-border)] bg-[var(--home-card-surface)] p-4 shadow-[var(--apollon-chrome-shadow-floating)]"
        >
          <Skeleton className="h-4 w-3/5" />
          <Skeleton className="h-3 w-4/5" />
          <Skeleton className="mt-auto h-3 w-2/5" />
        </div>
      ))}
    </div>
  )
}
