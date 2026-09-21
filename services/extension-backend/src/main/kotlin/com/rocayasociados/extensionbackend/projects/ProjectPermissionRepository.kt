package com.rocayasociados.extensionbackend.projects

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectPermissionRepository : JpaRepository<ProjectPermission, UUID> {
    fun findByProject_IdAndUser_Id(projectId: UUID, userId: UUID): ProjectPermission?
    fun findByProject_IdAndUser_KeycloakId(projectId: UUID, keycloakId: String): ProjectPermission?
    fun findAllByUser_KeycloakId(keycloakId: String): List<ProjectPermission>
    fun findByProject_IdAndPermission(projectId: UUID, permission: ProjectPermissionType): ProjectPermission?
}
