package com.notetaking.service;

import com.notetaking.dto.request.LoginRequest;
import com.notetaking.dto.request.RegisterRequest;
import com.notetaking.dto.response.AuthResponse;
import com.notetaking.dto.response.UserResponse;
import com.notetaking.entity.User;
import com.notetaking.exception.AppException;
import com.notetaking.repository.UserRepository;
import com.notetaking.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authManager;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new AppException(HttpStatus.CONFLICT, "Username already taken: " + req.getUsername());
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "Email already registered: " + req.getEmail());
        }
        var user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .displayName(req.getDisplayName())
                .build();
        userRepository.save(user);
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        var user = userRepository.findByUsername(req.getUsername()).orElseThrow();
        return buildResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        var newRefreshToken = refreshTokenService.validateAndRotate(refreshTokenValue);
        var user = userRepository.findByUsername(newRefreshToken.getUsername())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
        return AuthResponse.builder()
                .accessToken(tokenProvider.generateToken(user.getUsername()))
                .tokenType("Bearer")
                .refreshToken(newRefreshToken.getToken())
                .user(UserResponse.from(user))
                .build();
    }

    private AuthResponse buildResponse(User user) {
        return AuthResponse.builder()
                .accessToken(tokenProvider.generateToken(user.getUsername()))
                .tokenType("Bearer")
                .refreshToken(refreshTokenService.createToken(user.getUsername()).getToken())
                .user(UserResponse.from(user))
                .build();
    }
}
