# Spec Delta

## Purpose

Generates downloadable Java/Spring Boot source code from a project's class diagram, starting with the JPA entities and Spring Data repositories that can be derived purely from the diagram's structure, without needing AI or diagram-authored business logic.

## ADDED Requirements

### Requirement: Generation is available only for Class Diagrams
`extension-backend` SHALL let a user generate code only from a diagram of type Class Diagram belonging to a project. A request against any other diagram type SHALL be rejected without producing output.

#### Scenario: Class diagram
- **WHEN** an authorized user requests generation for a project's Class Diagram
- **THEN** `extension-backend` proceeds to validate and generate from that diagram

#### Scenario: Non-class diagram
- **WHEN** a request targets a diagram that is not a Class Diagram
- **THEN** `extension-backend` rejects the request and does not produce a ZIP

### Requirement: Generation requires the same access as reading the diagram's body
`extension-backend` SHALL require the same permission it requires to fetch that diagram's body before generating code from it. A request from a user without that permission SHALL be rejected.

#### Scenario: Authorized requester
- **WHEN** a user with sufficient permission on the diagram's project requests generation
- **THEN** `extension-backend` proceeds with validation and generation

#### Scenario: Unauthorized requester
- **WHEN** an authenticated user without sufficient permission on the diagram's project requests generation
- **THEN** the request is rejected and no code is generated

### Requirement: Attributes and methods must match the supported syntax
Each class or interface attribute SHALL be written as `[visibility] name : type [= default]`, and each method destined for generation SHALL be written as `[visibility] name(parameter : type, ...) : returnType`, where every `type`/`returnType` is either one of `extension-backend`'s supported primitive/wrapper types or the name of another class or enumeration in the same diagram. `extension-backend` SHALL reject generation for the whole diagram, without producing a partial ZIP, when any attribute or multiplicity that would be included in the output does not match this syntax or references an unsupported type.

#### Scenario: Well-formed attribute
- **WHEN** a class has an attribute written as `- nombre : String`
- **THEN** it generates as a `String` field on that class's JPA entity

#### Scenario: Unparseable attribute blocks generation
- **WHEN** a class has an attribute that does not match the supported syntax (for example, missing a type) or whose type is not supported
- **THEN** `extension-backend` rejects the generation request, identifies the offending class and attribute, and does not produce a ZIP

#### Scenario: Unparseable relationship multiplicity blocks generation
- **WHEN** a relationship attached to the diagram has a source or target multiplicity that does not resolve to a supported cardinality
- **THEN** `extension-backend` rejects the generation request, identifies the offending relationship, and does not produce a ZIP

### Requirement: Enumeration classes generate from their listed constants
For a class whose stereotype is "enumeration", `extension-backend` SHALL treat each of its attributes as the name of one enum constant (not as a `name : type` pair) and generate a Java `enum` with those constants.

#### Scenario: Enumeration class
- **WHEN** a class has the "enumeration" stereotype and attributes named `ACTIVE`, `INACTIVE`
- **THEN** it generates as a Java `enum` with constants `ACTIVE` and `INACTIVE`

### Requirement: Relationships map to JPA associations and Java inheritance
`extension-backend` SHALL translate each class-diagram relationship into the generated entities as follows: inheritance relationships become a Java `extends` between the corresponding entities; realization relationships become a Java `implements` against the corresponding interface; composition and aggregation relationships become a JPA one-to-many or one-to-one association (per the endpoints' multiplicities) on the containing entity, with composition additionally cascading delete to the contained entity and aggregation not cascading delete; association relationships (unidirectional or bidirectional) become a JPA association whose to-one/to-many shape follows each endpoint's multiplicity; dependency relationships produce no structural change in the generated entities.

#### Scenario: Composition cascades delete
- **WHEN** a class diagram has a composition relationship from `Pedido` (multiplicity `1`) to `LineaPedido` (multiplicity `*`)
- **THEN** `Pedido`'s generated entity has a one-to-many association to `LineaPedido` that cascades delete and removes orphaned `LineaPedido` rows

#### Scenario: Aggregation does not cascade delete
- **WHEN** a class diagram has an aggregation relationship from `Equipo` (multiplicity `1`) to `Jugador` (multiplicity `*`)
- **THEN** `Equipo`'s generated entity has a one-to-many association to `Jugador` that does not cascade delete

#### Scenario: Inheritance
- **WHEN** a class diagram has an inheritance relationship from `Empleado` to `Persona`
- **THEN** the generated `Empleado` entity extends the generated `Persona` entity

#### Scenario: Realization
- **WHEN** a class diagram has a realization relationship from `Factura` to an interface `Facturable`
- **THEN** the generated `Factura` entity implements the generated `Facturable` interface

#### Scenario: Dependency has no structural effect
- **WHEN** a class diagram has a dependency relationship between two classes
- **THEN** neither generated entity gains a field or association from that relationship

### Requirement: Only Spring-Data-style query methods are generated on repositories
`extension-backend` SHALL include a class's method in its generated repository only when the method's name matches Spring Data's derived-query convention (`findBy...`, `existsBy...`, `countBy...`, `deleteBy...`) and its parameters/return type match the supported syntax. Any other method on a class SHALL be left out of the generated output; omitting such a method SHALL NOT cause generation to fail or be reported as an error.

#### Scenario: Query-convention method is generated
- **WHEN** a class has a method `findByEmail(email : String) : Cliente`
- **THEN** the generated repository for that class declares a matching `findByEmail` method

#### Scenario: Non-query method is silently excluded
- **WHEN** a class has a method that does not match the `findBy`/`existsBy`/`countBy`/`deleteBy` convention, such as `calcularTotal() : BigDecimal`
- **THEN** that method is not present anywhere in the generated output, and its absence does not block generation or appear as an error

### Requirement: Output is a downloadable, compilable Java project
When validation succeeds, `extension-backend` SHALL produce a downloadable ZIP archive containing a complete Java/Spring Boot project - including build configuration and an application entry point - such that the project compiles without errors or manual changes using the Java and Spring Boot versions the project declares.

#### Scenario: Successful generation
- **WHEN** a Class Diagram passes validation
- **THEN** the user receives a downloadable ZIP whose contents compile without errors or manual intervention

### Requirement: Generation excludes services, controllers, DTOs, and validation in this iteration
This iteration of generation SHALL produce only JPA entities and Spring Data repositories. It SHALL NOT generate service classes, REST controllers, DTOs, Bean Validation annotations, or an OpenAPI specification.

#### Scenario: Generated project has no service or controller layer
- **WHEN** a user downloads a generated project from this capability
- **THEN** the archive contains entity and repository classes only, with no service, controller, or DTO classes
