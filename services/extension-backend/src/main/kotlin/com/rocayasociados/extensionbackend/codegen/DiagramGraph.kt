package com.rocayasociados.extensionbackend.codegen

/** A class-diagram class/interface/enumeration node, before its members are parsed. */
data class UmlClassNode(
    val id: String,
    val name: String,
    val isInterface: Boolean,
    val isEnumeration: Boolean,
    val isAbstract: Boolean,
    val rawAttributes: List<String>,
    val rawMethods: List<String>,
)

/** A `Class*`-typed edge between two class nodes. */
data class UmlRelationshipEdge(
    val id: String,
    val type: String,
    val sourceId: String,
    val targetId: String,
    val sourceMultiplicity: String?,
    val targetMultiplicity: String?,
)

data class DiagramGraph(
    val diagramType: String?,
    val classes: List<UmlClassNode>,
    val relationships: List<UmlRelationshipEdge>,
)

/**
 * Reads the class-diagram-relevant subset of a diagram body (as returned by
 * `ProjectsService.getDiagramBody` - the raw `library/schema/uml-model-4`
 * wire shape) into a typed graph. Non-class nodes/edges (any other diagram
 * type's shapes) are ignored rather than rejected here; callers reject
 * non-Class-Diagram bodies via `diagramType` before relying on the rest of
 * this graph.
 */
object DiagramGraphExtractor {
    private val CLASS_RELATIONSHIP_TYPES = setOf(
        "ClassInheritance",
        "ClassRealization",
        "ClassAggregation",
        "ClassComposition",
        "ClassBidirectional",
        "ClassUnidirectional",
        "ClassDependency",
    )

    @Suppress("UNCHECKED_CAST")
    fun extract(body: Map<String, Any?>): DiagramGraph {
        val diagramType = body["type"] as? String
        val nodes = (body["nodes"] as? List<Map<String, Any?>>).orEmpty()
        val edges = (body["edges"] as? List<Map<String, Any?>>).orEmpty()

        val classes = nodes
            .filter { it["type"] == "class" }
            .map { node ->
                val data = node["data"] as? Map<String, Any?> ?: emptyMap()
                val stereotype = data["stereotype"] as? String
                UmlClassNode(
                    id = node["id"] as String,
                    name = data["name"] as? String ?: "",
                    isInterface = stereotype == "interface",
                    isEnumeration = stereotype == "enumeration",
                    isAbstract = data["isAbstract"] as? Boolean ?: false,
                    rawAttributes = ((data["attributes"] as? List<Map<String, Any?>>).orEmpty())
                        .map { it["name"] as? String ?: "" },
                    rawMethods = ((data["methods"] as? List<Map<String, Any?>>).orEmpty())
                        .map { it["name"] as? String ?: "" },
                )
            }

        val relationships = edges
            .filter { (it["type"] as? String) in CLASS_RELATIONSHIP_TYPES }
            .map { edge ->
                val data = edge["data"] as? Map<String, Any?>
                UmlRelationshipEdge(
                    id = edge["id"] as String,
                    type = edge["type"] as String,
                    sourceId = edge["source"] as String,
                    targetId = edge["target"] as String,
                    sourceMultiplicity = data?.get("sourceMultiplicity") as? String,
                    targetMultiplicity = data?.get("targetMultiplicity") as? String,
                )
            }

        return DiagramGraph(diagramType, classes, relationships)
    }
}
