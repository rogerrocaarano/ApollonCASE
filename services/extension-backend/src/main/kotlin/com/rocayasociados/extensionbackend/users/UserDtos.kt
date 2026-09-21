package com.rocayasociados.extensionbackend.users

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class UserResponse(
    @field:Schema(description = "Id local del usuario")
    val id: UUID,
    @field:Schema(description = "Id (sub) del usuario en Keycloak")
    val keycloakId: String,
    @field:Schema(description = "Correo sincronizado desde el token de Keycloak; vacío si el token no lo trae", nullable = true)
    val email: String?,
)

fun User.toResponse() = UserResponse(
    id = requireNotNull(id) { "User must be persisted before it can be returned" },
    keycloakId = keycloakId,
    email = email,
)
