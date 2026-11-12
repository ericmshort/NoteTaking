package com.notetaking.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNoteRequest {

    @Size(max = 255)
    private String title;

    private String content;

    private Long notebookId;

    /**
     * Pass a team ID to share with that team, or null / omit to make private.
     * Use the sentinel value -1 to explicitly clear sharing (make private).
     */
    private Long sharedWithTeamId;

    private Boolean archived;
}
