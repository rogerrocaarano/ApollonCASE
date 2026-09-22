package com.rocayasociados.extensionbackend.codegen

/** Whether an association endpoint holds at most one related instance, or a collection. */
enum class Cardinality { TO_ONE, TO_MANY }

/**
 * Parses a class-diagram relationship endpoint's free-text multiplicity (e.g.
 * `1`, `0..1`, `*`, `0..*`, `1..*`) into a JPA-relevant cardinality. See
 * design.md - "Multiplicity parsing and cardinality mapping".
 */
object Multiplicity {
    private val VALUE = "0|1|\\*|\\d+"
    private val REGEX = Regex("^\\s*($VALUE)\\s*(\\.\\.\\s*($VALUE)\\s*)?$")

    /** Null when `raw` doesn't resolve to a supported cardinality - a validation error, not a default. */
    fun classify(raw: String?): Cardinality? {
        if (raw.isNullOrBlank()) return null
        val match = REGEX.matchEntire(raw) ?: return null
        val lower = match.groupValues[1]
        val upper = match.groups[3]?.value ?: lower
        return when {
            upper == "*" -> Cardinality.TO_MANY
            else -> {
                val upperValue = upper.toIntOrNull() ?: return null
                if (upperValue <= 1) Cardinality.TO_ONE else Cardinality.TO_MANY
            }
        }
    }
}
