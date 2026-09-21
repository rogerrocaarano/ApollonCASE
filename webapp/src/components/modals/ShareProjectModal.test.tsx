import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { ShareProjectModal } from "./ShareProjectModal"

const { closeModalMock, shareProjectMock, toastSuccessMock, FakeExtensionApiError } =
  vi.hoisted(() => {
    class FakeExtensionApiError extends Error {
      status: number
      constructor(status: number) {
        super(`extension-backend request failed with status ${status}`)
        this.status = status
      }
    }
    return {
      closeModalMock: vi.fn(),
      shareProjectMock: vi.fn(),
      toastSuccessMock: vi.fn(),
      FakeExtensionApiError,
    }
  })

vi.mock("@/contexts/ModalContext", () => ({
  useModalContext: () => ({ closeModal: closeModalMock }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    shareProject: (...args: unknown[]) => shareProjectMock(...args),
  },
  ExtensionApiError: FakeExtensionApiError,
}))

vi.mock("react-toastify", () => ({
  toast: { success: toastSuccessMock, error: vi.fn() },
}))

describe("ShareProjectModal", () => {
  beforeEach(() => {
    closeModalMock.mockReset()
    shareProjectMock.mockReset()
    toastSuccessMock.mockReset()
  })

  it("shares the project with the entered email and selected role", async () => {
    shareProjectMock.mockResolvedValue({ userId: "u2", role: "VIEWER" })

    render(<ShareProjectModal projectId="p1" />)

    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "friend@example.com" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Viewer" }))
    fireEvent.click(screen.getByRole("button", { name: "Share" }))

    await waitFor(() => {
      expect(shareProjectMock).toHaveBeenCalledWith("p1", {
        email: "friend@example.com",
        role: "VIEWER",
      })
      expect(toastSuccessMock).toHaveBeenCalled()
      expect(closeModalMock).toHaveBeenCalled()
    })
  })

  it("defaults the role to Collaborator", async () => {
    shareProjectMock.mockResolvedValue({ userId: "u2", role: "COLLABORATOR" })

    render(<ShareProjectModal projectId="p1" />)

    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "friend@example.com" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Share" }))

    await waitFor(() => {
      expect(shareProjectMock).toHaveBeenCalledWith("p1", {
        email: "friend@example.com",
        role: "COLLABORATOR",
      })
    })
  })

  it("shows a not-found message on a 404 and keeps the modal open", async () => {
    shareProjectMock.mockRejectedValue(new FakeExtensionApiError(404))

    render(<ShareProjectModal projectId="p1" />)

    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "unknown@example.com" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Share" }))

    await screen.findByText(/no user found with that email/i)
    expect(closeModalMock).not.toHaveBeenCalled()
  })

  it("shows a generic error on any other failure and keeps the modal open", async () => {
    shareProjectMock.mockRejectedValue(new FakeExtensionApiError(403))

    render(<ShareProjectModal projectId="p1" />)

    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "owner@example.com" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Share" }))

    await screen.findByText("Could not share the project. Please try again.")
    expect(closeModalMock).not.toHaveBeenCalled()
  })

  it("disables Share with a blank email", () => {
    render(<ShareProjectModal projectId="p1" />)
    const button = screen.getByRole("button", {
      name: "Share",
    }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })
})
