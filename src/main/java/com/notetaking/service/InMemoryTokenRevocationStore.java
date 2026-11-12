package com.notetaking.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default revocation store. Entries survive until JVM restart.
 * Acceptable for development and single-node deployments; use
 * RedisTokenRevocationStore for multi-instance or restart-safe revocation.
 */
@Component
public class InMemoryTokenRevocationStore implements TokenRevocationStore {

    private final Set<String> revokedJtis = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> userRevokedAt = new ConcurrentHashMap<>();

    @Override
    public void revokeToken(String jti, Instant expiresAt) {
        revokedJtis.add(jti);
    }

    @Override
    public void revokeAllTokensForUser(String username) {
        userRevokedAt.put(username, Instant.now());
    }

    @Override
    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        if (revokedJtis.contains(jti)) return true;
        var revokedAt = userRevokedAt.get(username);
        return revokedAt != null && issuedAt.isBefore(revokedAt);
    }
}
