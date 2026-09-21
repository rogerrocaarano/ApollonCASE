import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { NewClassDiagramModal } from "./NewClassDiagramModal"

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

describe("NewClassDiagramModal", () => {
  beforeEach(() => {
    closeModalMock.mockReset()
    createDiagramMock.mockReset()
    navigateMock.mockReset()
    toastErrorMock.mockReset()
  })

  it("disables creation until a name is entered", async () => {
    render(<NewClassDiagramModal projectId="p1" />)

    const createButton = screen.getByRole("button", {
      name: "Create Diagram",
    }) as HTMLButtonElement
    expect(createButton.disabled).toBe(true)

    fireEvent.change(screen.getByPlaceholderText("Enter diagram name"), {
      target: { value: "   " },
    })
    expect(createButton.disabled).toBe(true)

    fireEvent.change(screen.getByPlaceholderText("Enter diagram name"), {
      target: { value: "My Diagram" },
    })
    expect(createButton.disabled).toBe(false)
  })

  it("creates the diagram through extension-backend, then opens the project-diagram editor", async () => {
    createDiagramMock.mockResolvedValue({
      id: "d1",
      projectId: "p1",
      status: "ready",
    })

    render(<NewClassDiagramModal projectId="p1" />)
    fireEvent.change(screen.getByPlaceholderText("Enter diagram name"), {
      target: { value: "My Diagram" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(createDiagramMock).toHaveBeenCalledTimes(1)
      expect(createDiagramMock).toHaveBeenCalledWith(
        "p1",
        expect.objectContaining({ type: "ClassDiagram", title: "My Diagram" })
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

    render(<NewClassDiagramModal projectId="p1" />)
    fireEvent.change(screen.getByPlaceholderText("Enter diagram name"), {
      target: { value: "My Diagram" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalled()
    })
    expect(navigateMock).not.toHaveBeenCalled()
    expect(closeModalMock).not.toHaveBeenCalled()
  })

  it("without a projectId still uses the local (IndexedDB) creation path", async () => {
    render(<NewClassDiagramModal />)
    fireEvent.change(screen.getByPlaceholderText("Enter diagram name"), {
      target: { value: "My Diagram" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith(
        expect.objectContaining({ to: "/local/$id" })
      )
    })
    expect(createDiagramMock).not.toHaveBeenCalled()
  })
})
