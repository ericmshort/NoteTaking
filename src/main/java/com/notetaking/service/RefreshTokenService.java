package com.notetaking.service;

import com.notetaking.entity.RefreshToken;
import com.notetaking.exception.AppException;
import com.notetaking.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public record RotationResult(String username, String newRawToken) {}

    private final RefreshTokenRepository repo;

    @Value("${app.refresh-token.expiry-days:7}")
    private int expiryDays;

    /** Creates a refresh token, persists its hash, and returns the raw value for the client. */
    public String createToken(String username) {
        var raw = UUID.randomUUID().toString();
        repo.save(RefreshToken.builder()
                .tokenHash(hash(raw))
                .username(username)
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .build());
        return raw;
    }

    /**
     * Validates the token, then rotates: revokes the old entry and creates a new one.
     * Rotation ensures a stolen refresh token can only be used once before detection.
     */
    @Transactional
    public RotationResult validateAndRotate(String rawToken) {
        var stored = repo.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        stored.setRevoked(true);
        return new RotationResult(stored.getUsername(), createToken(stored.getUsername()));
    }

    @Transactional
    public void revokeToken(String rawToken) {
        repo.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeAllForUser(String username) {
        repo.revokeAllByUsername(username);
    }

    static String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
