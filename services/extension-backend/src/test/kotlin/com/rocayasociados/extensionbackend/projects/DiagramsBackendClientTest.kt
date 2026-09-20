package com.rocayasociados.extensionbackend.projects

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import kotlin.test.assertFailsWith

class DiagramsBackendClientTest {

    private fun clientWithServer(): Pair<DiagramsBackendClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = DiagramsBackendClient(builder, "http://diagrams-backend")
        return client to server
    }

    @Test
    fun `deleteDiagram borra el diagrama cuando diagrams-backend responde 204`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent())

        client.deleteDiagram("redis-1")

        server.verify()
    }

    @Test
    fun `deleteDiagram trata un 404 como exito`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/already-gone"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withResourceNotFound())

        client.deleteDiagram("already-gone")

        server.verify()
    }

    @Test
    fun `deleteDiagram propaga un error 5xx`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withServerError())

        assertFailsWith<RestClientException> { client.deleteDiagram("redis-1") }
    }
}
