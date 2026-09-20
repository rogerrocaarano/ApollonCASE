package com.rocayasociados.extensionbackend.projects

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateProjectRequest(
    @field:Schema(description = "Nombre visible del proyecto", example = "Diagrama de arquitectura")
    @field:NotBlank
    val name: String,
    @field:Schema(description = "Descripción libre del proyecto", example = "Modelo C4 del backend")
    val description: String,
)

data class UpdateProjectRequest(
    @field:Schema(description = "Nuevo nombre del proyecto; se omite si no se quiere cambiar", nullable = true)
    val name: String? = null,
    @field:Schema(description = "Nueva descripción del proyecto; se omite si no se quiere cambiar", nullable = true)
    val description: String? = null,
)

data class ProjectResponse(
    @field:Schema(description = "Id del proyecto")
    val id: UUID,
    @field:Schema(description = "Nombre del proyecto")
    val name: String,
    @field:Schema(description = "Descripción del proyecto")
    val description: String,
    @field:Schema(description = "Id local (keycloakId-linked) del usuario dueño del proyecto")
    val ownerId: UUID,
    @field:Schema(description = "Fecha de creación del proyecto")
    val createdAt: Instant,
    @field:Schema(description = "Fecha de la última modificación del proyecto (incluye cambios de nombre, descripción o diagramas enlazados)")
    val updatedAt: Instant,
)

fun Project.toResponse() = ProjectResponse(
    id = requireNotNull(id),
    name = name,
    description = description,
    ownerId = requireNotNull(owner.id),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
