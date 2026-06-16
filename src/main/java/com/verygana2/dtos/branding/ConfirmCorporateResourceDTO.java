package com.verygana2.dtos.branding;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmCorporateResourceDTO {

    @NotNull(message = "Resource ID is required")
    private Long resourceId;
}
