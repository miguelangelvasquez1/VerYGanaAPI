package com.verygana2.dtos.pet;

import java.time.LocalDate;

/**
 * Un día de la gráfica de evolución de ventas en el juego de mascotas.
 *
 * Los días sin ventas también vienen, con todo en cero: si se omitieran, la gráfica
 * uniría dos días distantes con una línea recta y aparentaría una tendencia continua
 * donde en realidad no hubo actividad.
 */
public record PetSalesPointDTO(
        LocalDate date,
        long unitsSold,
        long keysSpent,
        long revenueCents
) {}
