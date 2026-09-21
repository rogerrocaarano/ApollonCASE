import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, screen, waitFor, within } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { ProjectDetailPage } from "./ProjectDetailPage"

const {
  getMock,
  listDiagramsMock,
  openModalMock,
  deleteProjectMock,
  deleteDiagramMock,
  navigateMock,
  toastErrorMock,
} = vi.hoisted(() => ({
  getMock: vi.fn(),
  listDiagramsMock: vi.fn(),
  openModalMock: vi.fn(),
  deleteProjectMock: vi.fn(),
  deleteDiagramMock: vi.fn(),
  navigateMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock("@/contexts", () => ({
  useModalContext: () => ({ openModal: openModalMock, closeModal: vi.fn() }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    get: (...args: unknown[]) => getMock(...args),
    listDiagrams: (...args: unknown[]) => listDiagramsMock(...args),
    deleteProject: (...args: unknown[]) => deleteProjectMock(...args),
    deleteDiagram: (...args: unknown[]) => deleteDiagramMock(...args),
  },
}))

vi.mock("react-toastify", () => ({
  toast: { error: toastErrorMock },
}))

vi.mock("@tanstack/react-router", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@tanstack/react-router")>()
  return { ...actual, useNavigate: () => navigateMock }
})

const project = {
  id: "p1",
  name: "My Project",
  description: "A test project",
  ownerId: "u1",
  createdAt: "now",
  updatedAt: "now",
}

const readyDiagram = {
  id: "d1",
  projectId: "p1",
  status: "ready" as const,
  title: "My Diagram",
  type: "ClassDiagram",
  updatedAt: "2026-01-02T00:00:00.000Z",
}

const renderPage = () =>
  renderWithRouter(<ProjectDetailPage />, {
    initialEntry: "/projects/p1",
    // Registered so <ProjectDiagramCard>'s <Link to="/projects/.../diagrams/...">
    // resolves — it doesn't need to actually render anything here.
    routePaths: ["/projects/$id", "/projects/$projectId/diagrams/$diagramId"],
  })

describe("ProjectDetailPage", () => {
  beforeEach(() => {
    getMock.mockReset()
    listDiagramsMock.mockReset()
    openModalMock.mockReset()
    deleteProjectMock.mockReset()
    deleteDiagramMock.mockReset()
    navigateMock.mockReset()
    toastErrorMock.mockReset()
    getMock.mockResolvedValue(project)
  })

  it("renders the project's diagrams once resolved, with enriched metadata from extension-backend", async () => {
    listDiagramsMock.mockResolvedValue([readyDiagram])

    renderPage()

    await screen.findByText("My Project")
    await screen.findByText("My Diagram")
  })

  it("shows a failure state for a diagram extension-backend could not resolve", async () => {
    listDiagramsMock.mockResolvedValue([
      { id: "d1", projectId: "p1", status: "failed" },
    ])

    renderPage()

    await screen.findByText("Could not load this diagram")
  })

  it("shows an empty state when the project has no diagrams", async () => {
    listDiagramsMock.mockResolvedValue([])

    renderPage()

    await screen.findByText("No diagrams yet")
  })

  it("opens the new-diagram modal scoped to this project", async () => {
    listDiagramsMock.mockResolvedValue([])

    renderPage()

    const button = await screen.findByRole("button", { name: /new diagram/i })
    button.click()

    await waitFor(() => {
      expect(openModalMock).toHaveBeenCalledWith("NEW_DIAGRAM", {
        projectId: "p1",
      })
    })
  })

  it("deletes the project after confirmation and navigates home", async () => {
    listDiagramsMock.mockResolvedValue([])
    deleteProjectMock.mockResolvedValue(undefined)

    renderPage()

    fireEvent.click(await screen.findByRole("button", { name: "Delete" }))
    const dialog = await screen.findByRole("alertdialog")
    within(dialog).getByText("Delete this project?")
    fireEvent.click(within(dialog).getByRole("button", { name: "Delete" }))

    await waitFor(() => {
      expect(deleteProjectMock).toHaveBeenCalledWith("p1")
      expect(navigateMock).toHaveBeenCalledWith({ to: "/" })
    })
  })

  it("does not delete the project when the confirmation is cancelled", async () => {
    listDiagramsMock.mockResolvedValue([])

    renderPage()

    fireEvent.click(await screen.findByRole("button", { name: "Delete" }))
    await screen.findByText("Delete this project?")
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }))

    expect(deleteProjectMock).not.toHaveBeenCalled()
  })

  it("deletes a diagram after confirmation and removes it from the list", async () => {
    listDiagramsMock.mockResolvedValue([readyDiagram])
    deleteDiagramMock.mockResolvedValue(undefined)

    renderPage()

    await screen.findByText("My Diagram")
    fireEvent.click(await screen.findByRole("button", { name: "Delete diagram" }))
    await screen.findByText("Delete this diagram?")
    fireEvent.click(screen.getByRole("button", { name: "Delete" }))

    await waitFor(() => {
      expect(deleteDiagramMock).toHaveBeenCalledWith("p1", "d1")
    })
    await waitFor(() => {
      expect(screen.queryByText("My Diagram")).toBeNull()
    })
  })

  it("surfaces a toast and keeps the diagram when deleting it fails", async () => {
    listDiagramsMock.mockResolvedValue([readyDiagram])
    deleteDiagramMock.mockRejectedValue(new Error("network error"))

    renderPage()

    await screen.findByText("My Diagram")
    fireEvent.click(await screen.findByRole("button", { name: "Delete diagram" }))
    await screen.findByText("Delete this diagram?")
    fireEvent.click(screen.getByRole("button", { name: "Delete" }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalled()
    })
    expect(screen.getByText("My Diagram")).toBeTruthy()
  })
})
