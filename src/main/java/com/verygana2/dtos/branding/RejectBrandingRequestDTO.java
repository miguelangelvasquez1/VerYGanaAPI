package com.verygana2.dtos.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectBrandingRequestDTO {

    @NotBlank(message = "Rejection notes are required")
    @Size(max = 1000)
    private String adminNotes;
}
