package com.notetaking.dto.response;

import com.notetaking.entity.Notebook;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class NotebookResponse {
    private Long id;
    private String name;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NotebookResponse from(Notebook nb) {
        return NotebookResponse.builder()
                .id(nb.getId())
                .name(nb.getName())
                .ownerId(nb.getOwner().getId())
                .createdAt(nb.getCreatedAt())
                .updatedAt(nb.getUpdatedAt())
                .build();
    }
}
