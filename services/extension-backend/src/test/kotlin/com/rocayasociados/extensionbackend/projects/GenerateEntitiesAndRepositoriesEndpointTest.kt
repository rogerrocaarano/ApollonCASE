package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.UsersService
import java.io.ByteArrayInputStream
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/**
 * Exercises the `/generate` endpoint end to end against a real diagrams-backend
 * and the shared dev Postgres, same setup as ProjectsControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class GenerateEntitiesAndRepositoriesEndpointTest {

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

    private fun newSubject() = "test-subject-${UUID.randomUUID()}"

    private fun createProjectAs(subject: String, name: String): Project {
        val owner = usersService.trackKeycloakUser(subject)
        val project = projectsRepository.save(Project(name = name, description = "desc"))
        projectPermissionRepository.save(
            ProjectPermission(project = project, user = owner, permission = ProjectPermissionType.OWNER)
        )
        return project
    }

    private fun createDiagramWithBody(project: Project, body: Map<String, Any?>): Diagram {
        val created = diagramsBackendClient.createDiagram(body)
        val redisId = created["id"] as String
        return diagramsRepository.save(Diagram(project = project, redisId = redisId))
    }

    private fun classNode(id: String, name: String, attributes: List<String> = emptyList(), methods: List<String> = emptyList()) =
        mapOf(
            "id" to id,
            "type" to "class",
            "width" to 200.0,
            "height" to 100.0,
            "position" to mapOf("x" to 0.0, "y" to 0.0),
            "measured" to mapOf("width" to 200.0, "height" to 100.0),
            "data" to mapOf(
                "name" to name,
                "attributes" to attributes.map { mapOf("id" to "${id}_${it.hashCode()}", "name" to it) },
                "methods" to methods.map { mapOf("id" to "${id}_${it.hashCode()}", "name" to it) },
            ),
        )

    private fun validClassDiagramBody(title: String = "Test class diagram"): Map<String, Any?> = mapOf(
        "title" to title,
        "type" to "ClassDiagram",
        "version" to "4.0.0",
        "nodes" to listOf(
            classNode("n1", "Cliente", attributes = listOf("nombre : String"), methods = listOf("findByNombre(nombre : String) : Cliente")),
        ),
        "edges" to emptyList<Map<String, Any?>>(),
    )

    @Test
    fun `usuario sin permiso en el proyecto recibe 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        usersService.trackKeycloakUser(stranger)
        val project = createProjectAs(owner, "Proyecto ajeno")
        val diagram = createDiagramWithBody(project, validClassDiagramBody())

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/generate")
                .with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `diagrama que no es Class Diagram devuelve 400`() {
        val owner = newSubject()
        val project = createProjectAs(owner, "Proyecto con diagrama de objetos")
        val diagram = createDiagramWithBody(
            project,
            mapOf("title" to "No es de clases", "type" to "ObjectDiagram", "version" to "4.0.0", "nodes" to emptyList<Any>(), "edges" to emptyList<Any>()),
        )

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/generate")
                .with(jwt().jwt { it.subject(owner) })
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `atributo con sintaxis invalida devuelve 422 identificando la clase`() {
        val owner = newSubject()
        val project = createProjectAs(owner, "Proyecto con atributo invalido")
        val body = mapOf(
            "title" to "Diagrama invalido",
            "type" to "ClassDiagram",
            "version" to "4.0.0",
            "nodes" to listOf(classNode("n1", "Cliente", attributes = listOf("nombre sin tipo"))),
            "edges" to emptyList<Map<String, Any?>>(),
        )
        val diagram = createDiagramWithBody(project, body)

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/generate")
                .with(jwt().jwt { it.subject(owner) })
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$[0].className").value("Cliente"))
    }

    @Test
    fun `diagrama valido devuelve un zip descargable con entidad y repositorio`() {
        val owner = newSubject()
        val project = createProjectAs(owner, "Proyecto generable")
        val diagram = createDiagramWithBody(project, validClassDiagramBody())

        val result = mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/generate")
                .with(jwt().jwt { it.subject(owner) })
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"generated-project.zip\""))
            .andReturn()

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(result.response.contentAsByteArray)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries += it.name }
        }

        assertTrue(entries.any { it.endsWith("entity/Cliente.java") }, "entries were: $entries")
        assertTrue(entries.any { it.endsWith("repository/ClienteRepository.java") }, "entries were: $entries")
        assertTrue(entries.any { it == "pom.xml" }, "entries were: $entries")
        assertTrue(entries.none { it.contains("/service/") || it.contains("/controller/") }, "entries were: $entries")
    }
}
