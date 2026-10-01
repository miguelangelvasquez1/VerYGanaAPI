package com.verygana2.dtos.pet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CatalogIntegrationRequestDTO(

        @NotBlank(message = "El nombre del producto es requerido")
        @Size(max = 100)
        String productName,

        @NotBlank(message = "La descripción es requerida")
        @Size(max = 500)
        String description,

        @NotBlank(message = "La imagen del producto es requerida")
        String imageObjectKey,

        @NotBlank(message = "Debe describir los efectos deseados en el juego")
        @Size(max = 1000)
        String desiredEffects,

        /**
         * Bolsa que el comercial reserva de su wallet, en centavos. Cada compra del ítem
         * publicado descuenta de aquí el cobro por uso; se devuelve si la solicitud se
         * rechaza. El mínimo (un uso) lo valida el servicio, que conoce la tarifa.
         */
        @NotNull(message = "El presupuesto es requerido")
        @Positive(message = "El presupuesto debe ser mayor a cero")
        Long budgetCents
) {}