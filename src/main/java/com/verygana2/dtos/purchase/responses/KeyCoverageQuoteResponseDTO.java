package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Lo que la plataforma debe mostrar antes de confirmar una compra (Contrato B 7.9):
 * precio, tope de llaves, saldo, cobertura efectiva y dinero a pagar en cada caso.
 * Las llaves van en cantidad y el dinero en centavos, siempre en campos separados.
 */
public record KeyCoverageQuoteResponseDTO(
        /** Precio final total de la compra. */
        long totalCents,
        /** Llaves que el usuario puede gastar hoy (las vencidas no cuentan). */
        long availableKeys,
        /** Máximo de llaves que esta compra admite, sumando el tope de cada ítem. */
        long maxApplicableKeys,
        /** Llaves que el usuario pidió aplicar; si no indicó, las máximas que puede usar. */
        long requestedKeys,
        /** Llaves que se aplicarían: el menor entre tope, saldo, respaldo y lo pedido. */
        long effectiveKeys,
        /** Valor que cubren las llaves efectivas. */
        long effectiveCoverageCents,
        /** Llaves que le faltan al usuario para alcanzar la cobertura máxima. */
        long missingKeysForMaxCoverage,
        /** Dinero a pagar si compra ahora con las llaves efectivas. */
        long cashToPayCents,
        /** Dinero a pagar si llegara a completar la cobertura máxima. */
        long cashToPayAtMaxCoverageCents,
        /**
         * false si el respaldo promocional disponible hoy es menor que la cobertura que el
         * usuario podría usar por tope y saldo.
         */
        boolean backingSufficient,
        List<Item> items) {

    public record Item(
            Long productId,
            String productName,
            int quantity,
            long unitPriceCents,
            /** Tope de cobertura con llaves por unidad, en porcentaje. */
            int maxKeysPct,
            long maxKeysPerUnit,
            /** true si el tope viene de una promoción especial y no del ordinario. */
            boolean specialPromotion,
            /** Hasta cuándo rige la promoción especial. No es un derecho para compras futuras. */
            ZonedDateTime promotionEndsAt) {
    }
}
