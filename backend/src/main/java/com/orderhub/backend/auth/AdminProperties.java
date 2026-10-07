package com.orderhub.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code password} viene de APP_ADMIN_PASSWORD; {@code vaultPassword} es el respaldo
 * leído de Vault (clave {@code admin.password}). Se separan porque Compose inyecta
 * APP_ADMIN_PASSWORD vacío cuando no está definida y taparía el valor de Vault.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String email, String password, String vaultPassword) {

    public String effectivePassword() {
        if (password != null && !password.isBlank()) {
            return password;
        }
        return vaultPassword;
    }

    public boolean configured() {
        String effective = effectivePassword();
        return email != null && !email.isBlank() && effective != null && !effective.isBlank();
    }
}
