# Design

## Context

See `proposal.md` for motivation. Relevant existing state:

- `extension-backend`'s `User` (`users/User.kt`) has only `id` and `keycloakId`. It's created idempotently by `CurrentUserFilter` on the first authenticated request (`UsersService.trackKeycloakUser`), which only ever sees the JWT `sub` claim.
- `GET /api/v1/me` (`UsersController.kt`) already exists and returns `UserResponse { id, keycloakId }`, sourced from `CurrentUserHolder` (a request-scoped bean the filter populates).
- `PATCH /api/v1/projects/{id}` (`ProjectsController.kt` / `ProjectsService.kt`) already establishes this project's partial-update contract: a request DTO with nullable fields, `field?.let { ... }` per field, unspecified fields left untouched. This change's `PATCH /api/v1/me` follows the same shape.
- The webapp's `HomeHelpMenu.tsx` already calls `ExtensionApiClient.me()` (via `@tanstack/react-query`) and renders `Signed in as {currentUser.keycloakId}` — the only place identity is shown today.
- `webapp/src/components/modals/RenameProjectModal.tsx` is the direct template for the new edit-profile modal: `HomeDialog*` primitives, local field state seeded from the current value, a `ProjectsApiClient`-style call, `onRenamed`-style callback, error surfaced via `toast.error` (see the `add-projects` change's design.md for why toast over inline notice was chosen for this family of modals).

## Goals / Non-Goals

**Goals:**
- Let a user set a human-readable display name and email that replace the raw `keycloakId` wherever identity is shown.
- Reuse the exact partial-update and modal patterns already established by projects, rather than inventing new ones.

**Non-Goals:**
- Reading or writing anything to/from Keycloak itself (no Keycloak Admin API calls, no token-claim prefill — see proposal's "Option A" decision).
- Email verification (confirmation link, uniqueness enforcement across users, etc.) — format validation only.
- Any UI beyond the single edit-profile entry point (no dedicated "Account" page, no avatar, no other profile fields).
- Anything that *consumes* `displayName`/`email` beyond the one "Signed in as ..." line (collaborator lists, invitations, presence) — that's the collaboration work this change unblocks, not this change.

## Decisions

### `displayName` and `email` are plain nullable columns, not a separate `Profile` entity
A one-to-one `Profile` table would be the "correct" normalization for a set of fields that might grow later, but today it's exactly two optional strings on an entity (`User`) that already exists and already has exactly one owner (the user themself, via `/me`). Adding columns directly avoids a join for the single query path (`/me`) that needs them, matches how `Project.description` is modeled (plain nullable column, not a side table), and is a trivial migration to walk back if a real `Profile` concept emerges later.

### `PATCH /api/v1/me` takes no path parameter — it always acts on the caller
Unlike `PATCH /api/v1/projects/{id}`, there's no id in the path: the JWT `sub` (via `CurrentUserHolder`) is the only way to identify which `User` to update. This makes "edit someone else's profile" structurally unreachable rather than a case to authorize against — there is no request shape that names another user.

### No format constraint on `displayName`; `email` gets basic format validation only
`displayName` is freeform display text — no length cap or character restriction is introduced (none was requested, and inventing one wouldn't serve a stated need). `email`, if provided, must look like an email (`@field:Email`, the same `spring-boot-starter-validation` dependency `CreateProjectRequest.name`'s `@NotBlank` already uses) — but is not verified (no confirmation flow) and not checked for uniqueness across users, per the proposal's explicit scope.

### The webapp falls back to `keycloakId` when `displayName` is empty; `email` has no display fallback
`HomeHelpMenu`'s "Signed in as ..." line uses `displayName?.trim() || keycloakId` — identical fallback shape to how `DiagramCard`/`ProjectCard` already show "Untitled diagram"/"Untitled project" for blank names. `email` isn't shown anywhere on its own in this change (there's no second display surface for it yet), so it has no fallback to design.

## Risks / Trade-offs

- **[Risk]** A user sets a `displayName` that collides with another user's (no uniqueness enforced) → **Mitigation**: none needed yet — nothing in this change's scope uses `displayName` to *identify* a user (lookups still go through `keycloakId`/internal `id`); it's display-only. Worth revisiting once collaboration features start resolving a name back to a user.
- **[Trade-off]** No verification that a user's saved `email` is actually theirs → **Mitigation**: explicitly out of scope (proposal); acceptable because nothing in this change sends mail to it or uses it for account recovery.

## Migration Plan

Additive only: two new nullable columns on `users` (`ddl-auto=update`, same mechanism the `add-projects` change already used for `Project.createdAt`/`updatedAt` — no default needed here since `NULL` is itself the valid "unset" state, unlike those non-null timestamp columns). No data migration; existing `User` rows simply read back with both fields null until their owner edits them.
