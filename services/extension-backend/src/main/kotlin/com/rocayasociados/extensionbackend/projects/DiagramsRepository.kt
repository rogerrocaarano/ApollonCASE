package com.rocayasociados.extensionbackend.projects

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DiagramsRepository : JpaRepository<Diagram, UUID> {
}
