# Spec Delta

## ADDED Requirements

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
