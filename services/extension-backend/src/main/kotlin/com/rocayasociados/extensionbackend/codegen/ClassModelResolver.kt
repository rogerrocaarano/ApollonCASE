package com.rocayasociados.extensionbackend.codegen

/**
 * Parses and type-resolves every class's attributes/methods from a
 * [DiagramGraph]. Collects every attribute/name/type problem it finds as a
 * [GenerationError] instead of failing on the first one, per
 * specs/code-generation/spec.md.
 *
 * Methods are handled differently, by design (see design.md and the
 * "Only Spring-Data-style query methods" requirement): a method is either
 * successfully recognized as a `findBy...`/`existsBy...`/`countBy...`/
 * `deleteBy...` query with supported parameter/return types - in which case
 * it becomes a [ResolvedRepositoryMethod] - or it is left out of the result
 * entirely. Neither a syntactically malformed method nor one with an
 * unsupported type is ever reported as an error: the spec's rejection
 * language only names "attribute or multiplicity", and the method
 * requirement explicitly says omitting a method "SHALL NOT ... be reported
 * as an error".
 */
object ClassModelResolver {
    private val REPOSITORY_METHOD_PREFIXES = listOf("findBy", "existsBy", "countBy", "deleteBy")

    fun resolve(graph: DiagramGraph, entityPackage: String): Pair<List<ResolvedClass>, List<GenerationError>> {
        val errors = mutableListOf<GenerationError>()
        val classNames = graph.classes.map { it.name }.toSet()

        graph.classes.forEach { cls ->
            if (!JavaIdentifiers.isValid(cls.name)) {
                errors += GenerationError(
                    cls.name, cls.id,
                    "El nombre de clase \"${cls.name}\" no es un identificador Java valido"
                )
            }
        }

        val resolvedClasses = graph.classes.map { cls ->
            if (cls.isEnumeration) {
                resolveEnumeration(cls, errors)
            } else {
                resolveClassOrInterface(cls, classNames, entityPackage, errors)
            }
        }

        return resolvedClasses to errors
    }

    private fun resolveEnumeration(cls: UmlClassNode, errors: MutableList<GenerationError>): ResolvedClass {
        val constants = cls.rawAttributes.map { it.trim() }
        constants.forEach { constant ->
            if (!JavaIdentifiers.isValid(constant)) {
                errors += GenerationError(
                    cls.name, cls.id,
                    "La constante de enumeracion \"$constant\" no es un identificador Java valido"
                )
            }
        }
        return ResolvedClass(
            id = cls.id,
            name = cls.name,
            isInterface = false,
            isEnumeration = true,
            isAbstract = false,
            enumConstants = constants,
            attributes = emptyList(),
            repositoryMethods = emptyList(),
        )
    }

    private fun resolveClassOrInterface(
        cls: UmlClassNode,
        classNames: Set<String>,
        entityPackage: String,
        errors: MutableList<GenerationError>,
    ): ResolvedClass {
        val attributes = cls.rawAttributes.mapNotNull { raw ->
            resolveAttribute(cls, raw, classNames, entityPackage, errors)
        }
        val repositoryMethods = cls.rawMethods.mapNotNull { raw ->
            runCatching { resolveRepositoryMethod(raw, classNames, entityPackage) }.getOrNull()
        }
        return ResolvedClass(
            id = cls.id,
            name = cls.name,
            isInterface = cls.isInterface,
            isEnumeration = false,
            isAbstract = cls.isAbstract,
            enumConstants = emptyList(),
            attributes = attributes,
            repositoryMethods = repositoryMethods,
        )
    }

    private fun resolveAttribute(
        cls: UmlClassNode,
        raw: String,
        classNames: Set<String>,
        entityPackage: String,
        errors: MutableList<GenerationError>,
    ): ResolvedAttribute? {
        val parsed = try {
            MemberSyntax.parseAttribute(raw)
        } catch (ex: MemberSyntaxException) {
            errors += GenerationError(cls.name, cls.id, "En la clase \"${cls.name}\": ${ex.message}")
            return null
        }
        if (!JavaIdentifiers.isValid(parsed.name)) {
            errors += GenerationError(
                cls.name, cls.id,
                "El atributo \"$raw\" de la clase \"${cls.name}\" tiene un nombre invalido: \"${parsed.name}\""
            )
            return null
        }
        val type = TypeVocabulary.resolve(parsed.type, classNames, entityPackage)
        if (type == null) {
            errors += GenerationError(
                cls.name, cls.id,
                "El atributo \"$raw\" de la clase \"${cls.name}\" tiene un tipo no soportado: \"${parsed.type}\""
            )
            return null
        }
        return ResolvedAttribute(parsed.name, type, parsed.defaultValue)
    }

    /** Throws (caught by the caller) on anything that keeps this method from being a usable repository query. */
    private fun resolveRepositoryMethod(
        raw: String,
        classNames: Set<String>,
        entityPackage: String,
    ): ResolvedRepositoryMethod {
        val parsed = MemberSyntax.parseMethod(raw)
        require(REPOSITORY_METHOD_PREFIXES.any { parsed.name.startsWith(it) }) { "not a query method" }
        require(JavaIdentifiers.isValid(parsed.name)) { "invalid method name" }
        val parameters = parsed.parameters.map { param ->
            require(JavaIdentifiers.isValid(param.name)) { "invalid parameter name" }
            val type = requireNotNull(TypeVocabulary.resolve(param.type, classNames, entityPackage)) {
                "unsupported parameter type"
            }
            ResolvedParameter(param.name, type)
        }
        val returnType = parsed.returnType?.let { rt ->
            requireNotNull(TypeVocabulary.resolve(rt, classNames, entityPackage)) { "unsupported return type" }
        }
        return ResolvedRepositoryMethod(parsed.name, parameters, returnType)
    }
}
