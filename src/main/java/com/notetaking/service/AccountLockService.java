package com.notetaking.service;

import com.notetaking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final UserRepository userRepository;

    @Value("${app.security.max-failed-attempts:10}")
    private int maxFailedAttempts;

    @Value("${app.security.lockout-minutes:15}")
    private int lockoutMinutes;

    @Transactional
    public void recordFailedAttempt(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setAccountLockedUntil(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
            }
            // JPA auto-flushes the managed entity on transaction commit — no explicit save needed
        });
    }

    @Transactional
    public void resetAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0 || user.getAccountLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);
            }
        });
    }
}
