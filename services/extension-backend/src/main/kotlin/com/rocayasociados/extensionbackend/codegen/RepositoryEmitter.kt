package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

private val JPA_REPOSITORY = ClassName.get("org.springframework.data.jpa.repository", "JpaRepository")

/**
 * Emits one Spring Data `JpaRepository<Entity, Long>` interface per
 * non-enumeration, non-interface [ResolvedClass], declaring only the
 * `findBy...`/`existsBy...`/`countBy...`/`deleteBy...` methods
 * [ClassModelResolver] already recognized - Spring Data implements their
 * bodies at runtime from the method name alone, so no method body is ever
 * generated here. See specs/code-generation/spec.md - "Only Spring-Data-style
 * query methods are generated on repositories".
 */
object RepositoryEmitter {
    fun emit(cls: ResolvedClass, entityPackage: String, repositoryPackage: String): JavaFile {
        val entityType = ClassName.get(entityPackage, cls.name)
        val repositoryName = "${cls.name}Repository"
        val repositoryInterface = ParameterizedTypeName.get(JPA_REPOSITORY, entityType, ClassName.get("java.lang", "Long"))

        val builder = TypeSpec.interfaceBuilder(repositoryName)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(repositoryInterface)

        cls.repositoryMethods.forEach { method ->
            val returnType: TypeName = method.returnType ?: TypeName.VOID
            val methodBuilder = MethodSpec.methodBuilder(method.name)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(returnType)
            method.parameters.forEach { param ->
                methodBuilder.addParameter(ParameterSpec.builder(param.type, param.name).build())
            }
            builder.addMethod(methodBuilder.build())
        }

        return JavaFile.builder(repositoryPackage, builder.build()).build()
    }
}
