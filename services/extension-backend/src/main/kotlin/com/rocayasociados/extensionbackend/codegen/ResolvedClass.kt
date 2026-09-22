package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName

data class ResolvedAttribute(val name: String, val type: TypeName, val defaultValue: String?)

data class ResolvedParameter(val name: String, val type: TypeName)

data class ResolvedRepositoryMethod(
    val name: String,
    val parameters: List<ResolvedParameter>,
    val returnType: TypeName?,
)

/** Which JPA relationship annotation a [ResolvedAssociation] field needs. */
enum class JpaRelationAnnotation { ONE_TO_ONE, MANY_TO_ONE, ONE_TO_MANY, MANY_TO_MANY }

/**
 * A field generated on an entity because of a class-diagram relationship.
 * `owning` entities carry the `@JoinColumn`/`@JoinTable`; the other side (if
 * any - a Unidirectional association has none) carries `mappedBy`.
 */
data class ResolvedAssociation(
    val fieldName: String,
    val targetType: ClassName,
    val cardinality: Cardinality,
    val relation: JpaRelationAnnotation,
    val owning: Boolean,
    val mappedBy: String? = null,
    val cascadeAll: Boolean = false,
    val orphanRemoval: Boolean = false,
)

/** A class/interface/enumeration ready for emission: every member parsed, validated, and type-resolved. */
class ResolvedClass(
    val id: String,
    val name: String,
    val isInterface: Boolean,
    val isEnumeration: Boolean,
    val isAbstract: Boolean,
    val enumConstants: List<String>,
    val attributes: List<ResolvedAttribute>,
    val repositoryMethods: List<ResolvedRepositoryMethod>,
) {
    var superclass: ClassName? = null
    val interfaces: MutableList<ClassName> = mutableListOf()
    val associations: MutableList<ResolvedAssociation> = mutableListOf()
}
