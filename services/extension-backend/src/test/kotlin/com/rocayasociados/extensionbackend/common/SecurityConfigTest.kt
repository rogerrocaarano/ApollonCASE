package com.rocayasociados.extensionbackend.common

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// `/actuator/health` must be reachable without a bearer token; everything under `/api/v1` must not be.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityConfigTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `actuator health es accesible sin token y no expone detalle`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun `api v1 sigue exigiendo autenticacion`() {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized)
    }
}
