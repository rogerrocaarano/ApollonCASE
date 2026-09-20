package com.rocayasociados.extensionbackend.common

import com.rocayasociados.extensionbackend.users.UsersService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Runs after the OAuth2 resource server filter has authenticated the JWT.
 * Resolves (creating if needed) the User for the token's subject once per
 * request, so every authenticated endpoint - present and future - gets the
 * current user without repeating the trackKeycloakUser call itself.
 */
@Component
class CurrentUserFilter(
    private val usersService: UsersService,
    private val currentUserHolder: CurrentUserHolder,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken) {
            currentUserHolder.user = usersService.trackKeycloakUser(authentication.token.requiredSubject)
        }
        filterChain.doFilter(request, response)
    }
}
