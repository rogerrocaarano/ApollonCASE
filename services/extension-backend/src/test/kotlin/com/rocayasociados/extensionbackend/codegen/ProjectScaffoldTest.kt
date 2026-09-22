package com.rocayasociados.extensionbackend.codegen

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ProjectScaffoldTest {

    @Test
    fun `el andamiaje del proyecto compila solo`() {
        val diagnostics = JavaCompilationHelper.compile(ProjectScaffold.applicationJavaFile())
        assertTrue(diagnostics.isEmpty(), "Esperaba compilar sin errores pero obtuvo:\n$diagnostics")
    }

    @Test
    fun `pom y application-properties tienen contenido no vacio`() {
        assertTrue(ProjectScaffold.pomXml().contains("spring-boot-starter-data-jpa"))
        assertTrue(ProjectScaffold.applicationProperties().contains("spring.datasource.url"))
    }
}
