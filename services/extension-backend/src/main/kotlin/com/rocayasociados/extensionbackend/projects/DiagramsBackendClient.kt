package com.rocayasociados.extensionbackend.projects

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

/**
 * Thin client for the diagram-body operations `diagrams-backend` already
 * exposes. `extension-backend` had no outbound dependency on `diagrams-backend`
 * before project/diagram deletion needed one — see design.md's "Deleting a
 * project or a diagram calls diagrams-backend server-side" decision in the
 * `add-projects` change.
 */
@Component
class DiagramsBackendClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${app.diagrams-backend.base-url}") baseUrl: String,
) {
    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

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
}
