# Design

## Context

See `proposal.md` - Why/Impact for motivation and the free-text model gap. Concretely, this design builds on:

- `ProjectsController.getDiagramBody(projectId, diagramId, jwt)` (`services/extension-backend/src/main/kotlin/.../projects/ProjectsController.kt:172-178`) already returns a project diagram's raw body as `Map<String, Any?>`. Its Swagger `@Operation` text claims this is owner-only, but `ProjectsService.getDiagramBody` (`.../projects/ProjectsService.kt:115-118`) actually calls `requireDiagramPermission(..., ProjectPermissionType.VIEWER)` - the real minimum is VIEWER, and the Swagger description is stale. This design reads that same body at that same VIEWER minimum; it does not add new read access.
- The diagram body's wire shape is `library/schema/uml-model-4.schema.json`: `nodes[]` with `type` (`DiagramNodeType`, includes `"class"`) and an opaque `data` object, `edges[]` with `type` (`DiagramEdgeType`, includes the seven `Class*` edge types) and `data` shaped like `CustomEdgeProps` (`library/lib/edges/EdgeProps.ts`).
- A class node's `data` is `ClassNodeProps` (`library/lib/types/nodes/NodeProps.ts:52-62`): `attributes`/`methods` are `ClassNodeElement[]`, each just `{ id, name, isAbstract? }` - `name` is free text, unparsed anywhere in `library` today. `stereotype` (`"interface"`/`"enumeration"`) and class-level `isAbstract` are structured.
- `extension-backend` is Kotlin/Spring Boot/Gradle 9.7.1, JDK 25 toolchain (`build.gradle.kts`), organized as one package per concern (`projects`, `users`, `common`). No ZIP-writing or Java-source-generation dependency exists yet.

## Goals / Non-Goals

**Goals:**
- Parse the free-text attribute/method/multiplicity syntax deterministically, rejecting (with a precise, element-identified error) anything that doesn't fit, per the spec's validation requirements.
- Generate syntactically and semantically correct Java source (proper imports, no reserved-word/identifier collisions) rather than string-concatenated text that merely looks right.
- Keep the generator self-contained in a new `extension-backend` package, reusing the existing owner-only diagram-body read path.

**Non-Goals** (see proposal.md - What Changes for the product-level exclusions; these are implementation-level boundaries):
- Configurable output (base Java package name, Spring Boot/Java version choice, build tool choice). This iteration fixes these; making them user-configurable is future work if requested.
- Persisting or caching generated projects. Each request regenerates from the current diagram body; nothing is stored.
- Streaming very large archives. The ZIP is built in memory, appropriate for the entity-count sizes a class diagram realistically reaches.

## Decisions

### New `codegen` package in `extension-backend`
A new `com.rocayasociados.extensionbackend.codegen` package holds the parser, the type/relationship mapper, the JavaPoet-based emitters, and the ZIP assembly - mirroring the existing one-package-per-concern layout (`projects`, `users`, `common`). The existing `ProjectsController`/`ProjectsService` gain one new endpoint that delegates into this package rather than absorbing the generation logic themselves, since generation has no other overlap with project/permission CRUD beyond the one permission check it reuses.

### Hand-written recursive-descent-style parser, not a parser-generator
The attribute/method/multiplicity grammar (see `specs/code-generation/spec.md`) is small and regular enough (three short productions) that a hand-written parser using Kotlin regex/string operations is simpler to read, test, and debug than introducing ANTLR (a new build-time code-generation step in an already-multi-language Gradle build). Alternative considered: ANTLR - rejected as disproportionate to the grammar's size and as an added build-pipeline dependency for marginal benefit.

### JavaPoet for source emission, not string templates
Add `com.squareup:javapoet` to generate the `.java` files. It manages imports, naming, and formatting correctly by construction, which matters directly for the spec's "compiles without errors or manual intervention" requirement - hand-rolled string templates are exactly the kind of thing that silently breaks on an edge case (a name that collides with a Java keyword, a missing import, an unescaped character) that free-text diagram input will eventually hit. Alternative considered: raw string templates - rejected because correctness would depend on the template author anticipating every edge case, which is precisely what this change's validation step is trying to avoid doing informally.

### Closed type vocabulary, exact case-sensitive match
Supported `type`/`returnType` tokens: `String`, `int`, `Integer`, `long`, `Long`, `double`, `Double`, `float`, `Float`, `boolean`, `Boolean`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `UUID`, plus the exact name of any class or enumeration present in the same diagram. Matching is exact and case-sensitive (no `"string"` → `String` coercion) so behavior is predictable and testable, consistent with the spec's "reject rather than guess" requirement. Attribute/class/method names are additionally validated as legal Java identifiers that are not Java reserved words, using the same reject-with-location behavior as an unsupported type.

### Multiplicity parsing and cardinality mapping
Multiplicity strings are parsed against `^(0|1|\*|\d+)(\.\.(0|1|\*|\d+))?$`. An upper bound of `1` (bare `"1"` or `"0..1"`) is to-one; an upper bound of `*` or any integer greater than `1` is to-many. This covers every value `ClassDiagramEdgeEditPopover`'s free-text field can produce that has a sensible cardinality reading; anything else (blank, a non-matching string) is a validation error per the spec.

### In-memory ZIP assembly
Build the archive with `ByteArrayOutputStream` + `java.util.zip.ZipOutputStream` and return it as a single response body, rather than writing to a temp directory. Simpler, no cleanup/lifecycle to manage, and appropriate given the expected size (a class diagram's worth of entities/repositories, not a large codebase).

### Fixed generated-project shape
Base package `com.generated.app`, Maven (`pom.xml`) build, and the Java/Spring Boot versions matching `extension-backend`'s own JDK 25 toolchain baseline (pinned to a specific Spring Boot version at implementation time). Not configurable in this iteration (see Non-Goals) - a future change can expose these as request parameters if needed.

## Risks / Trade-offs

- **Existing diagrams almost certainly fail validation today** (free-text attributes were never constrained) → expected and accepted per `proposal.md` - Impact; the validation error must name the offending class/attribute/relationship precisely enough that a user can fix it in the editor and retry, not just report "generation failed".
- **JavaPoet is a new runtime dependency** → low risk: it is a widely used, actively maintained, source-only (no runtime footprint in the generated project itself) library; it only affects `extension-backend`'s own build.
- **Composition/aggregation "which side is the whole" depends on how the user drew the edge** → covered by the multiplicity/edge-endpoint mapping decision above (whichever end declares itself the one-side owns the collection via multiplicity, independent of `source`/`target` visual convention); see Open Questions for the one piece that still needs confirming against `library`'s rendering code before implementation.
- **A diagram with a relationship cycle or self-referential composition** → out of scope to specially detect in this iteration; JPA/Java itself will simply generate a (possibly awkward but compiling) self-referential association, which still satisfies the "compiles without errors" requirement even if the resulting model is not idiomatic.

## Migration Plan

Purely additive: one new endpoint, one new package, one new dependency (JavaPoet). No existing endpoint, data, or behavior changes. Rollback is removing the endpoint/package - no data migration involved since nothing is persisted by this feature.

## Open Questions

- **Which visual end (`source` vs `target`) `library` treats as the "whole" for `ClassComposition`/`ClassAggregation`** - the marker/rendering code wasn't conclusively located during design. This does not change the spec (cardinality mapping already keys off each end's own multiplicity, not off `source`/`target` labels) or the task breakdown; it only affects one implementation detail (which generated field name defaults to which side when both ends are otherwise symmetric) and should be confirmed by reading `library/lib/hooks/useConnect.ts` / `edgeRoutingBehavior.ts` or by testing against the running editor at implementation time.
