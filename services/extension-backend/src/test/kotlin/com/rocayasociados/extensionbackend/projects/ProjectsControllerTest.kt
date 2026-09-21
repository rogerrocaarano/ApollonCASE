package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.UsersService
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs against the real dev datasource (application-local.properties), like
 * UsersControllerTest. @Transactional rolls back every write at the end of
 * each test so the shared dev database is left untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ProjectsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersService: UsersService

    @Autowired
    lateinit var projectsRepository: ProjectsRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var diagramsBackendClient: DiagramsBackendClient

    private fun newSubject() = "test-subject-${UUID.randomUUID()}"

    private fun createProjectAs(subject: String, name: String, description: String = "desc"): Project {
        val owner = usersService.trackKeycloakUser(subject)
        return projectsRepository.save(Project(name = name, description = description, owner = owner))
    }

    /** Creates a real diagram body in the running diagrams-backend and links it to the project. */
    private fun createRealDiagramIn(project: Project, title: String = "Test diagram"): Diagram {
        val created = diagramsBackendClient.createDiagram(
            mapOf("title" to title, "type" to "ClassDiagram", "version" to "4.0.0")
        )
        val redisId = created["id"] as String
        return diagramsRepository.save(Diagram(project = project, redisId = redisId))
    }

    @Test
    fun `listProjects sin proyectos devuelve lista vacia`() {
        val subject = newSubject()

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `listProjects solo devuelve los proyectos del usuario autenticado`() {
        val owner = newSubject()
        val other = newSubject()
        createProjectAs(owner, "Mine")
        createProjectAs(other, "Not mine")

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(owner) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Mine"))
    }

    @Test
    fun `listProjects ordena por updatedAt descendente`() {
        val subject = newSubject()
        val first = createProjectAs(subject, "First")
        val second = createProjectAs(subject, "Second")

        // Touch "first" so it becomes the most recently updated.
        mockMvc.perform(
            patch("/api/v1/projects/${first.id}")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"name": "First renamed"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(first.id.toString()))
            .andExpect(jsonPath("$[1].id").value(second.id.toString()))
    }

    @Test
    fun `createProject con nombre vacio responde 400`() {
        val subject = newSubject()

        mockMvc.perform(
            post("/api/v1/projects")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"name": "", "description": "desc"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `createProject exitosa asigna owner y aparece en el listado`() {
        val subject = newSubject()

        mockMvc.perform(
            post("/api/v1/projects")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"name": "New project", "description": "desc"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("New project"))

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(subject) }))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `getProject de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(get("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(stranger) }))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `updateProject cambia nombre y actualiza updatedAt`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "Old name")
        val originalUpdatedAt = project.updatedAt

        mockMvc.perform(
            patch("/api/v1/projects/${project.id}")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"name": "New name"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New name"))

        val reloaded = projectsRepository.findById(requireNotNull(project.id)).orElseThrow()
        assertEquals("New name", reloaded.name)
        assertTrue(reloaded.updatedAt.isAfter(originalUpdatedAt))
    }

    @Test
    fun `updateProject de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            patch("/api/v1/projects/${project.id}")
                .with(jwt().jwt { it.subject(stranger) })
                .contentType("application/json")
                .content("""{"name": "Hijacked"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `listProjectDiagrams de un proyecto vacio devuelve lista vacia`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "Empty")

        mockMvc.perform(get("/api/v1/projects/${project.id}/diagrams").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `listProjectDiagrams devuelve los diagramas enlazados con metadata resuelta`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With diagrams")
        createRealDiagramIn(project, title = "Resolved title")

        mockMvc.perform(get("/api/v1/projects/${project.id}/diagrams").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("ready"))
            .andExpect(jsonPath("$[0].title").value("Resolved title"))
            .andExpect(jsonPath("$[0].redisId").doesNotExist())
    }

    @Test
    fun `listProjectDiagrams marca como failed un diagrama que ya no existe en diagrams-backend`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a stale diagram")
        diagramsRepository.save(Diagram(project = project, redisId = "redis-${UUID.randomUUID()}"))

        mockMvc.perform(get("/api/v1/projects/${project.id}/diagrams").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("failed"))
    }

    @Test
    fun `listProjectDiagrams de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(get("/api/v1/projects/${project.id}/diagrams").with(jwt().jwt { it.subject(stranger) }))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createDiagram de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams")
                .with(jwt().jwt { it.subject(stranger) })
                .contentType("application/json")
                .content("""{"model": {"title": "Hijacked", "type": "ClassDiagram", "version": "4.0.0"}}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `createDiagram crea el cuerpo en diagrams-backend, lo enlaza y actualiza updatedAt del proyecto`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "Gets a diagram")
        val originalUpdatedAt = project.updatedAt

        val result = mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content(
                    """{"model": {"title": "Brand new", "type": "ClassDiagram", "version": "4.0.0"}}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.redisId").doesNotExist())
            .andReturn()

        val diagramId = com.jayway.jsonpath.JsonPath.read<String>(result.response.contentAsString, "$.id")
        val saved = diagramsRepository.findById(UUID.fromString(diagramId)).orElseThrow()
        assertTrue(saved.redisId.isNotBlank())

        val reloaded = projectsRepository.findById(requireNotNull(project.id)).orElseThrow()
        assertTrue(reloaded.updatedAt.isAfter(originalUpdatedAt))
    }

    @Test
    fun `deleteDiagram elimina el diagrama del proyecto`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a diagram")
        val diagram = diagramsRepository.save(
            Diagram(project = project, redisId = "redis-${UUID.randomUUID()}")
        )

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}/diagrams/${diagram.id}")
                .with(jwt().jwt { it.subject(subject) })
        ).andExpect(status().isNoContent)

        assertTrue(diagramsRepository.findById(requireNotNull(diagram.id)).isEmpty)
    }

    @Test
    fun `deleteDiagram con un id que no pertenece al proyecto responde 404`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "Project A")
        val otherProject = createProjectAs(subject, "Project B")
        val diagramInOtherProject = diagramsRepository.save(
            Diagram(project = otherProject, redisId = "redis-${UUID.randomUUID()}")
        )

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}/diagrams/${diagramInOtherProject.id}")
                .with(jwt().jwt { it.subject(subject) })
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `deleteDiagram de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = diagramsRepository.save(
            Diagram(project = project, redisId = "redis-${UUID.randomUUID()}")
        )

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}/diagrams/${diagram.id}")
                .with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)

        assertTrue(diagramsRepository.findById(requireNotNull(diagram.id)).isPresent)
    }

    @Test
    fun `deleteProject elimina el proyecto vacio`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "Empty")

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(subject) })
        ).andExpect(status().isNoContent)

        assertTrue(projectsRepository.findById(requireNotNull(project.id)).isEmpty)
    }

    @Test
    fun `deleteProject elimina tambien sus diagramas`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With diagrams")
        val diagram = diagramsRepository.save(
            Diagram(project = project, redisId = "redis-${UUID.randomUUID()}")
        )

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(subject) })
        ).andExpect(status().isNoContent)

        assertTrue(projectsRepository.findById(requireNotNull(project.id)).isEmpty)
        assertTrue(diagramsRepository.findById(requireNotNull(diagram.id)).isEmpty)
    }

    @Test
    fun `deleteProject de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)

        assertTrue(projectsRepository.findById(requireNotNull(project.id)).isPresent)
    }

    @Test
    fun `getDiagramBody del owner devuelve el cuerpo de diagrams-backend`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a diagram")
        val diagram = createRealDiagramIn(project, title = "My diagram")

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(subject) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("My diagram"))
    }

    @Test
    fun `getDiagramBody de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `putDiagramBody del owner actualiza el cuerpo`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a diagram")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            put("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"title": "Updated title", "type": "ClassDiagram", "version": "4.0.0"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(subject) })
        ).andExpect(jsonPath("$.title").value("Updated title"))
    }

    @Test
    fun `putDiagramBody con If-Match desactualizado reenvia el 409 REVISION_MISMATCH de diagrams-backend`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a diagram")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            put("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .header("If-Match", "999")
                .content("""{"title": "Stale", "type": "ClassDiagram", "version": "4.0.0"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("REVISION_MISMATCH"))
    }

    @Test
    fun `putDiagramBody de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            put("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(stranger) })
                .contentType("application/json")
                .content("""{"title": "Hijacked", "type": "ClassDiagram", "version": "4.0.0"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `flujo completo de versiones para el owner`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a diagram")
        val diagram = createRealDiagramIn(project)

        val createResult = mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content(
                    """{"name": "v1", "body": {"id": "${diagram.redisId}", "title": "Test diagram", "type": "ClassDiagram", "version": "4.0.0"}}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("v1"))
            .andReturn()

        val versionId = com.jayway.jsonpath.JsonPath.read<String>(
            createResult.response.contentAsString, "$.id"
        )

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions")
                .with(jwt().jwt { it.subject(subject) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.versions.length()").value(1))

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions/$versionId")
                .with(jwt().jwt { it.subject(subject) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Test diagram"))

        mockMvc.perform(
            patch("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions/$versionId")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"name": "renamed"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("renamed"))

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions/$versionId/restore")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions/$versionId")
                .with(jwt().jwt { it.subject(subject) })
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `listDiagramVersions de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions")
                .with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `issueWsTicket del owner devuelve un ticket`() {
        val subject = newSubject()
        val project = createProjectAs(subject, "With a diagram")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/ws-ticket")
                .with(jwt().jwt { it.subject(subject) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ticket").isNotEmpty)
    }

    @Test
    fun `issueWsTicket de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/ws-ticket")
                .with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `createDiagramVersion de otro usuario responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = createRealDiagramIn(project)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions")
                .with(jwt().jwt { it.subject(stranger) })
                .contentType("application/json")
                .content("""{"body": {"id": "${diagram.redisId}", "title": "x", "type": "ClassDiagram", "version": "4.0.0"}}""")
        ).andExpect(status().isForbidden)
    }
}
