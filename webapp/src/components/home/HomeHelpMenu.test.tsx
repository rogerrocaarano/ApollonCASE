import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, screen, waitFor } from "@testing-library/react"
import { QueryClientProvider } from "@tanstack/react-query"
import { renderWithRouter } from "@/test/renderWithRouter"
import { createTestQueryClient } from "@/test/queryTestUtils"
import { HomeHelpMenu } from "./HomeHelpMenu"

const { meMock, openModalMock, signoutRedirectMock } = vi.hoisted(() => ({
  meMock: vi.fn(),
  openModalMock: vi.fn(),
  signoutRedirectMock: vi.fn(),
}))

vi.mock("react-oidc-context", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    signoutRedirect: signoutRedirectMock,
  }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ExtensionApiClient: {
    me: () => meMock(),
  },
}))

vi.mock("@/contexts", () => ({
  useModalContext: () => ({ openModal: openModalMock, closeModal: vi.fn() }),
}))

const renderMenu = () => {
  const client = createTestQueryClient()
  return renderWithRouter(<HomeHelpMenu />, {
    wrapper: (children) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  })
}

describe("HomeHelpMenu — profile", () => {
  beforeEach(() => {
    meMock.mockReset()
    openModalMock.mockReset()
    signoutRedirectMock.mockReset()
  })

  it("falls back to keycloakId when no display name is set", async () => {
    meMock.mockResolvedValue({
      id: "u1",
      keycloakId: "kc-1",
      displayName: null,
      email: null,
    })

    renderMenu()
    fireEvent.click(await screen.findByRole("button", { name: "Help" }))

    await screen.findByText("Signed in as kc-1")
  })

  it("shows the display name instead of keycloakId once set", async () => {
    meMock.mockResolvedValue({
      id: "u1",
      keycloakId: "kc-1",
      displayName: "Ada Lovelace",
      email: "ada@example.com",
    })

    renderMenu()
    fireEvent.click(await screen.findByRole("button", { name: "Help" }))

    await screen.findByText("Signed in as Ada Lovelace")
  })

  it("opens the edit-profile modal with the current user", async () => {
    const user = {
      id: "u1",
      keycloakId: "kc-1",
      displayName: "Ada Lovelace",
      email: "ada@example.com",
    }
    meMock.mockResolvedValue(user)

    renderMenu()
    fireEvent.click(await screen.findByRole("button", { name: "Help" }))
    await screen.findByText("Signed in as Ada Lovelace")

    fireEvent.click(screen.getByRole("menuitem", { name: /edit profile/i }))

    await waitFor(() => {
      expect(openModalMock).toHaveBeenCalledWith(
        "EDIT_PROFILE",
        expect.objectContaining({ user, dialogVariant: "home" })
      )
    })
  })
})
