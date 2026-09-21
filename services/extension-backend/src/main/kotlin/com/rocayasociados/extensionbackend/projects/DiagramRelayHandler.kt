package com.rocayasociados.extensionbackend.projects

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder

/**
 * Relays a project diagram's live collaboration session between the webapp
 * and `diagrams-backend`'s WebSocket, without ever exposing the diagram's
 * `diagrams-backend` id to the browser.
 *
 * A connection is accepted only when it carries a ticket that
 * [WsTicketService] recognizes as currently valid — single-use and
 * short-lived, since a browser cannot present a bearer JWT on a WS handshake.
 * Once accepted, frames are relayed verbatim in both directions: there is
 * only one role today (project owner), so this is a connect-or-reject
 * decision, not per-message filtering (see design.md decision 4).
 */
@Component
class DiagramRelayHandler(
    private val wsTicketService: WsTicketService,
    @Value("\${app.diagrams-backend.ws-base-url}") private val wsBaseUrl: String,
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(DiagramRelayHandler::class.java)
    private val httpClient = HttpClient.newHttpClient()
    private val upstreams = ConcurrentHashMap<String, WebSocket>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val ticket = extractTicket(session)
        val redisId = ticket?.let { wsTicketService.consumeTicket(it) }
        if (redisId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired ticket"))
            return
        }

        try {
            val upstreamUri = UriComponentsBuilder.fromUriString(wsBaseUrl)
                .queryParam("diagramId", redisId)
                .build(true)
                .toUri()
            val upstream = httpClient.newWebSocketBuilder()
                .buildAsync(upstreamUri, UpstreamListener(session))
                .get(5, TimeUnit.SECONDS)
            upstreams[session.id] = upstream
        } catch (ex: Exception) {
            logger.error("Failed to connect to diagrams-backend WS for relay", ex)
            session.close(CloseStatus.SERVER_ERROR)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        upstreams[session.id]?.sendText(message.payload, true)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        upstreams.remove(session.id)?.sendClose(WebSocket.NORMAL_CLOSURE, "downstream closed")
    }

    private fun extractTicket(session: WebSocketSession): String? =
        UriComponentsBuilder.fromUri(session.uri ?: return null)
            .build()
            .queryParams["ticket"]
            ?.firstOrNull()

    /** Forwards frames coming back from `diagrams-backend` down to the browser. */
    private inner class UpstreamListener(private val downstream: WebSocketSession) : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val full = buffer.toString()
                buffer.setLength(0)
                sendDownstream(full)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closeDownstream(statusCode, reason)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            logger.error("Upstream diagrams-backend WS error in relay", error)
            closeDownstream(CloseStatus.SERVER_ERROR.code, "upstream error")
        }

        private fun sendDownstream(payload: String) {
            try {
                if (downstream.isOpen) downstream.sendMessage(TextMessage(payload))
            } catch (ex: Exception) {
                logger.error("Failed to relay message downstream", ex)
            }
        }

        private fun closeDownstream(statusCode: Int, reason: String) {
            try {
                if (downstream.isOpen) downstream.close(CloseStatus(statusCode, reason.take(100)))
            } catch (ex: Exception) {
                logger.error("Failed to close downstream session after upstream close", ex)
            }
        }
    }
}
