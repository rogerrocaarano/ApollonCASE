# Design

## Context

See `proposal.md` - Why. Relevant current state:

- `extension-backend` (Spring Boot 4 / Kotlin) already has one outbound dependency on `diagrams-backend`: `DiagramsBackendClient` (a `RestClient` wrapper) with a single `deleteDiagram(redisId)` method, added in `add-projects`. `ProjectsService.getOwnedProjectOrThrow` is the existing ownership-check pattern every project endpoint uses.
- `Diagram` (extension-backend entity) already has its own `id: UUID` distinct from `redisId` (the `diagrams-backend` id). `DiagramResponse` currently returns both.
- The webapp currently reaches `diagrams-backend` directly through three independent call sites, none behind a swappable abstraction except versions:
  - `DiagramApiClient` (body CRUD) and `WebSocketManager` (Yjs relay) — hardcoded to `serverURL`/`serverWSSUrl`, used directly by `ApollonShared`, `useDiagramSeed`, `createDiagramAutosaver`.
  - `VersionApiClient` — already behind an adapter registry: `getVersionRepository(kind)` resolves a `VersionRepository` implementation from a `RepositoryKind` (`"local" | "remote"`) that each route declares via `VersionRepositoryProvider`. This is the one existing extension point for "which backend does this route talk to."
- `NewDiagramModal`'s project-creation path creates the diagram body directly in `diagrams-backend` from the browser (`DiagramApiClient.createDiagram`), then calls `ProjectsApiClient.linkDiagram(projectId, created.id)` — the browser mints the `redisId` and hands it to `extension-backend`, the reverse of what this change needs.
- `extension-backend` has no WebSocket support yet (`spring-boot-starter-websocket` is not a dependency).
- No project has ever had more than one member (owner only) - `projects` has no permission-tier concept, so every check in this change is a single boolean: caller is the owner, or not.

## Goals / Non-Goals

**Goals:**
- Every path the webapp uses to reach a project diagram's content (body, versions, live collaboration) goes through `extension-backend`, which checks project ownership before forwarding to `diagrams-backend`.
- The webapp never holds a project diagram's `redisId`, for diagrams created before or after this change.
- Keep the WS relay to the minimum that still makes unauthorized collaboration impossible: a connect-time ownership check and a byte-level relay, no Yjs frame parsing.

**Non-Goals:**
- No permission tiers (viewer vs. editor). Ownership stays binary.
- No changes to `diagrams-backend`.
- No changes to ad-hoc link sharing (`ShareModal`/`/shared/$diagramId` for non-project diagrams) or to `/embed`+`preview.svg`. Both are addressed in later, separate changes per the roadmap discussed for this capability.
- No rotation of `redisId` for diagrams that already exist. This change stops the webapp from ever requesting or displaying an existing project diagram's raw id again, but does not invalidate a link to it that a user already saved/copied/leaked before this change shipped — `diagrams-backend`'s id-based access is unchanged and out of this change's control. Rotating ids (delete + recreate in `diagrams-backend`, preserving version history) would close that residual gap but adds real migration risk for a threat that is no worse than today's status quo; it is a candidate follow-up, not part of this change.

## Decisions

### 1. Extend `DiagramsBackendClient` to mirror the full diagram-content REST surface
Add methods for the endpoints `diagrams-backend` exposes and the editor needs, each a thin `RestClient` passthrough like the existing `deleteDiagram`:
- `createDiagram(model) -> redisId + body`
- `getDiagram(redisId) -> body` / `putDiagram(redisId, body, ifMatch?) -> headRev`
- `listVersions`, `createVersion`, `getVersion`, `restoreVersion`, `renameVersion` (PATCH), `deleteVersion`

`ProjectsController` gains matching endpoints under `/api/v1/projects/{projectId}/diagrams/{diagramId}/...`, each resolving the `Diagram` row (and its `redisId`) via `getOwnedProjectOrThrow` + a project-scoped diagram lookup before calling the client. This is the same shape as the existing `deleteDiagram` endpoint, just extended to reads, writes, and versions.

**Alternative considered**: expose `redisId` to the browser but scope it with a signed, project-bound token diagrams-backend would need to check. Rejected — `diagrams-backend` cannot be modified to validate anything, so this buys nothing over the status quo.

### 2. Diagram creation moves server-side
`POST /api/v1/projects/{id}/diagrams` changes from "link an existing `redisId`" (`LinkDiagramRequest { redisId }`) to "create a diagram from a model body" (`CreateDiagramRequest { model: UMLModel }` or equivalent). `ProjectsService` calls `DiagramsBackendClient.createDiagram(model)` itself, then persists the `Diagram` row. `DiagramResponse` drops `redisId` from its JSON entirely — it stays on the entity (needed internally for every proxy call) but is never serialized to the client.

`NewDiagramModal`'s `createDiagramInProject` changes from two browser-initiated calls (create in `diagrams-backend`, then link) to one call to `extension-backend`.

### 3. Project diagram metadata is resolved server-side, not by the browser
`ProjectDetailPage.loadDiagrams` currently calls `DiagramApiClient.fetchStoredDiagram(row.redisId)` against `diagrams-backend` directly to get title/type/`updatedAt` for each card. That becomes `extension-backend`'s job: `GET /api/v1/projects/{id}/diagrams` (or a per-diagram detail endpoint) resolves each row's metadata server-side via the same `DiagramsBackendClient.getDiagram` call, extending the existing N+1-round-trip pattern (already accepted in `add-projects`' design) to run server-to-server instead of browser-to-`diagrams-backend`.

### 4. WebSocket: ticket-issued, ownership-checked, byte-level relay
- `POST /api/v1/projects/{projectId}/diagrams/{diagramId}/ws-ticket` — owner-only, mints a random single-use token (e.g. UUID), stored in an in-memory map with a short TTL (~30s) and the resolved `(redisId)` it authorizes, keyed by the raw token.
- A new WS endpoint (`spring-boot-starter-websocket`, added as a new dependency) accepts the connection with the ticket as a query parameter (`?ticket=...`) — browsers cannot set `Authorization` on a WS handshake, and a ticket avoids putting the long-lived JWT in a URL/access log. On connect: look up and consume the ticket (single use), reject if missing/expired/already used. The ticket carries only the resolved `redisId`, not the requester's identity, so there is no separate identity to re-check ownership against at this layer — validity of the ticket itself (issued to the owner, unexpired, unused) is the authorization, exactly as the spec requires. A project/diagram deleted in the few-second window between issuance and connect is not specially detected; the relay would simply proxy to whatever `diagrams-backend` does with a room for that id (accepting a socket regardless of whether a body exists), which is no worse than the status quo.
- On acceptance, `extension-backend` opens its own upstream WebSocket to `diagrams-backend` (JDK's built-in `java.net.http.WebSocket` client — no new dependency for this leg) using the real `redisId`, then relays frames verbatim in both directions. No decoding of Yjs message types: per Non-Goals, there is only one role, so this is connect-or-reject, not per-message filtering.
- The ticket store is in-memory and single-instance. If `extension-backend` is ever scaled horizontally, ticket issuance and the WS connection could land on different instances — flagged under Open Questions, not addressed now (matches current single-instance deployment reality).

**Alternative considered**: put the JWT itself in the WS URL query string. Rejected — access/proxy logs would capture bearer tokens.

**Alternative considered**: no relay, just don't expose `redisId` for REST but keep the WS direct-to-`diagrams-backend`. Rejected — the WS is the actual live-editing channel; leaving it ungated defeats the proposal's stated goal ("sin colaboradores no autorizados").

### 5. Webapp: extend the existing adapter pattern instead of duplicating `ApollonShared`
`ApollonShared`'s editor-lifecycle effect (~250 lines) is currently hardwired to `DiagramApiClient`/`WebSocketManager`. Rather than fork a near-duplicate page for project diagrams, extract the body-fetch/save/create and WS-connect calls behind a small gateway interface (mirroring the existing `VersionRepository` adapter-registry pattern: `local`/`remote` kinds), add a `"project"` kind backed by calls to the new `extension-backend` endpoints (including the ticket fetch before opening the WS), and add a matching `"project"` entry to the existing `RepositoryKind` registry for versions. Both are selected together by a new route.

New route `webapp/src/routes/projects.$projectId.diagrams.$diagramId.tsx`, rendering `ApollonShared` (or its extracted logic) wrapped in both providers set to `kind="project"`.

**Scope simplification**: a project diagram has exactly one possible accessor (the owner) — there is no second party to hand a reduced-capability link to. The `?view=` mode selector (EDIT/COLLABORATE/GIVE_FEEDBACK/SEE_FEEDBACK), which exists to encode *what a link recipient* is allowed to do, has no meaning here. The project-diagram route always opens live, editable, with collaboration/presence on (equivalent to today's COLLABORATE view) — no `?view=` param, no feedback-mode UI. This removes a meaningful slice of branching from the reused logic.

**Alternative considered**: keep `?view=` on the project route for parity. Rejected — it would resurrect a distinction (per-link capability) that only makes sense when different people can hold different links, which project diagrams deliberately don't have.

### 6. `ProjectDiagramCard` and `NewDiagramModal` link to the new route
Both currently call `sharedDiagramRoute(diagram.id)` (raw `redisId`) or navigate there after creation. They switch to the new project-diagram route, addressed by `extension-backend`'s `Diagram.id` (`diagram.diagramId` in `ProjectDiagramCard`'s existing naming) instead of `diagram.id` (`redisId`).

## Risks / Trade-offs

- **Breaking change for existing bookmarks**: any previously-copied `/shared/$diagramId` link to a project diagram stops being the way the UI opens it → mitigated by nothing further; this is an accepted, explicit break per the proposal (project diagrams are no longer reachable at that route). The `diagrams-backend` body itself remains technically reachable to anyone who already has that exact link (see Non-Goals - no id rotation).
- **New JVM dependency and code path** (`spring-boot-starter-websocket` + a hand-rolled relay) where none exists today → mitigated by keeping the relay dumb (no protocol parsing), the smallest version of "real" WS gating possible.
- **Relay adds one extra network hop and one extra process's worth of latency/memory per open collaboration session** compared to today's direct browser-to-`diagrams-backend` WS → accepted; there is no way to gate the socket without an intermediary, per the standing "never modify diagrams-backend" constraint.
- **N+1 metadata round trips move from browser→diagrams-backend to extension-backend→diagrams-backend**, same shape, different hop → no behavior change for the user, already an accepted trade-off from `add-projects`.

## Open Questions

- If `extension-backend` is ever deployed with more than one instance, the in-memory WS ticket store needs to become shared (e.g. Redis or the Postgres database already in use) or ticket issuance/connection need to be sticky to the same instance. Not a concern at the current single-instance deployment; revisit if/when horizontal scaling is introduced.
