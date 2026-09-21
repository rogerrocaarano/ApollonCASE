import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, render, screen } from "@testing-library/react"
import { UserMenu } from "./UserMenu"

const { useAuthMock, signoutRedirectMock } = vi.hoisted(() => ({
  useAuthMock: vi.fn(),
  signoutRedirectMock: vi.fn(),
}))

vi.mock("react-oidc-context", () => ({
  useAuth: () => useAuthMock(),
}))

describe("UserMenu", () => {
  beforeEach(() => {
    signoutRedirectMock.mockReset()
    useAuthMock.mockReturnValue({
      isAuthenticated: true,
      signoutRedirect: signoutRedirectMock,
      user: {
        profile: {
          given_name: "Ada",
          family_name: "Lovelace",
          email: "ada@example.com",
        },
      },
    })
  })

  it("renders nothing when unauthenticated", () => {
    useAuthMock.mockReturnValue({ isAuthenticated: false })
    render(<UserMenu />)
    expect(screen.queryByRole("button", { name: "Account" })).toBeNull()
  })

  it("shows the token-derived name and email", async () => {
    render(<UserMenu />)
    fireEvent.click(screen.getByRole("button", { name: "Account" }))
    await screen.findByText("Signed in as Ada Lovelace · ada@example.com")
  })

  it("signs out when Sign out is clicked", async () => {
    render(<UserMenu />)
    fireEvent.click(screen.getByRole("button", { name: "Account" }))
    fireEvent.click(await screen.findByRole("menuitem", { name: "Sign out" }))
    expect(signoutRedirectMock).toHaveBeenCalled()
  })
})
