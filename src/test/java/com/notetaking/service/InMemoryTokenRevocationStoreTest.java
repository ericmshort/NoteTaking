package com.notetaking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenRevocationStoreTest {

    private InMemoryTokenRevocationStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryTokenRevocationStore();
        ReflectionTestUtils.setField(store, "refreshTokenExpiryDays", 7);
    }

    // ── revokeToken ───────────────────────────────────────────────────────────

    @Test
    void revokeToken_isRevoked() {
        store.revokeToken("jti-1", Instant.now().plus(15, ChronoUnit.MINUTES));

        assertThat(store.isRevoked("jti-1", "alice", Instant.now().minus(1, ChronoUnit.HOURS))).isTrue();
    }

    @Test
    void revokeToken_afterTokenExpiry_notRevoked() {
        // Token whose expiry is already in the past — should behave as not revoked
        store.revokeToken("jti-old", Instant.now().minus(1, ChronoUnit.SECONDS));

        assertThat(store.isRevoked("jti-old", "alice", Instant.now().minus(1, ChronoUnit.HOURS))).isFalse();
    }

    @Test
    void unknownJti_notRevoked() {
        assertThat(store.isRevoked("no-such-jti", "alice", Instant.now())).isFalse();
    }

    // ── revokeAllTokensForUser ────────────────────────────────────────────────

    @Test
    void revokeAllTokensForUser_invalidatesTokensIssuedBefore() {
        Instant issuedBefore = Instant.now().minus(1, ChronoUnit.SECONDS);
        store.revokeAllTokensForUser("alice");

        assertThat(store.isRevoked("any-jti", "alice", issuedBefore)).isTrue();
    }

    @Test
    void revokeAllTokensForUser_doesNotInvalidateTokensIssuedAfter() {
        store.revokeAllTokensForUser("alice");
        Instant issuedAfter = Instant.now().plus(1, ChronoUnit.SECONDS);

        assertThat(store.isRevoked("any-jti", "alice", issuedAfter)).isFalse();
    }

    @Test
    void revokeAllTokensForUser_doesNotAffectOtherUsers() {
        store.revokeAllTokensForUser("alice");
        Instant issuedBefore = Instant.now().minus(1, ChronoUnit.SECONDS);

        assertThat(store.isRevoked("any-jti", "bob", issuedBefore)).isFalse();
    }

    // ── purgeExpired ──────────────────────────────────────────────────────────

    @Test
    void purgeExpired_removesExpiredJtis() {
        store.revokeToken("expired-jti", Instant.now().minus(1, ChronoUnit.SECONDS));
        store.revokeToken("valid-jti", Instant.now().plus(15, ChronoUnit.MINUTES));

        store.purgeExpired();

        // expired JTI is gone — no longer reports as revoked
        assertThat(store.isRevoked("expired-jti", "alice", Instant.now().minus(1, ChronoUnit.HOURS))).isFalse();
        // valid JTI is still tracked
        assertThat(store.isRevoked("valid-jti", "alice", Instant.now().minus(1, ChronoUnit.HOURS))).isTrue();
    }

    @Test
    void purgeExpired_removesExpiredUserRevocations() {
        // Simulate a user revocation whose purgeAfter window has closed by injecting
        // a negative expiryDays so purgeAfter = revokedAt - N days (already past)
        ReflectionTestUtils.setField(store, "refreshTokenExpiryDays", -1);
        store.revokeAllTokensForUser("alice");
        ReflectionTestUtils.setField(store, "refreshTokenExpiryDays", 7);

        store.purgeExpired();

        // Entry was purged — a token issued before revocation no longer appears revoked
        assertThat(store.isRevoked("any-jti", "alice", Instant.now().minus(1, ChronoUnit.HOURS))).isFalse();
    }
}
