import { describe, expect, it, vi } from "vitest"
import { fireEvent, screen } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { ProjectGallery } from "./ProjectGallery"

const project = {
  id: "p1",
  name: "My Project",
  description: "desc",
  myPermission: "OWNER" as const,
  createdAt: "now",
  updatedAt: "now",
}

describe("ProjectGallery", () => {
  it("navigates to the project's detail route when its card is clicked", async () => {
    const { router } = renderWithRouter(
      <ProjectGallery projects={[project]} onRename={vi.fn()} onDelete={vi.fn()} />,
      { routePaths: ["/", "/projects/$id"] }
    )

    fireEvent.click(
      await screen.findByRole("link", { name: "Open My Project" })
    )

    await vi.waitFor(() => {
      expect(router.state.location.pathname).toBe("/projects/p1")
    })
  })

  it("shows the empty state when there are no projects", async () => {
    renderWithRouter(<ProjectGallery projects={[]} onRename={vi.fn()} onDelete={vi.fn()} />)
    expect(await screen.findByText("No projects yet")).toBeTruthy()
  })

  it("shows Rename/Delete for an OWNER project", async () => {
    renderWithRouter(
      <ProjectGallery projects={[project]} onRename={vi.fn()} onDelete={vi.fn()} />
    )
    fireEvent.click(
      await screen.findByRole("button", { name: "Project actions" })
    )
    expect(screen.getByRole("menuitem", { name: "Rename" })).toBeTruthy()
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeTruthy()
  })

  it("hides the actions menu for a COLLABORATOR or VIEWER project", async () => {
    renderWithRouter(
      <ProjectGallery
        projects={[
          { ...project, id: "p2", myPermission: "COLLABORATOR" },
          { ...project, id: "p3", myPermission: "VIEWER" },
        ]}
        onRename={vi.fn()}
        onDelete={vi.fn()}
      />
    )
    await screen.findAllByRole("link")
    expect(screen.queryByRole("button", { name: "Project actions" })).toBeNull()
  })
})
