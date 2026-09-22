package com.rocayasociados.extensionbackend.codegen

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberSyntaxTest {

    @Test
    fun `parseAttribute con visibilidad y sin valor por defecto`() {
        val attr = MemberSyntax.parseAttribute("- nombre : String")
        assertEquals(Visibility.PRIVATE, attr.visibility)
        assertEquals("nombre", attr.name)
        assertEquals("String", attr.type)
        assertNull(attr.defaultValue)
    }

    @Test
    fun `parseAttribute sin visibilidad`() {
        val attr = MemberSyntax.parseAttribute("edad : int")
        assertEquals(null, attr.visibility)
        assertEquals("edad", attr.name)
        assertEquals("int", attr.type)
    }

    @Test
    fun `parseAttribute con valor por defecto`() {
        val attr = MemberSyntax.parseAttribute("+ activo : boolean = true")
        assertEquals(Visibility.PUBLIC, attr.visibility)
        assertEquals("boolean", attr.type)
        assertEquals("true", attr.defaultValue)
    }

    @Test
    fun `parseAttribute sin tipo falla`() {
        assertFailsWith<MemberSyntaxException> { MemberSyntax.parseAttribute("- nombre") }
    }

    @Test
    fun `parseAttribute con sintaxis malformada falla`() {
        assertFailsWith<MemberSyntaxException> { MemberSyntax.parseAttribute("nombre :: String") }
        assertFailsWith<MemberSyntaxException> { MemberSyntax.parseAttribute("") }
    }

    @Test
    fun `parseMethod sin parametros y sin tipo de retorno`() {
        val method = MemberSyntax.parseMethod("reset()")
        assertEquals("reset", method.name)
        assertTrue(method.parameters.isEmpty())
        assertNull(method.returnType)
    }

    @Test
    fun `parseMethod con un parametro`() {
        val method = MemberSyntax.parseMethod("findByEmail(email : String) : Cliente")
        assertEquals("findByEmail", method.name)
        assertEquals(1, method.parameters.size)
        assertEquals("email", method.parameters[0].name)
        assertEquals("String", method.parameters[0].type)
        assertEquals("Cliente", method.returnType)
    }

    @Test
    fun `parseMethod con multiples parametros`() {
        val method = MemberSyntax.parseMethod("+ findByNombreAndEdad(nombre : String, edad : int) : Cliente")
        assertEquals(Visibility.PUBLIC, method.visibility)
        assertEquals(2, method.parameters.size)
        assertEquals("nombre", method.parameters[0].name)
        assertEquals("edad", method.parameters[1].name)
    }

    @Test
    fun `parseMethod con lista de parametros malformada falla`() {
        assertFailsWith<MemberSyntaxException> { MemberSyntax.parseMethod("findByEmail(email) : Cliente") }
        assertFailsWith<MemberSyntaxException> { MemberSyntax.parseMethod("calcularTotal(") }
    }
}
