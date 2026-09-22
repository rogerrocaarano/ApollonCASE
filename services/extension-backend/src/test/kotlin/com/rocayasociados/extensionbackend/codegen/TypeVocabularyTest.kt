package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TypeVocabularyTest {

    @Test
    fun `resuelve tipos primitivos y envoltorio soportados`() {
        assertEquals(TypeName.INT, TypeVocabulary.resolve("int", emptySet(), "com.generated.app.entity"))
        assertEquals(
            ClassName.get("java.lang", "String"),
            TypeVocabulary.resolve("String", emptySet(), "com.generated.app.entity"),
        )
        assertEquals(
            ClassName.get("java.math", "BigDecimal"),
            TypeVocabulary.resolve("BigDecimal", emptySet(), "com.generated.app.entity"),
        )
    }

    @Test
    fun `no acepta variantes con distinta capitalizacion`() {
        assertNull(TypeVocabulary.resolve("string", emptySet(), "com.generated.app.entity"))
        assertNull(TypeVocabulary.resolve("INT", emptySet(), "com.generated.app.entity"))
    }

    @Test
    fun `resuelve el nombre de otra clase del diagrama`() {
        val resolved = TypeVocabulary.resolve("Cliente", setOf("Cliente", "Pedido"), "com.generated.app.entity")
        assertEquals(ClassName.get("com.generated.app.entity", "Cliente"), resolved)
    }

    @Test
    fun `tipo no soportado devuelve null`() {
        assertNull(TypeVocabulary.resolve("Texto", setOf("Cliente"), "com.generated.app.entity"))
    }
}
