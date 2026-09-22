# Proposal

## Why

`docs/requisitos.md` lists RF009 (*Crítico*) as generating a full Spring Boot backend — entities, repositories, services, controllers, DTOs, validations — from a class diagram. The full scope needs either AI or a much richer diagram model to interpret business-method bodies, neither of which exists today. But the part of RF009 that needs neither — JPA entities and Spring Data repositories, derived purely from the class diagram's structure (attributes, relationships, and Spring-Data-style finder method names) — is buildable now on top of what `extension-backend` already has: proxied read access to a project diagram's body (`gate-project-diagrams`) and a documented commitment to be the code-generation surface (`services/extension-backend/README.md`). Shipping this slice first also surfaces the class diagram's biggest gap for any future generation work: attributes and methods are free-text strings today (see Impact), and a real generator forces that gap into the open where it can be validated instead of silently producing broken code.

## What Changes

- Add a new `extension-backend` endpoint that generates a downloadable ZIP containing a compilable Java/Spring Boot project skeleton (JPA `@Entity` classes + Spring Data `JpaRepository` interfaces + a minimal `pom.xml`/`build.gradle` and `Application` class) from a project's class diagram.
- Add a parser for the class diagram's free-text attribute/method syntax (`[visibility] name : Type [= default]` for attributes, `[visibility] name(params) : ReturnType` for methods) against a closed vocabulary of supported types, so generation can be refused with a precise error instead of producing code that doesn't compile.
- Map class-diagram relationships (`ClassInheritance`, `ClassRealization`, `ClassAggregation`, `ClassComposition`, `ClassBidirectional`/`ClassUnidirectional`, `ClassDependency`) plus their multiplicities to JPA relationship annotations and Java inheritance/interface implementation.
- Recognize class-diagram methods whose name matches Spring Data's derived-query convention (`findBy…`, `existsBy…`, `countBy…`, `deleteBy…`) and emit them as real repository methods (no body needed - Spring Data implements them). Any other method is silently left out of this iteration's output.
- **Explicit non-goals for this change** (deferred to a later RF009 iteration, not solved here): services, controllers, DTOs, Bean Validation annotations, OpenAPI, and any interpretation of non-query business methods.

## Capabilities

### New Capabilities

- `code-generation`: generating downloadable source-code artifacts from a project's class diagram. This change covers its first, narrowest slice — JPA entities and Spring Data repositories only, delivered as a ZIP. `openspec list --specs` confirms no such capability exists yet; `identity`, `projects`, and `diagram-creation` are the only current specs and none of them cover generating code from a diagram's content.

### Modified Capabilities

_None._ `projects` already specifies that `extension-backend` is the sole path to a project diagram's body; this change is a new consumer of that existing access, not a change to its requirements.

## Impact

- **Affected code**: new package(s) in `services/extension-backend` (parser, JPA/relationship mapper, project-skeleton templating, ZIP assembly, a new controller endpoint); no changes to `library`, `webapp`, or `diagrams-backend`.
- **Depends on**: the diagram-body read path `gate-project-diagrams` is adding (`extension-backend` proxying a project owner's diagram body from `diagrams-backend`) - this change only reads that body, it does not add new access.
- **Known model gap surfaced, not fixed, by this change**: `library`'s `ClassNodeElement.name` (attributes/methods) and `CustomEdgeProps.{source,target}Multiplicity/Role` are unvalidated free-text strings (confirmed by reading `library/lib/types/nodes/NodeProps.ts` and `library/lib/edges/EdgeProps.ts` - no parser or validation exists anywhere in `library` today). This change adds a parser in `extension-backend` for that free text rather than changing the editor's data model, so any diagram written before this change may fail to generate until its attributes/methods/multiplicities are edited to match the supported grammar - that is expected, not a regression, since nothing could have generated from those diagrams before either.
- **Not a full RF009**: `docs/requisitos.md` should eventually reflect that RF009 ships incrementally; this change does not edit that document, since it is external requirements documentation rather than project code, but `design.md`/`tasks.md` should not claim RF009 is complete.
