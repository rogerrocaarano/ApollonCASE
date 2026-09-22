package com.rocayasociados.extensionbackend.codegen

/** Thrown when a class diagram attribute/method/parameter doesn't match the supported free-text syntax. */
class MemberSyntaxException(message: String) : Exception(message)

/** UML visibility marker, parsed from the leading `+`/`-`/`#`/`~` of an attribute or method. */
enum class Visibility(val symbol: Char) {
    PUBLIC('+'),
    PRIVATE('-'),
    PROTECTED('#'),
    PACKAGE_PRIVATE('~'),
    ;

    companion object {
        fun fromSymbol(symbol: Char?): Visibility? = entries.find { it.symbol == symbol }
    }
}

data class ParsedAttribute(
    val visibility: Visibility?,
    val name: String,
    val type: String,
    val defaultValue: String?,
)

data class ParsedParameter(val name: String, val type: String)

data class ParsedMethod(
    val visibility: Visibility?,
    val name: String,
    val parameters: List<ParsedParameter>,
    val returnType: String?,
)

/**
 * Parses the free-text `attributes[].name`/`methods[].name` syntax a class
 * diagram element carries (see specs/code-generation/spec.md - "Attributes
 * and methods must match the supported syntax"). Deliberately narrow: no
 * generics, arrays, or qualified type names - the closed type vocabulary
 * (see TypeVocabulary) only ever needs bare identifiers.
 */
object MemberSyntax {
    private const val IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*"
    private const val VISIBILITY = "[+\\-#~]"

    private val ATTRIBUTE_REGEX = Regex(
        "^\\s*(?<visibility>$VISIBILITY)?\\s*(?<name>$IDENTIFIER)\\s*:\\s*(?<type>$IDENTIFIER)\\s*(=\\s*(?<default>.+))?\\s*$"
    )

    private val METHOD_REGEX = Regex(
        "^\\s*(?<visibility>$VISIBILITY)?\\s*(?<name>$IDENTIFIER)\\s*\\(\\s*(?<params>[^)]*)\\)\\s*(:\\s*(?<returnType>$IDENTIFIER))?\\s*$"
    )

    private val PARAM_REGEX = Regex("^\\s*(?<name>$IDENTIFIER)\\s*:\\s*(?<type>$IDENTIFIER)\\s*$")

    fun parseAttribute(raw: String): ParsedAttribute {
        val match = ATTRIBUTE_REGEX.matchEntire(raw)
            ?: throw MemberSyntaxException(
                "\"$raw\" no cumple la sintaxis soportada para un atributo: `[visibilidad] nombre : tipo [= valor]`"
            )
        return ParsedAttribute(
            visibility = Visibility.fromSymbol(match.groups["visibility"]?.value?.firstOrNull()),
            name = match.groups["name"]!!.value,
            type = match.groups["type"]!!.value,
            defaultValue = match.groups["default"]?.value?.trim(),
        )
    }

    fun parseMethod(raw: String): ParsedMethod {
        val match = METHOD_REGEX.matchEntire(raw)
            ?: throw MemberSyntaxException(
                "\"$raw\" no cumple la sintaxis soportada para un metodo: `[visibilidad] nombre(parametro : tipo, ...) : tipoRetorno`"
            )
        val paramsRaw = match.groups["params"]!!.value.trim()
        val parameters = if (paramsRaw.isEmpty()) {
            emptyList()
        } else {
            paramsRaw.split(",").map { paramText ->
                val trimmed = paramText.trim()
                val paramMatch = PARAM_REGEX.matchEntire(trimmed)
                    ?: throw MemberSyntaxException(
                        "El parametro \"$trimmed\" del metodo \"$raw\" no cumple la sintaxis `nombre : tipo`"
                    )
                ParsedParameter(paramMatch.groups["name"]!!.value, paramMatch.groups["type"]!!.value)
            }
        }
        return ParsedMethod(
            visibility = Visibility.fromSymbol(match.groups["visibility"]?.value?.firstOrNull()),
            name = match.groups["name"]!!.value,
            parameters = parameters,
            returnType = match.groups["returnType"]?.value,
        )
    }
}
