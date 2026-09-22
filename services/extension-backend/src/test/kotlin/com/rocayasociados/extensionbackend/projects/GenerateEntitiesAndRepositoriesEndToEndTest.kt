package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.codegen.JavaCompilationHelper
import com.rocayasociados.extensionbackend.users.UsersService
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/**
 * End-to-end: a realistic fixture diagram (composition, aggregation, a
 * many-to-many association, inheritance, realization, an enum-typed
 * attribute, a Spring-Data-convention method, and a non-matching method) goes
 * through the real endpoint, and the resulting ZIP's actual Java sources are
 * compiled for real. There is no `mvn` in this environment, so - per the
 * task's own "or equivalent" - this compiles the extracted sources with the
 * JDK compiler against this test JVM's own classpath, which already carries
 * spring-boot-starter-data-jpa/Hibernate/the postgresql driver (the same
 * dependencies the generated `pom.xml` declares), rather than resolving them
 * again through Maven.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class GenerateEntitiesAndRepositoriesEndToEndTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersService: UsersService

    @Autowired
    lateinit var projectsRepository: ProjectsRepository

    @Autowired
    lateinit var projectPermissionRepository: ProjectPermissionRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var diagramsBackendClient: DiagramsBackendClient

    private fun classNode(
        id: String,
        name: String,
        stereotype: String? = null,
        attributes: List<String> = emptyList(),
        methods: List<String> = emptyList(),
    ) = mapOf(
        "id" to id,
        "type" to "class",
        "width" to 200.0,
        "height" to 100.0,
        "position" to mapOf("x" to 0.0, "y" to 0.0),
        "measured" to mapOf("width" to 200.0, "height" to 100.0),
        "data" to buildMap {
            put("name", name)
            stereotype?.let { put("stereotype", it) }
            put("attributes", attributes.map { mapOf("id" to "${id}_${it.hashCode()}", "name" to it) })
            put("methods", methods.map { mapOf("id" to "${id}_${it.hashCode()}", "name" to it) })
        },
    )

    private fun relationshipEdge(
        id: String,
        type: String,
        sourceId: String,
        targetId: String,
        sourceMultiplicity: String,
        targetMultiplicity: String,
    ) = mapOf(
        "id" to id,
        "type" to type,
        "source" to sourceId,
        "target" to targetId,
        "sourceHandle" to "$sourceId-handle",
        "targetHandle" to "$targetId-handle",
        "data" to mapOf(
            "sourceMultiplicity" to sourceMultiplicity,
            "sourceRole" to null,
            "targetMultiplicity" to targetMultiplicity,
            "targetRole" to null,
            "points" to emptyList<Any>(),
        ),
    )

    @Test
    fun `diagrama realista genera un proyecto que compila y excluye servicios y controllers`() {
        val owner = "test-subject-${UUID.randomUUID()}"
        usersService.trackKeycloakUser(owner)
        val project = projectsRepository.save(Project(name = "Proyecto e2e", description = "desc"))
        projectPermissionRepository.save(
            ProjectPermission(
                project = project,
                user = usersService.trackKeycloakUser(owner),
                permission = ProjectPermissionType.OWNER,
            )
        )

        val body = mapOf(
            "title" to "Diagrama realista",
            "type" to "ClassDiagram",
            "version" to "4.0.0",
            "nodes" to listOf(
                classNode("persona", "Persona", attributes = listOf("nombre : String")),
                classNode("empleado", "Empleado", attributes = listOf("salario : BigDecimal")),
                classNode("facturable", "Facturable", stereotype = "interface"),
                classNode(
                    "cliente", "Cliente",
                    attributes = listOf("nombre : String"),
                    methods = listOf(
                        "findByNombre(nombre : String) : Cliente",
                        "calcularDescuento() : BigDecimal",
                    ),
                ),
                classNode("estadoPedido", "EstadoPedido", stereotype = "enumeration", attributes = listOf("PENDIENTE", "ENVIADO", "ENTREGADO")),
                classNode("pedido", "Pedido", attributes = listOf("fecha : LocalDate", "estado : EstadoPedido")),
                classNode("lineaPedido", "LineaPedido", attributes = listOf("cantidad : int")),
                classNode("equipo", "Equipo", attributes = listOf("nombre : String")),
                classNode("jugador", "Jugador", attributes = listOf("dorsal : int")),
                classNode("alumno", "Alumno", attributes = listOf("nombre : String")),
                classNode("curso", "Curso", attributes = listOf("nombre : String")),
            ),
            "edges" to listOf(
                relationshipEdge("e-inherit", "ClassInheritance", "empleado", "persona", "1", "1"),
                relationshipEdge("e-realize", "ClassRealization", "empleado", "facturable", "1", "1"),
                relationshipEdge("e-comp", "ClassComposition", "lineaPedido", "pedido", "*", "1"),
                relationshipEdge("e-agg", "ClassAggregation", "jugador", "equipo", "*", "1"),
                relationshipEdge("e-assoc", "ClassBidirectional", "alumno", "curso", "*", "*"),
            ),
        )

        val created = diagramsBackendClient.createDiagram(body)
        val redisId = created["id"] as String
        val diagram = diagramsRepository.save(Diagram(project = project, redisId = redisId))

        val result = mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/generate")
                .with(jwt().jwt { it.subject(owner) })
        )
            .andExpect(status().isOk)
            .andReturn()

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                entries[entry.name] = zip.readAllBytes()
            }
        }

        // 6.3: no service/controller/DTO layer in this iteration.
        assertTrue(
            entries.keys.none { it.contains("/service/") || it.contains("/controller/") || it.contains("/dto/") },
            "entries were: ${entries.keys}"
        )

        // 6.1/6.2: extract every .java file and actually compile them together.
        val sourceDir = Files.createTempDirectory("e2e-generated-src")
        entries.filterKeys { it.endsWith(".java") }.forEach { (path, bytes) ->
            val javaPath = sourceDir.resolve(path.removePrefix("src/main/java/"))
            Files.createDirectories(javaPath.parent)
            Files.write(javaPath, bytes)
        }
        val javaFiles = Files.walk(sourceDir).filter { it.toString().endsWith(".java") }.map { it.toFile() }.toList()
        assertTrue(javaFiles.isNotEmpty(), "No .java files found in the generated ZIP")

        val diagnostics = JavaCompilationHelper.compileFiles(javaFiles)
        assertTrue(diagnostics.isEmpty(), "Esperaba que el proyecto generado compilara sin errores pero obtuvo:\n$diagnostics")
    }
}
