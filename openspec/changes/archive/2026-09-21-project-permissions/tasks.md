# Tasks

## 1. `ProjectPermission` data model

- [x] 1.1 Turn `ProjectPermission` into a persisted `@Entity` (`@Table(name = "project_permissions")`) with a surrogate `id: UUID` (`GenerationType.UUID`), `@ManyToOne` to `Project` and `User`, a `permission: ProjectPermissionType` column, and a unique constraint on `(project_id, user_id)`; order the enum `VIEWER, COLLABORATOR, OWNER` so `compareTo` expresses the hierarchy. Verify the app still starts (`./gradlew bootRun` with the `local` profile against a freshly dropped dev DB) and the table appears with the expected columns/constraint.
- [x] 1.2 Add `ProjectPermissionRepository : JpaRepository<ProjectPermission, UUID>` with `findByProject_IdAndUser_Id`, `findAllByUser_KeycloakId`, and `findByProject_IdAndPermission` (for resolving a project's `OWNER`); cover each with a `@DataJpaTest`.
- [x] 1.3 Remove `Project.owner` and its `@JoinColumn`; remove `User.projects` and its cascade/orphan-removal. Update `ProjectsRepository.findAllByOwner_KeycloakIdOrderByUpdatedAtDesc` to a new derived or `@Query` method that lists projects by any `ProjectPermission` for the user, ordered by `updatedAt` descending (`findAllByPermissions_User_KeycloakId...` or an explicit join query). Verify with a `@DataJpaTest` covering a user with `OWNER`, `COLLABORATOR`, and `VIEWER` rows across different projects.
- [x] 1.4 Update `ProjectsService.createProject` to also insert a `ProjectPermission(user = owner, project = project, permission = OWNER)` in the same transaction as the project insert; verify with a service test asserting the new project has exactly one `OWNER` permission row after creation.

## 2. Authorization: graduated permission check

- [x] 2.1 Replace `getOwnedProjectOrThrow`/`getOwnedDiagramOrThrow` in `ProjectsService` with a helper that resolves the requester's `ProjectPermission` for a project and throws `AccessDeniedException` unless it is at least a given minimum `ProjectPermissionType` (missing permission = below `VIEWER`); reuse it for both the project-level and diagram-level lookups. Verify with a service test matrix covering `VIEWER`/`COLLABORATOR`/`OWNER`/no-permission against an above/at/below-minimum check.
- [x] 2.2 Wire each `ProjectsService` method to its endpoint's minimum level per the `projects` spec delta: `VIEWER` for `getProject`, `listProjectDiagramsWithMetadata`, `getDiagramBody`, `listDiagramVersions`, `getDiagramVersion`; `COLLABORATOR` for `createDiagramInProject`, `putDiagramBody`, `createDiagramVersion`, `restoreDiagramVersion`, `renameDiagramVersion`, `issueWsTicket`; `OWNER` for `changeProjectName`, `changeProjectDescription`, `deleteProject`, `deleteDiagram`, `deleteDiagramVersion`. Verify with `ProjectsControllerTest` cases: a `VIEWER` succeeds on each `VIEWER`-tier endpoint and is rejected on every `COLLABORATOR`/`OWNER`-tier one; a `COLLABORATOR` succeeds through `COLLABORATOR`-tier and is rejected on `OWNER`-tier; an `OWNER` succeeds everywhere.
- [x] 2.3 Verify a user with no `ProjectPermission` row for a project is rejected identically to an explicitly-too-low role on every endpoint (`ProjectsControllerTest`).

## 3. `User.email` synced from the Keycloak token

- [x] 3.1 Add an `email` accessor to `JwtExtensions.kt` alongside `requiredSubject` (nullable — absence should not break `trackKeycloakUser`).
- [x] 3.2 Extend `UsersService.trackKeycloakUser` to accept the JWT's `email` claim and update the resolved `User`'s `email` whenever it differs from the stored value, on every call (not only on first creation); update `CurrentUserFilter` to pass it through. Verify with a `UsersService` test: first-time user gets `email` from the token; returning user whose token `email` changed gets it updated; returning user with an unchanged token `email` is not re-saved unnecessarily.
- [x] 3.3 Add `findByEmail(email: String): User?` to `UsersRepository` and a unique constraint on `users.email`. Verify with a `@DataJpaTest`.
- [x] 3.4 Remove `email` from `UpdateProfileRequest`/`UsersService.updateProfile`'s parameters; `PATCH /api/v1/me` only accepts `displayName` going forward. Update `UsersControllerTest` to assert an `email` field in the request body is ignored (or rejected) and never changes the stored `email`.

## 4. Share a project by email

- [x] 4.1 Add `ShareProjectRequest(email: String, role: ShareableProjectPermissionType)` to `ProjectDtos.kt`, where `ShareableProjectPermissionType` is a `COLLABORATOR`/`VIEWER`-only enum (no `OWNER` value, so granting `OWNER` is a deserialization error, not a runtime check).
- [x] 4.2 Add `ProjectsService.shareProject(projectId, requesterKeycloakId, email, role)`: requires the requester holds `OWNER` (via the task 2 helper); looks up the target by email (404/`NoSuchElementException` if not found, no changes made); rejects if the target is the requester themself; upserts a `ProjectPermission` for `(project, target)` with the given role (updates the role if a row already exists, inserts otherwise). Verify with `ProjectsServiceTest`/`ProjectsControllerTest` cases: unknown email is rejected with no row created; owner sharing with themselves is rejected; sharing with a user who has no existing permission creates one; sharing with a user who already has `VIEWER` and choosing `COLLABORATOR` updates the existing row instead of duplicating it; a non-owner (`COLLABORATOR`/`VIEWER`/no-permission) calling share is rejected.
- [x] 4.3 Add `POST /api/v1/projects/{projectId}/share` to `ProjectsController` wired to 4.2, returning the created/updated permission (e.g. `{ userId, role }`) on success. Verify with a controller test asserting the response shape on success and the correct status codes for the 404/self-share/non-owner rejection cases.

## 5. `ProjectResponse` DTO changes

- [x] 5.1 Remove `ownerId` from `ProjectResponse`; add a field carrying the requester's own role on that project (e.g. `myPermission: ProjectPermissionType`), resolved from the `ProjectPermission` the authorization check in task 2 already loaded. Update `ProjectsControllerTest` assertions on `listProjects`/`getProject` responses to check the new field instead of `ownerId`, including a case where the response differs correctly for an `OWNER` vs. a `COLLABORATOR` vs. a `VIEWER` requester on the same project.

## 6. Full verification

- [x] 6.1 Drop and recreate the local dev database; start `extension-backend` with the `local` profile and confirm it starts cleanly against the new schema (no leftover `owner_id` column, `project_permissions` table present).
- [x] 6.2 Run the full `extension-backend` test suite (`./gradlew test`) and confirm everything passes.
