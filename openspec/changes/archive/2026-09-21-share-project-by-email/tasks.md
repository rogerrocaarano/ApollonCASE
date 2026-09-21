# Tasks

## 1. `Project.ownerId` → `Project.myPermission`

- [x] 1.1 In `webapp/src/types/extensionApi.ts`, replace `Project.ownerId: string` with `Project.myPermission: "OWNER" | "COLLABORATOR" | "VIEWER"`.
- [x] 1.2 Update every `Project` fixture/assertion referencing `ownerId` to `myPermission` (`ProjectDetailPage.test.tsx`, `ExtensionApiClient.test.ts`, `ProjectGallery.test.tsx`, `ProjectCard.stories.tsx`, `ProjectGallery.stories.tsx`, `NewProjectModal.test.tsx`, `RenameProjectModal.test.tsx`, `ProjectCard.test.tsx`); verify with `grep -rn "ownerId" webapp/src` returning no results.
- [x] 1.3 Run the webapp test suite and verify it passes with the renamed field (`npm test` in `webapp/`).

## 2. Stop editing email in the profile modal

- [x] 2.1 In `webapp/src/services/ExtensionApiClient.ts`, drop `email` from `updateMe`'s input type (`{ displayName?: string }`); verify `webapp/src/components/modals/EditProfileModal.tsx` fails to compile until task 2.2 removes the caller's `email` field.
- [x] 2.2 In `EditProfileModal.tsx`, remove the email `HomeDialogField`/input and its `email` state; show `user.email` as static read-only text (or omit the row when null) instead. Update `EditProfileModal.test.tsx` to assert there is no editable email input and that `updateMe` is called with only `displayName`.
- [x] 2.3 Run `EditProfileModal.test.tsx` and verify it passes.

## 3. Add `shareProject` to `ProjectsApiClient`

- [x] 3.1 In `webapp/src/services/ExtensionApiClient.ts`, add `ProjectsApiClient.shareProject(projectId: string, input: { email: string; role: "COLLABORATOR" | "VIEWER" }) => request<{ userId: string; role: string }>(`/api/v1/projects/${projectId}/share`, { method: "POST", body: input })`. Also added an exported `ExtensionApiError` (carries the HTTP `status`) so `request()`'s failure can be branched on — needed by task 6.2's 404-vs-other error mapping.
- [x] 3.2 Add a test in `ExtensionApiClient.test.ts` verifying the request is POSTed to the right path with the right body and that a non-2xx response rejects; verify the test passes.

## 4. Gate project-list actions by role

- [x] 4.1 `ProjectGallery` (not `ProjectsPage`, to keep `ProjectsPage`'s handlers untouched) now checks `project.myPermission === "OWNER"` per card before passing `onRename`/`onDelete` down to `ProjectCard`.
- [x] 4.2 Update `ProjectGallery.test.tsx`/`ProjectCard.test.tsx` to cover: an `OWNER` project shows Rename/Delete in the card menu; a `COLLABORATOR` or `VIEWER` project shows neither. Verify tests pass.

## 5. Gate project-detail actions by role

- [x] 5.1 In `webapp/src/pages/ProjectDetailPage.tsx`, render the Rename and Delete buttons only when `project?.myPermission === "OWNER"`.
- [x] 5.2 Render "New diagram" only when `project?.myPermission === "OWNER" || project?.myPermission === "COLLABORATOR"`.
- [x] 5.3 Pass `onDelete` to `ProjectDiagramCard` only when `project?.myPermission === "OWNER"` (otherwise omit the prop, relying on `ProjectDiagramCard`'s existing optional-prop rendering).
- [x] 5.4 Update `ProjectDetailPage.test.tsx` with cases for `OWNER` (all controls present), `COLLABORATOR` (New diagram present; Rename/Delete/Share and diagram-delete absent), and `VIEWER` (none of Rename/Delete/Share/New diagram/diagram-delete present). Verified: 12/12 pass.

## 6. `ShareProjectModal`

- [x] 6.1 Create `webapp/src/components/modals/ShareProjectModal.tsx`: props `{ projectId: string }`; an email `HomeDialogTextInput` and a role selector (`HomeDialogOptionGroup`, `COLLABORATOR` default, `VIEWER` option) built from existing `HomeDialog*` primitives (mirror `RenameProjectModal.tsx`'s structure); `HomeDialogActions` with "Share"/"Cancel".
- [x] 6.2 On submit, call `ProjectsApiClient.shareProject`; on success, `toast.success` and close the modal; on failure, show an inline error distinguishing a 404 ("No user found with that email") from any other status ("Could not share the project. Please try again."), keep the modal open, and leave entered values intact.
- [x] 6.3 Add `ShareProjectModal.test.tsx` covering: successful share calls `shareProject` with the entered email and selected role and closes the modal; role defaults to Collaborator; a 404 response shows the "not found" message and keeps the modal open; a non-404 failure shows the generic error; Share is disabled with a blank email. Verified: 5/5 pass.
- [x] 6.4 Add `ShareProjectModal.stories.tsx`. Neither `NewProjectModal` nor `RenameProjectModal` (the closest analogs — API-calling project modals) have story files at all, so this follows `ShareDashboardModal.stories.tsx`'s convention instead: render the form and interact with it (role selection, enabling Share) without ever submitting, so no real network call fires. Verified via `npm run test:storybook` (real-browser interaction test): 2/2 pass.
- [x] 6.5 Export `ShareProjectModal` from `webapp/src/components/modals/index.ts`.

## 7. Wire the modal into the app

- [x] 7.1 Add `"SHARE_PROJECT"` to `ModalName` in `webapp/src/types/ModalTypes.ts`.
- [x] 7.2 Register `SHARE_PROJECT: ShareProjectModal` in `MODAL_COMPONENTS` and `SHARE_PROJECT: "Share project"` in `MODAL_TITLES` in `webapp/src/wrappers/ModalWrapper.tsx`; verify the `satisfies Record<ModalName, ...>` check still compiles.
- [x] 7.3 In `ProjectDetailPage.tsx`, add a "Share" button next to Rename/Delete, visible only when `project?.myPermission === "OWNER"`, calling `openModal("SHARE_PROJECT", { dialogVariant: "home", projectId: id })`.
- [x] 7.4 Update `ProjectDetailPage.test.tsx` to verify clicking "Share" (as `OWNER`) opens the `SHARE_PROJECT` modal with the current project's id.

## 8. End-to-end verification

- [x] 8.1 Manually verify against a running `extension-backend`: as a project's `OWNER`, share the project with a second, previously-authenticated user's email as `COLLABORATOR`; confirm that user's `GET /projects` now includes the project and the webapp's project list/detail page for them shows only the `COLLABORATOR`-level controls. Verified by the user directly.
- [x] 8.2 Manually verify sharing the same project with the same user as `VIEWER` updates their role (re-share, not a duplicate), and that the `VIEWER`'s project/detail page shows no management controls. Verified by the user directly.
- [x] 8.3 Manually verify error paths in the UI: sharing with an unknown email shows the "not found" message; sharing with the owner's own email shows an error; no permission changes in either case. Verified by the user directly.
- [x] 8.4 Run the full webapp test suite and confirm everything passes (`npm test` in `webapp/`). Verified: 397/397 pass (60 files).
