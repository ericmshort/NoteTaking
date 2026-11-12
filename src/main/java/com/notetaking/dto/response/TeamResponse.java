package com.notetaking.dto.response;

import com.notetaking.entity.Team;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class TeamResponse {
    private Long id;
    private String name;
    private String description;
    private UserResponse createdBy;
    private LocalDateTime createdAt;

    public static TeamResponse from(Team team) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .createdBy(UserResponse.from(team.getCreatedBy()))
                .createdAt(team.getCreatedAt())
                .build();
    }
}
