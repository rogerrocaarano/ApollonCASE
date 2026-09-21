# Proposal

## Why

`extension-backend` already has a full project-permission model — `OWNER`/`COLLABORATOR`/`VIEWER`, `GET /api/v1/projects` returning every project the user has any access to, and `POST /api/v1/projects/{id}/share` to grant a role by email — but that change (`project-permissions`) was scoped to `extension-backend` only and explicitly deferred "fixing the webapp" as follow-up debt. Today the webapp still assumes the pre-permissions world it replaced: it reads a `Project.ownerId` field the backend no longer sends, lets a user edit an `email` field the backend silently ignores (email is now synced from the Keycloak token), and offers Rename/Delete/New diagram/Delete-diagram to every project regardless of the caller's actual role — a real problem now that `GET /projects` can return projects the user only has `COLLABORATOR` or `VIEWER` access to. Worse, there is no UI anywhere to call the new share-by-email endpoint at all, so the core capability `project-permissions` introduced is unreachable from the app.

## What Changes

- `Project.ownerId` (webapp type, mirrors the removed backend field) is replaced with `Project.myPermission: "OWNER" | "COLLABORATOR" | "VIEWER"`, matching `ProjectResponse.myPermission`. Every usage, test, and story that references `ownerId` is updated.
- `EditProfileModal` drops its editable email input; email is shown as read-only (or omitted), and `ExtensionApiClient.updateMe` stops sending an `email` field — matching `identity`'s existing rule that email is system-managed and synced from the token, not user-editable.
- **BREAKING (webapp UI)**: Project and diagram management actions are gated by `myPermission` instead of always shown:
  - Rename project, Delete project, Share project → `OWNER` only.
  - New diagram → `OWNER` or `COLLABORATOR`.
  - Delete diagram → `OWNER` only.
  - A user with no qualifying role for an action no longer sees the control for it (not just a disabled button or a server-side 403 after the fact).
- A new "Share project" modal, separate from the existing ad-hoc `ShareModal`/`ShareDashboardModal` (link-based, roleless sharing of a diagram snapshot — untouched by this change): triggered by a "Share" button on `ProjectDetailPage`'s header, visible only to the project's `OWNER`. Lets the owner enter an email address and pick `COLLABORATOR` or `VIEWER`, and calls the existing `POST /api/v1/projects/{id}/share`. Surfaces the backend's rejection cases (unknown email, sharing with self) as inline errors.
- `ProjectsApiClient` gains a `shareProject(projectId, { email, role })` method.

Out of scope (explicitly deferred, not part of this change):
- Listing a project's current members/collaborators, changing an existing member's role from a roster, or revoking access — `extension-backend` has no endpoint for any of these yet (`project-permissions` left them as non-goals). The new modal can only grant/update a role by email; it cannot show who already has access.
- Letting a `VIEWER` actually open a project diagram. `extension-backend`'s WebSocket ticket issuance requires `COLLABORATOR` or higher, and the project-diagram route always opens live/editable with no read-only mode (`gate-project-diagrams` decision 5). A user shared as `VIEWER` can see the project and its diagram list but gets a failed connection attempting to open any diagram. This is a pre-existing gap (flagged as a non-goal in `project-permissions`), not introduced or fixed here.
- Any change to `extension-backend` or `diagrams-backend` — the API this change consumes already exists.

## Capabilities

### Modified Capabilities

- `projects`: adds requirements that the webapp lets a project's `OWNER` share the project by email through a dedicated UI, and that the webapp only presents project/diagram management actions consistent with the authenticated user's role.
- `identity`: adds a requirement that the webapp does not let a user edit their own email, consistent with email being system-managed.

## Impact

- **webapp**: `types/extensionApi.ts` (`Project.ownerId` → `myPermission`), `services/ExtensionApiClient.ts` (`shareProject`, drop `email` from `updateMe`'s input type), `components/modals/EditProfileModal.tsx` (+ test), `components/projects/ProjectCard.tsx`/`ProjectGallery.tsx` (+ tests/stories), `pages/ProjectsPage.tsx`, `pages/ProjectDetailPage.tsx` (+ test), `components/projects/ProjectDiagramCard.tsx`, a new `ShareProjectModal` component (+ test/story), `types/ModalTypes.ts` and `wrappers/ModalWrapper.tsx` (register the new modal).
- **extension-backend**: none — `POST /api/v1/projects/{id}/share` and `ProjectResponse.myPermission` already exist.
- **diagrams-backend**: none.
