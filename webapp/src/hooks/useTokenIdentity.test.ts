import { describe, expect, it, vi } from "vitest"
import { renderHook } from "@testing-library/react"
import { useTokenIdentity } from "./useTokenIdentity"

const { useAuthMock } = vi.hoisted(() => ({ useAuthMock: vi.fn() }))

vi.mock("react-oidc-context", () => ({
  useAuth: () => useAuthMock(),
}))

describe("useTokenIdentity", () => {
  it("concatenates given_name and family_name, and passes through email", () => {
    useAuthMock.mockReturnValue({
      user: {
        profile: {
          given_name: "Ada",
          family_name: "Lovelace",
          email: "ada@example.com",
        },
      },
    })

    const { result } = renderHook(() => useTokenIdentity())

    expect(result.current).toEqual({
      displayName: "Ada Lovelace",
      email: "ada@example.com",
    })
  })

  it("falls back to whichever name claim is present", () => {
    useAuthMock.mockReturnValue({
      user: { profile: { given_name: "Ada", email: null } },
    })

    const { result } = renderHook(() => useTokenIdentity())

    expect(result.current).toEqual({ displayName: "Ada", email: null })
  })

  it("returns an empty display name and null email when the token has neither", () => {
    useAuthMock.mockReturnValue({ user: { profile: {} } })

    const { result } = renderHook(() => useTokenIdentity())

    expect(result.current).toEqual({ displayName: "", email: null })
  })

  it("handles no authenticated user at all", () => {
    useAuthMock.mockReturnValue({ user: undefined })

    const { result } = renderHook(() => useTokenIdentity())

    expect(result.current).toEqual({ displayName: "", email: null })
  })
})
