# Proposal

## Why

A diagram that belongs to a project is only nominally protected: `extension-backend` records which project owns it, but the webapp opens, edits, collaborates on, and versions that diagram by talking straight to `diagrams-backend` with its raw id — a service with no authentication at all. Anyone who obtains that id (browser history, a copied URL, dev tools) gets full read/write/collaborate access, regardless of project ownership. `diagrams-backend` cannot be changed to fix this itself (standing constraint), so the only way to make project ownership a real access boundary is to put `extension-backend` in front of every path the browser uses to reach a project diagram's content — REST body, version history, and the live collaboration socket — and stop ever handing the raw `diagrams-backend` id to the browser, including for diagrams that already exist today.

## What Changes

- `extension-backend` proxies the full diagram-content REST surface for project diagrams: create, fetch/save body, and the version history endpoints (list, create, fetch, restore, rename, delete a version) — each checking the caller is the project's owner before forwarding to `diagrams-backend`.
- `extension-backend` gains a WebSocket relay for live collaboration on a project diagram: the browser requests a short-lived, single-use ticket over authenticated REST, connects to `extension-backend`'s WS endpoint with that ticket, and `extension-backend` — after re-checking project ownership — relays raw frames to and from `diagrams-backend`'s WS. No frame parsing (no Yjs awareness/update distinction): only one role exists today (owner), so this is a binary connect/reject decision, not a read/write filter.
- `DiagramResponse` (`GET /api/v1/projects/{id}/diagrams`, etc.) stops including `redisId`. The webapp never receives a project diagram's `diagrams-backend` id again — not for diagrams created after this change, and not for the ones that already exist, since they are addressed exclusively by `extension-backend`'s own `Diagram.id` going forward.
- **BREAKING**: A project diagram is no longer reachable at `/shared/$diagramId`. Opening one uses a new project-scoped route/flow that authenticates through `extension-backend`. Existing bookmarked/copied `/shared/...` links to a project diagram stop working.
- The webapp's `NewDiagramModal` project-creation flow stops creating the diagram body directly in `diagrams-backend`; `extension-backend` creates it server-side (via its existing outbound `diagrams-backend` client) and returns only its own `Diagram.id`.
- `ProjectDetailPage`/`ProjectDiagramCard` stop resolving a project diagram's title/type/last-modified by calling `diagrams-backend` directly with the raw id; that metadata comes from `extension-backend`'s proxy instead.

Out of scope (explicitly deferred, tracked as follow-ups, not part of this change):
- Ad-hoc link sharing (`ShareModal`, `sharedDiagramLinks`/`sharedDiagramStorage`, the generic `/shared/$diagramId` route for *non*-project diagrams) is untouched. It still creates a standalone `diagrams-backend` copy reachable by bare URL, exactly as today.
- The public `/embed/:id` and `/api/diagrams/:id/preview.svg` surface on `diagrams-backend` is untouched — still deliberately unauthenticated.
- No permission tiers (e.g. read-only project collaborator) are introduced. "Project membership" remains "is the owner."

## Capabilities

### Modified Capabilities
- `projects`: adds requirements that a project diagram's body, version history, and live collaboration session are only reachable through `extension-backend` after an ownership check, and that the raw `diagrams-backend` id is never exposed to the webapp for a diagram that belongs to a project.

## Impact

- **`extension-backend`**: `DiagramsBackendClient` gains proxy methods for the diagram body (create/get/put) and all version-history endpoints; a new WS ticket-issuing endpoint and a new WS relay component (new dependency: `spring-boot-starter-websocket`, not currently on the classpath); `ProjectsController`/`ProjectsService` gain the new proxy/ticket endpoints, all ownership-checked the same way existing project endpoints are; `DiagramResponse` drops `redisId` from its JSON.
- **`diagrams-backend`**: no changes (standing constraint).
- **webapp**: a new project-diagram route/page (adapting `ApollonShared`'s editor-lifecycle logic to call `extension-backend` instead of `DiagramApiClient`/`WebSocketManager`/`VersionApiClient` directly); a new `RepositoryKind` adapter alongside the existing `local`/`remote` ones for versions; `NewDiagramModal`'s project-creation path, `ProjectDetailPage`/`ProjectDiagramCard`, and anything else linking to `sharedDiagramRoute(diagram.id)` for a project diagram.
