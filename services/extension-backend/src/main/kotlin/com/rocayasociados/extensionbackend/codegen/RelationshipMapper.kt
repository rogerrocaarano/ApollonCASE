package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName

/**
 * Applies every class-diagram relationship onto the already-resolved
 * classes: `extends`/`implements` for inheritance/realization, and JPA
 * associations (with cardinality, ownership, and composition/aggregation
 * cascade behavior) for the rest. See specs/code-generation/spec.md -
 * "Relationships map to JPA associations and Java inheritance" and
 * design.md's edge-endpoint-ownership decisions.
 *
 * Marker convention this relies on (confirmed by reading
 * `library/lib/utils/edgeUtils.ts`'s `getEdgeMarkerStyles`): the arrow/
 * triangle/rhombus is always drawn at the edge's `target` end. So for
 * `ClassInheritance`/`ClassRealization`, `target` is the superclass/
 * interface; for `ClassAggregation`/`ClassComposition`, `target` is the
 * "whole" and `source` is the "part".
 */
object RelationshipMapper {
    fun apply(classes: List<ResolvedClass>, relationships: List<UmlRelationshipEdge>, entityPackage: String): List<GenerationError> {
        val errors = mutableListOf<GenerationError>()
        val byId = classes.associateBy { it.id }

        for (edge in relationships) {
            val source = byId[edge.sourceId]
            val target = byId[edge.targetId]
            if (source == null || target == null) {
                errors += GenerationError(null, edge.id, "La relacion \"${edge.id}\" referencia una clase que no existe en el diagrama")
                continue
            }
            when (edge.type) {
                "ClassInheritance" -> applyInheritance(source, target, edge, errors, entityPackage)
                "ClassRealization" -> applyRealization(source, target, edge, errors, entityPackage)
                "ClassAggregation" -> applyAssociation(source, target, edge, errors, entityPackage, cascade = false)
                "ClassComposition" -> applyAssociation(source, target, edge, errors, entityPackage, cascade = true)
                "ClassBidirectional" -> applyAssociation(source, target, edge, errors, entityPackage, cascade = false, bidirectional = true)
                "ClassUnidirectional" -> applyAssociation(source, target, edge, errors, entityPackage, cascade = false, bidirectional = false)
                "ClassDependency" -> Unit // no structural effect, per spec
            }
        }
        return errors
    }

    private fun applyInheritance(
        source: ResolvedClass,
        target: ResolvedClass,
        edge: UmlRelationshipEdge,
        errors: MutableList<GenerationError>,
        entityPackage: String,
    ) {
        if (source.superclass != null) {
            errors += GenerationError(source.name, edge.id, "La clase \"${source.name}\" tiene mas de una superclase, lo que no compila en Java")
            return
        }
        source.superclass = ClassName.get(entityPackage, target.name)
    }

    private fun applyRealization(
        source: ResolvedClass,
        target: ResolvedClass,
        edge: UmlRelationshipEdge,
        errors: MutableList<GenerationError>,
        entityPackage: String,
    ) {
        if (!target.isInterface) {
            errors += GenerationError(
                source.name, edge.id,
                "\"${source.name}\" realiza \"${target.name}\", pero \"${target.name}\" no tiene el estereotipo «interface»"
            )
            return
        }
        source.interfaces += ClassName.get(entityPackage, target.name)
    }

    private fun applyAssociation(
        source: ResolvedClass,
        target: ResolvedClass,
        edge: UmlRelationshipEdge,
        errors: MutableList<GenerationError>,
        entityPackage: String,
        cascade: Boolean,
        bidirectional: Boolean = true,
    ) {
        val sourceCard = Multiplicity.classify(edge.sourceMultiplicity)
        val targetCard = Multiplicity.classify(edge.targetMultiplicity)
        if (sourceCard == null || targetCard == null) {
            errors += GenerationError(
                source.name, edge.id,
                "La relacion entre \"${source.name}\" y \"${target.name}\" tiene una multiplicidad no soportada " +
                    "(origen: \"${edge.sourceMultiplicity}\", destino: \"${edge.targetMultiplicity}\")"
            )
            return
        }

        // source's field points at target; its shape follows targetCard (how
        // many targets per one source). target's field (if generated) points
        // at source; its shape follows sourceCard, symmetrically.
        val sourceFieldShape = targetCard
        val targetFieldShape = sourceCard
        val sourceFieldName = defaultFieldName(target.name, sourceFieldShape)
        val targetFieldName = defaultFieldName(source.name, targetFieldShape)

        // Whichever side holds the single reference in a *ToOne/*ToMany pair
        // owns the FK (that's the "many" instances, each pointing to the one
        // it belongs to); a *OneToOne arbitrarily makes source own it, and a
        // *ManyToMany arbitrarily makes source own the join table, since JPA
        // requires exactly one owning side either way.
        val sourceIsOwning = when {
            sourceFieldShape == Cardinality.TO_ONE && targetFieldShape == Cardinality.TO_MANY -> true
            sourceFieldShape == Cardinality.TO_MANY && targetFieldShape == Cardinality.TO_ONE -> false
            else -> true
        }

        source.associations += ResolvedAssociation(
            fieldName = sourceFieldName,
            targetType = ClassName.get(entityPackage, target.name),
            cardinality = sourceFieldShape,
            relation = relationFor(own = sourceFieldShape, other = targetFieldShape),
            owning = sourceIsOwning || !bidirectional,
            mappedBy = if (bidirectional && !sourceIsOwning) targetFieldName else null,
            cascadeAll = false,
            orphanRemoval = false,
        )

        if (bidirectional) {
            // For composition/aggregation, target is always the "whole" (see
            // the class doc's marker-convention note) - only its reference(s)
            // to the "part" (source) cascade delete, never the reverse.
            target.associations += ResolvedAssociation(
                fieldName = targetFieldName,
                targetType = ClassName.get(entityPackage, source.name),
                cardinality = targetFieldShape,
                relation = relationFor(own = targetFieldShape, other = sourceFieldShape),
                owning = !sourceIsOwning,
                mappedBy = if (sourceIsOwning) sourceFieldName else null,
                cascadeAll = cascade,
                orphanRemoval = cascade && targetFieldShape == Cardinality.TO_MANY,
            )
        }
    }

    private fun relationFor(own: Cardinality, other: Cardinality): JpaRelationAnnotation = when {
        own == Cardinality.TO_ONE && other == Cardinality.TO_ONE -> JpaRelationAnnotation.ONE_TO_ONE
        own == Cardinality.TO_ONE && other == Cardinality.TO_MANY -> JpaRelationAnnotation.MANY_TO_ONE
        own == Cardinality.TO_MANY && other == Cardinality.TO_ONE -> JpaRelationAnnotation.ONE_TO_MANY
        else -> JpaRelationAnnotation.MANY_TO_MANY
    }

    private fun defaultFieldName(otherClassName: String, shape: Cardinality): String {
        val decapitalized = otherClassName.replaceFirstChar { it.lowercase() }
        return if (shape == Cardinality.TO_MANY) "${decapitalized}List" else decapitalized
    }
}
