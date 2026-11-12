package com.notetaking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notetaking.dto.request.LoginRequest;
import com.notetaking.dto.request.LogoutRequest;
import com.notetaking.dto.request.RefreshRequest;
import com.notetaking.dto.request.RegisterRequest;
import com.notetaking.dto.response.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Full-stack integration tests: HTTP → Security → Controller → Service → H2.
 * No mocks — every layer exercises real code paths.
 */
@SpringBootTest(properties = {
        "ADMIN_USERNAME=integrationAdmin",
        "ADMIN_PASSWORD=adminPass1!",
        "app.security.max-failed-attempts=3"   // low threshold so lockout tests don't need 10 attempts
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── helpers ──────────────────────────────────────────────────────────────

    private AuthResponse register(String username) throws Exception {
        var req = new RegisterRequest();
        req.setUsername(username);
        req.setEmail(username + "@example.com");
        req.setPassword("password123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private AuthResponse login(String username, String password) throws Exception {
        var req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_returnsTokensAndUser() throws Exception {
        var auth = register("alice");
        assertThat(auth.getAccessToken()).isNotBlank();
        assertThat(auth.getRefreshToken()).isNotBlank();
        assertThat(auth.getUser().getUsername()).isEqualTo("alice");
        assertThat(auth.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        register("alice");
        var req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("other@example.com");
        req.setPassword("password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokens() throws Exception {
        register("bob");
        var auth = login("bob", "password123");
        assertThat(auth.getAccessToken()).isNotBlank();
        assertThat(auth.getRefreshToken()).isNotBlank();
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        register("carol");
        var req = new LoginRequest();
        req.setUsername("carol");
        req.setPassword("wrong");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── protected resource ───────────────────────────────────────────────────

    @Test
    void protectedEndpoint_withToken_returns200() throws Exception {
        var auth = register("dave");
        mockMvc.perform(get("/api/v1/notes")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notes"))
                .andExpect(status().isUnauthorized());
    }

    // ── refresh token ────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_returnsNewTokens() throws Exception {
        var auth = register("eve");
        var req = new RefreshRequest();
        req.setRefreshToken(auth.getRefreshToken());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        var refreshed = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken())
                .isNotBlank()
                .isNotEqualTo(auth.getRefreshToken()); // rotated
    }

    @Test
    void refresh_tokenUsedTwice_returns401() throws Exception {
        var auth = register("frank");
        var req = new RefreshRequest();
        req.setRefreshToken(auth.getRefreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        // second use — token was rotated, old value is now revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_thenAccessToken_returns401() throws Exception {
        var auth = register("grace");
        var logoutBody = new LogoutRequest();
        logoutBody.setRefreshToken(auth.getRefreshToken());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + auth.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutBody)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notes")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_thenRefreshToken_returns401() throws Exception {
        var auth = register("heidi");
        var logoutBody = new LogoutRequest();
        logoutBody.setRefreshToken(auth.getRefreshToken());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + auth.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutBody)))
                .andExpect(status().isNoContent());

        var req = new RefreshRequest();
        req.setRefreshToken(auth.getRefreshToken());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── account lockout ──────────────────────────────────────────────────────

    @Test
    void accountLockout_afterMaxFailedAttempts_returns401WithLockedMessage() throws Exception {
        register("kira");
        var badReq = new LoginRequest();
        badReq.setUsername("kira");
        badReq.setPassword("wrongpassword");
        var payload = objectMapper.writeValueAsString(badReq);

        // 3 failed attempts (matches app.security.max-failed-attempts=3 override)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized());
        }

        // 4th attempt — account is now locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("locked")));
    }

    // ── admin role ───────────────────────────────────────────────────────────

    @Test
    void adminEndpoint_withAdminUser_returns204() throws Exception {
        register("ivan");
        var adminAuth = login("integrationAdmin", "adminPass1!");
        mockMvc.perform(delete("/api/v1/admin/users/ivan/tokens")
                        .header("Authorization", "Bearer " + adminAuth.getAccessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminEndpoint_withRegularUser_returns403() throws Exception {
        register("judy");
        var auth = login("judy", "password123");
        mockMvc.perform(delete("/api/v1/admin/users/judy/tokens")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isForbidden());
    }
}
