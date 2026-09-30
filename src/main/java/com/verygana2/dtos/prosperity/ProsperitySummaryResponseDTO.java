package com.verygana2.dtos.prosperity;

import java.util.List;
import java.util.UUID;

/**
 * Resumen del Saldo de Prosperidad de un comercial.
 *
 * {@code status}: ACTIVE (STANDARD, absorbe ventas), FROZEN (dejó de ser STANDARD: saldo
 * conservado sin absorber) o NOT_APPLICABLE (nunca tuvo Umbral y no es STANDARD).
 */
public record ProsperitySummaryResponseDTO(
        UUID commercialPublicId,
        String status,
        long balanceCents,
        long accumulatedThresholdCents,
        long totalAbsorbedCents,
        long totalReintegratedCents,
        List<ProsperityThresholdResponseDTO> thresholds,
        String disclaimer) {
}
