package com.rocayasociados.extensionbackend.users

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UsersService(
    private val repository: UsersRepository
) {

    /**
     * Resolves (creating if needed) the `User` for a Keycloak subject, syncing
     * `email` from the token's `email` claim on every call. Only writes when
     * the claim is present and differs from what's stored — a token without
     * an `email` claim (e.g. a test JWT) never blanks out a known email.
     */
    fun trackKeycloakUser(keycloakId: String, tokenEmail: String? = null): User {
        val existing = repository.findByKeycloakId(keycloakId)
        val user = existing ?: User(keycloakId = keycloakId)
        val emailChanged = tokenEmail != null && tokenEmail != user.email
        if (emailChanged) user.email = tokenEmail
        return if (existing == null || emailChanged) repository.save(user) else user
    }
}
