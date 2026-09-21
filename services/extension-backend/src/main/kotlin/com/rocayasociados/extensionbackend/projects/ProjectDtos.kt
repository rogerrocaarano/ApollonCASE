package com.rocayasociados.extensionbackend.projects

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
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
    @field:Schema(description = "Permiso del usuario autenticado sobre este proyecto")
    val myPermission: ProjectPermissionType,
    @field:Schema(description = "Fecha de creación del proyecto")
    val createdAt: Instant,
    @field:Schema(description = "Fecha de la última modificación del proyecto (incluye cambios de nombre, descripción o diagramas enlazados)")
    val updatedAt: Instant,
)

fun ProjectAccess.toResponse() = ProjectResponse(
    id = requireNotNull(project.id),
    name = project.name,
    description = project.description,
    myPermission = permission,
    createdAt = project.createdAt,
    updatedAt = project.updatedAt,
)

/** The two roles a project's OWNER can grant through sharing; OWNER itself is structurally excluded. */
enum class ShareableProjectPermissionType {
    COLLABORATOR, VIEWER;

    fun toProjectPermissionType(): ProjectPermissionType = when (this) {
        COLLABORATOR -> ProjectPermissionType.COLLABORATOR
        VIEWER -> ProjectPermissionType.VIEWER
    }
}

data class ShareProjectRequest(
    @field:Schema(description = "Email del usuario con quien se comparte el proyecto")
    @field:NotBlank
    @field:Email
    val email: String,
    @field:Schema(description = "Rol otorgado: COLLABORATOR o VIEWER")
    val role: ShareableProjectPermissionType,
)

data class ProjectPermissionResponse(
    @field:Schema(description = "Id local del usuario con quien se comparte")
    val userId: UUID,
    @field:Schema(description = "Rol otorgado")
    val role: ProjectPermissionType,
)

fun ProjectPermission.toResponse() = ProjectPermissionResponse(
    userId = requireNotNull(user.id),
    role = permission,
)
