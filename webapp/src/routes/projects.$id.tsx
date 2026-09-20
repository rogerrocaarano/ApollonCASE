import { createFileRoute } from "@tanstack/react-router"
import { ProjectDetailPage } from "@/pages/ProjectDetailPage"

export const Route = createFileRoute("/projects/$id")({
  component: ProjectDetailPage,
})
