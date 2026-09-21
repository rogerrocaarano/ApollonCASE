import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { NewDiagramModal } from "./NewDiagramModal"

const { closeModalMock, createDiagramMock, navigateMock, toastErrorMock } =
  vi.hoisted(() => ({
    closeModalMock: vi.fn(),
    createDiagramMock: vi.fn(),
    navigateMock: vi.fn(),
    toastErrorMock: vi.fn(),
  }))

vi.mock("@/contexts/ModalContext", () => ({
  useModalContext: () => ({ closeModal: closeModalMock }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    createDiagram: (...args: unknown[]) => createDiagramMock(...args),
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

describe("NewDiagramModal — project-scoped creation", () => {
  beforeEach(() => {
    closeModalMock.mockReset()
    createDiagramMock.mockReset()
    navigateMock.mockReset()
    toastErrorMock.mockReset()
  })

  it("creates the diagram through extension-backend, then opens the project-diagram editor", async () => {
    createDiagramMock.mockResolvedValue({
      id: "d1",
      projectId: "p1",
      status: "ready",
    })

    render(<NewDiagramModal projectId="p1" />)
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(createDiagramMock).toHaveBeenCalledTimes(1)
      expect(createDiagramMock).toHaveBeenCalledWith(
        "p1",
        expect.objectContaining({ type: "ClassDiagram" })
      )
      expect(navigateMock).toHaveBeenCalledWith({
        to: "/projects/$projectId/diagrams/$diagramId",
        params: { projectId: "p1", diagramId: "d1" },
      })
      expect(closeModalMock).toHaveBeenCalled()
    })
  })

  it("does not navigate and surfaces a toast when creation fails", async () => {
    createDiagramMock.mockRejectedValue(new Error("network error"))

    render(<NewDiagramModal projectId="p1" />)
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalled()
    })
    expect(navigateMock).not.toHaveBeenCalled()
    expect(closeModalMock).not.toHaveBeenCalled()
  })

  it("without a projectId still uses the local (IndexedDB) creation path", async () => {
    render(<NewDiagramModal />)
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith(
        expect.objectContaining({ to: "/local/$id" })
      )
    })
    expect(createDiagramMock).not.toHaveBeenCalled()
  })
})
