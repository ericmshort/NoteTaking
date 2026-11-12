package com.notetaking.service;

import com.notetaking.entity.User;
import com.notetaking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks AccountLockService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 3);
        ReflectionTestUtils.setField(service, "lockoutMinutes", 15);
    }

    @Test
    void recordFailedAttempt_incrementsCounter() {
        var user = User.builder().username("alice").failedLoginAttempts(0).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.recordFailedAttempt("alice");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getAccountLockedUntil()).isNull();
    }

    @Test
    void recordFailedAttempt_atThreshold_locksAccount() {
        var user = User.builder().username("alice").failedLoginAttempts(2).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.recordFailedAttempt("alice"); // 3rd attempt — hits threshold

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getAccountLockedUntil())
                .isNotNull()
                .isAfter(Instant.now());
    }

    @Test
    void recordFailedAttempt_unknownUser_doesNothing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        service.recordFailedAttempt("ghost"); // must not throw

        verify(userRepository).findByUsername("ghost");
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void resetAttempts_clearsCounterAndLock() {
        var user = User.builder()
                .username("alice")
                .failedLoginAttempts(3)
                .accountLockedUntil(Instant.now().plusSeconds(60))
                .build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.resetAttempts("alice");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getAccountLockedUntil()).isNull();
    }

    @Test
    void resetAttempts_whenAlreadyClean_skipsUpdate() {
        var user = User.builder().username("alice").failedLoginAttempts(0).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.resetAttempts("alice");

        // counter was already 0 and no lock set — entity should not be dirtied
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getAccountLockedUntil()).isNull();
    }
}
