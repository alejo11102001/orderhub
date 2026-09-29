package com.orderhub.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-32b",
        "app.jwt.expiration-minutes=5"
})
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    private static final String CREDENTIALS = "{\"email\":\"ana@test.com\",\"password\":\"Password123!\"}";

    @Test
    void register_thenLogin_returnsToken_andDuplicateIsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(CREDENTIALS))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(CREDENTIALS))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(CREDENTIALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@test.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
    }
}