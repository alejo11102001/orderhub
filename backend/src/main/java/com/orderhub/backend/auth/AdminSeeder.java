package com.orderhub.backend.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.configured()) {
            log.info("Admin seed skipped: app.admin.email / app.admin.password not configured");
            return;
        }
        if (properties.effectivePassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.admin.password must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        String email = properties.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            log.info("Admin seed skipped: user {} already exists", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(properties.effectivePassword()));
        admin.setRole(Role.ADMIN);
        try {
            userRepository.save(admin);
            log.info("Admin user {} created", email);
        } catch (DataIntegrityViolationException e) {
            // otra réplica lo creó entre el existsByEmail y el save
            log.info("Admin seed skipped: user {} was created concurrently", email);
        }
    }
}
