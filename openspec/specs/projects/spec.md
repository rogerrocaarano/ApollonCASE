# projects

## Purpose

Lets an authenticated user organize their diagrams into projects that they create, rename, and browse, with every diagram belonging to exactly one project.

## Requirements

### Requirement: Projects screen is the webapp's landing page
After logging in, the webapp SHALL show the user's projects as the default landing screen instead of a flat list of diagrams.

#### Scenario: Authenticated user opens the webapp
- **WHEN** an authenticated user navigates to the webapp's root
- **THEN** the webapp shows the user's projects
- **AND** it does not show the diagrams the user has previously created or opened, grouped outside of a project

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
- **WHEN** an authenticated user who owns multiple projects requests their project list
- **THEN** the projects appear ordered from most recently modified to least recently modified

### Requirement: Create a project
`extension-backend` SHALL let an authenticated user create a project with a name and a description, and the requesting user SHALL become that project's owner.

#### Scenario: Successful creation
- **WHEN** an authenticated user creates a project with a non-empty name
- **THEN** a new project is created with that user as owner
- **AND** the new project appears in that user's project list

#### Scenario: Empty name rejected
- **WHEN** an authenticated user attempts to create a project with an empty or missing name
- **THEN** the project is not created
- **AND** the request is rejected

### Requirement: Rename a project
`extension-backend` SHALL let a project's owner change that project's name and/or description. Only the owner SHALL be allowed to do so.

#### Scenario: Owner renames a project
- **WHEN** a project's owner changes its name, its description, or both
- **THEN** the project reflects the new value(s)
- **AND** fields left unspecified keep their previous value

#### Scenario: Non-owner cannot rename
- **WHEN** an authenticated user who is not the project's owner attempts to rename it
- **THEN** the request is rejected
- **AND** the project is not changed

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

### Requirement: Delete a project
`extension-backend` SHALL let a project's owner delete that project. Deleting a project SHALL delete every diagram that belongs to it, since a diagram cannot exist without a project.

#### Scenario: Owner deletes an empty project
- **WHEN** a project's owner deletes a project that has no diagrams
- **THEN** the project no longer appears in that user's project list

#### Scenario: Owner deletes a project with diagrams
- **WHEN** a project's owner deletes a project that has diagrams
- **THEN** the project no longer appears in that user's project list
- **AND** none of that project's diagrams remain accessible, in `extension-backend` or in `diagrams-backend`

#### Scenario: Non-owner cannot delete
- **WHEN** an authenticated user who is not the project's owner attempts to delete it
- **THEN** the request is rejected
- **AND** the project is not deleted

### Requirement: Delete a diagram from a project
`extension-backend` SHALL let a project's owner delete a single diagram belonging to that project, removing it from both `extension-backend` and `diagrams-backend`.

#### Scenario: Owner deletes a diagram
- **WHEN** a project's owner deletes one of that project's diagrams
- **THEN** the diagram no longer appears in that project's list of diagrams
- **AND** the diagram is no longer accessible in `diagrams-backend`
- **AND** the project's other diagrams are unaffected

#### Scenario: Non-owner cannot delete a diagram
- **WHEN** an authenticated user who is not the project's owner attempts to delete one of its diagrams
- **THEN** the request is rejected
- **AND** the diagram is not deleted

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

### Requirement: Webapp lets an owner share a project by email
The webapp SHALL let a project's `OWNER` share that project with another user by entering an email address and choosing `COLLABORATOR` or `VIEWER`, through a dedicated UI reachable from the project's detail page. The webapp SHALL NOT show this UI to a user whose role on the project is not `OWNER`.

#### Scenario: Owner shares a project from the project detail page
- **WHEN** a project's `OWNER` opens that project's detail page
- **THEN** the webapp shows a control to share the project
- **AND** using it with a known user's email and a chosen role (`COLLABORATOR` or `VIEWER`) grants that user the chosen role

#### Scenario: Non-owner does not see the share control
- **WHEN** a user with `COLLABORATOR` or `VIEWER` access opens a project's detail page
- **THEN** the webapp does not show a control to share that project

#### Scenario: Sharing with an unknown email surfaces an error
- **WHEN** a project's `OWNER` submits the share form with an email that does not match any known user
- **THEN** the webapp shows an error explaining the email was not found
- **AND** no permission is granted

#### Scenario: Sharing with oneself surfaces an error
- **WHEN** a project's `OWNER` submits the share form with their own email address
- **THEN** the webapp shows an error and does not grant a permission

### Requirement: Webapp gates project and diagram actions by role
The webapp SHALL only present a project or diagram management action to a user whose role on that project meets the minimum role the action requires: renaming or deleting a project, and sharing a project, require `OWNER`; creating a diagram requires `COLLABORATOR` or `OWNER`; deleting a diagram requires `OWNER`. A user without the required role SHALL NOT see the corresponding control.

#### Scenario: Owner sees all management actions
- **WHEN** a project's `OWNER` views that project's card or detail page
- **THEN** the webapp shows controls to rename the project, delete the project, share the project, create a diagram, and delete a diagram

#### Scenario: Collaborator sees only collaborator-level actions
- **WHEN** a user with `COLLABORATOR` access views that project's card or detail page
- **THEN** the webapp shows a control to create a diagram
- **AND** it does not show controls to rename the project, delete the project, share the project, or delete a diagram

#### Scenario: Viewer sees no management actions
- **WHEN** a user with `VIEWER` access views that project's card or detail page
- **THEN** the webapp does not show controls to rename the project, delete the project, share the project, create a diagram, or delete a diagram
