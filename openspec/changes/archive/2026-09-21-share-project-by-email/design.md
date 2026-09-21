# Design

## Context

See `proposal.md` - Why. Relevant current state in `webapp/src`:

- `types/extensionApi.ts` defines `Project { id, name, description, ownerId, createdAt, updatedAt }` — a stale mirror of `ProjectResponse`, which now sends `myPermission: "OWNER" | "COLLABORATOR" | "VIEWER"` instead of `ownerId` (see `project-permissions`'s `ProjectDtos.kt`).
- `services/ExtensionApiClient.ts`'s `ProjectsApiClient` has no `shareProject` method; `ExtensionApiClient.updateMe` accepts `{ displayName?, email? }` and `EditProfileModal.tsx` always sends both.
- `pages/ProjectsPage.tsx` → `components/projects/ProjectGallery.tsx` → `ProjectCard.tsx` always pass `onRename`/`onDelete`, so every project card shows both actions regardless of role.
- `pages/ProjectDetailPage.tsx` always renders Rename, Delete, and New diagram buttons, and always passes `onDelete` to every `ProjectDiagramCard`.
- `components/modals/` holds `ShareModal`/`ShareDashboardModal` (ad-hoc, roleless diagram-link sharing — untouched) alongside `NewProjectModal`/`RenameProjectModal` (project modals built on the shared `HomeDialog*` primitives and registered in `types/ModalTypes.ts` + `wrappers/ModalWrapper.tsx`). The new share-project modal follows that second pattern, not the first.
- `POST /api/v1/projects/{id}/share` already exists and returns `ProjectPermissionResponse { userId, role }`; errors are a plain non-2xx status with no structured error body beyond what `ExtensionApiClient`'s `request()` currently surfaces (`Error("extension-backend request failed with status ${res.status}")`).

## Goals / Non-Goals

**Goals:**
- Make `Project`'s webapp type match what `extension-backend` actually sends, and update every reader of the old field.
- Gate every project/diagram management control by the viewer's `myPermission`, computed client-side from data already returned by `GET /api/v1/projects` and `GET /api/v1/projects/{id}`.
- Add a minimal, owner-only "share by email" modal wired to the existing endpoint.

**Non-Goals:**
- Any backend change — the consumed API is complete.
- A member list/roster, revoke, or a dedicated "change role" UI — no endpoint exists for any of these (see proposal.md - Out of scope).
- Making `VIEWER` able to open a project diagram — that requires WS frame filtering that doesn't exist yet (unrelated non-goal already recorded in `project-permissions`'s design).
- Distinguishing *why* a share request failed beyond "unknown email" vs. "everything else" — `extension-backend` doesn't return a structured error body to branch on more finely than the HTTP status.

## Decisions

### 1. `Project.myPermission` replaces `Project.ownerId`
`types/extensionApi.ts` changes the field directly (not additive) since `ownerId` no longer exists on the wire — keeping it would just leave dead code. Every place constructing or asserting on a `Project` fixture (`ProjectCard`/`ProjectGallery`/`NewProjectModal`/`RenameProjectModal` tests and stories, `ExtensionApiClient.test.ts`, `ProjectDetailPage.test.tsx`) is updated to set `myPermission` instead. `ProjectDiagram`/`DiagramResponse` are untouched — role only lives on `Project`.

**Alternative considered**: keep `ownerId` optional alongside `myPermission` for a soft transition. Rejected — there's no external consumer of the webapp's `Project` type to stay compatible with, and a dead field with no producer just invites new code to read it.

### 2. Role gating lives in the two pages, not inside `ProjectCard`/`ProjectDiagramCard`
`ProjectCard` and `ProjectDiagramCard` already take `onRename`/`onDelete` as optional callback props and only render the corresponding control when the callback is provided (see `ProjectCard.tsx`'s `onRename || onDelete` check). Role gating is therefore just deciding *whether to pass the callback* at the call site:
- `ProjectsPage`/`ProjectGallery`: pass `onRename`/`onDelete` to a `ProjectCard` only when `project.myPermission === "OWNER"`.
- `ProjectDetailPage`: render its own Rename/Delete/Share buttons only when `project.myPermission === "OWNER"`; render "New diagram" when `myPermission` is `"OWNER"` or `"COLLABORATOR"`; pass `onDelete` to `ProjectDiagramCard` only when `myPermission === "OWNER"`.

No new shared "permission gate" component/hook — each check is a single comparison against a `myPermission` string already in hand, used in at most two places per page. Introducing an abstraction for that would be more machinery than the logic it wraps.

**Alternative considered**: keep controls always rendered but `disabled` with a tooltip explaining the required role. Rejected — the spec delta requires the controls not to appear at all for a role that can't use them (matches how `extension-backend` already treats missing permission as no access, not as a visible-but-blocked action), and it avoids explaining a permission model in-UI for something as auxiliary as a delete button.

### 3. New `ShareProjectModal`, registered like `NewProjectModal`/`RenameProjectModal`
A new `components/modals/ShareProjectModal.tsx` built on the existing `HomeDialogContent`/`HomeDialogField`/`HomeDialogTextInput`/`HomeDialogActions` primitives (same as `NewProjectModal`/`RenameProjectModal`), not on `ShareLinkRow`/`useShareableDiagram` (that machinery is link-generation for the unrelated ad-hoc sharing feature and has nothing this modal needs). Props: `{ projectId: string }`. Fields: an email text input and a role select (`COLLABORATOR` | `VIEWER`, defaulting to `COLLABORATOR`). Registered as a new `"SHARE_PROJECT"` entry in `types/ModalTypes.ts` and `wrappers/ModalWrapper.tsx`, opened from `ProjectDetailPage` via `openModal("SHARE_PROJECT", { projectId: id })`, the same pattern as `RENAME_PROJECT`.

On submit, calls `ProjectsApiClient.shareProject(projectId, { email, role })`:
- Success: toast confirmation (matching the existing `toast.error` pattern already used for delete failures on this page) and close the modal. The modal does not attempt to show an updated member list (none exists to show).
- Failure: `ExtensionApiClient`'s `request()` only exposes an HTTP status, not a parsed error body, so the modal cannot distinguish "unknown email" (404) from "tried to share with self" (403) beyond the status code already available. It maps 404 to "No user found with that email — they need to have signed in at least once" and any other non-2xx to a generic "Could not share the project" inline error, keeping the form open so the owner can correct the email and retry.

**Alternative considered**: extend `ShareModal`/`ShareDashboardModal` with a project mode. Rejected in discovery — those components model "one generated link, no recipient, no role"; forcing an email+role form into the same component would branch its entire body on a mode flag for two features that share no data shape.

### 4. `EditProfileModal` stops sending `email`
`ExtensionApiClient.updateMe`'s input type drops `email` entirely (`{ displayName?: string }`), so it's a compile error for any caller to keep sending it, not just a runtime no-op. `EditProfileModal` removes the email `HomeDialogField`/input and its `email` state; the modal shows the current `user.email` as static text (or omits it if null) so the user can still see what's on file without being invited to edit it.

## Risks / Trade-offs

- **[Risk] A `VIEWER` can still be granted access whose only visible effect today is seeing project/diagram metadata, since opening a diagram fails.** → Mitigation: already called out in proposal.md - Out of scope and confirmed acceptable; the share modal doesn't hide the `VIEWER` option, matching the decision to offer both roles now rather than block on a future change.
- **[Risk] Coarse error mapping on share failure** (can't distinguish "unknown email" from "shared with self" beyond a 404 vs. other-status split, since 403 also covers "not the owner," which shouldn't be reachable from this UI anyway since the button is owner-gated). → Mitigation: acceptable because the only 403 a correctly-gated UI should ever produce here is the self-share case; a generic message still tells the owner nothing was granted.
- **[Trade-off] No optimistic member list after a successful share.** The owner gets a toast confirmation but no on-screen roster update, since there's nothing to update against (no member-list endpoint). → Accepted per proposal.md - Out of scope.
