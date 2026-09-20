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
) {
    fun getProject(projectId: UUID, requesterKeycloakId: String): Project =
        getOwnedProjectOrThrow(projectId, requesterKeycloakId)

    fun listOwnProjects(requesterKeycloakId: String): List<Project> =
        projectsRepository.findAllByOwner_KeycloakIdOrderByUpdatedAtDesc(requesterKeycloakId)

    fun listProjectDiagrams(projectId: UUID, requesterKeycloakId: String): List<Diagram> {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        return diagramsRepository.findAllByProject_Id(requireNotNull(project.id))
    }

    fun linkDiagramToProject(projectId: UUID, diagramRedisId: String, requesterKeycloakId: String): Diagram {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        val diagram = Diagram(project = project, redisId = diagramRedisId)
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
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        val diagram = diagramsRepository.findByIdOrNull(diagramId)
            ?.takeIf { it.project.id == project.id }
            ?: throw NoSuchElementException("Diagram $diagramId not found in project $projectId")
        diagramsBackendClient.deleteDiagram(diagram.redisId)
        diagramsRepository.delete(diagram)
    }

    /**
     * Deletes a project and every diagram that belongs to it, in both
     * extension-backend and diagrams-backend. Diagrams are queried and
     * deleted explicitly rather than relying on `Project.diagrams` +
     * cascade/orphanRemoval: that association field reflects whatever was
     * last loaded into it, not necessarily every row a concurrent
     * `linkDiagramToProject` call has since inserted on the owning side.
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
}