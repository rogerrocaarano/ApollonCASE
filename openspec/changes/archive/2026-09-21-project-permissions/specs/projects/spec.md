# Spec Delta

## MODIFIED Requirements

### Requirement: List own projects
`extension-backend` SHALL let an authenticated user list every project on which they have a `ProjectPermission` — as `OWNER`, `COLLABORATOR`, or `VIEWER` — ordered with the most recently modified project first.

#### Scenario: Owner with existing projects lists them
- **WHEN** an authenticated user requests their project list
- **AND** that user has `OWNER`, `COLLABORATOR`, or `VIEWER` access to one or more projects
- **THEN** the response includes exactly the projects they have any access to
- **AND** it does not include projects they have no permission on

#### Scenario: Owner with no projects lists them
- **WHEN** an authenticated user with no accessible projects requests their project list
- **THEN** the response is an empty list

#### Scenario: Projects are ordered by recency
- **WHEN** an authenticated user who has access to multiple projects requests their project list
- **THEN** the projects appear ordered from most recently modified to least recently modified

### Requirement: A diagram belongs to exactly one project
Every diagram SHALL belong to exactly one project from the moment it is created. There SHALL be no way to create a diagram that does not belong to a project.

#### Scenario: Creating a diagram inside a project
- **WHEN** a user with `COLLABORATOR` access or higher creates a new diagram from within a project
- **THEN** the diagram is created
- **AND** it is immediately associated with that project
- **AND** it appears in that project's list of diagrams

### Requirement: List a project's diagrams
`extension-backend` SHALL let a user with `VIEWER` access or higher list the diagrams that belong to a project.

#### Scenario: Owner lists diagrams of a project with diagrams
- **WHEN** a user with `VIEWER` access or higher requests the list of diagrams for a project that has diagrams
- **THEN** the response includes exactly the diagrams belonging to that project

#### Scenario: Owner lists diagrams of an empty project
- **WHEN** a user with `VIEWER` access or higher requests the list of diagrams for a project that has no diagrams yet
- **THEN** the response is an empty list

## REMOVED Requirements

### Requirement: Only the owner can access a project
**Reason**: Replaced by a graduated permission hierarchy (`OWNER`/`COLLABORATOR`/`VIEWER`) — access is no longer all-or-nothing based on ownership alone.
**Migration**: See the new "Permission hierarchy governs project access" requirement.

## ADDED Requirements

### Requirement: Permission hierarchy governs project access
`extension-backend` SHALL authorize every project and diagram operation against the requester's `ProjectPermission` for that project, using a hierarchy where `OWNER` includes every `COLLABORATOR` action, and `COLLABORATOR` includes every `VIEWER` action. A user with no `ProjectPermission` on a project SHALL be treated as having no access to it.

#### Scenario: VIEWER can view but not edit
- **WHEN** a user with `VIEWER` access to a project requests the project, its diagram list, a diagram's body, or its version history
- **THEN** the request succeeds
- **WHEN** that same user attempts to edit a diagram's content, create a diagram, open a live collaboration connection, rename or delete the project, delete a diagram, delete a version, or share the project
- **THEN** the request is rejected

#### Scenario: COLLABORATOR can edit diagram content but not manage the project
- **WHEN** a user with `COLLABORATOR` access to a project edits a diagram's content (saves its body, creates, restores, or renames a version), creates a new diagram in the project, or opens a live collaboration connection to a diagram
- **THEN** the request succeeds
- **WHEN** that same user attempts to rename or delete the project, delete a diagram, delete a version, or share the project
- **THEN** the request is rejected

#### Scenario: OWNER can do everything COLLABORATOR and VIEWER can, plus manage the project
- **WHEN** a project's `OWNER` performs any `VIEWER` or `COLLABORATOR` action, or renames/deletes the project, deletes a diagram, deletes a version, or shares the project
- **THEN** the request succeeds

#### Scenario: User with no permission on a project
- **WHEN** an authenticated user with no `ProjectPermission` row for a project requests that project, its diagrams, or attempts any operation on it
- **THEN** the request is rejected
- **AND** no project or diagram data is returned

### Requirement: A project has exactly one OWNER
`extension-backend` SHALL guarantee that a project has exactly one user with `OWNER` permission at all times. The user who creates a project SHALL become that project's `OWNER`. No operation SHALL be able to grant `OWNER` to a second user or leave a project without an `OWNER`.

#### Scenario: Creating a project assigns its OWNER
- **WHEN** an authenticated user creates a project
- **THEN** a `ProjectPermission` row with `OWNER` is created for that user on the new project

#### Scenario: Sharing can never grant OWNER
- **WHEN** a project's `OWNER` shares the project with another user
- **THEN** the granted role is `COLLABORATOR` or `VIEWER`
- **AND** there is no way to request `OWNER` through the share operation

### Requirement: Share a project by email
`extension-backend` SHALL let a project's `OWNER` grant another user `COLLABORATOR` or `VIEWER` access to the project by specifying that user's email address. Only a user who has previously authenticated with `extension-backend` (and so has a known email) can be found this way.

#### Scenario: Owner shares with a known email
- **WHEN** a project's `OWNER` shares the project with the email address of a user who has previously authenticated with `extension-backend`, choosing `COLLABORATOR` or `VIEWER`
- **THEN** that user is granted the chosen role on the project
- **AND** the project subsequently appears in that user's project list

#### Scenario: Owner shares with an unknown email
- **WHEN** a project's `OWNER` shares the project with an email address that does not match any known user
- **THEN** the request is rejected
- **AND** no permission is created

#### Scenario: Re-sharing changes an existing member's role
- **WHEN** a project's `OWNER` shares the project with a user who already has `COLLABORATOR` or `VIEWER` access, choosing a different role
- **THEN** that user's existing permission is updated to the newly chosen role
- **AND** no duplicate permission is created

#### Scenario: Owner cannot share with themselves
- **WHEN** a project's `OWNER` attempts to share the project using their own email address
- **THEN** the request is rejected

#### Scenario: Non-owner cannot share
- **WHEN** a user who is not a project's `OWNER` attempts to share that project with someone else
- **THEN** the request is rejected
- **AND** no permission is created or changed
