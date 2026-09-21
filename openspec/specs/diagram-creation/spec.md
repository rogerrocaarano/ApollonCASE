# diagram-creation Specification

## Purpose

Defines what the webapp must do when a user creates a new diagram, at a stage where the product only supports Class Diagrams.

## Requirements

### Requirement: Creating a diagram only asks for a name
The webapp SHALL let a user create a new diagram by providing only a name. The created diagram SHALL always be a Class Diagram; the webapp SHALL NOT present any other diagram type or any template as a choice in this flow.

#### Scenario: User creates a diagram by name
- **WHEN** a user opens the "new diagram" flow, enters a name, and confirms
- **THEN** the webapp creates a Class Diagram with that name and no type or template selection is presented

#### Scenario: Name is required
- **WHEN** a user opens the "new diagram" flow and has not entered a name
- **THEN** the webapp does not allow the diagram to be created

### Requirement: Diagram creation works both locally and inside a project
The "new diagram" flow described above SHALL apply whether it is opened from outside any project (local creation) or from within a project (project-scoped creation), preserving each context's existing ownership: a diagram created from within a project SHALL belong to that project, and a diagram created outside a project SHALL be created locally.

#### Scenario: Creating from within a project
- **WHEN** a user opens the "new diagram" flow from within a project and confirms with a name
- **THEN** the created Class Diagram belongs to that project

#### Scenario: Creating outside a project
- **WHEN** a user opens the "new diagram" flow outside of any project and confirms with a name
- **THEN** the created Class Diagram is created locally, not attached to any project

### Requirement: Diagram type is shown, not chosen
The "new diagram" flow SHALL visually indicate to the user that a Class Diagram is being created, without offering it as an interactive choice.

#### Scenario: Type indicator is not selectable
- **WHEN** a user views the "new diagram" flow
- **THEN** the Class Diagram indicator is shown for information only and cannot be changed to select a different type
