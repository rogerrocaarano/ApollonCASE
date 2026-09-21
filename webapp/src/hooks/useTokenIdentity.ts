import { useAuth } from "react-oidc-context"

/**
 * A user's identity derived directly from the current Keycloak token — never
 * persisted or round-tripped through `extension-backend` (see
 * `fix-identity-and-nav-gaps`). `displayName` concatenates `given_name` and
 * `family_name`; either can be absent without the other blanking out.
 */
export function useTokenIdentity(): { displayName: string; email: string | null } {
  const auth = useAuth()
  const profile = auth?.user?.profile
  const displayName = [profile?.given_name, profile?.family_name]
    .filter(Boolean)
    .join(" ")
  return { displayName, email: profile?.email ?? null }
}
