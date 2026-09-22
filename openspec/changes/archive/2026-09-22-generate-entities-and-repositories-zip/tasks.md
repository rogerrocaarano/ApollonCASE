# Tasks

## 1. Setup

- [x] 1.1 Add `com.squareup:javapoet` to `services/extension-backend/build.gradle.kts` and verify `./gradlew build` succeeds.
- [x] 1.2 Create the `com.rocayasociados.extensionbackend.codegen` package and verify it compiles empty (placeholder file removed once real code lands in it).
- [x] 1.3 Confirm which visual end (`source` or `target`) `library` treats as the "whole" for `ClassComposition`/`ClassAggregation` by reading `library/lib/hooks/useConnect.ts` and `library/lib/edges/edgeRoutingBehavior.ts`, or by testing in the running editor — record the finding as a code comment where the mapping is implemented (design.md's Open Question; does not change this task list otherwise, since cardinality is keyed off each end's own multiplicity, not off source/target). **Resolved via `library/lib/utils/edgeUtils.ts`'s `getEdgeMarkerStyles`: the rhombus/triangle is always `markerEnd`, i.e. drawn at `target`. So `target` is the "whole" (composition/aggregation) or superclass/interface (inheritance/realization); `source` is the "part"/subclass/implementor. Documented in `RelationshipMapper.kt`'s class doc.**

## 2. Attribute/method/multiplicity parser

- [x] 2.1 Implement the attribute parser for `[visibility] name : type [= default]` and verify unit tests cover: valid attribute with and without visibility/default, missing type, and malformed syntax (each malformed case producing a distinct, element-identifying parse failure rather than a generic error).
- [x] 2.2 Implement the method parser for `[visibility] name(param : type, ...) : returnType` and verify unit tests cover: zero/one/multiple parameters, missing return type (void), and malformed parameter lists.
- [x] 2.3 Implement the closed type vocabulary check (`String`, `int`, `Integer`, `long`, `Long`, `double`, `Double`, `float`, `Float`, `boolean`, `Boolean`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `UUID`, or another class/enum name in the same diagram) and verify unit tests cover an unsupported type name, a case-mismatched type name (e.g. `string`), and a type that correctly resolves to another diagram class.
- [x] 2.4 Implement Java-identifier/reserved-word validation for class, attribute, and method names, and verify unit tests cover a reserved word (e.g. `class`, `int` used as a name) and an invalid identifier (e.g. starting with a digit).
- [x] 2.5 Implement multiplicity parsing against `^(0|1|\*|\d+)(\.\.(0|1|\*|\d+))?$` with to-one/to-many classification, and verify unit tests cover `1`, `0..1`, `*`, `0..*`, `1..*`, a bare integer greater than 1, and an unparseable string.

## 3. Relationship and cardinality mapping

- [x] 3.1 Implement inheritance (`ClassInheritance` → `extends`) and realization (`ClassRealization` → `implements`) mapping and verify a unit test generates the correct `extends`/`implements` clause for a simple two-class fixture.
- [x] 3.2 Implement composition/aggregation mapping to one-to-many/one-to-one JPA associations per the confirmed "whole" side (task 1.3), with composition adding `cascade = CascadeType.ALL, orphanRemoval = true` and aggregation adding neither, and verify unit tests cover both relationship types at `1`↔`*` and `1`↔`0..1` multiplicities.
- [x] 3.3 Implement plain association (`ClassBidirectional`/`ClassUnidirectional`) mapping to the correct to-one/to-many JPA annotation per endpoint multiplicity, and verify a unit test covers a many-to-many case (`*` on both ends).
- [x] 3.4 Implement dependency (`ClassDependency`) as a no-op in the mapper and verify a unit test asserts neither entity gains a field from it.

## 4. Java source emission

- [x] 4.1 Implement JPA `@Entity` emission (fields, getters/setters or accessors per project convention, ID field/strategy, relationship annotations from section 3) using JavaPoet, and verify a unit test generates a simple entity whose emitted source parses/compiles (e.g. via an in-memory Java compiler check or a golden-file compile test).
- [x] 4.2 Implement `enum` emission for classes with the "enumeration" stereotype (attributes as constants, not `name : type` pairs) and verify a unit test generates a compiling enum with the expected constants.
- [x] 4.3 Implement Spring Data `JpaRepository` emission, including only methods whose name matches `findBy...`/`existsBy...`/`countBy...`/`deleteBy...`, and verify a unit test confirms a non-matching method (e.g. `calcularTotal()`) is absent from the generated repository without raising an error.
- [x] 4.4 Implement the fixed project scaffold (base package `com.generated.app`, `pom.xml`, Spring Boot application entry point, `application.properties`) and verify the scaffold alone (no entities) compiles.

## 5. Endpoint, permission, and ZIP assembly

- [x] 5.1 Add the generation endpoint to `ProjectsController`/`ProjectsService`, requiring the same permission as `getDiagramBody` (`ProjectPermissionType.VIEWER` minimum, per its actual implementation - not the stale owner-only wording in its Swagger `@Operation`), and verify a test confirms an authenticated user with no permission on the project is rejected.
- [x] 5.2 Wire the endpoint to reject non-Class-Diagram types before parsing, and verify a test confirms a non-class diagram is rejected without invoking the parser.
- [x] 5.3 Wire validation failures (sections 2-3) to reject the whole request with an error identifying the offending class/attribute/method/relationship, producing no ZIP, and verify a test covers at least one failure from each parser (attribute, method, multiplicity).
- [x] 5.4 Assemble the validated output into an in-memory ZIP (`ByteArrayOutputStream` + `ZipOutputStream`) and return it as a downloadable response, and verify an integration test downloads the ZIP for a valid fixture diagram and asserts its entry list matches the expected entity/repository/scaffold files.

## 6. End-to-end verification

- [x] 6.1 Build a realistic fixture class diagram (multiple classes, one enumeration, one composition, one aggregation, one plain association, one inheritance pair, one realization, at least one `findBy...` method and one non-matching method) and verify generating it end-to-end produces a ZIP.
- [x] 6.2 Extract that ZIP and verify the project compiles with `mvn -q compile` (or equivalent) without errors or manual changes, confirming the spec's "compiles without errors or manual intervention" requirement. **No `mvn` binary is installed in this environment; used the documented "or equivalent" - the extracted `.java` files are compiled for real with the JDK compiler against this test JVM's own classpath (which already carries the same spring-boot-starter-data-jpa/Hibernate/postgresql dependencies the generated `pom.xml` declares).**
- [x] 6.3 Verify the generated project's archive contains no service, controller, or DTO classes, confirming the "excludes services/controllers/DTOs" requirement.
