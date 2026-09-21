# Proposal

## Why

Every project in `extension-backend` is authorized through a single check: `project.owner.keycloakId == requester`. There is no way for an owner to give anyone else access to a project — not even read-only access — so a project's diagrams are only ever usable by the one account that created them. This change replaces the direct `Project -> owner` relationship with a permission model (`ProjectPermission`) that supports three roles (`OWNER`, `COLLABORATOR`, `VIEWER`) and adds the first way to grant access to another user: sharing a project by email.

This change is scoped to `extension-backend` only. How the webapp surfaces sharing/roles, and any implications for `diagrams-backend`, are left for a follow-up change.

## What Changes

- **BREAKING**: `Project` loses its direct `owner: User` relationship. Ownership becomes a `ProjectPermission` row like any other permission, not a distinct entity relationship.
- Add `ProjectPermission` as a persisted entity: `(project, user, permission)` with `permission` one of `OWNER`, `COLLABORATOR`, `VIEWER`, unique per `(project, user)`.
- Replace the single owner-only authorization gate in `ProjectsService` with a graduated check against the requester's permission level. The hierarchy is `OWNER ⊇ COLLABORATOR ⊇ VIEWER`:
  - `VIEWER`: view the project, list its diagrams, read diagram body/version history/version content (REST reads only).
  - `COLLABORATOR`: everything `VIEWER` can do, plus edit diagram content (save body, create/restore/rename a version), create new diagrams in the project, and open a live collaboration connection (ws-ticket).
  - `OWNER`: everything `COLLABORATOR` can do, plus rename/delete the project, delete a diagram, delete a version, and share the project.
- Add `POST /api/v1/projects/{projectId}/share`: owner-only. Body carries an email and a role (`COLLABORATOR` or `VIEWER` — `OWNER` can never be granted this way). Looks up the target by email among users who have previously authenticated with `extension-backend`. If found, creates or updates that user's `ProjectPermission` row for the project (re-sharing changes the existing role rather than erroring). If no user matches the email, the request is rejected (404) and nothing changes. Sharing a project with its own owner is rejected.
- `GET /api/v1/projects` (list) now returns every project the requester has any permission on (`OWNER`, `COLLABORATOR`, or `VIEWER`), not only projects they own.
- **BREAKING**: `ProjectResponse` drops the `ownerId` field tied to the removed direct relationship and gains a field identifying the requester's own role on that project (needed once the list can mix roles). Owner identity, where still needed, is resolved through the project's `OWNER` permission row.
- **BREAKING**: `User`'s `email` becomes system-managed, synced from the Keycloak JWT's `email` claim on every authenticated request, instead of a value the user sets manually. `PATCH /api/v1/me` stops accepting an `email` field; `displayName` remains user-editable. This is what makes "share by email" reliable — any user who has ever logged in has a trustworthy, queryable email — instead of depending on them having separately visited profile settings.
- No data migration: the local/dev database is dropped and recreated from the updated schema (`ddl-auto=update`) rather than backfilling existing `owner_id` data into `ProjectPermission` rows.
- Out of scope for this change: revoking access, changing an existing collaborator's role via a dedicated endpoint (re-sharing covers the "change role" case), and listing a project's members.

## Capabilities

### New Capabilities

(none — this change modifies two existing capabilities)

### Modified Capabilities

- `projects`: replaces every "only the owner" requirement with the `OWNER`/`COLLABORATOR`/`VIEWER` hierarchy, adds the share-by-email requirement, and changes "list own projects" to "list projects I have access to."
- `identity`: changes the "Gestión del perfil propio" requirement so `email` is system-managed (synced from the Keycloak token) instead of a value the user sets independently of their token.

## Impact

- **Code**: `Project.kt`, `ProjectPermission.kt`, `User.kt`, `ProjectsRepository.kt`, `ProjectsService.kt`, `ProjectsController.kt`, `ProjectDtos.kt`, `CurrentUserFilter.kt`, `UsersService.kt`, `UserDtos.kt`, `UsersController.kt`, `JwtExtensions.kt` (new `email` claim accessor), plus a new `ProjectPermissionRepository`.
- **API**: `GET /api/v1/projects` response shape and result set change; `ProjectResponse` schema changes; new `POST /api/v1/projects/{projectId}/share` endpoint; `PATCH /api/v1/me` no longer accepts `email`.
- **Data**: new `project_permissions` table; `projects.owner_id` column removed; new unique constraint on `users.email`. Local/dev database is dropped and recreated — no production data exists yet for this service.
- **Consumers**: the webapp currently reads `ProjectResponse.ownerId` and calls `PATCH /api/v1/me` with an `email` field (see `add-user-profile`/`collab-name-from-profile`) — both break. Fixing the webapp is explicitly deferred to a follow-up change; this proposal only flags the breakage.
