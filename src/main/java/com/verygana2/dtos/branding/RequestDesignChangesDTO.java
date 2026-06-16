package com.verygana2.dtos.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequestDesignChangesDTO {

    @NotBlank(message = "Feedback is required when requesting changes")
    @Size(max = 1000, message = "Feedback must not exceed 1000 characters")
    private String designerNotes;
}
