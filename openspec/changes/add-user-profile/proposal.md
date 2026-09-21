# Proposal

## Why

`extension-backend`'s `User` today only carries `id` and `keycloakId` — the raw Keycloak subject. The only place identity is surfaced in the webapp (`HomeHelpMenu`'s "Signed in as ...") prints that opaque string directly. Before user-directed collaboration (inviting a specific person to a project, showing who else is on it) can be built, users need a human-readable identity — a display name and an email — that they can see and correct themselves. This change adds exactly that, as the smallest useful slice: viewing and editing one's own profile. It does not yet build anything that uses this information (invitations, presence, collaborator lists) — that is deferred to the collaboration work it unblocks.

## What Changes

- `extension-backend`'s `User` gains two optional fields: `displayName` and `email`. Both start empty on a new user (Option A from exploration: no attempt to prefill them from Keycloak claims, since the access token `extension-backend` sees is not guaranteed to carry profile claims) and stay empty until the user sets them.
- `GET /api/v1/me` (already exists) starts returning these two fields alongside the existing ones.
- New `PATCH /api/v1/me`: lets the authenticated user set their own `displayName` and/or `email`, following the same partial-update contract already established by `PATCH /api/v1/projects/{id}` (omitted fields are left untouched).
- New **Edit profile** entry in the webapp's Help/account menu (`HomeHelpMenu.tsx`, replacing the "Signed in as {keycloakId}" plain label with an actionable one), opening a modal — reusing the existing `HomeDialog*` primitives and the same field/dialog/API-client pattern already built for renaming a project — with two fields: Display name, Email.
- While `displayName` is unset, the webapp keeps falling back to `keycloakId` for the "Signed in as ..." line, exactly as it does today — no blocking onboarding step, no forced profile completion.

## Capabilities

### New Capabilities
_None._

### Modified Capabilities
- `identity`: adds a requirement that a user can view and edit their own profile (display name, email); no existing requirement's behavior changes.

## Impact

- **`extension-backend`** (`users` package): `User` entity gains `displayName`/`email` columns (nullable); `UserResponse` gains the two fields; new `PATCH /api/v1/me` endpoint and corresponding service method.
- **`webapp`**: `ExtensionApiClient`'s `CurrentUser` type gains the two fields and a new `updateMe()` call; a new `EditProfileModal` (registered like `RenameProjectModal`); `HomeHelpMenu.tsx` gains an "Edit profile" menu item and uses `displayName` (falling back to `keycloakId`) in its "Signed in as ..." line.
- No changes to Keycloak, to login/logout/token-refresh behavior, or to `diagrams-backend`.
