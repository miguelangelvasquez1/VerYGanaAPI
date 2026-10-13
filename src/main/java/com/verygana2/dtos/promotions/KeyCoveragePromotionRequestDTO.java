package com.verygana2.dtos.promotions;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Condiciones de una promoción especial de cobertura con llaves, mientras es borrador. */
public record KeyCoveragePromotionRequestDTO(
        @NotBlank @Size(max = 150) String name,
        /** Por encima del 50 % ordinario y nunca más del 70 % (Contrato B 6.22.8). */
        @NotNull @Min(51) @Max(70) Integer coveragePct,
        @NotNull ZonedDateTime startsAt,
        /** Obligatoria: una promoción especial no puede ser permanente. */
        @NotNull ZonedDateTime endsAt,
        /** Tope, en centavos, de lo que los usuarios pueden cubrir con llaves por encima del 50 % ordinario. */
        @NotNull @Positive Long budgetCents,
        /** Tope del mismo exceso por usuario durante toda la promoción. Null = sin tope. */
        @Positive Long maxExcessCentsPerUser,
        @Size(max = 5000) String termsText) {
}
