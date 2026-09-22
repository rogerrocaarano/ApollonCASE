package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

private val JAKARTA_PERSISTENCE = "jakarta.persistence"
private val ENTITY = ClassName.get(JAKARTA_PERSISTENCE, "Entity")
private val ID = ClassName.get(JAKARTA_PERSISTENCE, "Id")
private val GENERATED_VALUE = ClassName.get(JAKARTA_PERSISTENCE, "GeneratedValue")
private val GENERATION_TYPE = ClassName.get(JAKARTA_PERSISTENCE, "GenerationType")
private val CASCADE_TYPE = ClassName.get(JAKARTA_PERSISTENCE, "CascadeType")
private val LIST = ClassName.get("java.util", "List")

/**
 * Emits one `.java` file per [ResolvedClass]: a JPA `@Entity` for a plain
 * class, a plain Java `enum` for an "enumeration"-stereotyped class, or a
 * plain Java `interface` for an "interface"-stereotyped class (a realization
 * target only - it carries no members in this iteration). See
 * specs/code-generation/spec.md.
 */
object EntityEmitter {
    fun emit(cls: ResolvedClass, entityPackage: String): JavaFile {
        val typeSpec = when {
            cls.isEnumeration -> emitEnum(cls)
            cls.isInterface -> emitInterface(cls)
            else -> emitEntity(cls)
        }
        return JavaFile.builder(entityPackage, typeSpec).build()
    }

    private fun emitEnum(cls: ResolvedClass): TypeSpec {
        val builder = TypeSpec.enumBuilder(cls.name).addModifiers(Modifier.PUBLIC)
        cls.enumConstants.forEach { builder.addEnumConstant(it) }
        return builder.build()
    }

    private fun emitInterface(cls: ResolvedClass): TypeSpec =
        TypeSpec.interfaceBuilder(cls.name).addModifiers(Modifier.PUBLIC).build()

    private fun emitEntity(cls: ResolvedClass): TypeSpec {
        val builder = TypeSpec.classBuilder(cls.name)
            .addAnnotation(ENTITY)
            .addModifiers(Modifier.PUBLIC)

        if (cls.isAbstract) builder.addModifiers(Modifier.ABSTRACT)
        cls.superclass?.let { builder.superclass(it) }
        cls.interfaces.forEach { builder.addSuperinterface(it) }

        // Only a root entity (no superclass) needs its own `@Id` - a subclass
        // inherits it, and JPA rejects a second `@Id` in the same hierarchy.
        if (cls.superclass == null) {
            addIdField(builder)
        }

        cls.attributes.forEach { attribute ->
            addProperty(builder, attribute.type, attribute.name)
        }

        cls.associations.forEach { association ->
            addAssociation(builder, association)
        }

        return builder.build()
    }

    private fun addIdField(builder: TypeSpec.Builder) {
        val idType = ClassName.get("java.lang", "Long")
        val idField = FieldSpec.builder(idType, "id", Modifier.PRIVATE)
            .addAnnotation(ID)
            .addAnnotation(
                AnnotationSpec.builder(GENERATED_VALUE)
                    .addMember("strategy", "\$T.\$L", GENERATION_TYPE, "IDENTITY")
                    .build()
            )
            .build()
        builder.addField(idField)
        addAccessors(builder, idType, "id")
    }

    private fun addAssociation(builder: TypeSpec.Builder, association: ResolvedAssociation) {
        val fieldType: TypeName = if (association.cardinality == Cardinality.TO_MANY) {
            ParameterizedTypeName.get(LIST, association.targetType)
        } else {
            association.targetType
        }

        val relationAnnotationName = when (association.relation) {
            JpaRelationAnnotation.ONE_TO_ONE -> "OneToOne"
            JpaRelationAnnotation.MANY_TO_ONE -> "ManyToOne"
            JpaRelationAnnotation.ONE_TO_MANY -> "OneToMany"
            JpaRelationAnnotation.MANY_TO_MANY -> "ManyToMany"
        }
        val relationAnnotation = AnnotationSpec.builder(ClassName.get(JAKARTA_PERSISTENCE, relationAnnotationName)).apply {
            if (association.mappedBy != null) {
                addMember("mappedBy", "\$S", association.mappedBy)
            }
            if (association.cascadeAll) {
                addMember("cascade", "\$T.ALL", CASCADE_TYPE)
            }
            if (association.orphanRemoval) {
                addMember("orphanRemoval", "true")
            }
        }.build()

        val fieldBuilder = FieldSpec.builder(fieldType, association.fieldName, Modifier.PRIVATE)
            .addAnnotation(relationAnnotation)

        // The owning side of a *ToOne needs an explicit @JoinColumn (Hibernate
        // infers a *ToMany join table on its own); the owning side of a
        // *ToMany with a join table is only reached when there is no
        // `mappedBy` at all - i.e. a Unidirectional association's *ToMany
        // field, which keeps Hibernate's default join-table behavior.
        if (association.owning && association.mappedBy == null &&
            (association.relation == JpaRelationAnnotation.MANY_TO_ONE || association.relation == JpaRelationAnnotation.ONE_TO_ONE)
        ) {
            fieldBuilder.addAnnotation(
                AnnotationSpec.builder(ClassName.get(JAKARTA_PERSISTENCE, "JoinColumn"))
                    .addMember("name", "\$S", "${association.fieldName}_id")
                    .build()
            )
        }

        builder.addField(fieldBuilder.build())
        addAccessors(builder, fieldType, association.fieldName)
    }

    private fun addProperty(builder: TypeSpec.Builder, type: TypeName, name: String) {
        builder.addField(FieldSpec.builder(type, name, Modifier.PRIVATE).build())
        addAccessors(builder, type, name)
    }

    private fun addAccessors(builder: TypeSpec.Builder, type: TypeName, name: String) {
        val capitalized = name.replaceFirstChar { it.uppercase() }
        builder.addMethod(
            MethodSpec.methodBuilder("get$capitalized")
                .addModifiers(Modifier.PUBLIC)
                .returns(type)
                .addStatement("return this.\$L", name)
                .build()
        )
        builder.addMethod(
            MethodSpec.methodBuilder("set$capitalized")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(type, name).build())
                .addStatement("this.\$L = \$L", name, name)
                .build()
        )
    }
}
