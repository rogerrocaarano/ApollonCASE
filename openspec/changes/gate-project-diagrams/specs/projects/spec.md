# Spec Delta

## ADDED Requirements

### Requirement: Project diagram content is only reachable through extension-backend
`extension-backend` SHALL be the only path the webapp uses to read or write a project diagram's body, read or write its version history, or join its live collaboration session. The webapp SHALL NOT be given the diagram's `diagrams-backend` id for this purpose.

#### Scenario: Diagram data never carries the diagrams-backend id
- **WHEN** an authenticated user requests a project, its diagram list, or a single diagram
- **THEN** the response identifies each diagram only by its `extension-backend` id
- **AND** it does not include the diagram's `diagrams-backend` id

### Requirement: Only the project owner can read or write a project diagram's body
`extension-backend` SHALL let a project's owner fetch and save the body of a diagram belonging to that project, forwarding to `diagrams-backend` on the owner's behalf. `extension-backend` SHALL reject the same request from any other authenticated user.

#### Scenario: Owner fetches a diagram's body
- **WHEN** a project's owner requests the body of a diagram belonging to that project
- **THEN** `extension-backend` returns the current body from `diagrams-backend`

#### Scenario: Owner saves a diagram's body
- **WHEN** a project's owner submits an updated body for a diagram belonging to that project
- **THEN** `extension-backend` forwards the update to `diagrams-backend`
- **AND** the diagram's stored body reflects the update

#### Scenario: Non-owner cannot read or write a diagram's body
- **WHEN** an authenticated user who is not the project's owner requests to fetch or save the body of one of its diagrams
- **THEN** the request is rejected
- **AND** the diagram's body is not returned or changed

### Requirement: Only the project owner can access a project diagram's version history
`extension-backend` SHALL let a project's owner list, create, fetch, rename, delete, and restore versions of a diagram belonging to that project, forwarding to `diagrams-backend` on the owner's behalf. `extension-backend` SHALL reject the same requests from any other authenticated user.

#### Scenario: Owner manages version history
- **WHEN** a project's owner lists, creates, fetches, renames, deletes, or restores a version of one of its diagrams
- **THEN** `extension-backend` forwards the request to `diagrams-backend`
- **AND** the response reflects the result of that operation

#### Scenario: Non-owner cannot access version history
- **WHEN** an authenticated user who is not the project's owner attempts to list, create, fetch, rename, delete, or restore a version of one of its diagrams
- **THEN** the request is rejected
- **AND** no version data is returned or changed

### Requirement: Live collaboration on a project diagram requires a ticket tied to project ownership
`extension-backend` SHALL let a project's owner obtain a short-lived, single-use ticket for a diagram belonging to that project, and SHALL only allow a collaboration WebSocket connection to proceed when it presents a currently valid ticket issued to that owner for that diagram. Once accepted, `extension-backend` SHALL relay the collaboration session between the webapp and `diagrams-backend` without exposing the diagram's `diagrams-backend` id to the webapp.

#### Scenario: Owner joins the collaboration session
- **WHEN** a project's owner requests a collaboration ticket for one of its diagrams and opens a WebSocket connection presenting that ticket before it expires or is reused
- **THEN** the connection is accepted
- **AND** the owner's collaboration messages are relayed to and from `diagrams-backend`

#### Scenario: Non-owner cannot obtain a ticket
- **WHEN** an authenticated user who is not the project's owner requests a collaboration ticket for one of its diagrams
- **THEN** the request is rejected
- **AND** no ticket is issued

#### Scenario: Connection without a valid ticket is rejected
- **WHEN** a WebSocket connection to the collaboration endpoint presents no ticket, an expired ticket, or a ticket that was already used
- **THEN** the connection is rejected
- **AND** no collaboration messages are relayed
