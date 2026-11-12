package com.notetaking.dto.response;

import com.notetaking.entity.TeamMembership;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class TeamMemberResponse {
    private UserResponse user;
    private String role;
    private LocalDateTime joinedAt;

    public static TeamMemberResponse from(TeamMembership m) {
        return TeamMemberResponse.builder()
                .user(UserResponse.from(m.getUser()))
                .role(m.getRole().name())
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
