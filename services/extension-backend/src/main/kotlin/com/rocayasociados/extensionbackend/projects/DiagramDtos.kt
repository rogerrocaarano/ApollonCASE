package com.rocayasociados.extensionbackend.projects

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class LinkDiagramRequest(
    @field:Schema(description = "Id del diagrama en Redis a asociar al proyecto")
    val redisId: String,
)

data class DiagramResponse(
    @field:Schema(description = "Id del diagrama")
    val id: UUID,
    @field:Schema(description = "Id del diagrama en Redis")
    val redisId: String,
    @field:Schema(description = "Id del proyecto al que pertenece el diagrama")
    val projectId: UUID,
)

fun Diagram.toResponse() = DiagramResponse(
    id = requireNotNull(id),
    redisId = redisId,
    projectId = requireNotNull(project.id),
)
