package com.notetaking.service;

import com.notetaking.entity.User;
import com.notetaking.exception.NotFoundException;
import com.notetaking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock TokenRevocationService revocationService;
    @Mock RefreshTokenService refreshTokenService;
    @InjectMocks AdminService service;

    // ── setUserRole ───────────────────────────────────────────────────────────

    @Test
    void setUserRole_changesRole_andRevokesAllSessions() {
        var user = User.builder().username("alice").role(User.Role.USER).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.setUserRole("alice", User.Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(User.Role.ADMIN);
        verify(revocationService).revokeAllTokensForUser("alice");
        verify(refreshTokenService).revokeAllForUser("alice");
    }

    @Test
    void setUserRole_unknownUser_throwsNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserRole("ghost", User.Role.ADMIN))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(revocationService, refreshTokenService);
    }

    // ── revokeAllTokensForUser ────────────────────────────────────────────────

    @Test
    void revokeAllTokensForUser_revokesAllSessions() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        service.revokeAllTokensForUser("alice");

        verify(revocationService).revokeAllTokensForUser("alice");
        verify(refreshTokenService).revokeAllForUser("alice");
    }

    @Test
    void revokeAllTokensForUser_unknownUser_throwsNotFoundException() {
        when(userRepository.existsByUsername("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.revokeAllTokensForUser("ghost"))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(revocationService, refreshTokenService);
    }
}
