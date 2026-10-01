package com.verygana2.dtos.prosperity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reversión del Umbral de una Inversión Computable anulada/reembolsada (Contrato B 10.20). */
public record ProsperityReversalRequestDTO(
        @NotBlank @Size(max = 500) String cause,
        @Size(max = 500) String supportRef) {
}
