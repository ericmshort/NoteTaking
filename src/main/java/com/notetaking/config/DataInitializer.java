package com.notetaking.config;

import com.notetaking.entity.User;
import com.notetaking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            log.info("ADMIN_USERNAME/ADMIN_PASSWORD not set; skipping admin bootstrap");
            return;
        }
        if (userRepository.existsByUsername(adminUsername)) {
            log.debug("Admin user '{}' already exists; skipping bootstrap", adminUsername);
            return;
        }
        var admin = User.builder()
                .username(adminUsername)
                .email(adminUsername + "@localhost")
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Bootstrap: created admin user '{}'", adminUsername);
    }
}
