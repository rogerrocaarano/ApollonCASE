import { getAccessToken } from "@/auth/oidcConfig"
import { extensionServerURL } from "@/constants"
import { ApiError } from "@/services/DiagramApiClient"
import type { ApiErrorBody, ApiErrorCode } from "@/types"

export interface ProjectExtensionRequestOpts {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

/**
 * A request against one of extension-backend's project-diagram-content
 * proxy endpoints (body, versions). Mirrors `DiagramApiClient`'s error shape
 * (`ApiError`) rather than `ExtensionApiClient`'s plain `Error` — these
 * endpoints forward `diagrams-backend`'s exact status/body on failure (see
 * `GlobalExceptionHandler.handleUpstreamClientError`), so the wire contract
 * is identical to talking to `diagrams-backend` directly, and callers like
 * `createDiagramAutosaver`'s `REVISION_MISMATCH` rebase logic depend on that
 * shape to keep working for project diagrams.
 */
export async function projectExtensionRequest<T>(
  path: string,
  opts: ProjectExtensionRequestOpts = {}
): Promise<T> {
  const token = await getAccessToken()
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...opts.headers,
  }
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers["Content-Type"] = "application/json"

  const res = await fetch(`${extensionServerURL}${path}`, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    signal: opts.signal,
  })

  if (res.status === 204) return undefined as unknown as T

  let parsed: unknown
  const contentType = res.headers.get("content-type") ?? ""
  if (contentType.includes("application/json")) {
    parsed = await res.json().catch(() => undefined)
  }

  if (!res.ok) {
    const body = (parsed as ApiErrorBody | undefined) ?? {
      error: "INTERNAL" as ApiErrorCode,
      message: `Request failed with status ${res.status}`,
      requestId: "",
    }
    throw new ApiError(res.status, body.error, body.message, body)
  }

  return parsed as T
}
