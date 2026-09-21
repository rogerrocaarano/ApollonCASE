package com.rocayasociados.extensionbackend.projects

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Short-lived, single-use tickets that authorize a WebSocket relay connection
 * for one diagram. A browser cannot set an `Authorization` header on a WS
 * handshake, so the collaboration endpoint authenticates via a ticket fetched
 * over an already-authenticated REST call instead of a bearer token in the
 * connection URL (which access/proxy logs would capture).
 *
 * In-memory and single-instance: see design.md's Open Questions — a
 * horizontally scaled `extension-backend` would need this shared (e.g. Redis
 * or the existing Postgres database) instead.
 */
@Component
class WsTicketService(private val clock: Clock) {
    private val ttl: Duration = Duration.ofSeconds(30)
    private val tickets = ConcurrentHashMap<String, Ticket>()

    private data class Ticket(val redisId: String, val expiresAt: Instant)

    /** Issues a new ticket authorizing a relay connection to the given `diagrams-backend` id. */
    fun issueTicket(redisId: String): String {
        val token = UUID.randomUUID().toString()
        tickets[token] = Ticket(redisId, clock.instant().plus(ttl))
        return token
    }

    /**
     * Consumes a ticket (removing it, so it can never be redeemed twice) and
     * returns the `redisId` it authorizes, or `null` if the ticket is
     * missing, already used, or expired.
     */
    fun consumeTicket(token: String): String? {
        val ticket = tickets.remove(token) ?: return null
        if (ticket.expiresAt.isBefore(clock.instant())) return null
        return ticket.redisId
    }
}
