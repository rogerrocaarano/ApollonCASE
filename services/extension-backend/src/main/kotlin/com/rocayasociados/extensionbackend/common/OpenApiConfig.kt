package com.rocayasociados.extensionbackend.common

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI {
        val bearerScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")

        return OpenAPI()
            .info(
                Info()
                    .title("BackendExtension API")
                    .version("v1")
                    .description(
                        "API de ApollonCASE. Todos los endpoints (salvo la documentación) " +
                            "requieren un JWT emitido por Keycloak (realm ApollonCASE) en el " +
                            "header Authorization: Bearer <token>."
                    )
            )
            .components(Components().addSecuritySchemes("bearerAuth", bearerScheme))
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }
}
