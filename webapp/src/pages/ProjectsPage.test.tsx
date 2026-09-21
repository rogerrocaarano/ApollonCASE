import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, screen } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { ProjectsPage } from "./ProjectsPage"

const { listMock, openModalMock, signoutRedirectMock } = vi.hoisted(() => ({
  listMock: vi.fn(),
  openModalMock: vi.fn(),
  signoutRedirectMock: vi.fn(),
}))

vi.mock("@/contexts", () => ({
  useModalContext: () => ({ openModal: openModalMock, closeModal: vi.fn() }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    list: (...args: unknown[]) => listMock(...args),
  },
}))

vi.mock("react-oidc-context", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    signoutRedirect: signoutRedirectMock,
    user: { profile: { given_name: "Ada", family_name: "Lovelace", email: null } },
  }),
}))

describe("ProjectsPage", () => {
  beforeEach(() => {
    listMock.mockReset()
    signoutRedirectMock.mockReset()
    listMock.mockResolvedValue([])
  })

  it("offers a sign-out control that signs the user out", async () => {
    renderWithRouter(<ProjectsPage />)

    fireEvent.click(await screen.findByRole("button", { name: "Account" }))
    fireEvent.click(await screen.findByRole("menuitem", { name: "Sign out" }))

    expect(signoutRedirectMock).toHaveBeenCalled()
  })
})
