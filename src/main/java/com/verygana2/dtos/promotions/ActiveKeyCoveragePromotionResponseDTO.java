package com.verygana2.dtos.promotions;

import java.time.ZonedDateTime;

/**
 * Producto que hoy admite una cobertura con llaves superior a la ordinaria, para que el
 * catálogo lo anuncie. El tope que realmente le rige a cada usuario lo da
 * POST /purchases/quote: depende del presupuesto restante y de su límite personal.
 */
public record ActiveKeyCoveragePromotionResponseDTO(
        Long productId,
        String promotionName,
        int keysPct,
        ZonedDateTime endsAt,
        String termsText) {
}
