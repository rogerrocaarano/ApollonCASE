import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { RenameProjectModal } from "./RenameProjectModal"

const { closeModalMock, renameMock, toastErrorMock } = vi.hoisted(() => ({
  closeModalMock: vi.fn(),
  renameMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock("@/contexts/ModalContext", () => ({
  useModalContext: () => ({ closeModal: closeModalMock }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    rename: (...args: unknown[]) => renameMock(...args),
  },
}))

vi.mock("react-toastify", () => ({
  toast: { error: toastErrorMock },
}))

const project = {
  id: "p1",
  name: "Old name",
  description: "Old description",
  myPermission: "OWNER" as const,
  createdAt: "now",
  updatedAt: "now",
}

describe("RenameProjectModal", () => {
  beforeEach(() => {
    closeModalMock.mockReset()
    renameMock.mockReset()
    toastErrorMock.mockReset()
  })

  it("renames the project and reports the update to the caller", async () => {
    const updated = { ...project, name: "New name" }
    renameMock.mockResolvedValue(updated)
    const onRenamed = vi.fn()

    render(<RenameProjectModal project={project} onRenamed={onRenamed} />)

    const nameInput = screen.getByLabelText("Name")
    fireEvent.change(nameInput, { target: { value: "New name" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))

    await waitFor(() => {
      expect(renameMock).toHaveBeenCalledWith("p1", {
        name: "New name",
        description: "Old description",
      })
      expect(onRenamed).toHaveBeenCalledWith(updated)
      expect(closeModalMock).toHaveBeenCalled()
    })
  })

  it("pre-fills the current name and description", () => {
    render(<RenameProjectModal project={project} />)
    expect((screen.getByLabelText("Name") as HTMLInputElement).value).toBe(
      "Old name"
    )
    expect(
      (screen.getByLabelText("Description") as HTMLInputElement).value
    ).toBe("Old description")
  })

  it("shows an error toast and keeps the modal open when rename fails", async () => {
    renameMock.mockRejectedValue(new Error("network error"))

    render(<RenameProjectModal project={project} />)
    fireEvent.click(screen.getByRole("button", { name: "Save" }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalled()
    })
    expect(closeModalMock).not.toHaveBeenCalled()
  })
})
