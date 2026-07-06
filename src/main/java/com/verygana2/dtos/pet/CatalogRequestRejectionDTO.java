package com.verygana2.dtos.pet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogRequestRejectionDTO(

        @NotBlank(message = "El motivo de rechazo es requerido")
        @Size(max = 500)
        String reason
) {}