package com.verygana2.dtos.product.responses;

import java.time.ZonedDateTime;

/**
 * Promoción especial de cobertura con llaves vigente para un producto (Contrato B
 * 7.9.2): mientras dure, admite más llaves que su tope ordinario.
 *
 * Es lo que el catálogo anuncia. El tope que de verdad le rige a cada usuario lo da
 * POST /purchases/quote: depende del presupuesto que le quede a la promoción y del
 * límite personal del usuario.
 */
public record ProductKeyPromotionDTO(
        /** Tope de cobertura con llaves bajo la promoción, en porcentaje (51 a 70). */
        int keysPct,
        /** Máximo de llaves para pagar una unidad bajo la promoción. */
        long maxKeysAllowed,
        /** Dinero mínimo, en centavos, a pagar por una unidad usando todas esas llaves. */
        long minCashCents,
        /** Hasta cuándo rige. No es un derecho para compras futuras. */
        ZonedDateTime endsAt) {
}
