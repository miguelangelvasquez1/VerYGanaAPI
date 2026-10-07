package com.verygana2.dtos.promotions;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus;

/**
 * Lo que el empresario debe conocer antes de incorporar su oferta a una promoción
 * especial (Contrato B 6.22.8). Mientras la oferta está pendiente, los valores económicos
 * son los vigentes; una vez aceptada, son los que aceptó.
 */
public record KeyCoveragePromotionOfferResponseDTO(
        Long offerId,
        KeyCoveragePromotionOfferStatus status,
        ZonedDateTime respondedAt,

        Long promotionId,
        String promotionName,
        KeyCoveragePromotionStatus promotionStatus,
        ZonedDateTime startsAt,
        ZonedDateTime endsAt,
        String termsText,

        Long productId,
        String productName,

        /** Precio de la oferta. La promoción no lo cambia ni exige descuento del empresario. */
        long priceCents,
        /** Tope ordinario de cobertura con llaves del producto, sin promoción. */
        int ordinaryKeysPct,
        /** Tope de cobertura con llaves bajo la promoción. */
        int promotionKeysPct,
        /** Máximo del precio que un usuario puede cubrir con llaves bajo la promoción. */
        long maxKeysCoverageCents,
        /** Dinero mínimo a cargo del usuario si usa toda la cobertura. */
        long minUserCashCents,
        int commissionPct,
        /**
         * Comisión estimada sobre el precio completo: las llaves no reducen la Venta
         * Computable. La definitiva puede ser menor si el Saldo de Prosperidad absorbe la venta.
         */
        long estimatedCommissionCents,
        /** Valor reconocido al empresario: el precio completo menos la comisión estimada. */
        long estimatedNetToCommercialCents) {
}
