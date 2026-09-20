import type { ReactNode } from "react"
import { useRouterState } from "@tanstack/react-router"
import { useAuth, useAutoSignin } from "react-oidc-context"
import { AppLoadingScreen } from "@/components/AppLoadingScreen"

// Imprint/privacy must stay reachable without login - legal pages required to
// be directly accessible regardless of authentication (see proposal.md's
// non-goals: this change does not touch diagrams-backend access either, but
// nothing here routes through it, so no other route needs a carve-out).
const PUBLIC_PATHS = new Set(["/imprint", "/privacy"])

interface Props {
  children: ReactNode
}

export function AuthGate({ children }: Props) {
  const path = useRouterState({ select: (s) => s.location.pathname })

  // useAutoSignin triggers a redirect on mount, so it must only run for
  // components that actually mount - i.e. it can't be called unconditionally
  // here and skipped for PUBLIC_PATHS afterwards.
  if (PUBLIC_PATHS.has(path)) return <>{children}</>
  return <ProtectedGate>{children}</ProtectedGate>
}

function ProtectedGate({ children }: Props) {
  const auth = useAuth()
  useAutoSignin()

  if (!auth.isAuthenticated) return <AppLoadingScreen />
  return <>{children}</>
}
