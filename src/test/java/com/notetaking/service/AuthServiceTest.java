package com.notetaking.service;

import com.notetaking.dto.request.LoginRequest;
import com.notetaking.dto.request.RegisterRequest;
import com.notetaking.entity.User;
import com.notetaking.exception.AppException;
import com.notetaking.repository.UserRepository;
import com.notetaking.security.JwtTokenProvider;
import com.notetaking.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock AuthenticationManager authManager;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks AuthService authService;

    @Test
    void register_success() {
        var req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(tokenProvider.generateToken("alice")).thenReturn("jwt-token");
        var fakeRefresh = com.notetaking.entity.RefreshToken.builder()
                .token("refresh-token").username("alice")
                .expiresAt(java.time.Instant.now().plusSeconds(604800)).build();
        when(refreshTokenService.createToken("alice")).thenReturn(fakeRefresh);

        var result = authService.register(req);

        assertThat(result.getAccessToken()).isEqualTo("jwt-token");
        assertThat(result.getUser().getUsername()).isEqualTo("alice");
    }

    @Test
    void register_duplicateUsername_throws409() {
        var req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void login_success() {
        var req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("password123");

        var user = User.builder().id(1L).username("alice").email("a@a.com").passwordHash("h").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken("alice")).thenReturn("jwt-token");
        var fakeRefresh = com.notetaking.entity.RefreshToken.builder()
                .token("refresh-token").username("alice")
                .expiresAt(java.time.Instant.now().plusSeconds(604800)).build();
        when(refreshTokenService.createToken("alice")).thenReturn(fakeRefresh);

        var result = authService.login(req);

        assertThat(result.getAccessToken()).isEqualTo("jwt-token");
        verify(authManager).authenticate(any());
    }
}
