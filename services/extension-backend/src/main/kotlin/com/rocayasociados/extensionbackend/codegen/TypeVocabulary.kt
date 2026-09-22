package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName

/**
 * The closed, case-sensitive vocabulary of attribute/parameter/return types
 * generation supports (see specs/code-generation/spec.md and design.md -
 * "Closed type vocabulary, exact case-sensitive match"). Anything not in this
 * list and not the name of another class/enum in the same diagram is
 * unsupported - resolution returns null rather than guessing.
 */
object TypeVocabulary {
    private val PRIMITIVE_AND_WRAPPER_TYPES: Map<String, TypeName> = mapOf(
        "String" to ClassName.get("java.lang", "String"),
        "int" to TypeName.INT,
        "Integer" to ClassName.get("java.lang", "Integer"),
        "long" to TypeName.LONG,
        "Long" to ClassName.get("java.lang", "Long"),
        "double" to TypeName.DOUBLE,
        "Double" to ClassName.get("java.lang", "Double"),
        "float" to TypeName.FLOAT,
        "Float" to ClassName.get("java.lang", "Float"),
        "boolean" to TypeName.BOOLEAN,
        "Boolean" to ClassName.get("java.lang", "Boolean"),
        "BigDecimal" to ClassName.get("java.math", "BigDecimal"),
        "LocalDate" to ClassName.get("java.time", "LocalDate"),
        "LocalDateTime" to ClassName.get("java.time", "LocalDateTime"),
        "UUID" to ClassName.get("java.util", "UUID"),
    )

    /**
     * Resolves a type token to its generated Java type. `diagramClassNames`
     * are the exact names of every class/enum in the same diagram (generated
     * into `entityPackage`); matching against them is exact and
     * case-sensitive, same as the primitive/wrapper vocabulary. Returns null
     * when the token isn't recognized - the caller turns that into a
     * validation error rather than a guessed type.
     */
    fun resolve(token: String, diagramClassNames: Set<String>, entityPackage: String): TypeName? {
        PRIMITIVE_AND_WRAPPER_TYPES[token]?.let { return it }
        if (token in diagramClassNames) return ClassName.get(entityPackage, token)
        return null
    }
}
