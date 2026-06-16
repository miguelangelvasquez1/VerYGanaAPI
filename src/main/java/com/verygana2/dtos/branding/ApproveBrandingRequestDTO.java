package com.verygana2.dtos.branding;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApproveBrandingRequestDTO {

    @NotNull(message = "Designer ID is required")
    private Long designerUserId;

    @Size(max = 1000)
    private String adminNotes;
}
