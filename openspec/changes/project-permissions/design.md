# Design

## Context

Today `Project.owner: User` is a direct, non-null `@ManyToOne`, and `User.projects` is its inverse `@OneToMany(cascade = ALL, orphanRemoval = true)`. Every authorization check in `ProjectsService` funnels through one private helper, `getOwnedProjectOrThrow`, which does a single equality check (`project.owner.keycloakId == requesterKeycloakId`) and is called by all 14 endpoints in `ProjectsController`. `ProjectPermission` exists in the codebase today only as an unpersisted stub class with no `@Entity`, no id, and no repository.

`User.email` is currently nullable, has no uniqueness constraint, and is only ever written by `UsersService.updateProfile` when a user explicitly edits their profile — `CurrentUserFilter`'s per-request `trackKeycloakUser` call only uses the JWT's `sub` claim today. The webapp's Keycloak login flow requests the `email` OIDC scope, so the JWT is expected to carry an `email` claim once Keycloak enforces profile completion at first login (see proposal.md - Why).

`DiagramRelayHandler` relays WebSocket frames between the browser and `diagrams-backend` verbatim in both directions once a ticket is accepted — it does not distinguish read frames from write frames. Its own code comment states this is a deliberate simplification: "there is only one role today (project owner), so this is a connect-or-reject decision, not per-message filtering."

There is no migration tool (`spring.jpa.hibernate.ddl-auto=update`, no Flyway/Liquibase). Per the proposal, the local/dev database will be dropped and recreated rather than migrated in place.

See proposal.md - Why for the motivation, and the `projects`/`identity` spec deltas for the full behavior contract.

## Goals / Non-Goals

**Goals:**
- Replace the direct owner relationship with a `ProjectPermission` entity and a graduated authorization check.
- Add the share-by-email endpoint.
- Make `User.email` reliably populated for any user who has ever authenticated, via JWT sync.

**Non-Goals:**
- Filtering WebSocket frames by role in `DiagramRelayHandler`. Because that filtering does not exist, `COLLABORATOR` is the minimum role that can obtain a ws-ticket at all in this change — `VIEWER` access stays REST-only. Building read/write frame filtering so `VIEWER` can join a live session read-only is left for a later change.
- Revoking access, a dedicated "change role" endpoint, or listing a project's members (re-sharing already covers changing an existing member's role — see proposal.md).
- Any webapp change, and any change to `diagrams-backend`.
- Searching for users in Keycloak directly (e.g. via its Admin API). Sharing only finds users who have a local `User` row, i.e. who have authenticated with `extension-backend` at least once.
- Preserving existing project data through a migration. The dev database is dropped and recreated.

## Decisions

### `ProjectPermission` as a first-class entity
Add `@Entity @Table(name = "project_permissions")` with a surrogate `id: UUID` primary key (`GenerationType.UUID`, matching `User`'s style) plus `@ManyToOne` to `Project` and `User` and a `permission: ProjectPermissionType` column, under a `@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "user_id"])])` constraint. A surrogate key was chosen over a composite `@EmbeddedId`/`@IdClass` because it matches every other entity in this codebase and Spring Data JPA's derived-query support works more naturally against a simple id; the uniqueness invariant that actually matters (one permission row per user per project) is still enforced by the unique constraint, not by the primary key shape.

`Project` drops its `owner` field and `@JoinColumn`; `User` drops its `projects` field and the cascade/orphan-removal behavior it carried. Deleting a `User` no longer automatically deletes every project they own via JPA cascade — `ProjectsService` must do this explicitly (find every project where this user holds `OWNER`, delete each the same way `deleteProject` already does) if that behavior needs to be preserved. This is flagged as a risk below since there's no user-deletion endpoint yet to observe the gap in.

### Authorization: a graduated check replaces the single owner gate
Replace `getOwnedProjectOrThrow` with a helper that loads the requester's `ProjectPermission` for a project (if any) and compares it against a required minimum, using the natural declaration order of the `ProjectPermissionType` enum (`VIEWER < COLLABORATOR < OWNER`, i.e. declare the enum least-privileged first so `Enum.compareTo` expresses the hierarchy directly). Every call site in `ProjectsService` that currently calls `getOwnedProjectOrThrow`/`getOwnedDiagramOrThrow` passes its endpoint's minimum level, per the mapping in the `projects` spec delta's "Permission hierarchy governs project access" requirement. A user with no `ProjectPermission` row is treated as below `VIEWER` (rejected), not as a missing-vs-present distinction that needs separate handling.

### `POST /projects/{projectId}/share`
Request body: `{ email: String, role: COLLABORATOR | VIEWER }` (an enum that structurally excludes `OWNER` — a Kotlin enum with only those two values, not the full `ProjectPermissionType`, so an `OWNER` value is a deserialization error rather than a runtime check). Handler:
1. Require the requester holds `OWNER` on the project (via the graduated check above).
2. Look up the target `User` by `email` (`UsersRepository.findByEmail`, new). Not found → reject with 404, no changes.
3. Reject if the target's `keycloakId` equals the requester's own.
4. Upsert: find an existing `ProjectPermission` for `(project, target)`; update its `permission` if present, otherwise insert a new row. Both branches run inside the existing `@Transactional` service, and the `(project_id, user_id)` unique constraint backstops the upsert against a concurrent duplicate insert.

### `User.email` synced from the JWT
Add an `email` accessor to `JwtExtensions.kt` alongside `requiredSubject`. `CurrentUserFilter` (or `UsersService.trackKeycloakUser`, which it calls) is extended to take the JWT's `email` claim and write it to the resolved `User` whenever it differs from the stored value, on every authenticated request — not only on first creation. `PATCH /api/v1/me` (`UpdateProfileRequest`) drops the `email` field; only `displayName` remains user-submitted. Add a unique constraint on `users.email` (nullable-safe — Postgres allows multiple `NULL`s under a unique constraint, which matters for any pre-first-login state, though with the dev DB being recreated this mostly matters going forward).

### DTO changes
`ProjectResponse` drops `ownerId` (there is no single FK to read it from anymore) and gains a field carrying the requester's own role on that project (e.g. `myPermission: ProjectPermissionType`) — necessary now that `GET /projects` mixes roles. Where an owner's identity is still needed server-side (none of today's endpoints expose it besides the dropped `ownerId`), it's resolved by querying the project's `OWNER` permission row, not stored redundantly.

## Risks / Trade-offs

- **[Risk] User-deletion cascade behavior changes silently.** There's no user-deletion endpoint today, so this has no observable effect yet, but it's a behavior regression waiting to surface the moment one is added. → Mitigation: note it here now; the follow-up that adds user deletion (if any) must explicitly decide and implement what happens to projects the deleted user owns.
- **[Risk] Granting `VIEWER` a false sense of read-only safety.** Since ws-ticket issuance requires `COLLABORATOR`, a `VIEWER` cannot reach the relay at all in this change — this avoids the frame-filtering gap entirely rather than papering over it. → Mitigation: already reflected in the Non-Goals and the spec delta; a later change must add frame-level filtering before `VIEWER` can be given live access.
- **[Risk] Dropping the dev database loses any real project data currently there.** → Mitigation: explicitly confirmed acceptable by the user for this environment; not applicable once this ships anywhere with real data, which would need an actual migration at that point.
- **[Trade-off] `email` is no longer user-editable**, which is a small UX regression if a user's Keycloak email is wrong or they want a different contact address for ApollonCASE than their SSO identity. Accepted because reliable share-by-email requires a trustworthy, always-populated email, and Keycloak forcing profile completion makes the token a reliable enough source.

## Migration Plan

None. The proposal specifies dropping and recreating the local/dev database; `ddl-auto=update` then creates the new schema (`project_permissions` table, `users.email` unique constraint, `projects.owner_id` column gone) from the updated entities. No rollback plan is needed for the same reason — there's no data to lose.
