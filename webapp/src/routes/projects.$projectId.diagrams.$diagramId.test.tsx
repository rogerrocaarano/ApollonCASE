import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { act, cleanup, screen, waitFor } from "@testing-library/react"
import { renderWithRouter } from "@/test/renderWithRouter"
import { wrapWithQueryClient } from "@/test/queryTestUtils"
import { EditorProvider, ModalProvider } from "@/contexts"

const { meMock } = vi.hoisted(() => ({ meMock: vi.fn() }))

vi.mock("react-oidc-context", () => ({
  useAuth: () => ({ isAuthenticated: true }),
}))

vi.mock("@/services/ExtensionApiClient", () => ({
  ExtensionApiClient: { me: () => meMock() },
  ProjectsApiClient: { issueWsTicket: vi.fn() },
}))

const editorHoisted = vi.hoisted(() => ({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  instance: null as any,
}))

const FakeApollonEditor = vi.hoisted(
  () =>
    class FakeApollonEditor {
      model: object = {}
      constructor() {
        editorHoisted.instance = this
      }
      destroy() {}
      subscribeToModelChange() {
        return 1
      }
      subscribeToDiagramNameChange() {
        return 5
      }
      getDiagramMetadata() {
        return { diagramTitle: "Project Diagram" }
      }
      unsubscribe() {}
      setReadonly() {}
      setPreviewMode() {}
      fitView() {}
    }
)

vi.mock("@tumaet/apollon", async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>
  const React = await import("react")
  const Apollon = (props: {
    onMount?: (e: unknown) => void | (() => void)
    ref?: React.Ref<unknown>
  }) => {
    const { ref } = props
    React.useEffect(() => {
      const instance = new FakeApollonEditor()
      if (typeof ref === "function") ref(instance)
      else if (ref) (ref as { current: unknown }).current = instance
      const cleanupFn = props.onMount?.(instance)
      return () => {
        if (typeof cleanupFn === "function") cleanupFn()
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])
    return React.createElement("div", { "data-testid": "apollon-canvas" })
  }
  return {
    ...actual,
    Apollon,
    ApollonEditor: FakeApollonEditor,
    ApollonMode: { Modelling: "Modelling", Assessment: "Assessment" },
    importDiagram: (m: unknown) => m,
  }
})

const gatewayHoisted = vi.hoisted(() => {
  const state: { pending: { resolve: (v: object) => void } | null } = {
    pending: null,
  }
  return { state }
})

vi.mock("@/services/diagramGateway/ProjectDiagramGateway", () => ({
  createProjectDiagramGateway: (projectId: string) => ({
    kind: "project" as const,
    client: {
      fetchDiagram: () =>
        new Promise((resolve) => {
          gatewayHoisted.state.pending = { resolve }
        }),
      sendDiagramUpdate: () =>
        Promise.resolve({ headRev: 1, updatedAt: "" }),
    },
    connect: () =>
      Promise.resolve({
        onControl: () => () => {},
        publishControl: () => {},
        cleanup: () => {},
      }),
    __projectId: projectId,
  }),
}))

const LOADING_TEXT = "Loading diagram…"

// `renderWithRouter` mounts whatever `ui` it's given as the matched route's
// component — but this route's real component builds the gateway/composite
// version id from ITS OWN route params, so the test needs the real route
// module, not a hand-rolled stand-in. Import it directly and mount its
// exported `Route.options.component`.
import { Route as ProjectDiagramRoute } from "./projects.$projectId.diagrams.$diagramId"

function mountProjectDiagramRoute(initialEntry: string) {
  const Component = ProjectDiagramRoute.options.component!
  return renderWithRouter(<Component />, {
    initialEntry,
    routePaths: ["/projects/$projectId/diagrams/$diagramId", "/"],
    wrapper: (children) =>
      wrapWithQueryClient(
        <EditorProvider>
          <ModalProvider>{children}</ModalProvider>
        </EditorProvider>
      ),
  })
}

async function resolveFetch(model: object = { nodes: [], edges: [] }) {
  await waitFor(() => expect(gatewayHoisted.state.pending).not.toBeNull())
  await act(async () => {
    const pending = gatewayHoisted.state.pending!
    gatewayHoisted.state.pending = null
    pending.resolve(model)
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  gatewayHoisted.state.pending = null
  editorHoisted.instance = null
  meMock.mockReset()
  meMock.mockResolvedValue({
    id: "u1",
    keycloakId: "kc-1",
    displayName: "tester",
    email: null,
  })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe("project diagram route", () => {
  it("loads the diagram through the project gateway and mounts the editor", async () => {
    mountProjectDiagramRoute("/projects/p1/diagrams/d1")
    expect(await screen.findByText(LOADING_TEXT)).toBeTruthy()

    await resolveFetch({ id: "d1", nodes: [], edges: [] })

    await waitFor(() => expect(screen.queryByText(LOADING_TEXT)).toBeNull())
    expect(editorHoisted.instance).not.toBeNull()
  })
})
