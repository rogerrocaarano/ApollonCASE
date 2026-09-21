import type { UMLModel } from "@tumaet/apollon"
import { MAX_VERSIONS_PER_DIAGRAM } from "@/constants"
import { resolveShareOrigin } from "@/utils/sharedDiagramLinks"
import { projectExtensionRequest } from "@/services/diagramGateway/projectExtensionRequest"
import type {
  CreateVersionResult,
  ListVersionsResponse,
  RestoreVersionResult,
  VersionRepository,
} from "./types"

/**
 * `getVersionRepository(kind)` resolves a static, parameterless adapter —
 * fine for `local`/`remote`, whose diagram ids are globally addressable. A
 * project diagram's version endpoints are scoped to its project
 * (`/projects/{projectId}/diagrams/{diagramId}/versions`), so this adapter
 * encodes both ids into the one `diagramId` string callers already thread
 * everywhere (query keys, `useDiagramSeed`, `useEditorShortcuts`, ...) as
 * `"{projectId}:{diagramId}"`, and splits it back out here. Contained to
 * this one file: every other version call site stays untouched, treating
 * the composite string as just another opaque diagram id.
 */
function splitCompositeId(compositeId: string): {
  projectId: string
  diagramId: string
} {
  const separatorIndex = compositeId.indexOf(":")
  if (separatorIndex === -1) {
    throw new Error(
      `ProjectVersionRepository expects a "projectId:diagramId" id, got: ${compositeId}`
    )
  }
  return {
    projectId: compositeId.slice(0, separatorIndex),
    diagramId: compositeId.slice(separatorIndex + 1),
  }
}

/** Builds the "{projectId}:{diagramId}" id this adapter expects. */
export function toProjectVersionDiagramId(
  projectId: string,
  diagramId: string
): string {
  return `${projectId}:${diagramId}`
}

function versionsPath(compositeId: string, suffix = ""): string {
  const { projectId, diagramId } = splitCompositeId(compositeId)
  return `/api/v1/projects/${projectId}/diagrams/${diagramId}/versions${suffix}`
}

export const ProjectVersionRepository = {
  kind: "project" as const,
  cap: MAX_VERSIONS_PER_DIAGRAM,

  // Every method here is declared `async` even where it's a single
  // passthrough call, deliberately: `splitCompositeId` can throw
  // synchronously (a caller passed a bare id, not "projectId:diagramId"),
  // and `async` converts that into a rejected Promise instead of a
  // synchronous throw at the call site — the contract every other adapter
  // (and every caller) already assumes for these methods.
  async list(compositeId, opts?): Promise<ListVersionsResponse> {
    const params = new URLSearchParams()
    if (opts?.limit !== undefined) params.set("limit", String(opts.limit))
    if (opts?.before !== undefined) params.set("before", opts.before)
    const qs = params.toString()
    return projectExtensionRequest<ListVersionsResponse>(
      versionsPath(compositeId, qs ? `?${qs}` : ""),
      { signal: opts?.signal }
    )
  },

  async create(compositeId, body, opts): Promise<CreateVersionResult> {
    return projectExtensionRequest<CreateVersionResult>(
      versionsPath(compositeId),
      {
        method: "POST",
        body: {
          name: opts.name,
          description: opts.description,
          actor: opts.actor,
          body,
        },
      }
    )
  },

  async getBody(compositeId, versionId, opts?) {
    const { projectId, diagramId } = splitCompositeId(compositeId)
    return projectExtensionRequest(
      `/api/v1/projects/${projectId}/diagrams/${diagramId}/versions/${versionId}`,
      { signal: opts?.signal }
    )
  },

  async restore(compositeId, versionId, opts): Promise<RestoreVersionResult> {
    const { projectId, diagramId } = splitCompositeId(compositeId)
    return projectExtensionRequest<RestoreVersionResult>(
      `/api/v1/projects/${projectId}/diagrams/${diagramId}/versions/${versionId}/restore`,
      {
        method: "POST",
        body: {
          currentBody: opts.currentBody as UMLModel,
          actor: opts.actor,
        },
      }
    )
  },

  async editInfo(compositeId, versionId, patch) {
    const { projectId, diagramId } = splitCompositeId(compositeId)
    return projectExtensionRequest(
      `/api/v1/projects/${projectId}/diagrams/${diagramId}/versions/${versionId}`,
      { method: "PATCH", body: patch }
    )
  },

  async delete(compositeId, versionId) {
    const { projectId, diagramId } = splitCompositeId(compositeId)
    return projectExtensionRequest(
      `/api/v1/projects/${projectId}/diagrams/${diagramId}/versions/${versionId}`,
      { method: "DELETE" }
    )
  },

  permalink(compositeId, versionId) {
    const { projectId, diagramId } = splitCompositeId(compositeId)
    const origin = resolveShareOrigin()
    return `${origin}/projects/${projectId}/diagrams/${diagramId}?version=${versionId}`
  },
} satisfies VersionRepository
