package com.notetaking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateNoteRequest {

    @NotBlank @Size(max = 255)
    private String title;

    private String content;

    /** Optional: place the note in this notebook (must be owned by the caller). */
    private Long notebookId;

    /** Optional: share this note with a team the caller belongs to. */
    private Long sharedWithTeamId;
}
