# Proposal

## Why

Three small but user-visible gaps were left behind by earlier changes. `EditProfileModal` still lets a user edit a `displayName` that only exists to work around a pre-Keycloak world where the webapp had no other way to know who was editing — now that Keycloak reliably provides a user's given/family name, storing and editing a separate copy is redundant, and it drags along a "complete your profile before you can collaborate" gate that has no reason to exist anymore. Second, the "Signed in as / Sign out" control lives buried inside the editor's Help dropdown, and `ProjectsPage`/`ProjectDetailPage` don't render that dropdown at all — so there is currently no way to sign out from the projects view. Third, the editor's "All diagrams" back link is hardcoded to `/`, so leaving a project diagram always dumps the user at the top-level projects list instead of back into the project they came from.

## What Changes

- **BREAKING**: `displayName` stops being a stored, user-editable field. The webapp computes a user's display name directly from their Keycloak token (`given_name` + `family_name`), every time it's needed — never persisted, never round-tripped through `extension-backend`.
- `EditProfileModal` (and the "Edit profile" menu item that opens it) is removed entirely — nothing user-editable remains (`email` is already token-managed; `displayName` becomes token-derived too).
- `ApollonShared.tsx`'s "complete your profile before collaborating" gate is removed. A collaborative session starts immediately using the token-derived name; there is no longer an empty-name case to gate on.
- **BREAKING**: `extension-backend` drops `User.displayName` and `PATCH /api/v1/me` entirely. `GET /api/v1/me` (kept — it exists to verify the auth flow end-to-end) now returns only `{ id, keycloakId, email }`.
- A new `UserMenu` component (extracted from `HomeHelpMenu`'s "Signed in as … / Sign out" block) becomes its own button, separate from Help. It's mounted everywhere `HomeHelpMenu` already appears (editor, legal/404 pages, mobile overflow) **and** newly added to `ProjectsPage` and `ProjectDetailPage`, which have no account/sign-out affordance today.
- The editor's back-to-dashboard control (`HeaderBrandIsland` desktop + `MobileBackPill` mobile, both currently `<BackNav to="/" label="All diagrams">`) becomes context-aware: opened from a project diagram, it links back to that project's diagram list (`/projects/$projectId`, labeled "Project diagrams") instead of the top-level projects list.

Out of scope (explicitly not touched):
- Ad-hoc link sharing (`ShareModal`/`ShareDashboardModal`) and its own in-editor Share button.
- Adding the full Help/Theme island to `ProjectsPage`/`ProjectDetailPage` — only the account/sign-out control is added there, matching what was actually missing.
- Any change to how `extension-backend` authorizes requests (`keycloakId`-based `ProjectPermission` lookups are unaffected — `keycloakId` and `email` are exactly the fields that stay).

## Capabilities

### Modified Capabilities
- `identity`: removes `displayName` as a stored/editable concept — "Gestión del perfil propio" narrows to just the token-synced `email`; "La identidad de colaboración proviene del perfil del usuario" is rewritten so collaboration identity comes directly from the Keycloak token's name claims, with no gate for an incomplete profile; "Webapp does not let a user edit their own email" broadens to cover identity generally (no editable display name either); "Cierre de sesión" gains a scenario that the sign-out control must be reachable from every authenticated view, including the projects list and a project's detail page.
- `projects`: adds a requirement that returning from a project diagram goes back to that project's diagram list, not the top-level projects list.

## Impact

- **webapp**: `EditProfileModal.tsx` (+ test, stories) deleted; `ExtensionApiClient.updateMe` deleted; `HomeHelpMenu.tsx` loses its account block (and the now-unused `EDIT_PROFILE` modal wiring); new `UserMenu.tsx` (+ test) mounted in `EditorHeader.tsx`, `MobileIslands.tsx`, `ChromeSubHeader.tsx`, `ProjectsPage.tsx`, `ProjectDetailPage.tsx`; `ApollonShared.tsx` loses its profile-completion branch and computes `collaborationUser` from the OIDC token instead of `useCurrentUser()`; `HeaderBrandIsland`/`MobileBackPill` (in `EditorHeader.tsx`/`MobileIslands.tsx`) gain project-aware back-target resolution, following the existing `useBackTarget`/`navProvenance` pattern.
- **extension-backend**: `User.kt` drops `displayName`; `UserDtos.kt` drops `UpdateProfileRequest` and the `displayName` field from `UserResponse`; `UsersController.kt` drops `PATCH /api/v1/me`; `UsersService.updateProfile` removed.
- **Data**: `users.display_name` column removed. No migration — the user will drop and recreate the dev database manually, same as `project-permissions`.
- **diagrams-backend**: not touched.
