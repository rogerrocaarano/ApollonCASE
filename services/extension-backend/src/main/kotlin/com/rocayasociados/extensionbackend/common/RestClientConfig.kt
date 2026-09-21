package com.rocayasociados.extensionbackend.common

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * `spring-boot-starter-webmvc` doesn't auto-configure a `RestClient.Builder`
 * bean on its own (that lives in a separate Boot 4 autoconfigure module this
 * project doesn't depend on) — provide one explicitly so clients like
 * `DiagramsBackendClient` can inject it and stay testable via
 * `MockRestServiceServer.bindTo(builder)`.
 */
@Configuration
class RestClientConfig {
    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()

    /** Injectable so time-based components (e.g. `WsTicketService`) can be tested with a fixed clock. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
