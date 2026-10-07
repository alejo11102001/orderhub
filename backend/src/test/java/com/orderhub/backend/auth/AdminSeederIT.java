package com.orderhub.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "app.admin.email=Root@OrderHub.test",
        "app.admin.password=una-clave-larga-123"
})
class AdminSeederIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired AdminSeeder seeder;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void startingTwice_createsAdminOnlyOnce() {
        // el primer arranque ya ocurrió al levantar el contexto; simulamos el segundo
        seeder.run(null);

        assertThat(userRepository.findAll())
                .filteredOn(u -> u.getEmail().equals("root@orderhub.test"))
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.getRole()).isEqualTo(Role.ADMIN);
                    assertThat(passwordEncoder.matches("una-clave-larga-123", u.getPasswordHash())).isTrue();
                });
    }
}
