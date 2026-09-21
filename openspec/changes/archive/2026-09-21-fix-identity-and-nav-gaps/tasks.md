# Tasks

## 1. Webapp: derive identity from the OIDC token

- [x] 1.1 Add `webapp/src/hooks/useTokenIdentity.ts`: reads `useAuth().user?.profile` and returns `{ displayName, email }`, where `displayName = [given_name, family_name].filter(Boolean).join(" ")` and `email = profile?.email ?? null`. Add a unit test covering both claims present, one missing, and both missing (empty string). Verified: 4/4 pass.
- [x] 1.2 In `ApollonShared.tsx`, replace `useCurrentUser()` + `currentUser?.displayName` with `useTokenIdentity()`; `collaborationUser` is now `{ name: displayName, color: collabColorFromName(displayName) }` whenever `displayName` is non-empty (no more null/gate case in practice, per design.md decision 3).
- [x] 1.3 In `ApollonShared.tsx`, delete `needsCollabName`, the `hasPromptedRef`-guarded `openModal("EDIT_PROFILE", ...)` effect, and the `isCollaborationView && !collaborationUser` early-return in the editor-mount effect. Remove the now-unused `CURRENT_USER_QUERY_KEY`/`useCurrentUser`/`CurrentUser` imports.
- [x] 1.4 Update `ApollonShared.test.tsx` and `ApollonShared.collab.test.tsx`: change the `react-oidc-context` mock to return `{ isAuthenticated: true, user: { profile: { given_name: "Test", family_name: "User", email: null } } }` (or per-test overrides), remove the `ExtensionApiClient`/`meMock` mocks (no longer imported by `ApollonShared`), and delete the two now-obsolete tests "re-opens the profile prompt for each new diagram when no display name is set" and "returns home when the profile-completion prompt is dismissed". Verified: 14/14 pass across both files.

## 2. Webapp: extract `UserMenu`

- [x] 2.1 Create `webapp/src/components/navbar/UserMenu.tsx`: a `DropdownMenu` behind a `navbarButtonStyle()` trigger (mirroring `HomeHelpMenu`'s trigger shape/props: `reveal`, `color`, `className`), body = `DropdownMenuLabel` "Signed in as `{displayName}` · `{email}`" using `useTokenIdentity()` (omitted when both are empty), then a single "Sign out" `DropdownMenuItem` calling `useAuth().signoutRedirect()`. Only rendered when `useAuth().isAuthenticated`.
- [x] 2.2 Add `UserMenu.test.tsx` covering: renders nothing when unauthenticated; shows "Signed in as …" with the token-derived name/email; clicking "Sign out" calls `signoutRedirect`. Verified: 3/3 pass.
- [x] 2.3 In `HomeHelpMenu.tsx`, delete the trailing `auth.isAuthenticated && (...)` account block and its now-unused imports (`useQueryClient`, `UserPenIcon`, `LogOutIcon`, `CurrentUser`, `CURRENT_USER_QUERY_KEY`, `useCurrentUser`, `useAuth`, the `openEditProfile` function, `useModalContext`, `DropdownMenuGroup`, `DropdownMenuLabel`). `HomeHelpMenu.test.tsx` contained only the now-obsolete profile/account tests (nothing else), so the file is deleted rather than left empty.
- [x] 2.4 No barrel file exists under `components/navbar` (every consumer imports `HomeHelpMenu`/`BackNav`/etc. by direct path) — `UserMenu` follows the same direct-import convention; nothing to add.

## 3. Webapp: mount `UserMenu` on every surface that has Help today

- [x] 3.1 `EditorHeader.tsx`'s `HeaderActionsIsland`: add `<UserMenu reveal="lg" />` next to `<HelpMenu />`.
- [x] 3.2 `MobileIslands.tsx`'s `MobileActionsPill`: add `<UserMenu reveal="lg" />` next to the Help pill (reused directly, same as `ThemeSwitcherMenu`, rather than wrapped in `MobileMenuButton` — it already has its own trigger+dropdown).
- [x] 3.3 `ChromeSubHeader.tsx`: add `<UserMenu reveal="lg" />` next to `<HomeHelpMenu reveal="lg" />`.
- [x] 3.4 `HomeHeaderRow.tsx`'s `HomeActionsIsland`: add `<UserMenu reveal="wide" />` next to `<HomeHelpMenu reveal="wide" />`, for consistency (per design.md decision 2 — not the page this proposal is fixing, but kept in sync).
- [x] 3.5 Ran the full webapp unit suite (399/399 pass) and the full Storybook interaction suite. Found and fixed a real bug this surfaced: unlike the old `HelpMenuItems` (lazy-mounted only when a dropdown opens), `UserMenu` calls `useAuth()` eagerly on every render, and `react-oidc-context`'s `useAuth()` returns `undefined` outside an `<AuthProvider>` (several stories, e.g. `LegalPage.stories.tsx`/`ErrorPage.stories.tsx`, render without one) — added optional chaining (`auth?.user`, `auth?.isAuthenticated`) in `useTokenIdentity.ts`/`UserMenu.tsx`. After the fix, `HelpMenu.stories.tsx`/`ErrorPage.stories.tsx`/`LegalPage.stories.tsx` pass (11/11); the 24 remaining Storybook failures (slider, PPTXExportModal, islandPrimitives "Glass Surface", playground AssessmentDataBox, ClassDiagram/Modes hover-style assertions) are pre-existing environmental/CSS flakes (e.g. `backdrop-filter` not computed under headless Chromium) unrelated to any file this change touches — confirmed by spot-checking `islandPrimitives.stories.tsx` in isolation.

## 4. Webapp: close the missing-logout gap on Projects pages

- [x] 4.1 Add `<UserMenu reveal="always" />` to `ProjectsPage.tsx`'s header, next to "New project" (this header has no responsive island system, so `reveal="always"` keeps the label visible rather than collapsing at a breakpoint that doesn't apply here).
- [x] 4.2 Add `<UserMenu reveal="always" />` to `ProjectDetailPage.tsx`'s header, next to the existing action buttons.
- [x] 4.3 Added a `react-oidc-context` mock to `ProjectDetailPage.test.tsx` and a new `ProjectsPage.test.tsx` (none existed), each asserting the sign-out control is present and calls `signoutRedirect`. Verified: 14/14 pass across both files.

## 5. Webapp: remove `EditProfileModal`

- [x] 5.1 Delete `EditProfileModal.tsx` and `EditProfileModal.test.tsx`; remove its export from `components/modals/index.ts`.
- [x] 5.2 Remove `"EDIT_PROFILE"` from `ModalName` in `types/ModalTypes.ts` and its entries in `MODAL_COMPONENTS`/`MODAL_TITLES` in `wrappers/ModalWrapper.tsx`; verify the `satisfies Record<ModalName, ...>` check still compiles.
- [x] 5.3 Removed `ExtensionApiClient.updateMe`, `ExtensionApiClient.me` (per design.md decision 1 — nothing in the webapp calls `/me` anymore), the whole now-empty `ExtensionApiClient` export, the `CurrentUser` type, and `hooks/useCurrentUser.ts` (with `CURRENT_USER_QUERY_KEY`). Confirmed via grep: no remaining references anywhere in `webapp/src`. Removed the corresponding tests in `ExtensionApiClient.test.ts` (preserved the Authorization-header assertion by moving it onto the `list()` test, so that coverage isn't lost).
- [x] 5.4 `npx tsc --noEmit`: clean. Full webapp test suite: 393/393 pass (61 files).

## 6. Webapp: editor back-target awareness ("All diagrams" → "Project diagrams")

- [x] 6.1 Add `webapp/src/hooks/useEditorBackTarget.ts`. Design.md named `useMatches()`, but `useDiagramIdFromPath.ts` (an existing hook solving the identical "navbar renders above the matched route" problem) documents that route-scoped hooks return nothing useful at that depth — so this parses `useLocation().pathname` directly instead, matching that proven pattern; design.md decision 4 updated to match. Returns `{ to: "/projects/$id", params: { id }, label: "Project diagrams" }` for `/projects/:id/diagrams/...` (the project detail route's own param is `$id`, not `$projectId` — confirmed against `routes/projects.$id.tsx`), else `{ to: "/", label: ALL_DIAGRAMS_LABEL }`. Also widened `BackNav`'s prop type to `BackTarget | EditorBackTarget` so it can render either hook's result. Added a unit test covering both branches. Verified: 2/2 pass.
- [x] 6.2 Update `HeaderBrandIsland` (`EditorHeader.tsx`) and `MobileBackPill` (`MobileIslands.tsx`) to use `useEditorBackTarget()` instead of the hardcoded `<BackNav to="/" label={ALL_DIAGRAMS_LABEL}>`. Full webapp suite verified: 395/395 pass (62 files).
- [x] 6.3 The project-diagram route's own test never actually renders the editor chrome: `EditorChromeHeader` portals `EditorHeaderRow` into a region the real Apollon editor registers (`useRegionHost`), which that test's lightweight fake editor doesn't implement — so `BackNav` isn't in that test's DOM at all, and adding coverage there wouldn't exercise anything. Instead added `components/navbar/EditorHeader.test.tsx`, asserting `HeaderBrandIsland` directly at both `useEditorBackTarget` branches: mounted at a project-diagram path, the link is `/projects/p1` labeled "Project diagrams"; mounted at `/shared/abc`, it's `/` labeled "All diagrams" (today's behavior, confirmed unchanged). Verified: 2/2 pass.

## 7. Backend: remove `User.displayName`

- [x] 7.1 In `User.kt`, remove the `displayName` column.
- [x] 7.2 In `UserDtos.kt`, remove `UpdateProfileRequest` and the `displayName` field from `UserResponse`/`User.toResponse()`.
- [x] 7.3 In `UsersController.kt`, remove the `PATCH /api/v1/me` endpoint (and the now-unused `usersService` constructor param); in `UsersService.kt`, remove `updateProfile`.
- [x] 7.4 Updated `UsersControllerTest.kt`: removed the two `PATCH /api/v1/me` tests and the `displayName` assertions from the remaining ones (renamed "usuario nuevo tiene displayName y email vacios" → "usuario nuevo tiene email vacio si el token no lo trae"). `./gradlew compileKotlin compileTestKotlin`: clean. `./gradlew test`: `UsersControllerTest` 7/7 pass, `ProjectsControllerTest` 50/50 pass; the only failure in the run is `ExtensionBackendApplicationTests.contextLoads()`, unrelated — it uses no `@ActiveProfiles`, so it hits a missing datasource for the default Spring profile (every other test explicitly runs under `@ActiveProfiles("local")`, which has one configured) — a pre-existing environment gap, not caused by this change.

## 8. End-to-end verification

- [x] 8.1 Manually verify: sign in, open a project diagram, confirm collaboration starts immediately (no profile-completion modal), and another authenticated session sees you identified by your Keycloak given/family name. Verified by the user directly.
- [x] 8.2 Manually verify: `UserMenu` shows the right name/email and "Sign out" actually signs out, from the editor, a project's detail page, and the projects list. Verified by the user directly.
- [x] 8.3 Manually verify: opening a project's diagram then clicking the back control returns to that project's diagram list, labeled "Project diagrams"; opening a `/shared/:id` diagram and clicking back still goes to `/` labeled "All diagrams". Verified by the user directly.
- [x] 8.4 Webapp: 397/397 pass (63 files). Backend: 88/89 pass — the only failure is the pre-existing, unrelated `ExtensionBackendApplicationTests.contextLoads()` (missing datasource for the default Spring profile; see 7.4).
