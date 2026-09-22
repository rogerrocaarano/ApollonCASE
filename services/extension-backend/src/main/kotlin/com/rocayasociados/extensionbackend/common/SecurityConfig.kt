package com.rocayasociados.extensionbackend.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authorization.AuthenticatedAuthorizationManager.authenticated
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private val allowedOrigins: String,
    private val currentUserFilter: CurrentUserFilter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { }
            authorizeHttpRequests {
                authorize("/v3/api-docs/**") { _, _ -> AuthorizationDecision(true) }
                authorize("/swagger-ui/**") { _, _ -> AuthorizationDecision(true) }
                // Container/Traefik healthcheck; only a bare UP/DOWN status is
                // exposed (management.endpoint.health.show-details=never).
                authorize("/actuator/health") { _, _ -> AuthorizationDecision(true) }
                // A browser cannot set an Authorization header on a WS handshake; this
                // endpoint authenticates via a short-lived, single-use ticket instead
                // (see WsTicketService / DiagramRelayHandler), not the JWT filter.
                authorize("/ws/diagrams") { _, _ -> AuthorizationDecision(true) }
                authorize(anyRequest, authenticated())
            }
            oauth2ResourceServer {
                jwt { }
            }
            addFilterAfter(currentUserFilter, BearerTokenAuthenticationFilter::class.java)
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.split(",").map { it.trim() }
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "If-Match")
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
