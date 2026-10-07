package com.orderhub.backend.auth;

import com.orderhub.backend.auth.dto.LoginRequest;
import com.orderhub.backend.auth.dto.RegisterRequest;
import com.orderhub.backend.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;
    private final AttemptLimiter limiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        String key = key("register", http, request.email());
        limiter.checkAllowed(key);
        try {
            service.register(request);
            limiter.reset(key);
        } catch (EmailAlreadyExistsException e) {
            limiter.recordFailure(key);
            throw e;
        }
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String key = key("login", http, request.email());
        limiter.checkAllowed(key);
        try {
            TokenResponse token = service.login(request);
            limiter.reset(key);
            return token;
        } catch (InvalidCredentialsException e) {
            limiter.recordFailure(key);
            throw e;
        }
    }

    /** IP + email: el límite se aplica igual exista o no la cuenta (no revela su existencia). */
    private static String key(String action, HttpServletRequest http, String email) {
        return action + ":" + http.getRemoteAddr() + ":" + email.trim().toLowerCase();
    }
}