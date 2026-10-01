package com.verygana2.dtos.branding;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApproveBrandingRequestDTO {

    @NotNull(message = "Designer public ID is required")
    private UUID designerPublicId;

    @Size(max = 1000)
    private String adminNotes;
}
