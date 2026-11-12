package com.notetaking.service;

import com.notetaking.entity.RefreshToken;
import com.notetaking.exception.AppException;
import com.notetaking.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    @Value("${app.refresh-token.expiry-days:7}")
    private int expiryDays;

    public RefreshToken createToken(String username) {
        var token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .username(username)
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .build();
        return repo.save(token);
    }

    /**
     * Validates the token, then rotates it: revokes the old one and returns a fresh one.
     * Rotation means a stolen refresh token can only be used once before detection.
     */
    @Transactional
    public RefreshToken validateAndRotate(String tokenValue) {
        var stored = repo.findByToken(tokenValue)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        stored.setRevoked(true);
        repo.save(stored);
        return createToken(stored.getUsername());
    }

    @Transactional
    public void revokeToken(String tokenValue) {
        repo.findByToken(tokenValue).ifPresent(t -> {
            t.setRevoked(true);
            repo.save(t);
        });
    }

    @Transactional
    public void revokeAllForUser(String username) {
        repo.revokeAllByUsername(username);
    }
}
