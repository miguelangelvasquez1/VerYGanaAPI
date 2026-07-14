package com.verygana2.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnlockAccountDTO {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "Code is required")
    private String code;
}
