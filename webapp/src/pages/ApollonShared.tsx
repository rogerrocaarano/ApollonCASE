import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react"
import { useEditorContext } from "@/contexts"
import {
  ApollonEditor,
  ApollonMode,
  collabColorFromName,
  importDiagram,
  type ApollonOptions,
  type UMLModel,
} from "@tumaet/apollon"
import { useNavigate } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "react-toastify"
import { DiagramView } from "@/types"
import { useTokenIdentity } from "@/hooks/useTokenIdentity"
import {
  createDiagramAutosaver,
  type DiagramAutosaver,
} from "@/services/createDiagramAutosaver"
import type { CollaborationConnection } from "@/services/diagramGateway/types"
import { useDiagramGateway } from "@/contexts/DiagramGatewayContext"
import { selectScopedPreview, useVersionStore } from "@/stores/useVersionStore"
import { useDiagramSeed } from "@/hooks/useDiagramSeed"
import { prefetchVersions } from "@/queries/versionQueries"
import { useVersionRepositoryKind } from "@/contexts/VersionRepositoryContext"
import { useRestoreVersionMutation } from "@/queries/versionMutations"
import { applyControlEventToCache } from "@/queries/versionCacheEvents"
import { useDocumentTitle } from "@/hooks/useDocumentTitle"
import {
  UndoRestoreToast,
  VersionDrawer,
  VersionPreviewBanner,
  VersionRail,
} from "@/components/versioning"
import { versioningStrings as t } from "@/components/versioning/strings"
import { structuralFingerprint } from "@/lib/version/predicates"
import { useVersionPreviewUrlSync } from "@/hooks/useVersionPreviewUrlSync"
import { useElementWidth } from "@/hooks/useElementWidth"
import { useFlushOnUnload } from "@/hooks/useFlushOnUnload"
import { useEditorShortcuts } from "@/hooks/useEditorShortcuts"
import { log } from "@/logger"
import { addSharedDiagramEntry } from "@/utils/sharedDiagramStorage"

/** True for a fetch rejected by its own `AbortController`. */
function isAbort(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError"
}

export interface ApollonSharedProps {
  diagramId: string
  /** The link's access mode. A project diagram (no per-link modes) always passes `DiagramView.COLLABORATE`. */
  viewType: DiagramView | undefined
  previewFromUrl: string | undefined
  /**
   * Id the version subsystem (`useVersionStore`, `getVersionRepository`,
   * `VersionRail`/`VersionDrawer`/`VersionPreviewBanner`) addresses this
   * diagram by. Defaults to `diagramId`. A project diagram's version
   * endpoints are scoped to its project, so its route passes a distinct
   * composite id here (see `toProjectVersionDiagramId`) while `diagramId`
   * itself stays the plain id the diagram-content gateway expects.
   */
  versionDiagramId?: string
  /**
   * Whether this open counts as an ad-hoc "shared by link" diagram: recorded
   * in the local shared-diagrams list, and covered by the tab-close best-
   * effort flush. Defaults to true (today's `/shared/*` behavior). A project
   * diagram is neither — it's reached through its project, not a link, and
   * per-diagram sharing bookkeeping doesn't apply to it.
   */
  isAdHocShare?: boolean
}

/**
 * The collaborative (server-backed) editor. Route-agnostic: the route
 * mounting it owns its own params/search (`getRouteApi`/`Route.useParams`)
 * and passes them down, so this same component serves both an ad-hoc shared
 * diagram (`/shared/$diagramId`) and a project diagram
 * (`/projects/$projectId/diagrams/$diagramId`) without duplicating the
 * editor lifecycle.
 */
export const ApollonShared: React.FC<ApollonSharedProps> = ({
  diagramId,
  viewType,
  previewFromUrl,
  versionDiagramId = diagramId,
  isAdHocShare = true,
}) => {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const kind = useVersionRepositoryKind()
  const gateway = useDiagramGateway()
  const { setEditor, editor } = useEditorContext()
  const [diagramTitle, setDiagramTitle] = useState<string | null>(null)
  useDocumentTitle(diagramTitle)

  // Keep the tab title in sync with the (possibly collaborator-edited) shared
  // diagram name, the same way the local editor tracks it reactively.
  useEffect(() => {
    if (!editor) return
    // Seed the title from the editor once it mounts; subsequent updates come
    // through the subscription below.
    setDiagramTitle(editor.getDiagramMetadata().diagramTitle || null)
    const subscriptionId = editor.subscribeToDiagramNameChange((title) =>
      setDiagramTitle(title || null)
    )
    return () => editor.unsubscribe(subscriptionId)
  }, [editor])
  const containerRef = useRef<HTMLDivElement | null>(null)
  const canvasColumnRef = useRef<HTMLDivElement | null>(null)
  const canvasColumnWidth = useElementWidth(canvasColumnRef)
  const wsManagerRef = useRef<CollaborationConnection | null>(null)
  const autosaverRef = useRef<DiagramAutosaver | null>(null)
  const diagramIsUpdated = useRef(false)
  const editorRef = useRef<ApollonEditor | null>(null)
  const restoredDuringPreviewRef = useRef(false)
  /**
   * Fingerprint of the canvas at the FIRST preview entry of the current
   * preview session. Held until the user exits preview, so clicking
   * V1 → V2 → V3 always compares each against the user's pre-preview
   * canvas — not against whichever overlay is currently shown.
   */
  const prePreviewFingerprintRef = useRef<string | null>(null)
  const lifecycleKeyRef = useRef<string | null>(null)
  // True when the current preview's body differs from the canvas the user
  // had before entering preview. Computed once on preview entry and held
  // through the preview so the banner can show/hide "Restore" without
  // re-fingerprinting on every render. False = restoring would be a no-op
  // (e.g. the latest saved version with no unsaved local changes).
  const [canRestoreFromPreview, setCanRestoreFromPreview] = useState(false)
  const { displayName } = useTokenIdentity()
  // Derived from the current Keycloak token on every render (see
  // `useTokenIdentity`) — never stored, never gated on. Memoized on the
  // trimmed name (a primitive) rather than recreated fresh every render — the
  // editor-mount effect below depends on this object's identity, and a fresh
  // literal each render would re-trigger it (and reconnect the editor)
  // continuously.
  const collaborationUser = useMemo(
    () =>
      displayName
        ? { name: displayName, color: collabColorFromName(displayName) }
        : null,
    [displayName]
  )

  const preview = useVersionStore((s) =>
    selectScopedPreview(s, versionDiagramId)
  )
  const restoreMutation = useRestoreVersionMutation(kind, versionDiagramId)
  const { openPreview, closePreview } = useVersionPreviewUrlSync(
    kind,
    versionDiagramId,
    previewFromUrl,
    Boolean(editor)
  )

  useEditorShortcuts(versionDiagramId)

  useFlushOnUnload({
    // Best-effort tab-close flush only applies to the ad-hoc share flow
    // today; a project diagram relies on the debounced autosave's own
    // teardown flush (see the editor-mount effect's cleanup below).
    diagramId: isAdHocShare ? diagramId : undefined,
    getModel: () => editorRef.current?.model,
    isDirty: () => diagramIsUpdated.current,
  })

  // The component instance survives diagramId / viewType changes (same
  // `/:diagramId` route), so per-diagram refs must be reset explicitly per
  // route lifecycle. Declared FIRST so it runs before the prompt and mount
  // effects below.
  useEffect(() => {
    const nextLifecycleKey = `${diagramId ?? ""}:${viewType ?? ""}`
    if (lifecycleKeyRef.current === nextLifecycleKey) return
    lifecycleKeyRef.current = nextLifecycleKey
    diagramIsUpdated.current = false
    restoredDuringPreviewRef.current = false
  }, [diagramId, viewType])

  // validateSearch has already coerced an unknown ?view to undefined.
  useEffect(() => {
    if (viewType) return
    toast.error("Invalid view type")
    navigate({ to: "/" })
  }, [viewType, navigate])

  const isCollaborationView = viewType === DiagramView.COLLABORATE

  // One-shot editor seed; while its preconditions are unmet (missing view) it
  // stays pending, which keeps the overlay up.
  const {
    diagram,
    error: seedError,
    isPending: seedPending,
  } = useDiagramSeed(
    diagramId,
    Boolean(diagramId) && Boolean(viewType),
    gateway.client
  )

  // Seed failure = nothing to edit. Aborted loads never land here.
  useEffect(() => {
    if (!seedError) return
    log.error("Failed to initialize diagram", seedError)
    toast.error("Failed to initialize diagram")
    navigate({ to: "/" })
  }, [seedError, navigate])

  // Editor + connection lifecycle. Runs when the seed body arrives and tears
  // down on route identity change/unmount. The seed's `data` identity is
  // stable for the whole mount (staleTime Infinity; imperative HEAD refetches
  // use a separate cache key), so this effect can safely depend on it without
  // ever rebuilding the editor mid-session.
  useEffect(() => {
    const container = containerRef.current
    if (!container || !diagramId || !viewType || !diagram) return

    let instance: ApollonEditor | null = null
    let modelChangeSubscriptionId: number | null = null
    // Owned by this effect: a peer's VERSION_RESTORED refresh must not outlive
    // the editor it would assign to.
    const peerRefreshAbort = new AbortController()
    // `gateway.connect` is async (a project gateway fetches a ticket first);
    // guards a connection that resolves after this effect has already torn
    // down (route change, unmount) from attaching itself anyway.
    let cancelled = false

    try {
      log.debug("Initializing Apollon editor with view type:", viewType)
      if (isAdHocShare) {
        addSharedDiagramEntry(diagramId, { lastSharedView: viewType })
      }
      log.debug("Fetched diagram", {
        diagramId,
        nodeCount: diagram.nodes?.length ?? 0,
        edgeCount: diagram.edges?.length ?? 0,
      })

      const editorOptions: ApollonOptions = {
        model: diagram,
        collaborationEnabled: true,
        collaboration:
          isCollaborationView && collaborationUser
            ? {
                enabled: true,
                user: collaborationUser,
                showPresence: true,
                showCursors: true,
                showSelectionHighlights: true,
                showFollow: true,
              }
            : undefined,
      }

      if (viewType === DiagramView.GIVE_FEEDBACK) {
        editorOptions.mode = ApollonMode.Assessment
        editorOptions.readonly = false
      } else if (viewType === DiagramView.SEE_FEEDBACK) {
        editorOptions.mode = ApollonMode.Assessment
        editorOptions.readonly = true
      } else {
        editorOptions.mode = ApollonMode.Modelling
        editorOptions.readonly = false
      }

      instance = new ApollonEditor(container, editorOptions)
      editorRef.current = instance
      setEditor(instance)

      if (
        [
          DiagramView.COLLABORATE,
          DiagramView.GIVE_FEEDBACK,
          DiagramView.SEE_FEEDBACK,
        ].includes(viewType)
      ) {
        gateway
          .connect(diagramId, instance, () => toast.error("WebSocket error"))
          .then((connection) => {
            if (cancelled) {
              connection.cleanup()
              return
            }
            wsManagerRef.current = connection
            connection.onControl((event) => {
              applyControlEventToCache(queryClient, kind, versionDiagramId, event)
              if (event.type === "VERSION_DELETED") {
                // Clearing `?version=` is what actually leaves preview — the URL
                // is the source of truth, so dropping only the store state would
                // be undone by the sync effect and bounce the user back into a
                // snapshot the server no longer has.
                const previewing = selectScopedPreview(
                  useVersionStore.getState(),
                  versionDiagramId
                )
                if (previewing?.versionId === event.versionId) closePreview()
              }
              if (event.type === "VERSION_RESTORED") {
                const state = useVersionStore.getState()
                const isLocalRestore =
                  state.pendingRestoreFromId === event.restoredFromVersionId ||
                  state.undoRestore?.restoredFromVersionId ===
                    event.restoredFromVersionId
                if (!isLocalRestore) {
                  const actor = event.actor || "A collaborator"
                  gateway.client
                    .fetchDiagram(diagramId, {
                      signal: peerRefreshAbort.signal,
                    })
                    .then((next) => {
                      if (!instance) return
                      // Not while previewing: `editor.model` is the read-only
                      // overlay, so this would swap the snapshot out from under
                      // the user and be discarded on exit anyway. Exiting preview
                      // resyncs from Yjs, which has the restore already.
                      if (
                        selectScopedPreview(
                          useVersionStore.getState(),
                          versionDiagramId
                        ) !== null
                      ) {
                        return
                      }
                      instance.model = next
                    })
                    .catch((err) => {
                      if (isAbort(err)) return
                      toast.error(
                        `${actor} restored a version but we couldn't refresh.`,
                        { toastId: "version-restored-refetch-failed" }
                      )
                    })
                  toast.info(t.collaboratorRestoredTitle(actor), {
                    toastId: "version-restored-by-collaborator",
                    autoClose: 4000,
                  })
                }
              }
            })
          })
          .catch((err) => {
            log.error("Failed to connect collaboration WebSocket", err)
          })
      }

      const editorInstance = instance
      const autosaver = createDiagramAutosaver({
        diagramId,
        client: gateway.client,
        getModel: () => editorInstance.model,
        isPaused: () =>
          selectScopedPreview(useVersionStore.getState(), versionDiagramId) !==
          null,
        collaboration: isCollaborationView,
        onSaved: () => {
          diagramIsUpdated.current = false
        },
        onError: () => toast.error("Failed to sync changes"),
      })
      autosaverRef.current = autosaver

      modelChangeSubscriptionId = instance.subscribeToModelChange(() => {
        if (selectScopedPreview(useVersionStore.getState(), versionDiagramId))
          return
        diagramIsUpdated.current = true
        autosaver.schedule()
      })

      void prefetchVersions(queryClient, kind, versionDiagramId)
    } catch (err) {
      log.error("Failed to initialize diagram", err)
      toast.error("Failed to initialize diagram")
      navigate({ to: "/" })
    }

    return () => {
      // Don't let an in-flight peer-restore refresh apply to a torn-down editor.
      peerRefreshAbort.abort()
      // A gateway.connect() still in flight must not attach itself after teardown.
      cancelled = true
      setEditor(undefined)
      wsManagerRef.current?.cleanup()
      wsManagerRef.current = null
      // If a version preview is still open at teardown, the editor's local
      // model is the previewed snapshot and the autosaver is paused — flushing
      // as-is would either skip a pending pre-preview edit (paused) or persist
      // the stale snapshot. Exit preview first: setPreviewMode(false) resyncs
      // the editor from the live Yjs doc (the real diagram), and exitPreview()
      // clears the pause, so the flush below captures and persists it.
      if (
        instance &&
        selectScopedPreview(useVersionStore.getState(), versionDiagramId) !==
          null
      ) {
        instance.setPreviewMode(false)
        useVersionStore.getState().exitPreview()
      }
      // Persist any pending debounced edits before tearing down (e.g. SPA
      // navigation): flush() reads the model synchronously, so it captures the
      // latest state while the editor is still alive, then dispose() stops it.
      void autosaverRef.current?.flush()
      autosaverRef.current?.dispose()
      autosaverRef.current = null
      if (instance) {
        if (modelChangeSubscriptionId !== null) {
          instance.unsubscribe(modelChangeSubscriptionId)
        }
      }
      instance?.destroy()
      editorRef.current = null
    }
  }, [
    closePreview,
    collaborationUser,
    diagram,
    diagramId,
    gateway,
    isAdHocShare,
    isCollaborationView,
    kind,
    navigate,
    queryClient,
    setEditor,
    versionDiagramId,
    viewType,
  ])

  const baseReadonly = viewType === DiagramView.SEE_FEEDBACK

  // Imperative preview-mode driver: caches a pre-preview fingerprint in a ref
  // and overlays the preview model via the editor's imperative API. These are
  // effect-phase side effects, not render-time mutations.
  // eslint-disable-next-line react-hooks/immutability
  useEffect(() => {
    if (!editor) return
    const abort = new AbortController()
    if (preview) {
      // First preview entry of this session — capture the user's
      // pre-preview canvas fingerprint so V1→V2→V3 hops keep comparing
      // each candidate against the same baseline.
      if (prePreviewFingerprintRef.current === null) {
        prePreviewFingerprintRef.current = structuralFingerprint(editor.model)
      }
      setCanRestoreFromPreview(
        prePreviewFingerprintRef.current !== structuralFingerprint(preview.body)
      )
      // Library-level preview mode: store mutators stop writing to the
      // Yjs doc (the collaborative source of truth). The next
      // `editor.model =` assignment overlays the preview body purely in
      // the local Zustand cache. Peer edits keep flowing into Yjs in the
      // background; on exit, Zustand re-syncs from Yjs and the canvas
      // catches up to whatever collaborators committed during preview.
      editor.setPreviewMode(true)
      try {
        // Imperative editor API (accessor setter), applied in an effect.
        // eslint-disable-next-line react-hooks/immutability
        editor.model = importDiagram(preview.body) as UMLModel
        editor.setReadonly(true)
        editor.fitView()
      } catch (err) {
        editor.setPreviewMode(false)
        prePreviewFingerprintRef.current = null
        log.error("Failed to apply previewed snapshot", err)
        const isSchemaError =
          err instanceof Error && /schema|version|import/i.test(err.message)
        toast.error(
          isSchemaError ? t.failureSchemaUnsupported : t.previewFailed
        )
      }
    } else {
      editor.setReadonly(baseReadonly)
      prePreviewFingerprintRef.current = null
      if (!diagramId) return

      if (restoredDuringPreviewRef.current) {
        // After a restore, the server's HEAD is now the new canonical
        // state. Flip preview off (Zustand resyncs from Yjs to a stale
        // intermediate), then fetch HEAD and apply — that final
        // `editor.model = head` runs with previewActive=false so writes
        // hit Yjs and broadcast to peers.
        restoredDuringPreviewRef.current = false
        editor.setPreviewMode(false)
        gateway.client
          .fetchDiagram(diagramId, { signal: abort.signal })
          .then((head) => {
            editor.model = importDiagram(head) as UMLModel
            editor.fitView()
          })
          .catch((err) => {
            if (isAbort(err)) return
            log.error("Failed to reload diagram after restore", err)
            toast.error(t.failureSchemaUnsupported)
          })
      } else {
        // Plain preview exit: turn off preview mode. The library
        // resyncs Zustand from Yjs which has been receiving peer edits
        // throughout, so the canvas catches up automatically — no
        // model snapshot, no resync round-trip, no Yjs mutation.
        editor.setPreviewMode(false)
        editor.fitView()
      }
    }
    // A late `editor.model = head` would clobber the next preview. This
    // controller is this effect's alone, so aborting it cannot touch the
    // WebSocket handler's peer-restore refresh.
    return () => abort.abort()
  }, [preview, editor, diagramId, baseReadonly, queryClient, gateway])

  // Stable identity: `handleRestore` deps on it, and the banner's `onRestore`
  // prop would churn every render otherwise.
  const handleVersionSaved = useCallback((headRev?: number) => {
    autosaverRef.current?.setHeadRev(headRev)
    diagramIsUpdated.current = false
  }, [])

  const handleExitPreview = useCallback(() => {
    // Removing `?version=` makes the URL↔preview hook exit preview.
    closePreview()
  }, [closePreview])

  // One restore path for the banner and the drawer. While previewing,
  // `editor.model` is the read-only overlay — persisting it as the pre-restore
  // undo snapshot would make Undo restore the very version we just restored.
  // Leave preview first so editor.model resyncs to the live canvas.
  const handleRestore = useCallback(
    async (versionId: string) => {
      if (!diagramId || !editor) return
      const previewing =
        selectScopedPreview(useVersionStore.getState(), versionDiagramId) !==
        null
      if (previewing) {
        restoredDuringPreviewRef.current = true
        editor.setPreviewMode(false)
      }
      const liveBody = editor.model
      try {
        const { headRev } = await restoreMutation.mutateAsync({
          versionId,
          currentBody: liveBody,
        })
        handleVersionSaved(headRev)
        // Strip `?version=` so the URL sync doesn't re-enter the restored version.
        if (previewing) closePreview()
      } catch {
        restoredDuringPreviewRef.current = false
        toast.error(t.restoreFailed)
      }
    },
    [
      diagramId,
      versionDiagramId,
      editor,
      handleVersionSaved,
      // `mutateAsync` is stable; the mutation object is not.
      restoreMutation.mutateAsync,
      closePreview,
    ]
  )

  const isLoading = seedPending || !editor

  return (
    <div className="h-full flex flex-col">
      <div className="flex flex-1 min-h-0 overflow-hidden">
        <div ref={canvasColumnRef} className="relative h-full min-w-0 flex-1">
          {isLoading && (
            <div className="absolute inset-0 z-[1] flex items-center justify-center">
              Loading diagram…
            </div>
          )}
          <div
            className={`h-full w-full ${isLoading ? "invisible" : ""}`}
            ref={containerRef}
          />
          {!isLoading && preview && versionDiagramId && (
            <div className="pointer-events-none absolute left-0 right-0 top-3 z-[5] flex justify-center px-4 [&>*]:pointer-events-auto">
              <VersionPreviewBanner
                containerWidth={canvasColumnWidth}
                diagramId={versionDiagramId}
                canRestore={canRestoreFromPreview}
                onExitPreview={handleExitPreview}
                onRestore={handleRestore}
              />
            </div>
          )}
        </div>
        {versionDiagramId && (
          <VersionRail
            diagramId={versionDiagramId}
            onVersionSaved={handleVersionSaved}
            onConfirmedRestore={handleRestore}
            onPreview={openPreview}
          />
        )}
      </div>

      {versionDiagramId && (
        <VersionDrawer
          diagramId={versionDiagramId}
          onVersionSaved={handleVersionSaved}
          onConfirmedRestore={handleRestore}
          onPreview={openPreview}
        />
      )}
      <UndoRestoreToast />
    </div>
  )
}
