package com.verygana2.dtos.pet;

import java.time.ZonedDateTime;

/**
 * Rendimiento de un producto que un comercial metió al catálogo del juego.
 *
 * Solo mide VENTAS, no exposición: el juego no manda eventos de "se mostró en la
 * tienda", así que no hay impresiones y por tanto tampoco tasa de conversión. Es
 * importante no presentarlo como si midiera alcance.
 *
 * @param unitsSold      unidades vendidas
 * @param keysSpent      llaves gastadas por los jugadores en este producto
 * @param revenueCents   esas llaves en centavos de COP
 * @param uniqueBuyers   jugadores distintos que lo compraron
 * @param repeatBuyers   de esos, cuántos compraron más de una vez
 */
public record PetProductMetricsDTO(
        Long catalogItemId,
        Integer externalId,
        String productName,
        Integer priceKeys,
        Boolean active,
        long unitsSold,
        long keysSpent,
        long revenueCents,
        long uniqueBuyers,
        long repeatBuyers,
        ZonedDateTime firstSale,
        ZonedDateTime lastSale
) {}
