package com.rocayasociados.extensionbackend.codegen

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaIdentifiersTest {

    @Test
    fun `acepta identificadores validos`() {
        assertTrue(JavaIdentifiers.isValid("nombre"))
        assertTrue(JavaIdentifiers.isValid("_privado"))
        assertTrue(JavaIdentifiers.isValid("Cliente2"))
    }

    @Test
    fun `rechaza palabras reservadas`() {
        assertFalse(JavaIdentifiers.isValid("class"))
        assertFalse(JavaIdentifiers.isValid("int"))
        assertFalse(JavaIdentifiers.isValid("return"))
    }

    @Test
    fun `rechaza identificadores invalidos`() {
        assertFalse(JavaIdentifiers.isValid("2nombre"))
        assertFalse(JavaIdentifiers.isValid("nombre con espacio"))
        assertFalse(JavaIdentifiers.isValid(""))
    }
}
