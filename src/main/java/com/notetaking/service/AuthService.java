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
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
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
    private final AccountLockService accountLockService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        boolean usernameTaken = userRepository.existsByUsername(req.getUsername());
        boolean emailTaken    = userRepository.existsByEmail(req.getEmail());
        if (usernameTaken || emailTaken) {
            // Generic message prevents username/email enumeration
            throw new AppException(HttpStatus.CONFLICT,
                    "An account with this username or email already exists");
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

    @Transactional(noRollbackFor = AuthenticationException.class)
    public AuthResponse login(LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        } catch (LockedException e) {
            throw e; // account is already locked — counter stays; GlobalExceptionHandler returns 401
        } catch (AuthenticationException e) {
            accountLockService.recordFailedAttempt(req.getUsername());
            throw e; // GlobalExceptionHandler.handleBadCredentials returns 401
        }
        var user = userRepository.findByUsername(req.getUsername()).orElseThrow();
        accountLockService.resetAttempts(req.getUsername());
        return buildResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        var result = refreshTokenService.validateAndRotate(rawRefreshToken);
        var user = userRepository.findByUsername(result.username())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
        return AuthResponse.builder()
                .accessToken(tokenProvider.generateToken(user.getUsername()))
                .tokenType("Bearer")
                .refreshToken(result.newRawToken())
                .user(UserResponse.from(user))
                .build();
    }

    private AuthResponse buildResponse(User user) {
        return AuthResponse.builder()
                .accessToken(tokenProvider.generateToken(user.getUsername()))
                .tokenType("Bearer")
                .refreshToken(refreshTokenService.createToken(user.getUsername()))
                .user(UserResponse.from(user))
                .build();
    }
}
