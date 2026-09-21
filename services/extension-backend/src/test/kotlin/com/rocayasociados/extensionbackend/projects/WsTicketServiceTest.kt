package com.rocayasociados.extensionbackend.projects

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WsTicketServiceTest {

    private class MutableClock(private var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = instant
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }

    @Test
    fun `consumeTicket devuelve el redisId autorizado`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val service = WsTicketService(clock)

        val token = service.issueTicket("redis-1")

        assertEquals("redis-1", service.consumeTicket(token))
    }

    @Test
    fun `consumeTicket es de un solo uso`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val service = WsTicketService(clock)
        val token = service.issueTicket("redis-1")

        service.consumeTicket(token)
        val second = service.consumeTicket(token)

        assertNull(second)
    }

    @Test
    fun `consumeTicket con un token desconocido devuelve null`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val service = WsTicketService(clock)

        assertNull(service.consumeTicket("unknown-token"))
    }

    @Test
    fun `consumeTicket despues de expirar devuelve null`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val service = WsTicketService(clock)
        val token = service.issueTicket("redis-1")

        clock.advance(Duration.ofSeconds(31))

        assertNull(service.consumeTicket(token))
    }

    @Test
    fun `consumeTicket justo antes de expirar sigue siendo valido`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val service = WsTicketService(clock)
        val token = service.issueTicket("redis-1")

        clock.advance(Duration.ofSeconds(29))

        assertEquals("redis-1", service.consumeTicket(token))
    }
}
