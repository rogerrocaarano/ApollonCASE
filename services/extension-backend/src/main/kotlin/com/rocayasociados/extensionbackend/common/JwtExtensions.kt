package com.rocayasociados.extensionbackend.common

import org.springframework.security.oauth2.jwt.Jwt

val Jwt.requiredSubject: String
    get() = subject ?: error("JWT is missing the 'sub' claim")

/** The token's `email` claim, if present. Keycloak's `email` OIDC scope supplies it once profile setup is enforced. */
val Jwt.email: String?
    get() = getClaimAsString("email")
