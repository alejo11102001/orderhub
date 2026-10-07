package com.orderhub.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.login-max-attempts=3",
        "app.security.login-window-minutes=15"
})
class LoginRateLimitIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mockMvc;

    private MockHttpServletRequestBuilder login(String email, String password, String ip) {
        return post("/api/auth/login").contentType("application/json")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void blocksAfterMaxFailures_with429AndRetryAfter_evenForUnknownEmails() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("nadie@test.com", "incorrecta", "10.0.0.1")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("nadie@test.com", "incorrecta", "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void sameResponseForExistingAndNonExistingEmail() throws Exception {
        register("existe@test.com", "Password123!");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("existe@test.com", "mala", "10.0.0.2")).andExpect(status().isUnauthorized());
            mockMvc.perform(login("fantasma@test.com", "mala", "10.0.0.2")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("existe@test.com", "mala", "10.0.0.2")).andExpect(status().isTooManyRequests());
        mockMvc.perform(login("fantasma@test.com", "mala", "10.0.0.2")).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        register("reset@test.com", "Password123!");
        for (int round = 0; round < 3; round++) {
            mockMvc.perform(login("reset@test.com", "mala", "10.0.0.3")).andExpect(status().isUnauthorized());
            mockMvc.perform(login("reset@test.com", "mala", "10.0.0.3")).andExpect(status().isUnauthorized());
            mockMvc.perform(login("reset@test.com", "Password123!", "10.0.0.3")).andExpect(status().isOk());
        }
    }

    @Test
    void limitIsPerIpAndEmail() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login("otro@test.com", "mala", "10.0.0.4")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("otro@test.com", "mala", "10.0.0.4")).andExpect(status().isTooManyRequests());
        mockMvc.perform(login("otro@test.com", "mala", "10.0.0.5")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("distinto@test.com", "mala", "10.0.0.4")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerDuplicatesAreAlsoLimited() throws Exception {
        register("dup@test.com", "Password123!");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/register").contentType("application/json")
                            .content("{\"email\":\"dup@test.com\",\"password\":\"Password123!\"}"))
                    .andExpect(status().isConflict());
        }

        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"email\":\"dup@test.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
