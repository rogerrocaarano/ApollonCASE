package com.rocayasociados.extensionbackend.common

import org.springframework.security.oauth2.jwt.Jwt

val Jwt.requiredSubject: String
    get() = subject ?: error("JWT is missing the 'sub' claim")
