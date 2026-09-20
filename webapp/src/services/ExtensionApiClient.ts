import { extensionServerURL } from "@/constants"
import { getAccessToken } from "@/auth/oidcConfig"

export interface CurrentUser {
  id: string
  keycloakId: string
}

async function request<T>(path: string): Promise<T> {
  const token = await getAccessToken()
  const headers: Record<string, string> = { Accept: "application/json" }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${extensionServerURL}${path}`, { headers })
  if (!res.ok) {
    throw new Error(`extension-backend request failed with status ${res.status}`)
  }
  return res.json() as Promise<T>
}

export const ExtensionApiClient = {
  me: () => request<CurrentUser>("/api/v1/me"),
}
