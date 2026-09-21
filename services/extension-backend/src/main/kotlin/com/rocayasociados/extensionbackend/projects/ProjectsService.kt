package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.User
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ProjectsService(
    private val projectsRepository: ProjectsRepository,
    private val diagramsRepository: DiagramsRepository,
    private val diagramsBackendClient: DiagramsBackendClient,
    private val wsTicketService: WsTicketService,
) {
    fun getProject(projectId: UUID, requesterKeycloakId: String): Project =
        getOwnedProjectOrThrow(projectId, requesterKeycloakId)

    fun listOwnProjects(requesterKeycloakId: String): List<Project> =
        projectsRepository.findAllByOwner_KeycloakIdOrderByUpdatedAtDesc(requesterKeycloakId)

    fun listProjectDiagrams(projectId: UUID, requesterKeycloakId: String): List<Diagram> {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        return diagramsRepository.findAllByProject_Id(requireNotNull(project.id))
    }

    /**
     * Lists a project's diagrams enriched with title/type/updatedAt resolved
     * server-side from `diagrams-backend`, so the browser never needs the
     * `redisId` to display them. A diagram whose body can no longer be
     * resolved is reported with `status = "failed"` instead of failing the
     * whole list.
     */
    fun listProjectDiagramsWithMetadata(projectId: UUID, requesterKeycloakId: String): List<ProjectDiagramResponse> {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        val diagrams = diagramsRepository.findAllByProject_Id(requireNotNull(project.id))
        return diagrams.map { diagram ->
            try {
                val body = diagramsBackendClient.getDiagram(diagram.redisId)
                ProjectDiagramResponse(
                    id = requireNotNull(diagram.id),
                    projectId = requireNotNull(diagram.project.id),
                    status = "ready",
                    title = body["title"] as? String,
                    type = body["type"] as? String,
                    updatedAt = (body["updatedAt"] ?: body["createdAt"]) as? String,
                )
            } catch (ex: NoSuchElementException) {
                ProjectDiagramResponse(
                    id = requireNotNull(diagram.id),
                    projectId = requireNotNull(diagram.project.id),
                    status = "failed",
                )
            }
        }
    }

    /** Creates a diagram body in `diagrams-backend` and links it to the project. */
    fun createDiagramInProject(projectId: UUID, model: Map<String, Any?>, requesterKeycloakId: String): Diagram {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        val created = diagramsBackendClient.createDiagram(model)
        val redisId = created["id"] as? String
            ?: throw IllegalStateException("diagrams-backend did not return an id for the created diagram")
        val diagram = Diagram(project = project, redisId = redisId)
        val saved = diagramsRepository.save(diagram)
        touch(project)
        return saved
    }

    fun createProject(name: String, description: String, owner: User): Project {
        val project = Project(name = name, description = description, owner = owner)
        return projectsRepository.save(project)
    }

    fun changeProjectName(projectId: UUID, newName: String, requesterKeycloakId: String): Project {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        project.name = newName
        return projectsRepository.save(touch(project))
    }

    fun changeProjectDescription(projectId: UUID, newDescription: String, requesterKeycloakId: String): Project {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        project.description = newDescription
        return projectsRepository.save(touch(project))
    }

    /** Deletes a single diagram from a project, in both extension-backend and diagrams-backend. */
    fun deleteDiagram(projectId: UUID, diagramId: UUID, requesterKeycloakId: String) {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        diagramsBackendClient.deleteDiagram(diagram.redisId)
        diagramsRepository.delete(diagram)
    }

    /** Fetches a project diagram's current body from `diagrams-backend`. */
    fun getDiagramBody(projectId: UUID, diagramId: UUID, requesterKeycloakId: String): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.getDiagram(diagram.redisId)
    }

    /** Saves a project diagram's body to `diagrams-backend`. */
    fun putDiagramBody(
        projectId: UUID,
        diagramId: UUID,
        requesterKeycloakId: String,
        body: Map<String, Any?>,
        ifMatch: String?,
    ): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.putDiagram(diagram.redisId, body, ifMatch)
    }

    fun listDiagramVersions(
        projectId: UUID,
        diagramId: UUID,
        requesterKeycloakId: String,
        limit: Int?,
        before: String?,
    ): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.listVersions(diagram.redisId, limit, before)
    }

    fun createDiagramVersion(
        projectId: UUID,
        diagramId: UUID,
        requesterKeycloakId: String,
        request: Map<String, Any?>,
    ): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.createVersion(diagram.redisId, request)
    }

    fun getDiagramVersion(
        projectId: UUID,
        diagramId: UUID,
        versionId: String,
        requesterKeycloakId: String,
    ): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.getVersion(diagram.redisId, versionId)
    }

    fun restoreDiagramVersion(
        projectId: UUID,
        diagramId: UUID,
        versionId: String,
        requesterKeycloakId: String,
        request: Map<String, Any?>,
    ): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.restoreVersion(diagram.redisId, versionId, request)
    }

    fun renameDiagramVersion(
        projectId: UUID,
        diagramId: UUID,
        versionId: String,
        requesterKeycloakId: String,
        request: Map<String, Any?>,
    ): Map<String, Any?> {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return diagramsBackendClient.renameVersion(diagram.redisId, versionId, request)
    }

    fun deleteDiagramVersion(
        projectId: UUID,
        diagramId: UUID,
        versionId: String,
        requesterKeycloakId: String,
    ) {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        diagramsBackendClient.deleteVersion(diagram.redisId, versionId)
    }

    /** Issues a short-lived, single-use ticket authorizing a collaboration WS connection to this diagram. */
    fun issueWsTicket(projectId: UUID, diagramId: UUID, requesterKeycloakId: String): String {
        val diagram = getOwnedDiagramOrThrow(projectId, diagramId, requesterKeycloakId)
        return wsTicketService.issueTicket(diagram.redisId)
    }

    /**
     * Deletes a project and every diagram that belongs to it, in both
     * extension-backend and diagrams-backend. Diagrams are queried and
     * deleted explicitly rather than relying on `Project.diagrams` +
     * cascade/orphanRemoval: that association field reflects whatever was
     * last loaded into it, not necessarily every row a concurrent
     * `createDiagramInProject` call has since inserted on the owning side.
     */
    fun deleteProject(projectId: UUID, requesterKeycloakId: String) {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        val diagrams = diagramsRepository.findAllByProject_Id(requireNotNull(project.id))
        diagrams.forEach { diagramsBackendClient.deleteDiagram(it.redisId) }
        diagramsRepository.deleteAll(diagrams)
        projectsRepository.delete(project)
    }

    private fun touch(project: Project): Project {
        project.updatedAt = Instant.now()
        return project
    }

    private fun getOwnedProjectOrThrow(projectId: UUID, requesterKeycloakId: String): Project {
        val project = projectsRepository.findByIdOrNull(projectId)
            ?: throw NoSuchElementException("Project with id $projectId not found")
        if (project.owner.keycloakId != requesterKeycloakId) {
            throw AccessDeniedException("User does not own project $projectId")
        }
        return project
    }

    private fun getOwnedDiagramOrThrow(projectId: UUID, diagramId: UUID, requesterKeycloakId: String): Diagram {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        return diagramsRepository.findByIdOrNull(diagramId)
            ?.takeIf { it.project.id == project.id }
            ?: throw NoSuchElementException("Diagram $diagramId not found in project $projectId")
    }
}