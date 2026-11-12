package com.notetaking.service;

import java.time.Instant;

/**
 * Pluggable backend for token revocation.
 * Default: in-memory (dev/single-node).
 * Production: swap in RedisTokenRevocationStore by setting spring.data.redis.host.
 */
public interface TokenRevocationStore {
    void revokeToken(String jti, Instant expiresAt);
    void revokeAllTokensForUser(String username);
    boolean isRevoked(String jti, String username, Instant issuedAt);
}
