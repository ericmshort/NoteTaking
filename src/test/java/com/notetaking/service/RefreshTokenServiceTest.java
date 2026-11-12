package com.notetaking.service;

import com.notetaking.entity.RefreshToken;
import com.notetaking.exception.AppException;
import com.notetaking.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repo;
    @InjectMocks RefreshTokenService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expiryDays", 7);
    }

    // ── createToken ───────────────────────────────────────────────────────────

    @Test
    void createToken_savesHashedToken_andReturnsRaw() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.createToken("alice");

        assertThat(raw).isNotBlank();
        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw));
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void createToken_rawTokenNotStoredInHash() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.createToken("alice");

        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        // The stored hash must differ from the raw value — raw is never persisted
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(raw);
    }

    // ── validateAndRotate ─────────────────────────────────────────────────────

    @Test
    void validateAndRotate_validToken_returnsNewTokenAndRevokesOld() {
        String raw = "raw-refresh-token";
        var stored = RefreshToken.builder()
                .tokenHash(RefreshTokenService.hash(raw))
                .username("alice")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        when(repo.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.validateAndRotate(raw);

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.newRawToken()).isNotBlank().isNotEqualTo(raw);
        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void validateAndRotate_unknownToken_throws401() {
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndRotate("bad-token"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void validateAndRotate_revokedToken_throws401() {
        String raw = "raw-token";
        var stored = RefreshToken.builder()
                .tokenHash(RefreshTokenService.hash(raw))
                .username("alice")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();
        when(repo.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.validateAndRotate(raw))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void validateAndRotate_expiredToken_throws401() {
        String raw = "raw-token";
        var stored = RefreshToken.builder()
                .tokenHash(RefreshTokenService.hash(raw))
                .username("alice")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        when(repo.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.validateAndRotate(raw))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ── revokeToken ───────────────────────────────────────────────────────────

    @Test
    void revokeToken_marksTokenRevoked() {
        String raw = "raw-token";
        var stored = RefreshToken.builder()
                .tokenHash(RefreshTokenService.hash(raw))
                .username("alice")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        when(repo.findByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(stored));

        service.revokeToken(raw);

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void revokeToken_unknownToken_doesNotThrow() {
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatCode(() -> service.revokeToken("unknown")).doesNotThrowAnyException();
    }

    // ── revokeAllForUser ──────────────────────────────────────────────────────

    @Test
    void revokeAllForUser_callsRepository() {
        service.revokeAllForUser("alice");

        verify(repo).revokeAllByUsername("alice");
    }
}
