package com.rocayasociados.extensionbackend.projects

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketRelayConfig(
    private val diagramRelayHandler: DiagramRelayHandler,
    @Value("\${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private val allowedOrigins: String,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(diagramRelayHandler, "/ws/diagrams")
            .setAllowedOrigins(*allowedOrigins.split(",").map { it.trim() }.toTypedArray())
    }
}
