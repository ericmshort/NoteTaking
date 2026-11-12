package com.notetaking.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blacklist. Revocations are lost on restart.
 * Replace the backing stores with Redis for production persistence.
 */
@Service
public class TokenRevocationService {

    // Individual token revocations (JTI → revoked)
    private final Set<String> revokedJtis = ConcurrentHashMap.newKeySet();

    // User-level revocations: all tokens issued before this timestamp are invalid
    private final Map<String, Instant> userRevokedAt = new ConcurrentHashMap<>();

    public void revokeToken(String jti) {
        revokedJtis.add(jti);
    }

    /** Invalidates all tokens issued to this user before now. */
    public void revokeAllTokensForUser(String username) {
        userRevokedAt.put(username, Instant.now());
    }

    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        if (revokedJtis.contains(jti)) return true;
        var revokedAt = userRevokedAt.get(username);
        return revokedAt != null && issuedAt.isBefore(revokedAt);
    }
}
