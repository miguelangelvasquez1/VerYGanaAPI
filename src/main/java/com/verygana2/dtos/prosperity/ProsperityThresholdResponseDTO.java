package com.verygana2.dtos.prosperity;

import java.time.ZonedDateTime;

/** Un Umbral de Prosperidad con su inversión de origen (Contrato B 5.7: trazabilidad individual). */
public record ProsperityThresholdResponseDTO(
        Long id,
        Long investmentId,
        long investmentNetCents,
        int multiplier,
        long generatedCents,
        int planVersion,
        ZonedDateTime validatedAt) {
}
