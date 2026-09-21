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
    lateinit var projectPermissionRepository: ProjectPermissionRepository

    @Autowired
    lateinit var diagramsRepository: DiagramsRepository

    @Autowired
    lateinit var diagramsBackendClient: DiagramsBackendClient

    private fun newSubject() = "test-subject-${UUID.randomUUID()}"

    private fun createProjectAs(subject: String, name: String, description: String = "desc"): Project {
        val owner = usersService.trackKeycloakUser(subject)
        val project = projectsRepository.save(Project(name = name, description = description))
        projectPermissionRepository.save(
            ProjectPermission(project = project, user = owner, permission = ProjectPermissionType.OWNER)
        )
        return project
    }

    private fun grant(project: Project, subject: String, permission: ProjectPermissionType) {
        val user = usersService.trackKeycloakUser(subject)
        projectPermissionRepository.save(ProjectPermission(project = project, user = user, permission = permission))
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
            .andExpect(jsonPath("$[0].myPermission").value("OWNER"))
    }

    @Test
    fun `listProjects incluye proyectos donde el usuario es COLLABORATOR o VIEWER`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val viewer = newSubject()
        val collabProject = createProjectAs(owner, "Shared as collaborator")
        val viewerProject = createProjectAs(owner, "Shared as viewer")
        grant(collabProject, collaborator, ProjectPermissionType.COLLABORATOR)
        grant(viewerProject, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(collaborator) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].myPermission").value("COLLABORATOR"))

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(viewer) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].myPermission").value("VIEWER"))
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
    fun `createProject exitosa asigna OWNER y aparece en el listado`() {
        val subject = newSubject()

        mockMvc.perform(
            post("/api/v1/projects")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"name": "New project", "description": "desc"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("New project"))
            .andExpect(jsonPath("$.myPermission").value("OWNER"))

        mockMvc.perform(get("/api/v1/projects").with(jwt().jwt { it.subject(subject) }))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `getProject sin ningun permiso responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(get("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(stranger) }))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getProject como VIEWER lo devuelve con myPermission VIEWER`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "Shared")
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(get("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(viewer) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPermission").value("VIEWER"))
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
    fun `updateProject de un usuario sin permiso responde 403`() {
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
    fun `updateProject como COLLABORATOR responde 403`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "Private")
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            patch("/api/v1/projects/${project.id}")
                .with(jwt().jwt { it.subject(collaborator) })
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
    fun `listProjectDiagrams como VIEWER funciona`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "With diagrams")
        createRealDiagramIn(project, title = "Visible to viewer")
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(get("/api/v1/projects/${project.id}/diagrams").with(jwt().jwt { it.subject(viewer) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
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
    fun `listProjectDiagrams sin ningun permiso responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(get("/api/v1/projects/${project.id}/diagrams").with(jwt().jwt { it.subject(stranger) }))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createDiagram como COLLABORATOR funciona`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "Shared")
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams")
                .with(jwt().jwt { it.subject(collaborator) })
                .contentType("application/json")
                .content("""{"model": {"title": "By collaborator", "type": "ClassDiagram", "version": "4.0.0"}}""")
        ).andExpect(status().isCreated)
    }

    @Test
    fun `createDiagram como VIEWER responde 403`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "Shared")
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams")
                .with(jwt().jwt { it.subject(viewer) })
                .contentType("application/json")
                .content("""{"model": {"title": "By viewer", "type": "ClassDiagram", "version": "4.0.0"}}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `createDiagram sin ningun permiso responde 403`() {
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
    fun `deleteDiagram sin ningun permiso responde 403`() {
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
    fun `deleteDiagram como COLLABORATOR responde 403`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "Shared")
        val diagram = diagramsRepository.save(
            Diagram(project = project, redisId = "redis-${UUID.randomUUID()}")
        )
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}/diagrams/${diagram.id}")
                .with(jwt().jwt { it.subject(collaborator) })
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
    fun `deleteProject sin ningun permiso responde 403`() {
        val owner = newSubject()
        val stranger = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(stranger) })
        ).andExpect(status().isForbidden)

        assertTrue(projectsRepository.findById(requireNotNull(project.id)).isPresent)
    }

    @Test
    fun `deleteProject como COLLABORATOR responde 403`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "Private")
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(collaborator) })
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
    fun `getDiagramBody como VIEWER funciona`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "With a diagram")
        val diagram = createRealDiagramIn(project, title = "Visible")
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(
            get("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(viewer) })
        ).andExpect(status().isOk)
    }

    @Test
    fun `getDiagramBody sin ningun permiso responde 403`() {
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
    fun `putDiagramBody como COLLABORATOR funciona`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "With a diagram")
        val diagram = createRealDiagramIn(project)
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            put("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(collaborator) })
                .contentType("application/json")
                .content("""{"title": "By collaborator", "type": "ClassDiagram", "version": "4.0.0"}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `putDiagramBody como VIEWER responde 403`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "With a diagram")
        val diagram = createRealDiagramIn(project)
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(
            put("/api/v1/projects/${project.id}/diagrams/${diagram.id}/body")
                .with(jwt().jwt { it.subject(viewer) })
                .contentType("application/json")
                .content("""{"title": "By viewer", "type": "ClassDiagram", "version": "4.0.0"}""")
        ).andExpect(status().isForbidden)
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
    fun `putDiagramBody sin ningun permiso responde 403`() {
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
    fun `deleteDiagramVersion como COLLABORATOR responde 403`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "With a diagram")
        val diagram = createRealDiagramIn(project)
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        val versionId = com.jayway.jsonpath.JsonPath.read<String>(
            mockMvc.perform(
                post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions")
                    .with(jwt().jwt { it.subject(owner) })
                    .contentType("application/json")
                    .content(
                        """{"name": "v1", "body": {"id": "${diagram.redisId}", "title": "t", "type": "ClassDiagram", "version": "4.0.0"}}"""
                    )
            ).andReturn().response.contentAsString,
            "$.id",
        )

        mockMvc.perform(
            delete("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions/$versionId")
                .with(jwt().jwt { it.subject(collaborator) })
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `listDiagramVersions sin ningun permiso responde 403`() {
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
    fun `issueWsTicket como COLLABORATOR funciona`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val project = createProjectAs(owner, "With a diagram")
        val diagram = createRealDiagramIn(project)
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/ws-ticket")
                .with(jwt().jwt { it.subject(collaborator) })
        ).andExpect(status().isOk)
    }

    @Test
    fun `issueWsTicket como VIEWER responde 403`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "With a diagram")
        val diagram = createRealDiagramIn(project)
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/ws-ticket")
                .with(jwt().jwt { it.subject(viewer) })
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `issueWsTicket sin ningun permiso responde 403`() {
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
    fun `createDiagramVersion sin ningun permiso responde 403`() {
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

    @Test
    fun `createDiagramVersion como VIEWER responde 403`() {
        val owner = newSubject()
        val viewer = newSubject()
        val project = createProjectAs(owner, "Private")
        val diagram = createRealDiagramIn(project)
        grant(project, viewer, ProjectPermissionType.VIEWER)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/diagrams/${diagram.id}/versions")
                .with(jwt().jwt { it.subject(viewer) })
                .contentType("application/json")
                .content("""{"body": {"id": "${diagram.redisId}", "title": "x", "type": "ClassDiagram", "version": "4.0.0"}}""")
        ).andExpect(status().isForbidden)
    }

    // --- share ---

    @Test
    fun `shareProject con email desconocido responde 404 y no crea permiso`() {
        val owner = newSubject()
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/share")
                .with(jwt().jwt { it.subject(owner) })
                .contentType("application/json")
                .content("""{"email": "unknown@example.com", "role": "VIEWER"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `shareProject otorga COLLABORATOR a un usuario conocido`() {
        val owner = newSubject()
        val target = newSubject()
        usersService.trackKeycloakUser(target, "target@example.com")
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/share")
                .with(jwt().jwt { it.subject(owner) })
                .contentType("application/json")
                .content("""{"email": "target@example.com", "role": "COLLABORATOR"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("COLLABORATOR"))

        mockMvc.perform(get("/api/v1/projects/${project.id}").with(jwt().jwt { it.subject(target) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myPermission").value("COLLABORATOR"))
    }

    @Test
    fun `re-compartir actualiza el rol existente en vez de duplicarlo`() {
        val owner = newSubject()
        val target = newSubject()
        usersService.trackKeycloakUser(target, "target2@example.com")
        val project = createProjectAs(owner, "Private")
        grant(project, target, ProjectPermissionType.VIEWER)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/share")
                .with(jwt().jwt { it.subject(owner) })
                .contentType("application/json")
                .content("""{"email": "target2@example.com", "role": "COLLABORATOR"}""")
        ).andExpect(status().isOk)

        val targetUser = usersService.trackKeycloakUser(target)
        val permissions = projectPermissionRepository.findAllByUser_KeycloakId(target)
        assertEquals(1, permissions.size, "no debe duplicar la fila de permiso")
        assertEquals(ProjectPermissionType.COLLABORATOR, permissions.single().permission)
    }

    @Test
    fun `shareProject con el propio email del owner responde 403`() {
        val owner = newSubject()
        usersService.trackKeycloakUser(owner, "owner@example.com")
        val project = createProjectAs(owner, "Private")

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/share")
                .with(jwt().jwt { it.subject(owner) })
                .contentType("application/json")
                .content("""{"email": "owner@example.com", "role": "VIEWER"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `shareProject de un usuario que no es OWNER responde 403`() {
        val owner = newSubject()
        val collaborator = newSubject()
        val target = newSubject()
        usersService.trackKeycloakUser(target, "target3@example.com")
        val project = createProjectAs(owner, "Private")
        grant(project, collaborator, ProjectPermissionType.COLLABORATOR)

        mockMvc.perform(
            post("/api/v1/projects/${project.id}/share")
                .with(jwt().jwt { it.subject(collaborator) })
                .contentType("application/json")
                .content("""{"email": "target3@example.com", "role": "VIEWER"}""")
        ).andExpect(status().isForbidden)

        assertEquals(0, projectPermissionRepository.findAllByUser_KeycloakId(target).size)
    }
}
