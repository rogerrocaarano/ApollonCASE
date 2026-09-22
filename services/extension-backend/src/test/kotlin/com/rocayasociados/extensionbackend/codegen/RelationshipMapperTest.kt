package com.rocayasociados.extensionbackend.codegen

import com.squareup.javapoet.ClassName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PKG = "com.generated.app.entity"

class RelationshipMapperTest {

    private fun classNode(id: String, name: String, isInterface: Boolean = false) =
        ResolvedClass(
            id = id,
            name = name,
            isInterface = isInterface,
            isEnumeration = false,
            isAbstract = false,
            enumConstants = emptyList(),
            attributes = emptyList(),
            repositoryMethods = emptyList(),
        )

    private fun edge(
        type: String,
        sourceId: String,
        targetId: String,
        sourceMultiplicity: String? = null,
        targetMultiplicity: String? = null,
    ) = UmlRelationshipEdge(
        id = "$sourceId-$targetId",
        type = type,
        sourceId = sourceId,
        targetId = targetId,
        sourceMultiplicity = sourceMultiplicity,
        targetMultiplicity = targetMultiplicity,
    )

    @Test
    fun `herencia hace que la subclase extienda la superclase`() {
        val empleado = classNode("s", "Empleado")
        val persona = classNode("t", "Persona")
        val errors = RelationshipMapper.apply(
            listOf(empleado, persona),
            listOf(edge("ClassInheritance", "s", "t")),
            PKG,
        )
        assertTrue(errors.isEmpty())
        assertEquals(ClassName.get(PKG, "Persona"), empleado.superclass)
        assertNull(persona.superclass)
    }

    @Test
    fun `realizacion hace que la clase implemente la interfaz`() {
        val factura = classNode("s", "Factura")
        val facturable = classNode("t", "Facturable", isInterface = true)
        val errors = RelationshipMapper.apply(
            listOf(factura, facturable),
            listOf(edge("ClassRealization", "s", "t")),
            PKG,
        )
        assertTrue(errors.isEmpty())
        assertEquals(listOf(ClassName.get(PKG, "Facturable")), factura.interfaces)
    }

    @Test
    fun `realizacion contra una clase que no es interfaz falla`() {
        val factura = classNode("s", "Factura")
        val noInterfaz = classNode("t", "NoInterfaz")
        val errors = RelationshipMapper.apply(
            listOf(factura, noInterfaz),
            listOf(edge("ClassRealization", "s", "t")),
            PKG,
        )
        assertEquals(1, errors.size)
        assertTrue(factura.interfaces.isEmpty())
    }

    @Test
    fun `composicion 1 a muchos - el todo cascada sobre la parte`() {
        val lineaPedido = classNode("s", "LineaPedido")
        val pedido = classNode("t", "Pedido")
        val errors = RelationshipMapper.apply(
            listOf(lineaPedido, pedido),
            listOf(edge("ClassComposition", "s", "t", sourceMultiplicity = "*", targetMultiplicity = "1")),
            PKG,
        )
        assertTrue(errors.isEmpty())

        // LineaPedido (part) owns a single reference to Pedido (whole), no cascade.
        assertEquals(1, lineaPedido.associations.size)
        val partSide = lineaPedido.associations[0]
        assertEquals("pedido", partSide.fieldName)
        assertEquals(Cardinality.TO_ONE, partSide.cardinality)
        assertTrue(partSide.owning)
        assertNull(partSide.mappedBy)
        assertEquals(false, partSide.cascadeAll)

        // Pedido (whole) has a cascading, orphan-removing collection of LineaPedido.
        assertEquals(1, pedido.associations.size)
        val wholeSide = pedido.associations[0]
        assertEquals("lineaPedidoList", wholeSide.fieldName)
        assertEquals(Cardinality.TO_MANY, wholeSide.cardinality)
        assertEquals(false, wholeSide.owning)
        assertEquals("pedido", wholeSide.mappedBy)
        assertTrue(wholeSide.cascadeAll)
        assertTrue(wholeSide.orphanRemoval)
    }

    @Test
    fun `agregacion 1 a muchos - el todo no cascada sobre la parte`() {
        val jugador = classNode("s", "Jugador")
        val equipo = classNode("t", "Equipo")
        val errors = RelationshipMapper.apply(
            listOf(jugador, equipo),
            listOf(edge("ClassAggregation", "s", "t", sourceMultiplicity = "*", targetMultiplicity = "1")),
            PKG,
        )
        assertTrue(errors.isEmpty())
        val wholeSide = equipo.associations.single()
        assertEquals(Cardinality.TO_MANY, wholeSide.cardinality)
        assertEquals(false, wholeSide.cascadeAll)
        assertEquals(false, wholeSide.orphanRemoval)
    }

    @Test
    fun `composicion 1 a 0 o 1 sigue cascadeando en el lado unico`() {
        val motor = classNode("s", "Motor")
        val coche = classNode("t", "Coche")
        val errors = RelationshipMapper.apply(
            listOf(motor, coche),
            listOf(edge("ClassComposition", "s", "t", sourceMultiplicity = "0..1", targetMultiplicity = "1")),
            PKG,
        )
        assertTrue(errors.isEmpty())
        val wholeSide = coche.associations.single()
        assertEquals(Cardinality.TO_ONE, wholeSide.cardinality)
        assertTrue(wholeSide.cascadeAll)
        // orphanRemoval is only meaningful (and only asserted) for TO_MANY collections.
        assertEquals(false, wholeSide.orphanRemoval)
    }

    @Test
    fun `asociacion muchos a muchos`() {
        val alumno = classNode("s", "Alumno")
        val curso = classNode("t", "Curso")
        val errors = RelationshipMapper.apply(
            listOf(alumno, curso),
            listOf(edge("ClassBidirectional", "s", "t", sourceMultiplicity = "*", targetMultiplicity = "*")),
            PKG,
        )
        assertTrue(errors.isEmpty())

        val sourceSide = alumno.associations.single()
        assertEquals(Cardinality.TO_MANY, sourceSide.cardinality)
        assertTrue(sourceSide.owning)
        assertNull(sourceSide.mappedBy)

        val targetSide = curso.associations.single()
        assertEquals(Cardinality.TO_MANY, targetSide.cardinality)
        assertEquals(false, targetSide.owning)
        assertEquals(sourceSide.fieldName, targetSide.mappedBy)
    }

    @Test
    fun `asociacion unidireccional no genera campo en el destino`() {
        val pedido = classNode("s", "Pedido")
        val cliente = classNode("t", "Cliente")
        val errors = RelationshipMapper.apply(
            listOf(pedido, cliente),
            listOf(edge("ClassUnidirectional", "s", "t", sourceMultiplicity = "*", targetMultiplicity = "1")),
            PKG,
        )
        assertTrue(errors.isEmpty())
        assertEquals(1, pedido.associations.size)
        assertTrue(cliente.associations.isEmpty())
    }

    @Test
    fun `dependencia no genera ningun campo`() {
        val a = classNode("s", "A")
        val b = classNode("t", "B")
        val errors = RelationshipMapper.apply(
            listOf(a, b),
            listOf(edge("ClassDependency", "s", "t")),
            PKG,
        )
        assertTrue(errors.isEmpty())
        assertTrue(a.associations.isEmpty())
        assertTrue(b.associations.isEmpty())
    }

    @Test
    fun `multiplicidad no soportada bloquea la relacion con error`() {
        val a = classNode("s", "A")
        val b = classNode("t", "B")
        val errors = RelationshipMapper.apply(
            listOf(a, b),
            listOf(edge("ClassBidirectional", "s", "t", sourceMultiplicity = "muchos", targetMultiplicity = "1")),
            PKG,
        )
        assertEquals(1, errors.size)
        assertTrue(a.associations.isEmpty())
        assertTrue(b.associations.isEmpty())
    }
}
