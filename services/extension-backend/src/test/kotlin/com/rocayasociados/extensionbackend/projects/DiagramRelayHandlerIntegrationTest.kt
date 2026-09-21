package com.rocayasociados.extensionbackend.projects

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.CloseStatus
import kotlin.test.assertEquals

/**
 * End-to-end: a real WS connection through [DiagramRelayHandler] against the
 * actual `diagrams-backend` dev instance (same real-datasource/real-dependency
 * convention as `ProjectsControllerTest`).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class DiagramRelayHandlerIntegrationTest {

    @Value("\${local.server.port}")
    var port: Int = 0

    @Autowired
    lateinit var wsTicketService: WsTicketService

    @Autowired
    lateinit var diagramsBackendClient: DiagramsBackendClient

    @Value("\${app.diagrams-backend.ws-base-url}")
    lateinit var diagramsBackendWsUrl: String

    private val httpClient = HttpClient.newHttpClient()

    private class RecordingListener : WebSocket.Listener {
        val messages = LinkedBlockingQueue<String>()
        val closeCode = CompletableFuture<Int>()
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                messages.add(buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closeCode.complete(statusCode)
            return null
        }
    }

    private fun connect(uri: String): Pair<WebSocket, RecordingListener> {
        val listener = RecordingListener()
        val socket = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(uri), listener)
            .get(5, TimeUnit.SECONDS)
        return socket to listener
    }

    private fun createRealDiagram(): String {
        val created = diagramsBackendClient.createDiagram(
            mapOf("title" to "Relay test", "type" to "ClassDiagram", "version" to "4.0.0")
        )
        return created["id"] as String
    }

    @Test
    fun `una conexion con ticket valido relaya mensajes en ambas direcciones`() {
        val redisId = createRealDiagram()
        val ticket = wsTicketService.issueTicket(redisId)

        val (relaySocket, relayListener) = connect("ws://localhost:$port/ws/diagrams?ticket=$ticket")
        val (directSocket, directListener) = connect("$diagramsBackendWsUrl?diagramId=$redisId")

        // Let both sockets finish joining diagrams-backend's room before exchanging messages.
        Thread.sleep(300)

        relaySocket.sendText("hello-from-relay", true).get(5, TimeUnit.SECONDS)
        assertEquals("hello-from-relay", directListener.messages.poll(5, TimeUnit.SECONDS))

        directSocket.sendText("hello-from-direct", true).get(5, TimeUnit.SECONDS)
        assertEquals("hello-from-direct", relayListener.messages.poll(5, TimeUnit.SECONDS))

        relaySocket.sendClose(WebSocket.NORMAL_CLOSURE, "test done")
        directSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test done")
    }

    @Test
    fun `una conexion sin ticket es rechazada sin abrir conexion upstream`() {
        val (_, listener) = connect("ws://localhost:$port/ws/diagrams")

        assertEquals(CloseStatus.POLICY_VIOLATION.code, listener.closeCode.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `una conexion con un ticket desconocido es rechazada`() {
        val (_, listener) = connect("ws://localhost:$port/ws/diagrams?ticket=does-not-exist")

        assertEquals(CloseStatus.POLICY_VIOLATION.code, listener.closeCode.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `una conexion con un ticket ya usado es rechazada`() {
        val redisId = createRealDiagram()
        val ticket = wsTicketService.issueTicket(redisId)
        assertEquals(redisId, wsTicketService.consumeTicket(ticket))

        val (_, listener) = connect("ws://localhost:$port/ws/diagrams?ticket=$ticket")

        assertEquals(CloseStatus.POLICY_VIOLATION.code, listener.closeCode.get(5, TimeUnit.SECONDS))
    }
}
