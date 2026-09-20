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
`extension-backend` SHALL let an authenticated user list only the projects they own, ordered with the most recently modified project first.

#### Scenario: Owner with existing projects lists them
- **WHEN** an authenticated user requests their project list
- **AND** that user owns one or more projects
- **THEN** the response includes exactly the projects they own
- **AND** it does not include projects owned by other users

#### Scenario: Owner with no projects lists them
- **WHEN** an authenticated user with no projects requests their project list
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
- **WHEN** a project's owner creates a new diagram from within that project
- **THEN** the diagram is created
- **AND** it is immediately associated with that project
- **AND** it appears in that project's list of diagrams

### Requirement: List a project's diagrams
`extension-backend` SHALL let a project's owner list the diagrams that belong to that project.

#### Scenario: Owner lists diagrams of a project with diagrams
- **WHEN** a project's owner requests the list of diagrams for a project that has diagrams
- **THEN** the response includes exactly the diagrams belonging to that project

#### Scenario: Owner lists diagrams of an empty project
- **WHEN** a project's owner requests the list of diagrams for a project that has no diagrams yet
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

### Requirement: Only the owner can access a project
`extension-backend` SHALL reject any attempt by a user other than a project's owner to view that project, list its diagrams, rename it, delete it, create a diagram inside it, or delete one of its diagrams.

#### Scenario: Non-owner attempts to view a project
- **WHEN** an authenticated user who is not a project's owner requests that project, its diagram list, or attempts to create a diagram inside it
- **THEN** the request is rejected
- **AND** no project or diagram data is returned
