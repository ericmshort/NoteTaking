package com.notetaking.dto.request;

import com.notetaking.validation.NullOrNotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNoteRequest {

    @NullOrNotBlank
    @Size(max = 255)
    private String title;

    private String content;

    @Positive
    private Long notebookId;

    /**
     * Pass a team ID to share with that team, or null / omit to make private.
     * Use the sentinel value -1 to explicitly clear sharing (make private).
     */
    @Min(-1)
    private Long sharedWithTeamId;

    private Boolean archived;
}
