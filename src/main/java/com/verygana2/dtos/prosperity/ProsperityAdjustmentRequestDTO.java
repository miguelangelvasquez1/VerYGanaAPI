package com.verygana2.dtos.prosperity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Ajuste compensatorio manual del Saldo de Prosperidad (Contrato B 10.27).
 * {@code idempotencyKey} opcional: si el cliente lo envía, reintentar el mismo ajuste no lo duplica.
 */
public record ProsperityAdjustmentRequestDTO(
        @NotNull Boolean credit,
        @NotNull @Positive Long amountCents,
        @NotBlank @Size(max = 500) String cause,
        @Size(max = 500) String supportRef,
        Long relatedEntryId,
        @Size(max = 80) String idempotencyKey) {
}
