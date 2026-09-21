import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { EditProfileModal } from "./EditProfileModal"

const { closeModalMock, updateMeMock, toastErrorMock } = vi.hoisted(() => ({
  closeModalMock: vi.fn(),
  updateMeMock: vi.fn(),
  toastErrorMock: vi.fn(),
}))

vi.mock("@/contexts/ModalContext", () => ({
  useModalContext: () => ({ closeModal: closeModalMock }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ExtensionApiClient: {
    updateMe: (...args: unknown[]) => updateMeMock(...args),
  },
}))

vi.mock("react-toastify", () => ({
  toast: { error: toastErrorMock },
}))

const user = {
  id: "u1",
  keycloakId: "kc-1",
  displayName: "Old name",
  email: "old@example.com",
}

describe("EditProfileModal", () => {
  beforeEach(() => {
    closeModalMock.mockReset()
    updateMeMock.mockReset()
    toastErrorMock.mockReset()
  })

  it("updates the profile and reports it to the caller", async () => {
    const updated = { ...user, displayName: "New name" }
    updateMeMock.mockResolvedValue(updated)
    const onUpdated = vi.fn()

    render(<EditProfileModal user={user} onUpdated={onUpdated} />)

    fireEvent.change(screen.getByLabelText("Display name"), {
      target: { value: "New name" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))

    await waitFor(() => {
      expect(updateMeMock).toHaveBeenCalledWith({
        displayName: "New name",
        email: "old@example.com",
      })
      expect(onUpdated).toHaveBeenCalledWith(updated)
      expect(closeModalMock).toHaveBeenCalled()
    })
  })

  it("pre-fills the current display name and email", () => {
    render(<EditProfileModal user={user} />)
    expect(
      (screen.getByLabelText("Display name") as HTMLInputElement).value
    ).toBe("Old name")
    expect((screen.getByLabelText("Email") as HTMLInputElement).value).toBe(
      "old@example.com"
    )
  })

  it("pre-fills blank fields when the user has no profile set yet", () => {
    render(
      <EditProfileModal
        user={{ id: "u1", keycloakId: "kc-1", displayName: null, email: null }}
      />
    )
    expect(
      (screen.getByLabelText("Display name") as HTMLInputElement).value
    ).toBe("")
    expect((screen.getByLabelText("Email") as HTMLInputElement).value).toBe("")
  })

  it("shows an error toast and keeps the modal open when saving fails", async () => {
    updateMeMock.mockRejectedValue(new Error("invalid email"))

    render(<EditProfileModal user={user} />)
    fireEvent.click(screen.getByRole("button", { name: "Save" }))

    await waitFor(() => {
      expect(toastErrorMock).toHaveBeenCalled()
    })
    expect(closeModalMock).not.toHaveBeenCalled()
  })
})
