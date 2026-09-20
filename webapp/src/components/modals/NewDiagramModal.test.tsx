import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { NewDiagramModal } from "./NewDiagramModal"

const {
  closeModalMock,
  createDiagramMock,
  linkDiagramMock,
  navigateMock,
  toastErrorMock,
} = vi.hoisted(() => ({
  closeModalMock: vi.fn(),
  createDiagramMock: vi.fn(),
  linkDiagramMock: vi.fn(),
  navigateMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock("@/contexts/ModalContext", () => ({
  useModalContext: () => ({ closeModal: closeModalMock }),
}))

vi.mock("@/services/DiagramApiClient", () => ({
  DiagramApiClient: {
    createDiagram: (...args: unknown[]) => createDiagramMock(...args),
  },
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    linkDiagram: (...args: unknown[]) => linkDiagramMock(...args),
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
    linkDiagramMock.mockReset()
    navigateMock.mockReset()
    toastErrorMock.mockReset()
  })

  it("creates the diagram in diagrams-backend, links it to the project, then opens the collaborative editor", async () => {
    createDiagramMock.mockResolvedValue({ id: "d1" })
    linkDiagramMock.mockResolvedValue({ id: "link1", redisId: "d1" })

    render(<NewDiagramModal projectId="p1" />)
    fireEvent.click(screen.getByRole("button", { name: "Create Diagram" }))

    await waitFor(() => {
      expect(createDiagramMock).toHaveBeenCalledTimes(1)
      expect(linkDiagramMock).toHaveBeenCalledWith("p1", "d1")
      expect(navigateMock).toHaveBeenCalledWith(
        expect.objectContaining({
          to: "/shared/$diagramId",
          params: { diagramId: "d1" },
        })
      )
      expect(closeModalMock).toHaveBeenCalled()
    })

    // Link must happen after create, not before/concurrently.
    const createOrder = createDiagramMock.mock.invocationCallOrder[0]
    const linkOrder = linkDiagramMock.mock.invocationCallOrder[0]
    expect(createOrder).toBeLessThan(linkOrder)
  })

  it("does not navigate and surfaces a toast when linking the created diagram fails", async () => {
    createDiagramMock.mockResolvedValue({ id: "d1" })
    linkDiagramMock.mockRejectedValue(new Error("network error"))

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
    expect(linkDiagramMock).not.toHaveBeenCalled()
  })
})
