import { describe, expect, it } from "vitest"
import { screen } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { useEditorBackTarget } from "./useEditorBackTarget"

const Probe = () => {
  const target = useEditorBackTarget()
  return (
    <div>
      <span data-testid="to">{target.to}</span>
      <span data-testid="label">{target.label}</span>
      {"params" in target ? (
        <span data-testid="projectId">{target.params.id}</span>
      ) : null}
    </div>
  )
}

describe("useEditorBackTarget", () => {
  it("points back to the project's diagram list when opened from a project diagram", async () => {
    renderWithRouter(<Probe />, {
      initialEntry: "/projects/p1/diagrams/d1",
      routePaths: ["/projects/$projectId/diagrams/$diagramId"],
    })

    expect((await screen.findByTestId("to")).textContent).toBe(
      "/projects/$id"
    )
    expect(screen.getByTestId("label").textContent).toBe("Project diagrams")
    expect(screen.getByTestId("projectId").textContent).toBe("p1")
  })

  it("points to the top-level projects list for a non-project diagram", async () => {
    renderWithRouter(<Probe />, {
      initialEntry: "/shared/abc",
      routePaths: ["/shared/$diagramId"],
    })

    expect((await screen.findByTestId("to")).textContent).toBe("/")
    expect(screen.getByTestId("label").textContent).toBe("All diagrams")
    expect(screen.queryByTestId("projectId")).toBeNull()
  })
})
