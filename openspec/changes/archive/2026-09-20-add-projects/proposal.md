# Proposal

## Why

Diagrams today are either purely local (IndexedDB, no owner, no server identity) or reachable-by-link (stored in `diagrams-backend`/Redis with no owner and no access control). Now that roadmap item 1 (Keycloak login) gives every diagram creator a `User` in `extension-backend`, there is no way for that user to see, organize, or come back to "their" diagrams as a set. Roadmap item 2 asks for a *Proyecto → Diagramas* model; this change delivers the first slice of it: a webapp screen where a user lists, creates, and renames their projects, with every diagram now belonging to exactly one project. This is also a prerequisite for roadmap item 3 (per-project permissions), which needs projects to exist and own diagrams before it can attach ACLs to them.

## What Changes

- New **Projects** screen replaces the webapp's Home page as the post-login landing point: lists the authenticated user's own projects (name, description, diagram count, last-modified), lets them create a project (name + description) and rename one (name and/or description) in place.
- Opening a project shows the diagrams that belong to it (reusing the existing diagram-card gallery UI patterns) and lets the user create a new diagram *inside* that project.
- **BREAKING**: Creating a diagram no longer produces a local, offline-only diagram. Every new diagram is created against `diagrams-backend` (as `/shared/$diagramId` already does) and immediately registered as belonging to the project the user created it from — there is no more diagram that exists outside a project.
- **BREAKING**: The Home page's flat gallery (local diagrams + "shared by link" diagrams) is removed from the navigable UI. The `/local/$id` route, `ApollonLocal` page, and the local (IndexedDB) `usePersistenceModelStore` creation path are left in place as unreferenced code for a later cleanup change — not deleted here, and not reachable from the new UI.
- `extension-backend`'s `projects` module gains what a listing screen needs: an endpoint to list the caller's own projects, an endpoint to list a project's diagrams, and `createdAt`/`updatedAt` on `Project` so the list can be sorted like the current diagram gallery is.
- A project's owner can delete a project. Deleting a project deletes all of its diagrams too — both the tracking row in `extension-backend` and the diagram body in `diagrams-backend` — since a diagram cannot exist without a project.
- A project's owner can delete a single diagram from within a project, removing both its `extension-backend` tracking row and its body in `diagrams-backend`.
- Out of scope for this change: archiving projects (as opposed to deleting them), multi-user collaboration on a project (still single-owner, matching today's `ProjectsController` access check), and routing diagram body read/write/collaboration traffic through `extension-backend` (the webapp keeps talking to `diagrams-backend` directly for that, exactly as `/shared/$diagramId` does today).

## Capabilities

### New Capabilities
- `projects`: A user can list, create, rename, and delete their own projects; a project owns a list of diagrams; creating a diagram happens within a project and immediately associates it with that project; a project's owner can delete a diagram from within it.

### Modified Capabilities
_None._ This change builds on the existing `identity` capability (login, current user, bearer-token auth) without changing its requirements.

## Impact

- **`extension-backend`** (`projects` package): new `GET /api/v1/projects` (list owned projects), `GET /api/v1/projects/{id}/diagrams` (list a project's diagrams), `DELETE /api/v1/projects/{id}` (delete a project and its diagrams), and `DELETE /api/v1/projects/{id}/diagrams/{diagramId}` (delete one diagram) endpoints; `Project` entity gains `createdAt`/`updatedAt`. `extension-backend` gains its first outbound HTTP call to `diagrams-backend` (to delete a diagram body on cascade); `diagrams-backend`'s own code is not modified.
- **`webapp`**: new "Projects" route/page replacing `HomePage`/`DiagramGallery` as the default (`/`) route; new project-detail route showing a project's diagrams; `ExtensionApiClient` gains project list/create/rename/delete/diagram-list/diagram-delete calls; `NewDiagramModal`'s creation flow changes from "create locally, navigate to `/local/$id`" to "create against `diagrams-backend`, link to the current project via `extension-backend`, navigate to the project-scoped editor".
- Diagrams created before this change (local-only or shared-by-link) are not migrated; they become unreachable from the new UI. Acceptable per explicit decision — no real users yet.
