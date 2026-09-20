import { UserManager } from "oidc-client-ts"
import { keycloakAuthority, keycloakClientId } from "@/constants"

// A UserManager built explicitly (rather than letting AuthProvider build one
// internally from settings) so non-React code - like ExtensionApiClient -
// can read the current access token without going through a hook.
//
// automaticSilentRenew defaults to true and renews via the refresh token
// without an iframe as long as one is present (no silent_redirect_uri is
// configured here) - see design.md's "Renovación de sesión" decision.
// userStore also already defaults to sessionStorage, matching that decision.
export const userManager = new UserManager({
  authority: keycloakAuthority,
  client_id: keycloakClientId,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: "openid profile email",
})

export function onSigninCallback() {
  window.history.replaceState({}, document.title, window.location.pathname)
}

export async function getAccessToken(): Promise<string | undefined> {
  const user = await userManager.getUser()
  return user && !user.expired ? user.access_token : undefined
}
