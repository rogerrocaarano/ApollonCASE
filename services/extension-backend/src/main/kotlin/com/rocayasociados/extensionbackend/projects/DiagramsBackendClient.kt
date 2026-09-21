package com.rocayasociados.extensionbackend.projects

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * Thin client for the diagram-content operations `diagrams-backend` exposes:
 * the diagram body, its version history, and (originally) deletion. A diagram
 * body is a library-owned, evolving shape (Apollon's `nodes`/`edges`/
 * `assessments`) that `diagrams-backend` itself only validates loosely (see
 * its `_schemas.ts` — those fields are `z.unknown()`), so this client passes
 * it through as a generic JSON map rather than modelling it, exactly like
 * `diagrams-backend` avoids modelling it server-side.
 */
@Component
class DiagramsBackendClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${app.diagrams-backend.base-url}") baseUrl: String,
) {
    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    private val jsonMapType = object : ParameterizedTypeReference<Map<String, Any?>>() {}

    /**
     * Deletes a diagram body by its `diagrams-backend` id. A 404 (already
     * gone) is treated as success: the end state the caller wants — "this
     * diagram is not reachable" — is already true.
     */
    fun deleteDiagram(redisId: String) {
        try {
            restClient.delete()
                .uri("/api/diagrams/{id}", redisId)
                .retrieve()
                .toBodilessEntity()
        } catch (ex: HttpClientErrorException.NotFound) {
            // Already gone — nothing to do.
        }
    }

    /** Creates a diagram body in `diagrams-backend` and returns the stored body (with its new id). */
    fun createDiagram(model: Map<String, Any?>): Map<String, Any?> =
        restClient.post()
            .uri("/api/diagrams")
            .body(model)
            .retrieve()
            .body(jsonMapType) ?: emptyMap()

    /** Fetches a diagram's current body. Throws [NoSuchElementException] if it no longer exists. */
    fun getDiagram(redisId: String): Map<String, Any?> {
        try {
            return restClient.get()
                .uri("/api/diagrams/{id}", redisId)
                .retrieve()
                .body(jsonMapType) ?: emptyMap()
        } catch (ex: HttpClientErrorException.NotFound) {
            throw NoSuchElementException("Diagram $redisId not found in diagrams-backend")
        }
    }

    /** Saves a diagram's body. `ifMatch`, when present, is forwarded for optimistic concurrency. */
    fun putDiagram(redisId: String, model: Map<String, Any?>, ifMatch: String? = null): Map<String, Any?> =
        restClient.put()
            .uri("/api/diagrams/{id}", redisId)
            .apply { if (ifMatch != null) header("If-Match", ifMatch) }
            .body(model)
            .retrieve()
            .body(jsonMapType) ?: emptyMap()

    fun listVersions(redisId: String, limit: Int?, before: String?): Map<String, Any?> =
        restClient.get()
            .uri { builder ->
                builder.path("/api/diagrams/{id}/versions")
                limit?.let { builder.queryParam("limit", it) }
                before?.let { builder.queryParam("before", it) }
                builder.build(redisId)
            }
            .retrieve()
            .body(jsonMapType) ?: emptyMap()

    fun createVersion(redisId: String, request: Map<String, Any?>): Map<String, Any?> =
        restClient.post()
            .uri("/api/diagrams/{id}/versions", redisId)
            .body(request)
            .retrieve()
            .body(jsonMapType) ?: emptyMap()

    fun getVersion(redisId: String, versionId: String): Map<String, Any?> {
        try {
            return restClient.get()
                .uri("/api/diagrams/{id}/versions/{versionId}", redisId, versionId)
                .retrieve()
                .body(jsonMapType) ?: emptyMap()
        } catch (ex: HttpClientErrorException.NotFound) {
            throw NoSuchElementException("Version $versionId not found for diagram $redisId")
        }
    }

    fun restoreVersion(redisId: String, versionId: String, request: Map<String, Any?>): Map<String, Any?> =
        restClient.post()
            .uri("/api/diagrams/{id}/versions/{versionId}/restore", redisId, versionId)
            .body(request)
            .retrieve()
            .body(jsonMapType) ?: emptyMap()

    fun renameVersion(redisId: String, versionId: String, request: Map<String, Any?>): Map<String, Any?> =
        restClient.patch()
            .uri("/api/diagrams/{id}/versions/{versionId}", redisId, versionId)
            .body(request)
            .retrieve()
            .body(jsonMapType) ?: emptyMap()

    fun deleteVersion(redisId: String, versionId: String) {
        restClient.delete()
            .uri("/api/diagrams/{id}/versions/{versionId}", redisId, versionId)
            .retrieve()
            .toBodilessEntity()
    }
}
