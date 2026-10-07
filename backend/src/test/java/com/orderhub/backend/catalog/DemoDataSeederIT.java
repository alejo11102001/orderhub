package com.orderhub.backend.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "app.demo-data=true")
class DemoDataSeederIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired DemoDataSeeder seeder;
    @Autowired ProductRepository repository;

    @Test
    void seedsOnStartup_andRunningAgainDoesNotDuplicate() {
        assertThat(repository.count()).isEqualTo(12);

        seeder.run(null);

        assertThat(repository.count()).isEqualTo(12);
    }
}
