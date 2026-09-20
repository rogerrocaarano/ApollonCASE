import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { ExtensionApiClient, ProjectsApiClient } from "./ExtensionApiClient"

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

describe("ExtensionApiClient / ProjectsApiClient", () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal("fetch", fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("me() sends a GET with a bearer token", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: "u1", keycloakId: "kc-1" })
    )

    const result = await ExtensionApiClient.me()

    expect(result).toEqual({ id: "u1", keycloakId: "kc-1" })
    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/me")
    expect(init.method).toBe("GET")
    expect(init.headers.Authorization).toBe("Bearer test-token")
  })

  it("list() sends a GET to /api/v1/projects", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([]))

    await ProjectsApiClient.list()

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects")
    expect(init.method).toBe("GET")
    expect(init.body).toBeUndefined()
  })

  it("get() sends a GET to the project id", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        id: "p1",
        name: "Mine",
        description: "desc",
        ownerId: "u1",
        createdAt: "now",
        updatedAt: "now",
      })
    )

    await ProjectsApiClient.get("p1")

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1")
    expect(init.method).toBe("GET")
  })

  it("create() sends a POST with a JSON body", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        id: "p1",
        name: "New",
        description: "desc",
        ownerId: "u1",
        createdAt: "now",
        updatedAt: "now",
      })
    )

    await ProjectsApiClient.create({ name: "New", description: "desc" })

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects")
    expect(init.method).toBe("POST")
    expect(init.headers["Content-Type"]).toBe("application/json")
    expect(JSON.parse(init.body)).toEqual({ name: "New", description: "desc" })
  })

  it("rename() sends a PATCH to the project id", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        id: "p1",
        name: "Renamed",
        description: "desc",
        ownerId: "u1",
        createdAt: "now",
        updatedAt: "now",
      })
    )

    await ProjectsApiClient.rename("p1", { name: "Renamed" })

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1")
    expect(init.method).toBe("PATCH")
    expect(JSON.parse(init.body)).toEqual({ name: "Renamed" })
  })

  it("listDiagrams() sends a GET to the project's diagrams", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([]))

    await ProjectsApiClient.listDiagrams("p1")

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams")
    expect(init.method).toBe("GET")
  })

  it("linkDiagram() sends a POST with the redisId", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: "d1", redisId: "r1", projectId: "p1" })
    )

    await ProjectsApiClient.linkDiagram("p1", "r1")

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams")
    expect(init.method).toBe("POST")
    expect(JSON.parse(init.body)).toEqual({ redisId: "r1" })
  })

  it("deleteProject() sends a DELETE to the project id", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await ProjectsApiClient.deleteProject("p1")

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1")
    expect(init.method).toBe("DELETE")
  })

  it("deleteDiagram() sends a DELETE to the project's diagram id", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await ProjectsApiClient.deleteDiagram("p1", "d1")

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams/d1")
    expect(init.method).toBe("DELETE")
  })

  it("throws when the response is not ok", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))

    await expect(ProjectsApiClient.list()).rejects.toThrow(
      "extension-backend request failed with status 403"
    )
  })
})
