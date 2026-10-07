package com.verygana2.dtos.promotions;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus;

/** Vista de administración de una promoción especial, con su consumo y sus ofertas. */
public record KeyCoveragePromotionResponseDTO(
        Long id,
        String name,
        int coveragePct,
        ZonedDateTime startsAt,
        ZonedDateTime endsAt,
        KeyCoveragePromotionStatus status,
        int configVersion,
        String termsText,
        long budgetCents,
        /** Comprometido por compras con pago en curso. */
        long reservedCents,
        /** Usado por compras ya aprobadas. */
        long consumedCents,
        long availableBudgetCents,
        Long maxExcessCentsPerUser,
        /** Parte de la garantía que tuvo que reponer KEYS_RESERVE. */
        long guaranteeDrawnCents,
        ZonedDateTime guaranteeReturnedAt,
        ZonedDateTime authorizedAt,
        List<KeyCoveragePromotionOfferResponseDTO> offers) {
}
