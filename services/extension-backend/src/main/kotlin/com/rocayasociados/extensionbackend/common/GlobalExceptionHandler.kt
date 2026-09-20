package com.rocayasociados.extensionbackend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * `NoSuchElementException` (thrown by the various `*ServiceOrThrow` lookups)
 * has no built-in Spring mapping, unlike `AccessDeniedException`, which
 * Spring Security's filter chain already turns into 403 on its own.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}
