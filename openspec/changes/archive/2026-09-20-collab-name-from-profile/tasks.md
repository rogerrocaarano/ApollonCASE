# Tasks

## 1. `webapp` — shared current-user hook

- [x] 1.1 Extract `useCurrentUser()` into `webapp/src/hooks/useCurrentUser.ts`, moving the `useQuery(["extension-backend", "me"], ExtensionApiClient.me, { enabled: auth.isAuthenticated, staleTime: 5 * 60 * 1000, retry: false })` config currently inlined in `HomeHelpMenu.tsx` (reads `useAuth()` internally so callers don't need to pass it in). Update `HomeHelpMenu.tsx` to use it. Verify: existing `HomeHelpMenu.test.tsx` (from `add-user-profile`) still passes unmodified — the hook is a pure extraction, no behavior change.

## 2. `webapp` — `EditProfileModal` required-name mode

- [x] 2.1 Add a `requireDisplayName?: boolean` prop to `EditProfileModal` (`webapp/src/components/modals/EditProfileModal.tsx`): when `true`, pass `confirmDisabled={!displayName.trim()}` to `HomeDialogActions` (same mechanism `RenameProjectModal`/`NewProjectModal` already use). Defaults to `false` (today's fully-optional behavior, unchanged). Verify: extend `EditProfileModal.test.tsx` with a case asserting `Save` is disabled with an empty name when `requireDisplayName` is set, and that the existing (default) tests still pass unmodified.

## 3. `webapp` — collaboration identity from the profile

- [x] 3.1 In `ApollonShared.tsx`: replace `readStoredCollabUser()`/the `sessionStorage` lazy-init of `collaborationUser` with `useCurrentUser()`. Derive `collaborationUser` as `{ name: currentUser.displayName, color: collabColorFromName(currentUser.displayName) }` when `currentUser?.displayName` is non-empty; otherwise `null` (not yet resolved, matching today's gating of `needsCollabName`/the editor-mount effect while `collaborationUser` is falsy). Verify: `./gradlew`-equivalent `npm run test --workspace=@tumaet/webapp` compiles; a diagram opens directly (no modal) when the mocked current user has a `displayName`.
- [x] 3.2 Replace the `COLLABORATE_NAME`-opening effect with one that opens `EDIT_PROFILE` (`{ user: currentUser, requireDisplayName: true, onUpdated: (updated) => setCollaborationUser({ name: updated.displayName!, color: collabColorFromName(updated.displayName!) }), onClose: () => navigate({ to: "/" }) }`) when `currentUser` has resolved but has no `displayName`. Remove the `sessionStorage.setItem("apollon-collab-name", ...)` write. Verify: covered by the test updates in task 4.

## 4. `webapp` — update existing tests for the new flow

- [x] 4.1 In `ApollonShared.test.tsx`: replace the `sessionStorage.setItem/removeItem("apollon-collab-name", ...)` setup across all three affected tests ("re-opens the collab-name prompt for each new un-named diagram", "returns home when the collaboration-name prompt is dismissed", and the one seeding `"tester"`) with a mock of `useCurrentUser` (or `ExtensionApiClient.me`, whichever `useCurrentUser` is built on) returning a user with/without `displayName` as each test needs, and update the `openModal` assertions from `"COLLABORATE_NAME"` to `"EDIT_PROFILE"`. Verify: all three tests pass with the new mock, asserting the same "prompted on each new unnamed diagram" and "dismiss navigates home" behavior against the new modal name.
- [x] 4.2 In `ApollonShared.collab.test.tsx`: replace the `beforeEach`'s `sessionStorage.setItem("apollon-collab-name", "tester")` with a mock current user that has `displayName: "tester"`, so the collaboration-mechanics tests (cursors, presence, etc.) keep mounting the editor directly without a prompt. Verify: the full collab test suite in this file still passes unmodified otherwise.

## 5. End-to-end verification

- [x] 5.1 Manually exercise against a running stack: as a user with no `displayName` set, open a project diagram — confirm the profile modal opens (not the old name prompt), `Save` is disabled until a name is typed, saving enters the collaborative session identified by that name; as a user who already has a `displayName`, open a project diagram and confirm it enters directly with no modal, identified by that name to a second browser/session watching the same diagram.
- [x] 5.2 Run the full test suites and confirm they pass: `npm run test --workspace=@tumaet/webapp` (366/366).
