package com.rocayasociados.extensionbackend.projects

import com.rocayasociados.extensionbackend.users.User
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class ProjectsService(
    private val projectsRepository: ProjectsRepository,
    private val diagramsRepository: DiagramsRepository
) {
    fun getProject(projectId: UUID, requesterKeycloakId: String): Project =
        getOwnedProjectOrThrow(projectId, requesterKeycloakId)

    fun linkDiagramToProject(projectId: UUID, diagramRedisId: String, requesterKeycloakId: String): Diagram {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        val diagram = Diagram(project = project, redisId = diagramRedisId)
        return diagramsRepository.save(diagram)
    }

    fun createProject(name: String, description: String, owner: User): Project {
        val project = Project(name = name, description = description, owner = owner)
        return projectsRepository.save(project)
    }

    fun changeProjectName(projectId: UUID, newName: String, requesterKeycloakId: String): Project {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        project.name = newName
        return projectsRepository.save(project)
    }

    fun changeProjectDescription(projectId: UUID, newDescription: String, requesterKeycloakId: String): Project {
        val project = getOwnedProjectOrThrow(projectId, requesterKeycloakId)
        project.description = newDescription
        return projectsRepository.save(project)
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