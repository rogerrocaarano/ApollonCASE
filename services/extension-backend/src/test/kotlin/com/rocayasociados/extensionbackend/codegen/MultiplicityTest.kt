package com.rocayasociados.extensionbackend.codegen

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultiplicityTest {

    @Test
    fun `valores to-one`() {
        assertEquals(Cardinality.TO_ONE, Multiplicity.classify("1"))
        assertEquals(Cardinality.TO_ONE, Multiplicity.classify("0..1"))
        assertEquals(Cardinality.TO_ONE, Multiplicity.classify("0"))
    }

    @Test
    fun `valores to-many`() {
        assertEquals(Cardinality.TO_MANY, Multiplicity.classify("*"))
        assertEquals(Cardinality.TO_MANY, Multiplicity.classify("0..*"))
        assertEquals(Cardinality.TO_MANY, Multiplicity.classify("1..*"))
    }

    @Test
    fun `entero suelto mayor que uno es to-many`() {
        assertEquals(Cardinality.TO_MANY, Multiplicity.classify("5"))
    }

    @Test
    fun `cadena no reconocida devuelve null`() {
        assertNull(Multiplicity.classify("muchos"))
        assertNull(Multiplicity.classify(""))
        assertNull(Multiplicity.classify(null))
        assertNull(Multiplicity.classify("1..muchos"))
    }
}
