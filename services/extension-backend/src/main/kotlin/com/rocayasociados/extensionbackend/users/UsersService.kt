package com.rocayasociados.extensionbackend.users

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UsersService(
    private val repository: UsersRepository
) {

    fun trackKeycloakUser(keycloakId: String): User =
        repository.findByKeycloakId(keycloakId) ?: repository.save(User(keycloakId = keycloakId))

    fun updateProfile(keycloakId: String, displayName: String?, email: String?): User {
        val user = repository.findByKeycloakId(keycloakId)
            ?: throw NoSuchElementException("User with keycloakId $keycloakId not found")
        displayName?.let { user.displayName = it }
        email?.let { user.email = it }
        return repository.save(user)
    }
}
