package com.rocayasociados.extensionbackend.common

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException

/**
 * `NoSuchElementException` (thrown by the various `*ServiceOrThrow` lookups)
 * has no built-in Spring mapping, unlike `AccessDeniedException`, which
 * Spring Security's filter chain already turns into 403 on its own.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()

    /**
     * Forwards a `diagrams-backend` client error (e.g. 409 REVISION_MISMATCH
     * on a diagram body PUT) verbatim — same status, same JSON body — instead
     * of collapsing it to a generic 500. The diagram-content endpoints are a
     * transparent proxy, and the webapp's autosave rebase logic
     * (`createDiagramAutosaver`) depends on seeing the original status/body
     * to recover from a conflict instead of just failing.
     */
    @ExceptionHandler(HttpClientErrorException::class)
    fun handleUpstreamClientError(ex: HttpClientErrorException): ResponseEntity<ByteArray> {
        val headers = HttpHeaders()
        ex.responseHeaders?.contentType?.let { headers.contentType = it }
        return ResponseEntity.status(ex.statusCode).headers(headers).body(ex.responseBodyAsByteArray)
    }
}
