package com.rocayasociados.extensionbackend.projects

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import kotlin.test.assertEquals
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

    @Test
    fun `createDiagram envia el modelo y devuelve el cuerpo creado`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"id": "redis-new", "title": "New", "type": "ClassDiagram", "version": "4.0.0"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val result = client.createDiagram(mapOf("title" to "New"))

        assertEquals("redis-new", result["id"])
        server.verify()
    }

    @Test
    fun `getDiagram devuelve el cuerpo actual`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id": "redis-1", "title": "Mine"}""", MediaType.APPLICATION_JSON))

        val result = client.getDiagram("redis-1")

        assertEquals("Mine", result["title"])
        server.verify()
    }

    @Test
    fun `getDiagram lanza NoSuchElementException cuando diagrams-backend responde 404`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/missing"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withResourceNotFound())

        assertFailsWith<NoSuchElementException> { client.getDiagram("missing") }
    }

    @Test
    fun `putDiagram sin If-Match no envia la cabecera`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("""{"headRev": 1, "updatedAt": "now"}""", MediaType.APPLICATION_JSON))

        val result = client.putDiagram("redis-1", mapOf("title" to "Updated"))

        assertEquals(1, (result["headRev"] as Number).toInt())
        server.verify()
    }

    @Test
    fun `putDiagram con If-Match reenvia la cabecera`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", "3"))
            .andRespond(withSuccess("""{"headRev": 4, "updatedAt": "now"}""", MediaType.APPLICATION_JSON))

        client.putDiagram("redis-1", mapOf("title" to "Updated"), ifMatch = "3")

        server.verify()
    }

    @Test
    fun `putDiagram propaga un conflicto de revision`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", "1"))
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators
                    .withStatus(org.springframework.http.HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"currentHeadRev": 5}""")
            )

        assertFailsWith<HttpClientErrorException.Conflict> {
            client.putDiagram("redis-1", mapOf("title" to "Stale"), ifMatch = "1")
        }
    }

    @Test
    fun `listVersions devuelve la respuesta de diagrams-backend`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions?limit=10"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"versions": [], "total": 0}""", MediaType.APPLICATION_JSON))

        val result = client.listVersions("redis-1", limit = 10, before = null)

        assertEquals(0, (result["total"] as Number).toInt())
        server.verify()
    }

    @Test
    fun `createVersion envia el cuerpo y devuelve el resumen`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"id": "v1", "name": ""}""", MediaType.APPLICATION_JSON))

        val result = client.createVersion("redis-1", mapOf("body" to mapOf("id" to "redis-1")))

        assertEquals("v1", result["id"])
        server.verify()
    }

    @Test
    fun `getVersion devuelve el cuerpo de la version`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions/v1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"id": "redis-1", "title": "Snapshot"}""", MediaType.APPLICATION_JSON))

        val result = client.getVersion("redis-1", "v1")

        assertEquals("Snapshot", result["title"])
        server.verify()
    }

    @Test
    fun `getVersion lanza NoSuchElementException cuando diagrams-backend responde 404`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions/missing"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withResourceNotFound())

        assertFailsWith<NoSuchElementException> { client.getVersion("redis-1", "missing") }
    }

    @Test
    fun `restoreVersion envia el cuerpo y devuelve el resultado`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions/v1/restore"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"headRev": 2, "updatedAt": "now"}""", MediaType.APPLICATION_JSON))

        val result = client.restoreVersion("redis-1", "v1", emptyMap())

        assertEquals(2, (result["headRev"] as Number).toInt())
        server.verify()
    }

    @Test
    fun `renameVersion envia PATCH y devuelve el resumen actualizado`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions/v1"))
            .andExpect(method(HttpMethod.PATCH))
            .andRespond(withSuccess("""{"id": "v1", "name": "Renamed"}""", MediaType.APPLICATION_JSON))

        val result = client.renameVersion("redis-1", "v1", mapOf("name" to "Renamed"))

        assertEquals("Renamed", result["name"])
        server.verify()
    }

    @Test
    fun `deleteVersion envia DELETE`() {
        val (client, server) = clientWithServer()
        server.expect(requestTo("http://diagrams-backend/api/diagrams/redis-1/versions/v1"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withNoContent())

        client.deleteVersion("redis-1", "v1")

        server.verify()
    }
}
