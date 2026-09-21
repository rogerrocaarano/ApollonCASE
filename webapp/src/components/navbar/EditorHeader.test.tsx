import { describe, expect, it } from "vitest"
import { screen } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { HeaderBrandIsland } from "./EditorHeader"

/**
 * `HeaderBrandIsland` carries the editor's back-to-dashboard control
 * (`BackNav` + `useEditorBackTarget`). The full editor chrome only mounts via
 * a portal into a region the real Apollon editor registers (see
 * `EditorChromeHeader`), which the project-diagram route's lightweight fake
 * editor doesn't implement — so this asserts the actual rendered link
 * directly on the component that owns it, at both route shapes
 * `useEditorBackTarget` branches on.
 */
describe("HeaderBrandIsland — back target", () => {
  it("links to the owning project's diagram list when opened from a project diagram", async () => {
    renderWithRouter(<HeaderBrandIsland />, {
      initialEntry: "/projects/p1/diagrams/d1",
      routePaths: ["/projects/$projectId/diagrams/$diagramId"],
    })

    const link = await screen.findByRole("link", { name: "Project diagrams" })
    expect(link.getAttribute("href")).toBe("/projects/p1")
  })

  it("links to the top-level projects list for a non-project diagram", async () => {
    renderWithRouter(<HeaderBrandIsland />, {
      initialEntry: "/shared/abc",
      routePaths: ["/shared/$diagramId"],
    })

    const link = await screen.findByRole("link", { name: "All diagrams" })
    expect(link.getAttribute("href")).toBe("/")
  })
})
