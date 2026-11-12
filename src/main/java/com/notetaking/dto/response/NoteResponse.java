package com.notetaking.dto.response;

import com.notetaking.entity.Note;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class NoteResponse {
    private Long id;
    private String title;
    private String content;
    private Long ownerId;
    private Long notebookId;
    private Long sharedWithTeamId;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoteResponse from(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .ownerId(note.getOwner().getId())
                .notebookId(note.getNotebook() != null ? note.getNotebook().getId() : null)
                .sharedWithTeamId(note.getSharedWithTeam() != null ? note.getSharedWithTeam().getId() : null)
                .archived(note.isArchived())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
