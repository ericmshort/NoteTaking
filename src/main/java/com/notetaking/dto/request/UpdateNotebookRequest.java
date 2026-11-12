package com.notetaking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNotebookRequest {

    @NotBlank @Size(max = 100)
    private String name;
}
