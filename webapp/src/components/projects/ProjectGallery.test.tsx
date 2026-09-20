import { describe, expect, it, vi } from "vitest"
import { fireEvent, screen } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { ProjectGallery } from "./ProjectGallery"

const project = {
  id: "p1",
  name: "My Project",
  description: "desc",
  ownerId: "u1",
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
})
