package com.verygana2.dtos.prosperity;

import java.time.ZonedDateTime;

/** Un asiento del Libro Mayor de Prosperidad, con saldo antes/después (Contrato B 10.5, 10.23). */
public record ProsperityMovementResponseDTO(
        Long id,
        long sequence,
        String type,
        long amountCents,
        long balanceBeforeCents,
        long balanceAfterCents,
        String originType,
        String originId,
        Long thresholdId,
        Long relatedEntryId,
        Long saleAmountCents,
        Long uncoveredCents,
        ZonedDateTime effectiveAt,
        String cause,
        String supportRef,
        String performedBy) {
}
