package com.notetaking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final TokenRevocationStore store;

    public void revokeToken(String jti, Instant expiresAt) {
        store.revokeToken(jti, expiresAt);
    }

    public void revokeAllTokensForUser(String username) {
        store.revokeAllTokensForUser(username);
    }

    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        return store.isRevoked(jti, username, issuedAt);
    }
}
