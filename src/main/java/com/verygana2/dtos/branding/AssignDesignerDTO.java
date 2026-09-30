package com.verygana2.dtos.branding;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data 
public class AssignDesignerDTO {
    @NotNull (message = "designer public id is required")
    private UUID designerPublicId;
}
