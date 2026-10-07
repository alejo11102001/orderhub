package com.orderhub.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Límites de intentos fallidos de autenticación (por instancia, en memoria). */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(int loginMaxAttempts, long loginWindowMinutes) {

    public SecurityProperties {
        if (loginMaxAttempts <= 0) {
            loginMaxAttempts = 5;
        }
        if (loginWindowMinutes <= 0) {
            loginWindowMinutes = 15;
        }
    }
}
