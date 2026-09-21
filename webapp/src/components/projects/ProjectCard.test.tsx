import { describe, expect, it, vi } from "vitest"
import { fireEvent, screen } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { ProjectCard } from "./ProjectCard"

const project = {
  id: "p1",
  name: "My Project",
  description: "desc",
  myPermission: "OWNER" as const,
  createdAt: "now",
  updatedAt: "now",
}

describe("ProjectCard — delete", () => {
  it("asks for confirmation before calling onDelete", async () => {
    const onDelete = vi.fn()
    renderWithRouter(
      <div role="list">
        <ProjectCard project={project} onDelete={onDelete} />
      </div>
    )

    fireEvent.click(
      await screen.findByRole("button", { name: "Project actions" })
    )
    fireEvent.click(await screen.findByRole("menuitem", { name: "Delete" }))

    // Confirmation dialog is up; onDelete has not fired yet.
    await screen.findByText("Delete this project?")
    expect(onDelete).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole("button", { name: "Delete" }))
    expect(onDelete).toHaveBeenCalledWith(project)
  })

  it("does not call onDelete when the confirmation is cancelled", async () => {
    const onDelete = vi.fn()
    renderWithRouter(
      <div role="list">
        <ProjectCard project={project} onDelete={onDelete} />
      </div>
    )

    fireEvent.click(
      await screen.findByRole("button", { name: "Project actions" })
    )
    fireEvent.click(await screen.findByRole("menuitem", { name: "Delete" }))
    await screen.findByText("Delete this project?")

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))

    expect(onDelete).not.toHaveBeenCalled()
  })

  it("without an onDelete handler, no actions menu is rendered", async () => {
    renderWithRouter(
      <div role="list">
        <ProjectCard project={project} />
      </div>
    )
    await screen.findByRole("link", { name: "Open My Project" })
    expect(screen.queryByRole("button", { name: "Project actions" })).toBeNull()
  })
})
