# Spec Delta

## Purpose

Defines which actions the diagram editor's header exposes on desktop and mobile, so the editor stays focused on actions specific to the open diagram rather than duplicating controls already available elsewhere in the app.

## ADDED Requirements

### Requirement: Editor header does not expose ad-hoc diagram sharing
The diagram editor's header SHALL NOT offer a control to share the open diagram, on either the desktop actions island or the narrow-viewport mobile actions pill.

#### Scenario: No Share control on desktop
- **WHEN** a user views the editor header at a desktop width
- **THEN** no "Share" control is present in the header

#### Scenario: No Share control on mobile
- **WHEN** a user views the editor header at a narrow (phone) width
- **THEN** no "Share" control is present in the mobile actions pill

### Requirement: Editor File menu does not offer creating a new diagram
The editor's File menu SHALL NOT include an item to create a new diagram, on either the desktop File dropdown or the mobile File menu.

#### Scenario: No "New Diagram" item in the desktop File menu
- **WHEN** a user opens the File menu from the desktop editor header
- **THEN** no "New Diagram" item is present

#### Scenario: No "New Diagram" item in the mobile File menu
- **WHEN** a user opens the File menu from the narrow-viewport mobile actions pill
- **THEN** no "New Diagram" item is present

### Requirement: Editor header does not expose a Help control
The diagram editor's header SHALL NOT offer a Help control, on either the desktop actions island or the narrow-viewport mobile actions pill.

#### Scenario: No Help control on desktop
- **WHEN** a user views the editor header at a desktop width
- **THEN** no "Help" control is present in the header

#### Scenario: No Help control on mobile
- **WHEN** a user views the editor header at a narrow (phone) width
- **THEN** no "Help" control is present in the mobile actions pill
