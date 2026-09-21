# Spec Delta

## ADDED Requirements

### Requirement: Webapp does not let a user edit their own email
Since `email` is system-managed and synced from the user's Keycloak token, the webapp SHALL NOT present an editable email field or submit an email value when updating the user's own profile. The webapp MAY display the current email as read-only information.

#### Scenario: Profile editor has no editable email field
- **WHEN** an authenticated user opens their profile editor
- **THEN** the webapp does not show an input the user can type into to change their email
- **AND** saving the profile does not send an email value to the server

#### Scenario: Email shown reflects the current token
- **WHEN** an authenticated user opens their profile editor
- **THEN** any email shown matches the `email` claim of their current session
