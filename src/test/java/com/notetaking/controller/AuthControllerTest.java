package com.notetaking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notetaking.config.SecurityConfig;
import com.notetaking.dto.request.LoginRequest;
import com.notetaking.dto.request.RegisterRequest;
import com.notetaking.dto.response.AuthResponse;
import com.notetaking.dto.response.UserResponse;
import com.notetaking.service.AuthService;
import com.notetaking.service.RefreshTokenService;
import com.notetaking.service.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean com.notetaking.security.JwtTokenProvider tokenProvider;
    @MockBean com.notetaking.security.UserDetailsServiceImpl userDetailsService;
    @MockBean com.notetaking.repository.UserRepository userRepository;
    @MockBean TokenRevocationService revocationService;
    @MockBean RefreshTokenService refreshTokenService;

    private AuthResponse stubResponse() {
        return AuthResponse.builder()
                .accessToken("test-token")
                .tokenType("Bearer")
                .refreshToken("test-refresh-token")
                .user(UserResponse.builder()
                        .id(1L).username("alice").email("alice@example.com")
                        .createdAt(LocalDateTime.now()).build())
                .build();
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        when(authService.register(any())).thenReturn(stubResponse());

        var req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.user.username").value("alice"));
    }

    @Test
    void register_missingUsername_returns400() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validRequest_returns200() throws Exception {
        when(authService.login(any())).thenReturn(stubResponse());

        var req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}
