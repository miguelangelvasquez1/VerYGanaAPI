package com.verygana2.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendUnlockCodeDTO {

    @NotBlank(message = "Identifier is required")
    private String identifier;
}
