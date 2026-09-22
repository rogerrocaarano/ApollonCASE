package com.rocayasociados.extensionbackend.codegen

/**
 * Validates that a class-diagram-derived name is safe to emit as a Java
 * identifier: shaped like one (ASCII letters/digits/underscore, not starting
 * with a digit) and not a reserved word. Deliberately ASCII-only - a
 * deliberate simplification, not a Java-language limitation.
 */
object JavaIdentifiers {
    // JLS 3.9 keywords plus the contextual/reserved-by-convention words that
    // would still break generated code if used as an identifier.
    private val RESERVED_WORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits",
    )

    private val IDENTIFIER_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun isValid(name: String): Boolean = IDENTIFIER_REGEX.matches(name) && name !in RESERVED_WORDS
}
