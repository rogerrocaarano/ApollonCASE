import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import type { ApollonEditor } from "@tumaet/apollon"
import { ApiError } from "@/services/DiagramApiClient"
import { createProjectDiagramGateway } from "./ProjectDiagramGateway"

vi.mock("@/auth/oidcConfig", () => ({
  getAccessToken: vi.fn().mockResolvedValue("test-token"),
}))

const { issueWsTicketMock } = vi.hoisted(() => ({
  issueWsTicketMock: vi.fn(),
}))
vi.mock("@/services/ExtensionApiClient", () => ({
  ProjectsApiClient: {
    issueWsTicket: (...args: unknown[]) => issueWsTicketMock(...args),
  },
}))

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

/** jsdom resolves the client's relative URL against its default origin. */
const pathOf = (url: string) => new URL(url, "http://localhost").pathname

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  onopen: (() => void) | null = null
  onmessage: (() => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  readyState = 0
  constructor(public url: string) {
    FakeWebSocket.instances.push(this)
  }
  send() {}
  close() {}
}

function makeFakeEditor() {
  return {
    sendBroadcastMessage: vi.fn(),
    broadcastFullState: vi.fn(),
  } as unknown as ApollonEditor
}

describe("createProjectDiagramGateway", () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal("fetch", fetchMock)
    issueWsTicketMock.mockReset()
    FakeWebSocket.instances = []
    vi.stubGlobal("WebSocket", FakeWebSocket)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("has kind 'project'", () => {
    expect(createProjectDiagramGateway("p1").kind).toBe("project")
  })

  it("fetchDiagram GETs the project-scoped body endpoint", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: "d1", title: "Mine" }))
    const gateway = createProjectDiagramGateway("p1")

    const result = await gateway.client.fetchDiagram("d1")

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams/d1/body")
    expect(init.method).toBe("GET")
    expect(init.headers.Authorization).toBe("Bearer test-token")
    expect(result).toEqual({ id: "d1", title: "Mine" })
  })

  it("sendDiagramUpdate PUTs the body and forwards If-Match", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ headRev: 2, updatedAt: "now" })
    )
    const gateway = createProjectDiagramGateway("p1")

    await gateway.client.sendDiagramUpdate(
      "d1",
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { id: "d1" } as any,
      { ifMatch: 1 }
    )

    const [url, init] = fetchMock.mock.calls[0]
    expect(pathOf(url)).toBe("/api/v1/projects/p1/diagrams/d1/body")
    expect(init.method).toBe("PUT")
    expect(init.headers["If-Match"]).toBe("1")
  })

  it("sendDiagramUpdate throws an ApiError carrying REVISION_MISMATCH on a 409", async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          error: "REVISION_MISMATCH",
          message: "stale",
          currentHeadRev: 5,
        },
        409
      )
    )
    const gateway = createProjectDiagramGateway("p1")

    await expect(
      gateway.client.sendDiagramUpdate(
        "d1",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        { id: "d1" } as any,
        { ifMatch: 1 }
      )
    ).rejects.toMatchObject({
      code: "REVISION_MISMATCH",
      meta: { currentHeadRev: 5 },
    })
  })

  it("sendDiagramUpdate error is an instance of the shared ApiError class", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ error: "NOT_FOUND" }, 404))
    const gateway = createProjectDiagramGateway("p1")

    await expect(
      gateway.client.sendDiagramUpdate(
        "d1",
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        { id: "d1" } as any
      )
    ).rejects.toBeInstanceOf(ApiError)
  })

  it("connect() fetches a fresh ticket and opens the relay WS with it", async () => {
    issueWsTicketMock.mockResolvedValue({ ticket: "tkt-1" })
    const gateway = createProjectDiagramGateway("p1")

    await gateway.connect("d1", makeFakeEditor(), vi.fn())
    // The URL is resolved asynchronously inside WebSocketManager; flush microtasks.
    await Promise.resolve()
    await Promise.resolve()

    expect(issueWsTicketMock).toHaveBeenCalledWith("p1", "d1")
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(FakeWebSocket.instances[0].url).toContain("ticket=tkt-1")
  })
})
