package com.rocayasociados.extensionbackend.projects

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectsRepository : JpaRepository<Project, UUID> {
    fun findAllByOwner_KeycloakIdOrderByUpdatedAtDesc(keycloakId: String): List<Project>
}
