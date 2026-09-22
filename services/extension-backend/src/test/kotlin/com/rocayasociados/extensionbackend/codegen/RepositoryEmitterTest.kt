package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ENTITY_PKG = "com.generated.app.entity"
private const val REPO_PKG = "com.generated.app.repository"

class RepositoryEmitterTest {

    @Test
    fun `metodo con convencion de spring data se declara en el repositorio y compila`() {
        val cliente = ResolvedClass(
            id = "Cliente",
            name = "Cliente",
            isInterface = false,
            isEnumeration = false,
            isAbstract = false,
            enumConstants = emptyList(),
            attributes = listOf(ResolvedAttribute("email", ClassName.get("java.lang", "String"), null)),
            repositoryMethods = listOf(
                ResolvedRepositoryMethod(
                    name = "findByEmail",
                    parameters = listOf(ResolvedParameter("email", ClassName.get("java.lang", "String"))),
                    returnType = ClassName.get(ENTITY_PKG, "Cliente"),
                )
            ),
        )

        val entityFile = EntityEmitter.emit(cliente, ENTITY_PKG)
        val repositoryFile = RepositoryEmitter.emit(cliente, ENTITY_PKG, REPO_PKG)

        assertTrue(repositoryFile.toString().contains("findByEmail"))

        val diagnostics = JavaCompilationHelper.compile(entityFile, repositoryFile)
        assertTrue(diagnostics.isEmpty(), "Esperaba compilar sin errores pero obtuvo:\n$diagnostics")
    }

    @Test
    fun `metodo que no encaja en la convencion no aparece en el repositorio`() {
        val cliente = ResolvedClass(
            id = "Cliente",
            name = "Cliente",
            isInterface = false,
            isEnumeration = false,
            isAbstract = false,
            enumConstants = emptyList(),
            attributes = emptyList(),
            repositoryMethods = emptyList(), // ClassModelResolver already excluded calcularTotal() upstream
        )
        val repositoryFile = RepositoryEmitter.emit(cliente, ENTITY_PKG, REPO_PKG)
        assertFalse(repositoryFile.toString().contains("calcularTotal"))
    }

    @Test
    fun `ClassModelResolver excluye en silencio un metodo que no es de consulta`() {
        val graph = DiagramGraph(
            diagramType = "ClassDiagram",
            classes = listOf(
                UmlClassNode(
                    id = "Cliente", name = "Cliente", isInterface = false, isEnumeration = false, isAbstract = false,
                    rawAttributes = emptyList(),
                    rawMethods = listOf("findByEmail(email : String) : Cliente", "calcularTotal() : BigDecimal"),
                )
            ),
            relationships = emptyList(),
        )
        val (resolved, errors) = ClassModelResolver.resolve(graph, ENTITY_PKG)
        assertTrue(errors.isEmpty())
        val cliente = resolved.single()
        assertTrue(cliente.repositoryMethods.any { it.name == "findByEmail" })
        assertFalse(cliente.repositoryMethods.any { it.name == "calcularTotal" })
    }
}
