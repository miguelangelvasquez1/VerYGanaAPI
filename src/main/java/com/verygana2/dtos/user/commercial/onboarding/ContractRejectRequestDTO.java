package com.verygana2.dtos.user.commercial.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContractRejectRequestDTO {
    @NotBlank(message = "El motivo del rechazo es requerido")
    private String reason;

    @NotNull(message = "Debe indicar si el rechazo es por documentos")
    private Boolean documentsIssue;
}
