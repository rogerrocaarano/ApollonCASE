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
      })
      expect(onUpdated).toHaveBeenCalledWith(updated)
      expect(closeModalMock).toHaveBeenCalled()
    })
  })

  it("pre-fills the current display name and shows email read-only", () => {
    render(<EditProfileModal user={user} />)
    expect(
      (screen.getByLabelText("Display name") as HTMLInputElement).value
    ).toBe("Old name")
    expect(
      screen.queryByLabelText("Email") as HTMLInputElement | null
    ).toBeNull()
    expect(screen.getByText("old@example.com")).toBeTruthy()
  })

  it("pre-fills a blank display name when the user has no profile set yet, and shows no email row", () => {
    render(
      <EditProfileModal
        user={{ id: "u1", keycloakId: "kc-1", displayName: null, email: null }}
      />
    )
    expect(
      (screen.getByLabelText("Display name") as HTMLInputElement).value
    ).toBe("")
    expect(screen.queryByText("Email")).toBeNull()
  })

  it("disables Save with a blank name when requireDisplayName is set", () => {
    render(
      <EditProfileModal
        user={{ id: "u1", keycloakId: "kc-1", displayName: null, email: null }}
        requireDisplayName
      />
    )
    const button = screen.getByRole("button", { name: "Save" }) as HTMLButtonElement
    expect(button.disabled).toBe(true)

    fireEvent.change(screen.getByLabelText("Display name"), {
      target: { value: "New name" },
    })
    expect(button.disabled).toBe(false)
  })

  it("does not disable Save with a blank name when requireDisplayName is unset", () => {
    render(
      <EditProfileModal
        user={{ id: "u1", keycloakId: "kc-1", displayName: null, email: null }}
      />
    )
    const button = screen.getByRole("button", { name: "Save" }) as HTMLButtonElement
    expect(button.disabled).toBe(false)
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
