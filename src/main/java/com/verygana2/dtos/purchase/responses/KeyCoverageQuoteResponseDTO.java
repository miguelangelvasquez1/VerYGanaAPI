package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Límites de una compra para pagar con llaves (Contrato B 7.9). Trae solo lo que el
 * cliente no puede saber por su cuenta; el resto de lo que debe mostrar antes de
 * confirmar se deriva de aquí, para las llaves {@code n} que el usuario elija:
 *
 * <pre>
 * dinero a pagar                     = totalCents − n × keyValueCents
 * dinero a pagar con cobertura máxima = totalCents − maxApplicableKeys × keyValueCents
 * llaves que le faltan               = max(0, maxApplicableKeys − availableKeys)
 * </pre>
 */
public record KeyCoverageQuoteResponseDTO(
        /** Precio final total de la compra. */
        long totalCents,
        /** Llaves que el usuario puede gastar hoy (las vencidas no cuentan). */
        long availableKeys,
        /** Tope de la compra: máximo de llaves que admite, sumando el tope de cada unidad. */
        long maxApplicableKeys,
        /**
         * Máximo que el usuario puede aplicar ahora: el menor entre el tope, su saldo y el
         * respaldo disponible. Es el límite superior de lo que POST /purchases/buy acepta.
         */
        long usableKeys,
        /** Valor de una llave en centavos. */
        long keyValueCents,
        /**
         * false si el respaldo promocional disponible hoy es lo que limita
         * {@code usableKeys}: el usuario tendría tope y saldo para más.
         */
        boolean backingSufficient,
        List<Item> items) {

    public record Item(
            Long productId,
            /**
             * Tope de cobertura con llaves del producto, en porcentaje. Si una promoción
             * especial no alcanza para todas las unidades pedidas, es el ordinario.
             */
            int maxKeysPct,
            /**
             * Hasta cuándo rige la promoción especial que da ese tope; null si es el
             * ordinario. No es un derecho para compras futuras.
             */
            ZonedDateTime promotionEndsAt) {
    }
}
