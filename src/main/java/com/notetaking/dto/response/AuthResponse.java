package com.notetaking.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private String refreshToken;
    private UserResponse user;
}
