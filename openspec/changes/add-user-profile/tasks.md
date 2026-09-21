# Tasks

## 1. `extension-backend` — profile fields and endpoint

- [x] 1.1 Add nullable `displayName: String?` and `email: String?` to `User` in `services/extension-backend/src/main/kotlin/.../users/User.kt`. Verify: `./gradlew compileKotlin` succeeds; a new `User` persists with both fields `null`.
- [x] 1.2 Add `displayName`/`email` to `UserResponse` and `User.toResponse()` in the existing `UserDtos.kt`. Verify: `GET /api/v1/me` response includes both fields (empty for a fresh user).
- [x] 1.3 Add `UpdateProfileRequest(displayName: String? = null, @field:Email email: String? = null)` to `UserDtos.kt`. Verify: a manual `PATCH` with `"email": "not-an-email"` returns 400.
- [x] 1.4 Add `UsersService.updateProfile(keycloakId: String, displayName: String?, email: String?): User`: loads the user by `keycloakId`, applies only the fields present in the request (same `field?.let { ... }` shape as `ProjectsService.changeProjectName`/`changeProjectDescription`), saves. Verify: a unit/integration test sends only `displayName` and asserts `email` is unchanged, and vice versa.
- [x] 1.5 Add `PATCH /api/v1/me` to `UsersController.kt`, using `CurrentUserHolder`/the JWT subject (no path parameter — see design.md) and `@Valid` on the request body. Verify: `UsersControllerTest` covers setting `displayName` only, `email` only, both, an invalid email (400), and that a fresh user's `GET /api/v1/me` returns empty strings/nulls for both fields.

## 2. `webapp` — API client

- [x] 2.1 Add `displayName`/`email` to `ExtensionApiClient`'s `CurrentUser` interface in `webapp/src/services/ExtensionApiClient.ts`, and add `ExtensionApiClient.updateMe(input: { displayName?: string; email?: string })` calling `PATCH /api/v1/me`, following the existing `request()` helper. Verify: a unit test mocks `fetch` and asserts URL (`/api/v1/me`), method (`PATCH`), and body.

## 3. `webapp` — Edit profile UI

- [x] 3.1 Add `EditProfileModal` (`webapp/src/components/modals/EditProfileModal.tsx`), modeled directly on `RenameProjectModal.tsx`: two `HomeDialog*` fields (Display name, Email) seeded from the current `CurrentUser`, calling `ExtensionApiClient.updateMe`, reporting the updated user via an `onUpdated` callback, `toast.error` on failure (matching `RenameProjectModal`'s error handling). Register it as a new `ModalName` (`EDIT_PROFILE`) in `ModalTypes.ts` and `ModalWrapper.tsx`, opened with `dialogVariant: "home"`. Verify: a component test (mirroring `RenameProjectModal.test.tsx`) covers save (both fields, partial), invalid-email failure surfacing a toast without closing, and pre-filled initial values.
- [x] 3.2 In `HomeHelpMenu.tsx`: replace the plain `DropdownMenuLabel` "Signed in as {currentUser.keycloakId}" with `currentUser.displayName?.trim() || currentUser.keycloakId`, and add an "Edit profile" `DropdownMenuItem` above "Sign out" that opens `EDIT_PROFILE` with the current user and an `onUpdated` handler that updates the `["extension-backend", "me"]` query cache (`queryClient.setQueryData`) so the menu reflects the new name immediately. Verify: a component test opens the menu, asserts the fallback-to-`keycloakId` display, edits the name via the modal, and asserts the menu label updates without a refetch.

## 4. End-to-end verification

- [ ] 4.1 Manually exercise the flow against a running stack: log in as a user with no profile set, confirm the Help menu shows "Signed in as `<keycloakId>`", open "Edit profile", set a display name and email, save, confirm the menu now shows the display name, reload the page, confirm it persists.
- [x] 4.2 Run the full test suites and confirm they pass: `npm run test --workspace=@tumaet/webapp` (364/364) and `./gradlew test` in `services/extension-backend` (30/31 — the 1 failure, `ExtensionBackendApplicationTests.contextLoads`, is the same pre-existing, unrelated failure noted in `add-projects`).
