package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PKG = "com.generated.app.entity"

class EntityEmitterTest {

    private fun simpleClass(name: String, attributes: List<ResolvedAttribute> = emptyList()) = ResolvedClass(
        id = name,
        name = name,
        isInterface = false,
        isEnumeration = false,
        isAbstract = false,
        enumConstants = emptyList(),
        attributes = attributes,
        repositoryMethods = emptyList(),
    )

    @Test
    fun `entidad simple con atributos compila`() {
        val cliente = simpleClass(
            "Cliente",
            listOf(
                ResolvedAttribute("nombre", ClassName.get("java.lang", "String"), null),
                ResolvedAttribute("edad", com.squareup.javapoet.TypeName.INT, null),
            ),
        )
        val javaFile = EntityEmitter.emit(cliente, PKG)
        val diagnostics = JavaCompilationHelper.compile(javaFile)
        assertTrue(diagnostics.isEmpty(), "Esperaba compilar sin errores pero obtuvo:\n$diagnostics\n${javaFile}")
    }

    @Test
    fun `enumeracion genera un enum de java con las constantes esperadas`() {
        val estado = ResolvedClass(
            id = "Estado",
            name = "Estado",
            isInterface = false,
            isEnumeration = true,
            isAbstract = false,
            enumConstants = listOf("ACTIVE", "INACTIVE"),
            attributes = emptyList(),
            repositoryMethods = emptyList(),
        )
        val javaFile = EntityEmitter.emit(estado, PKG)
        val source = javaFile.toString()
        assertTrue(source.contains("enum Estado"))
        assertTrue(source.contains("ACTIVE"))
        assertTrue(source.contains("INACTIVE"))

        val diagnostics = JavaCompilationHelper.compile(javaFile)
        assertTrue(diagnostics.isEmpty(), "Esperaba compilar sin errores pero obtuvo:\n$diagnostics")
    }

    @Test
    fun `interfaz de realizacion compila vacia`() {
        val facturable = ResolvedClass(
            id = "Facturable",
            name = "Facturable",
            isInterface = true,
            isEnumeration = false,
            isAbstract = false,
            enumConstants = emptyList(),
            attributes = emptyList(),
            repositoryMethods = emptyList(),
        )
        val javaFile = EntityEmitter.emit(facturable, PKG)
        assertTrue(javaFile.toString().contains("interface Facturable"))
        val diagnostics = JavaCompilationHelper.compile(javaFile)
        assertTrue(diagnostics.isEmpty(), "Esperaba compilar sin errores pero obtuvo:\n$diagnostics")
    }

    @Test
    fun `entidad con herencia composicion y realizacion compila junta`() {
        val facturable = ResolvedClass(
            "Facturable", "Facturable", isInterface = true, isEnumeration = false, isAbstract = false,
            enumConstants = emptyList(), attributes = emptyList(), repositoryMethods = emptyList(),
        )
        val persona = simpleClass("Persona", listOf(ResolvedAttribute("nombre", ClassName.get("java.lang", "String"), null)))
        val lineaPedido = simpleClass("LineaPedido")
        val pedido = simpleClass("Pedido")

        val empleado = simpleClass("Empleado").apply {
            superclass = ClassName.get(PKG, "Persona")
            interfaces += ClassName.get(PKG, "Facturable")
        }

        RelationshipMapper.apply(
            listOf(lineaPedido, pedido),
            listOf(
                UmlRelationshipEdge(
                    id = "e1", type = "ClassComposition",
                    sourceId = "LineaPedido", targetId = "Pedido",
                    sourceMultiplicity = "*", targetMultiplicity = "1",
                )
            ),
            PKG,
        )

        val allFiles = listOf(
            EntityEmitter.emit(facturable, PKG),
            EntityEmitter.emit(persona, PKG),
            EntityEmitter.emit(empleado, PKG),
            EntityEmitter.emit(lineaPedido, PKG),
            EntityEmitter.emit(pedido, PKG),
        )
        val diagnostics = JavaCompilationHelper.compile(*allFiles.toTypedArray())
        assertTrue(diagnostics.isEmpty(), "Esperaba compilar sin errores pero obtuvo:\n$diagnostics")
    }
}
