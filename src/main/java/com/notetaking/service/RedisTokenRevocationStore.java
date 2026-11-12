package com.notetaking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed revocation store. Activated when spring.data.redis.host is set.
 * Redis TTLs handle automatic cleanup — no background sweep needed.
 * This bean is @Primary so it wins over InMemoryTokenRevocationStore when present.
 */
@Primary
@Component
@ConditionalOnProperty(name = "spring.data.redis.host")
@RequiredArgsConstructor
public class RedisTokenRevocationStore implements TokenRevocationStore {

    private static final String JTI_PREFIX  = "revoked:token:";
    private static final String USER_PREFIX = "revoked:user:";

    private final StringRedisTemplate redis;

    @Value("${app.refresh-token.expiry-days:7}")
    private int refreshTokenExpiryDays;

    @Override
    public void revokeToken(String jti, Instant expiresAt) {
        var ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative()) {
            redis.opsForValue().set(JTI_PREFIX + jti, "1", ttl);
        }
    }

    @Override
    public void revokeAllTokensForUser(String username) {
        // Store the revocation timestamp; TTL covers the longest possible refresh token lifetime.
        redis.opsForValue().set(
                USER_PREFIX + username,
                Instant.now().toString(),
                Duration.ofDays(refreshTokenExpiryDays));
    }

    @Override
    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        if (Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + jti))) return true;
        var raw = redis.opsForValue().get(USER_PREFIX + username);
        if (raw == null) return false;
        return issuedAt.isBefore(Instant.parse(raw));
    }
}
