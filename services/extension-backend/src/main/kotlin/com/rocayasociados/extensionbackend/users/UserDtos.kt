package com.rocayasociados.extensionbackend.users

import java.util.UUID

data class UserResponse(
    val id: UUID,
    val keycloakId: String,
)

fun User.toResponse() = UserResponse(
    id = requireNotNull(id) { "User must be persisted before it can be returned" },
    keycloakId = keycloakId,
)
