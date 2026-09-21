package com.rocayasociados.extensionbackend.projects

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ProjectsRepository : JpaRepository<Project, UUID> {
    /** Every project the given user has any `ProjectPermission` on, most recently modified first. */
    @Query(
        "select p from Project p join ProjectPermission pp on pp.project = p " +
            "where pp.user.keycloakId = :keycloakId order by p.updatedAt desc"
    )
    fun findAllAccessibleByUser_KeycloakIdOrderByUpdatedAtDesc(@Param("keycloakId") keycloakId: String): List<Project>
}
