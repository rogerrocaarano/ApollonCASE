package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.JavaFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider

/**
 * Actually compiles emitted [JavaFile]s with the JDK's own compiler, against
 * this test run's own classpath (which already carries jakarta.persistence,
 * Hibernate, and spring-data-jpa - the same annotations/interfaces the
 * generated code references). Returns the compiler's combined stdout/stderr;
 * empty means a clean compile. This is what actually verifies "emitted
 * source parses/compiles", rather than only asserting on JavaPoet's AST.
 */
object JavaCompilationHelper {
    /** Empty string on a clean compile; otherwise the compiler's diagnostics. */
    fun compile(vararg javaFiles: JavaFile): String {
        val sourceDir = Files.createTempDirectory("codegen-src")
        javaFiles.forEach { it.writeTo(sourceDir) }
        val sourceFiles = Files.walk(sourceDir)
            .filter { it.toString().endsWith(".java") }
            .map { it.toFile() }
            .toList()
        return compileFiles(sourceFiles)
    }

    /** Same as [compile], but for already-on-disk `.java` files (e.g. extracted from a generated ZIP). */
    fun compileFiles(sourceFiles: List<File>): String {
        val outputDir = Files.createTempDirectory("codegen-out")
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "No system Java compiler available" }
        val output = ByteArrayOutputStream()
        val classpath = System.getProperty("java.class.path")

        val args = arrayOf(
            "-cp", classpath,
            "-d", outputDir.toString(),
            "-proc:none",
        ) + sourceFiles.map { it.absolutePath }

        val exitCode = compiler.run(null, output, output, *args)
        return if (exitCode == 0) "" else output.toString(Charsets.UTF_8)
    }
}
