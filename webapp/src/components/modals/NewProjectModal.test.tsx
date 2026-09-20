import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { NewProjectModal } from "./NewProjectModal"

const { closeModalMock, createMock, toastErrorMock } = vi.hoisted(() => ({
  closeModalMock: vi.fn(),
  createMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock("@/contexts/ModalContext", () => ({
  useModalContext: () => ({ closeModal: closeModalMock }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    create: (...args: unknown[]) => createMock(...args),
  },
}))

vi.mock("react-toastify", () => ({
  toast: { error: toastErrorMock },
}))

describe("NewProjectModal", () => {
  beforeEach(() => {
    closeModalMock.mockReset()
    createMock.mockReset()
    toastErrorMock.mockReset()
  })

  it("creates a project and reports it to the caller", async () => {
    const project = {
      id: "p1",
      name: "New project",
      description: "desc",
      ownerId: "u1",
      createdAt: "now",
      updatedAt: "now",
    }
    createMock.mockResolvedValue(project)
    const onCreated = vi.fn()

    render(<NewProjectModal onCreated={onCreated} />)

    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "New project" },
    })
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: "desc" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create Project" }))

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledWith({
        name: "New project",
        description: "desc",
      })
      expect(onCreated).toHaveBeenCalledWith(project)
      expect(closeModalMock).toHaveBeenCalled()
    })
  })

  it("disables Create with a blank name", () => {
    render(<NewProjectModal />)
    const button = screen.getByRole("button", {
      name: "Create Project",
    }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })

  it("shows an error toast and keeps the modal open when creation fails", async () => {
    createMock.mockRejectedValue(new Error("network error"))

    render(<NewProjectModal />)

    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "New project" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Create Project" }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalled()
    })
    expect(closeModalMock).not.toHaveBeenCalled()
  })
})
