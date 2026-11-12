package com.notetaking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default revocation store. Entries survive until JVM restart.
 * Acceptable for development and single-node deployments; use
 * RedisTokenRevocationStore for multi-instance or restart-safe revocation.
 * A scheduled task purges expired entries hourly to prevent unbounded growth.
 */
@Component
public class InMemoryTokenRevocationStore implements TokenRevocationStore {

    private record UserRevocation(Instant revokedAt, Instant purgeAfter) {}

    // Maps jti → token expiry; entries are purged once the token would have expired anyway
    private final Map<String, Instant> revokedJtis = new ConcurrentHashMap<>();
    // Maps username → (revokedAt, purgeAfter); purgeAfter = revokedAt + max refresh token lifetime
    private final Map<String, UserRevocation> userRevocations = new ConcurrentHashMap<>();

    @Value("${app.refresh-token.expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Override
    public void revokeToken(String jti, Instant expiresAt) {
        revokedJtis.put(jti, expiresAt);
    }

    @Override
    public void revokeAllTokensForUser(String username) {
        var now = Instant.now();
        userRevocations.put(username, new UserRevocation(
                now, now.plus(refreshTokenExpiryDays, ChronoUnit.DAYS)));
    }

    @Override
    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        var jtiExpiry = revokedJtis.get(jti);
        if (jtiExpiry != null && Instant.now().isBefore(jtiExpiry)) return true;
        var rev = userRevocations.get(username);
        return rev != null && issuedAt.isBefore(rev.revokedAt());
    }

    @Scheduled(fixedDelayString = "${app.revocation-store.cleanup-interval-ms:3600000}",
               initialDelayString = "${app.revocation-store.cleanup-interval-ms:3600000}")
    public void purgeExpired() {
        var now = Instant.now();
        revokedJtis.entrySet().removeIf(e -> now.isAfter(e.getValue()));
        userRevocations.entrySet().removeIf(e -> now.isAfter(e.getValue().purgeAfter()));
    }
}
