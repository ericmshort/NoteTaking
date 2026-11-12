package com.notetaking.controller;

import com.notetaking.dto.request.LoginRequest;
import com.notetaking.dto.request.LogoutRequest;
import com.notetaking.dto.request.RefreshRequest;
import com.notetaking.dto.request.RegisterRequest;
import com.notetaking.dto.response.AuthResponse;
import com.notetaking.security.JwtTokenProvider;
import com.notetaking.service.AuthService;
import com.notetaking.service.RefreshTokenService;
import com.notetaking.service.TokenRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;
    private final TokenRevocationService revocationService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.getRefreshToken());
    }

    /** Revoke the caller's access token and optionally their refresh token (full logout). */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request,
                       @RequestBody(required = false) LogoutRequest body) {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var token = header.substring(7);
            if (tokenProvider.validateToken(token)) {
                revocationService.revokeToken(
                        tokenProvider.getJtiFromToken(token),
                        tokenProvider.getExpirationFromToken(token));
            }
        }
        if (body != null && body.getRefreshToken() != null) {
            refreshTokenService.revokeToken(body.getRefreshToken());
        }
    }
}
