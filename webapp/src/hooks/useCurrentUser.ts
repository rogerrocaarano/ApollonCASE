import { useQuery } from "@tanstack/react-query"
import { useAuth } from "react-oidc-context"
import { ExtensionApiClient } from "@/services/ExtensionApiClient"

/** Shared cache key for the current user, so every caller reads/writes the same entry. */
export const CURRENT_USER_QUERY_KEY = ["extension-backend", "me"] as const

/**
 * The authenticated user's own identity (`GET /api/v1/me`), cached once and
 * shared across every consumer (the Help menu's account section, the
 * collaboration entry flow, ...) instead of each one running its own query.
 */
export function useCurrentUser() {
  const auth = useAuth()
  return useQuery({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: ExtensionApiClient.me,
    enabled: auth.isAuthenticated,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
}
