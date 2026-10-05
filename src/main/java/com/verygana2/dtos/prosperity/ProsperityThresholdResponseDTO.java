package com.verygana2.dtos.prosperity;

import java.time.ZonedDateTime;

/**
 * Un Umbral de Prosperidad con su inversión de origen (Contrato B 5.7: trazabilidad individual).
 *
 * {@code reversed}/{@code reversedAt} no son columnas del Umbral (es inmutable): se derivan
 * de su asiento THRESHOLD_REVERSAL en el libro.
 */
public record ProsperityThresholdResponseDTO(
        Long id,
        Long investmentId,
        long investmentNetCents,
        int multiplier,
        long generatedCents,
        int planVersion,
        ZonedDateTime validatedAt,
        boolean reversed,
        ZonedDateTime reversedAt) {
}
