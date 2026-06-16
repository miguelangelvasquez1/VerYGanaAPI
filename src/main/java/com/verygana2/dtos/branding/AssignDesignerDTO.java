package com.verygana2.dtos.branding;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignDesignerDTO {

    @NotNull(message = "Designer user ID is required")
    private Long designerUserId;
}
