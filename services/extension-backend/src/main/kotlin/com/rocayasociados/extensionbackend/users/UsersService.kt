package com.rocayasociados.extensionbackend.users

import org.springframework.stereotype.Service

@Service
class UsersService(
    private val repository: UsersRepository
) {

    fun trackKeycloakUser(keycloakId: String): User =
        repository.findByKeycloakId(keycloakId) ?: repository.save(User(keycloakId = keycloakId))

}
