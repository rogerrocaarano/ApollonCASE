package com.rocayasociados.extensionbackend.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authorization.AuthenticatedAuthorizationManager.authenticated
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests {
                authorize("/v3/api-docs/**") { _, _ -> AuthorizationDecision(true) }
                authorize("/swagger-ui/**") { _, _ -> AuthorizationDecision(true) }
                authorize(anyRequest, authenticated())
            }
            oauth2ResourceServer {
                jwt { }
            }
        }
        return http.build()
    }
}
