# Design

## Context

See `proposal.md` for motivation. Relevant existing state:

- `extension-backend`'s `projects` package (`Project`, `Diagram`, `ProjectsController/Service/Repository`) already exists but predates Keycloak auth and was never wired to the webapp. It has `GET /{id}`, `POST` (create), `PATCH /{id}` (rename), and `POST /{id}/diagrams` (`LinkDiagramRequest{redisId}` — attaches a diagram that *already exists* in `diagrams-backend` to a project). It has no list endpoint, no diagram-list endpoint, and `Project` has no timestamps. Ownership checks (`getOwnedProjectOrThrow`) already exist and already return 403 for non-owners.
- `diagrams-backend` (Redis-backed, via `DiagramApiClient`) already supports creating a diagram body (`POST /api/diagrams`) and fetching one by id (`GET /api/diagrams/:id`). It has no auth and is not modified by this change (hard project constraint — see root `README.md`).
- The webapp already has a working pattern for "resolve a list of ids owned elsewhere into full diagram cards": `DiagramGallery`'s shared-diagram loading fetches each id from `diagrams-backend` via `DiagramApiClient.fetchStoredDiagram` and renders `DiagramCard`s. This change reuses that pattern for a project's diagrams instead of inventing a new one.
- `NewDiagramModal` today creates a diagram in local state (`usePersistenceModelStore.createModelByTitleAndType`) and navigates to `/local/$id`. `AuthGate` already requires login for every route except `/imprint` and `/privacy`, so no new auth gating is needed for the new screen.

## Goals / Non-Goals

**Goals:**
- Define how a diagram gets from "created" to "owned by a project" without modifying `diagrams-backend`.
- Define the minimal `extension-backend` API surface the new webapp screens need.
- Keep the existing single-owner access model; do not anticipate roadmap item 3's multi-user permissions.

**Non-Goals:**
- Routing diagram body reads/writes/WebSocket collaboration through `extension-backend`. The webapp keeps talking to `diagrams-backend` directly for editing, exactly as `/shared/$diagramId` does today.
- Archiving projects (soft-delete/status). Deletion here is hard: the row is gone.
- Migrating pre-existing local or shared-by-link diagrams into projects.
- Removing `/local/$id`, `ApollonLocal`, or the local `usePersistenceModelStore` creation path — they become unreferenced, not deleted.

## Decisions

### Creating a diagram reuses the existing `linkDiagram` endpoint — no new "create" endpoint on `extension-backend`
Rather than adding a new `extension-backend` endpoint that creates a diagram body, the webapp does it in two calls it already has the pieces for:
1. `DiagramApiClient.createDiagram(model)` against `diagrams-backend` (unchanged, already exists) → returns a diagram with a `diagrams-backend` id.
2. `POST /api/v1/projects/{projectId}/diagrams` (existing `linkDiagram`, unchanged) with that id → registers the `Diagram` row in `extension-backend`, scoped to the project.

**Alternative considered**: add a `POST /api/v1/projects/{id}/diagrams/create` that calls `diagrams-backend` server-side. Rejected: it would give `extension-backend` a new runtime dependency on `diagrams-backend`'s network reachability from the server side (today it has none), duplicate a request `diagrams-backend` already handles, and buys nothing — the client already must call `diagrams-backend` directly afterward anyway to open the editor and start autosaving/collaborating.

**Consequence**: between step 1 and step 2, a diagram body exists in `diagrams-backend` momentarily unlinked to any project. If step 2 fails (network error, project renamed/deleted mid-flight, etc.), the diagram is orphaned in `diagrams-backend` (reachable by id, unowned) with no project referencing it. This mirrors the existing "shared by link" diagrams, which are already unowned and reachable by id today — it does not regress the current security posture, only carries it forward. Retrying step 2 (same diagram id, idempotent link) is the recommended recovery; no automatic cleanup is proposed here.

### Project's diagram list is resolved client-side from two sources, not denormalized server-side
`GET /api/v1/projects/{id}/diagrams` returns `extension-backend`'s own `Diagram` rows (`id`, `redisId`, `projectId`) — it does not proxy or embed `diagrams-backend` body data (title, type, thumbnail, updatedAt). The webapp fetches those per-id from `diagrams-backend`, the same way `DiagramGallery` already does for shared diagrams.

**Alternative considered**: have `extension-backend` fetch each diagram's metadata from `diagrams-backend` server-side and return it embedded. Rejected for this change: it would add a new server-to-server dependency and a new response shape to design and version, for a capability (`diagrams-backend` proxying) explicitly deferred to roadmap item 3. Fetching client-side reuses code that already exists and already handles the "diagram fetch failed" case (`DiagramGallery`'s per-entry try/catch).

### `Project` gains `createdAt`/`updatedAt`; no new `archived`/status field
The list requirement needs recency ordering. Rename (`PATCH`) and diagram creation should bump `updatedAt`. No status field is added because archiving is explicitly out of scope (proposal's Impact section).

### Empty project name is rejected; description may be empty
`CreateProjectRequest.name` becomes a validated non-blank field (`@NotBlank`, matching the `spring-boot-starter-validation` dependency already present). This is a minor assumption, not asked about explicitly: a projects list is unusable with unnamed entries, unlike diagrams (which already support "Untitled").

### Deleting a project or a diagram calls `diagrams-backend` server-side — `extension-backend`'s first outbound HTTP dependency
Unlike creation (client-driven two-call sequence, see above), deletion cascades from a single client call: `DELETE /api/v1/projects/{id}` and `DELETE /api/v1/projects/{id}/diagrams/{diagramId}` are each one request from the webapp. `extension-backend` itself calls `diagrams-backend`'s existing `DELETE /api/diagrams/{redisId}` (unchanged, already used by `DiagramApiClient.deleteDiagram` today) for every diagram body being removed, via a small `DiagramsBackendClient` (Spring `RestClient`, synchronous — the project doesn't use WebFlux) pointed at a new `app.diagrams-backend.base-url` property.

**Alternative considered**: make the webapp call `diagrams-backend` directly for each diagram, then call `extension-backend` to remove the tracking rows — mirroring the creation flow's client-driven approach. Rejected: deleting a project can mean deleting an unbounded number of diagram bodies; doing that as N sequential client-driven calls (webapp → diagrams-backend, one per diagram) is slower and leaves a wider window for a partial failure the client has to reconcile (e.g., project row deleted client-side while 3 of 5 diagram-body deletes are still in flight). A single server-side call keeps the operation atomic-enough (see below) and matches how a "delete cascade" is conventionally owned by the service that holds the parent-child relationship.

**Error handling policy**: a `404` from `diagrams-backend` (diagram body already gone) is treated as success — the end state ("this diagram is not reachable") is what the caller wants either way. Any other error (network failure, `5xx`) fails the whole delete: the `extension-backend` row is *not* removed, so a retry is safe and the diagram doesn't silently vanish from a project's list while its body still exists in Redis (which would orphan it the other direction — reachable by id, invisible to the user who owned it).

**Consequence**: `extension-backend` now has a real runtime dependency on `diagrams-backend`'s reachability for deletes (it had none before this change). If `diagrams-backend` is down, project/diagram deletion fails loudly (5xx) rather than silently orphaning data — acceptable, and consistent with the error-handling policy above.

### New diagram creation flow moves out of `NewDiagramModal`'s local-only path
The modal's "Blank diagram" / "Use template" flows currently call `createModelByTitleAndType`/`createModel` (local store) and navigate to `/local/$id`. Inside a project, they instead call `DiagramApiClient.createDiagram` (or the from-template equivalent), then `ExtensionApiClient`'s new link call, then navigate to `/shared/$diagramId` (the existing collaborative editor route — unchanged). The modal needs to know which project it was opened from (passed through the existing modal-options mechanism used for `dialogVariant`).

## Risks / Trade-offs

- **[Risk]** Orphaned `diagrams-backend` entries if the link call fails after diagram creation → **Mitigation**: none automated in this change (see Decisions above); acceptable because `diagrams-backend` already has unowned, reachable-by-id diagrams today (shared-by-link), so this doesn't introduce a new class of exposure, only a possible increase in unused Redis entries. Worth a follow-up if it proves to matter in practice.
- **[Risk]** Two-network-call diagram creation (create then link) means a user can navigate to the editor while the link call is still in flight or has failed silently if not awaited → **Mitigation**: the webapp must await the link call and block navigation on its success, surfacing an error otherwise (same pattern as other mutation-then-navigate flows already in the codebase, e.g. `ShareModal`).
- **[Trade-off]** Diagram cards inside a project require one `diagrams-backend` round trip per diagram to render title/thumbnail (N+1 from the browser), same cost the existing shared-diagrams gallery already pays. Not addressed here; would need server-side aggregation (deferred, see Decisions).

## Migration Plan

No data migration (see proposal: pre-existing local/shared diagrams are not migrated). Deployment order: ship the `extension-backend` endpoints first (additive, non-breaking), then the webapp changes that depend on them. `Project.createdAt`/`updatedAt` are added as non-null columns with a default (`ddl-auto=update` per current dev setup); existing rows (if any, from prior manual testing) get the migration-time default.
