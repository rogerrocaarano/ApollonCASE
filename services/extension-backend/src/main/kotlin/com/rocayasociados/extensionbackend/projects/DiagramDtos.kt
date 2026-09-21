package com.rocayasociados.extensionbackend.projects

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class CreateDiagramRequest(
    @field:Schema(description = "Modelo inicial del diagrama (formato Apollon); extension-backend lo crea en diagrams-backend")
    val model: Map<String, Any?> = emptyMap(),
)

data class DiagramResponse(
    @field:Schema(description = "Id del diagrama")
    val id: UUID,
    @field:Schema(description = "Id del proyecto al que pertenece el diagrama")
    val projectId: UUID,
)

fun Diagram.toResponse() = DiagramResponse(
    id = requireNotNull(id),
    projectId = requireNotNull(project.id),
)

data class WsTicketResponse(
    @field:Schema(description = "Ticket de un solo uso, valido brevemente, para conectar al relay de colaboracion")
    val ticket: String,
)

data class ProjectDiagramResponse(
    @field:Schema(description = "Id del diagrama")
    val id: UUID,
    @field:Schema(description = "Id del proyecto al que pertenece el diagrama")
    val projectId: UUID,
    @field:Schema(description = "'ready' si se pudo resolver en diagrams-backend, 'failed' si no")
    val status: String,
    @field:Schema(description = "Titulo del diagrama, resuelto desde diagrams-backend", nullable = true)
    val title: String? = null,
    @field:Schema(description = "Tipo de diagrama UML, resuelto desde diagrams-backend", nullable = true)
    val type: String? = null,
    @field:Schema(description = "Fecha de la ultima modificacion, resuelta desde diagrams-backend", nullable = true)
    val updatedAt: String? = null,
)
