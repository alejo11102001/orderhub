package com.orderhub.backend.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    private static final String PASSWORD = "una-clave-larga-123";

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AdminSeeder seeder(String email, String password) {
        return seeder(email, password, null);
    }

    private AdminSeeder seeder(String email, String password, String vaultPassword) {
        return new AdminSeeder(new AdminProperties(email, password, vaultPassword), userRepository, passwordEncoder);
    }

    @Test
    void notConfigured_createsNothing() {
        seeder(null, null).run(null);
        seeder("admin@orderhub.test", "").run(null);
        seeder("", PASSWORD).run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void blankEnvPassword_fallsBackToVaultPassword() {
        when(userRepository.existsByEmail("admin@orderhub.test")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");

        seeder("admin@orderhub.test", "", PASSWORD).run(null);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void existingUser_isNotTouched() {
        when(userRepository.existsByEmail("admin@orderhub.test")).thenReturn(true);

        seeder("admin@orderhub.test", PASSWORD).run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void creates_adminWithHashedPassword() {
        when(userRepository.existsByEmail("admin@orderhub.test")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");

        seeder(" Admin@OrderHub.test ", PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@orderhub.test");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void shortPassword_failsStartupWithoutLeakingIt() {
        assertThatThrownBy(() -> seeder("admin@orderhub.test", "corta123").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12")
                .hasMessageNotContaining("corta123");

        verifyNoInteractions(userRepository);
    }
}
