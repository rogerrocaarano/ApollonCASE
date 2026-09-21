import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  ProjectVersionRepository,
  toProjectVersionDiagramId,
} from "../ProjectVersionRepository"

vi.mock("@/auth/oidcConfig", () => ({
  getAccessToken: vi.fn().mockResolvedValue("test-token"),
}))

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

/** jsdom resolves the client's relative URL against its default origin. */
const pathOf = (url: string) => new URL(url, "http://localhost").pathname
const queryOf = (url: string) => new URL(url, "http://localhost").search

const COMPOSITE_ID = toProjectVersionDiagramId("p1", "d1")
const VERSION_ID = "v1"

describe("ProjectVersionRepository", () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("kind is 'project'", () => {
    expect(ProjectVersionRepository.kind).toBe("project")
  })

  it("toProjectVersionDiagramId builds the composite id this adapter splits", () => {
    expect(COMPOSITE_ID).toBe("p1:d1")
  })

  it("list() GETs the project-scoped versions endpoint with query params", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ versions: [], total: 0 }))

    await ProjectVersionRepository.list(COMPOSITE_ID, {
      limit: 10,
      before: "cursor-1",
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams/d1/versions")
    expect(queryOf(url)).toBe("?limit=10&before=cursor-1")
    expect(init.method).toBe("GET")
  })

  it("create() POSTs the body under the composite id's project/diagram", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: VERSION_ID, name: "v", total: 1, headRev: 2 })
    )

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const body = { id: "d1", nodes: [], edges: [] } as any
    await ProjectVersionRepository.create(COMPOSITE_ID, body, { name: "v" })

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams/d1/versions")
    expect(init.method).toBe("POST")
    expect(JSON.parse(init.body)).toEqual({
      name: "v",
      description: undefined,
      actor: undefined,
      body,
    })
  })

  it("getBody() GETs a specific version", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: "d1" }))

    await ProjectVersionRepository.getBody(COMPOSITE_ID, VERSION_ID)

    const [url] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe(
      `/api/v1/projects/p1/diagrams/d1/versions/${VERSION_ID}`
    )
  })

  it("restore() POSTs to the restore sub-resource", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ headRev: 3, updatedAt: "now", autoSnapshotVersionId: "a" })
    )

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const currentBody = { id: "d1" } as any
    await ProjectVersionRepository.restore(COMPOSITE_ID, VERSION_ID, {
      currentBody,
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe(
      `/api/v1/projects/p1/diagrams/d1/versions/${VERSION_ID}/restore`
    )
    expect(init.method).toBe("POST")
  })

  it("editInfo() PATCHes the version", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: VERSION_ID, name: "n" }))

    await ProjectVersionRepository.editInfo(COMPOSITE_ID, VERSION_ID, {
      name: "n",
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe(
      `/api/v1/projects/p1/diagrams/d1/versions/${VERSION_ID}`
    )
    expect(init.method).toBe("PATCH")
  })

  it("delete() DELETEs the version", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await ProjectVersionRepository.delete(COMPOSITE_ID, VERSION_ID)

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe(
      `/api/v1/projects/p1/diagrams/d1/versions/${VERSION_ID}`
    )
    expect(init.method).toBe("DELETE")
  })

  it("permalink() builds a project-diagram URL with ?version=", () => {
    const link = ProjectVersionRepository.permalink(COMPOSITE_ID, VERSION_ID)
    expect(link).toContain(`/projects/p1/diagrams/d1?version=${VERSION_ID}`)
  })

  it("rejects a non-composite id", async () => {
    await expect(ProjectVersionRepository.list("not-composite")).rejects.toThrow(
      /composite/
    )
  })
})
