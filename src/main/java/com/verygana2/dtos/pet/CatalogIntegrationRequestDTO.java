package com.verygana2.dtos.pet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogIntegrationRequestDTO(

        @NotBlank(message = "El nombre del producto es requerido")
        @Size(max = 100)
        String productName,

        @NotBlank(message = "La descripción es requerida")
        @Size(max = 500)
        String description,

        String imageObjectKey,

        @NotBlank(message = "Debe describir los efectos deseados en el juego")
        @Size(max = 1000)
        String desiredEffects
) {}