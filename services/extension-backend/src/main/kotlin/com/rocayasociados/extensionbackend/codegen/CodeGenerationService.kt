package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.JavaFile
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.springframework.stereotype.Service

/** Thrown when generation is requested for a diagram that isn't a Class Diagram. */
class UnsupportedDiagramTypeException(val actualType: String?) :
    Exception("Solo se puede generar codigo a partir de un Class Diagram (tipo recibido: $actualType)")

/**
 * Orchestrates the whole "diagram body in, ZIP out" pipeline: extract the
 * class-diagram graph, resolve+validate every class's members, apply
 * relationships, and - only if nothing failed - emit and zip the project.
 * See specs/code-generation/spec.md.
 */
@Service
class CodeGenerationService {
    fun generateEntitiesAndRepositoriesZip(diagramBody: Map<String, Any?>): ByteArray {
        val graph = DiagramGraphExtractor.extract(diagramBody)
        if (graph.diagramType != "ClassDiagram") {
            throw UnsupportedDiagramTypeException(graph.diagramType)
        }

        val (classes, resolveErrors) = ClassModelResolver.resolve(graph, ProjectScaffold.ENTITY_PACKAGE)
        val relationshipErrors = RelationshipMapper.apply(classes, graph.relationships, ProjectScaffold.ENTITY_PACKAGE)

        val errors = resolveErrors + relationshipErrors
        if (errors.isNotEmpty()) {
            throw CodeGenerationValidationException(errors)
        }

        return assembleZip(classes)
    }

    private fun assembleZip(classes: List<ResolvedClass>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            addJavaFile(zip, ProjectScaffold.applicationJavaFile())
            addTextEntry(zip, "pom.xml", ProjectScaffold.pomXml())
            addTextEntry(zip, "src/main/resources/application.properties", ProjectScaffold.applicationProperties())

            classes.forEach { cls ->
                addJavaFile(zip, EntityEmitter.emit(cls, ProjectScaffold.ENTITY_PACKAGE))
                if (!cls.isInterface && !cls.isEnumeration) {
                    addJavaFile(
                        zip,
                        RepositoryEmitter.emit(cls, ProjectScaffold.ENTITY_PACKAGE, ProjectScaffold.REPOSITORY_PACKAGE)
                    )
                }
            }
        }
        return buffer.toByteArray()
    }

    private fun addJavaFile(zip: ZipOutputStream, javaFile: JavaFile) {
        val path = "src/main/java/${javaFile.packageName.replace('.', '/')}/${javaFile.typeSpec.name}.java"
        addTextEntry(zip, path, javaFile.toString())
    }

    private fun addTextEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
