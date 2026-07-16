package com.verygana2.dtos.user.commercial.onboarding;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContractRejectRequestDTO {
    @NotBlank(message = "El motivo del rechazo es requerido")
    private String reason;
}
