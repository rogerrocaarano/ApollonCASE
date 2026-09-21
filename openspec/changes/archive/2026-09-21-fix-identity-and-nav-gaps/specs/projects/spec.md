# Spec Delta

## ADDED Requirements

### Requirement: Returning from a project diagram goes back to its project
The webapp's editor back-to-dashboard control SHALL return the user to the diagram's owning project's diagram list when opened from a project diagram, instead of the top-level projects list.

#### Scenario: Leaving a project diagram returns to its project
- **WHEN** a user opens a diagram that belongs to a project and uses the editor's back control
- **THEN** the webapp navigates to that project's detail page (its diagram list)
- **AND** it does not navigate to the top-level projects list

#### Scenario: Leaving a non-project diagram is unaffected
- **WHEN** a user opens a diagram that does not belong to a project (a local, shared, or playground diagram) and uses the editor's back control
- **THEN** the webapp navigates to the top-level projects list, unchanged from today
