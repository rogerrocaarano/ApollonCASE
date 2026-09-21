import type { UMLModel } from "@tumaet/apollon"
import { extensionServerURL } from "@/constants"
import { getAccessToken } from "@/auth/oidcConfig"
import type { Project, ProjectDiagram } from "@/types"

export interface CurrentUser {
  id: string
  keycloakId: string
  displayName: string | null
  email: string | null
}

interface RequestOpts {
  method?: "GET" | "POST" | "PATCH" | "DELETE"
  body?: unknown
}

async function request<T>(path: string, opts: RequestOpts = {}): Promise<T> {
  const token = await getAccessToken()
  const headers: Record<string, string> = { Accept: "application/json" }
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers["Content-Type"] = "application/json"

  const res = await fetch(`${extensionServerURL}${path}`, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  })
  if (!res.ok) {
    throw new Error(`extension-backend request failed with status ${res.status}`)
  }
  if (res.status === 204) {
    return undefined as unknown as T
  }
  return res.json() as Promise<T>
}

export const ExtensionApiClient = {
  me: () => request<CurrentUser>("/api/v1/me"),

  updateMe: (input: { displayName?: string; email?: string }) =>
    request<CurrentUser>("/api/v1/me", { method: "PATCH", body: input }),
}

export const ProjectsApiClient = {
  list: () => request<Project[]>("/api/v1/projects"),

  get: (id: string) => request<Project>(`/api/v1/projects/${id}`),

  create: (input: { name: string; description: string }) =>
    request<Project>("/api/v1/projects", { method: "POST", body: input }),

  rename: (id: string, input: { name?: string; description?: string }) =>
    request<Project>(`/api/v1/projects/${id}`, { method: "PATCH", body: input }),

  listDiagrams: (id: string) =>
    request<ProjectDiagram[]>(`/api/v1/projects/${id}/diagrams`),

  /** Creates a diagram from a model: extension-backend creates the body in diagrams-backend server-side. */
  createDiagram: (id: string, model: UMLModel) =>
    request<ProjectDiagram>(`/api/v1/projects/${id}/diagrams`, {
      method: "POST",
      body: { model },
    }),

  deleteProject: (id: string) =>
    request<void>(`/api/v1/projects/${id}`, { method: "DELETE" }),

  deleteDiagram: (projectId: string, diagramId: string) =>
    request<void>(`/api/v1/projects/${projectId}/diagrams/${diagramId}`, {
      method: "DELETE",
    }),

  /** Short-lived, single-use ticket authorizing one collaboration WS connection. */
  issueWsTicket: (projectId: string, diagramId: string) =>
    request<{ ticket: string }>(
      `/api/v1/projects/${projectId}/diagrams/${diagramId}/ws-ticket`,
      { method: "POST" }
    ),
}
