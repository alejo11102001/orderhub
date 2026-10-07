package com.orderhub.backend.auth;

import com.orderhub.backend.auth.dto.LoginRequest;
import com.orderhub.backend.auth.dto.RegisterRequest;
import com.orderhub.backend.auth.dto.TokenResponse;
import com.orderhub.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-32b",
        "app.jwt.expiration-minutes=5"
})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService service;
    @MockitoBean AttemptLimiter limiter;

    private static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void register_valid_returns201() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json("ana@test.com", "Password123!")))
                .andExpect(status().isCreated());

        verify(service).register(new RegisterRequest("ana@test.com", "Password123!"));
    }

    @Test
    void register_invalidBody_returns400WithFieldErrors_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json("no-es-un-correo", "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        verify(service, never()).register(any());
    }

    @Test
    void register_tooLongPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json("ana@test.com", "x".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new EmailAlreadyExistsException("ana@test.com"))
                .when(service).register(any());

        mockMvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json("ana@test.com", "Password123!")))
                .andExpect(status().isConflict());
    }

    @Test
    void register_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content("{no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_valid_returnsToken() throws Exception {
        when(service.login(new LoginRequest("ana@test.com", "Password123!")))
                .thenReturn(new TokenResponse("jwt-de-prueba", "Bearer", 300));

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json("ana@test.com", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-de-prueba"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    @Test
    void login_blankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(json("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_badCredentials_returns401_andRecordsFailure() throws Exception {
        when(service.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json("ana@test.com", "mala")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        verify(limiter).recordFailure("login:127.0.0.1:ana@test.com");
    }

    @Test
    void login_whenLimiterBlocks_returns429WithRetryAfter_andSkipsService() throws Exception {
        org.mockito.Mockito.doThrow(new TooManyAttemptsException(120)).when(limiter).checkAllowed(any());

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json("ana@test.com", "x")))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(120));

        verify(service, never()).login(any());
    }

    @Test
    void login_success_resetsLimiter() throws Exception {
        when(service.login(any())).thenReturn(new TokenResponse("t", "Bearer", 1));

        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json("Ana@Test.com", "ok")))
                .andExpect(status().isOk());

        verify(limiter).reset("login:127.0.0.1:ana@test.com");
    }
}
