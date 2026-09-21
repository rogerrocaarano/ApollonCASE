package com.rocayasociados.extensionbackend.users

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.hamcrest.Matchers.nullValue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Runs against the real dev datasource (application-local.properties), like the
 * rest of this project's tests today. @Transactional rolls back every write at
 * the end of each test so the shared dev database is left untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class UsersControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var usersRepository: UsersRepository

    @Test
    fun `me sin token responde 401`() {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me con token invalido responde 401`() {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `primera peticion autenticada crea el usuario, la segunda lo reutiliza`() {
        val subject = "test-subject-${UUID.randomUUID()}"

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keycloakId").value(subject))

        val created = usersRepository.findByKeycloakId(subject)
        assertNotNull(created, "El primer request autenticado debe crear el User")

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(created.id.toString()))

        assertEquals(
            1,
            usersRepository.findAll().count { it.keycloakId == subject },
            "El segundo request no debe crear un User duplicado",
        )
    }

    @Test
    fun `usuario nuevo tiene displayName y email vacios`() {
        val subject = "test-subject-${UUID.randomUUID()}"

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt { it.subject(subject) }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value(nullValue()))
            .andExpect(jsonPath("$.email").value(nullValue()))
    }

    @Test
    fun `updateMe con solo displayName no toca el email`() {
        val subject = "test-subject-${UUID.randomUUID()}"
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt { it.subject(subject) }))

        mockMvc.perform(
            patch("/api/v1/me")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"displayName": "Ada Lovelace"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.email").value(nullValue()))

        assertEquals("Ada Lovelace", usersRepository.findByKeycloakId(subject)?.displayName)
        assertEquals(null, usersRepository.findByKeycloakId(subject)?.email)
    }

    @Test
    fun `updateMe con solo email no toca el displayName`() {
        val subject = "test-subject-${UUID.randomUUID()}"
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt { it.subject(subject) }))
        mockMvc.perform(
            patch("/api/v1/me")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"displayName": "Ada Lovelace"}""")
        )

        mockMvc.perform(
            patch("/api/v1/me")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"email": "ada@example.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("ada@example.com"))
            .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
    }

    @Test
    fun `updateMe con email invalido responde 400`() {
        val subject = "test-subject-${UUID.randomUUID()}"
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt { it.subject(subject) }))

        mockMvc.perform(
            patch("/api/v1/me")
                .with(jwt().jwt { it.subject(subject) })
                .contentType("application/json")
                .content("""{"email": "not-an-email"}""")
        ).andExpect(status().isBadRequest)

        assertEquals(null, usersRepository.findByKeycloakId(subject)?.email)
    }
}
