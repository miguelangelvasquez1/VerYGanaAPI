package com.verygana2.dtos.finance.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectPayoutMethodRequestDTO {

    @NotBlank(message = "El motivo de rechazo es requerido")
    @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
    private String reason;
}
