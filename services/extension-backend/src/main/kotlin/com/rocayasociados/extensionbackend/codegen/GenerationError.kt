package com.rocayasociados.extensionbackend.codegen

/** One reason generation was rejected, naming the diagram element it came from. */
data class GenerationError(
    val className: String?,
    val elementId: String?,
    val message: String,
)

/**
 * Thrown when one or more diagram elements fail validation. Carries every
 * failure found, not just the first - the spec requires generation to
 * "identify the offending class and attribute"/"relationship", and a user
 * fixing diagrams one round-trip at a time benefits from seeing every problem
 * at once.
 */
class CodeGenerationValidationException(val errors: List<GenerationError>) :
    Exception("Code generation validation failed: ${errors.joinToString("; ") { it.message }}")
