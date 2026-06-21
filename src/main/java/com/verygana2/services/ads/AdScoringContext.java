package com.verygana2.services.ads;

import com.verygana2.models.enums.TargetGender;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Datos del consumidor necesarios para el scoring de anuncios.
 * Inmutable: se construye una vez por ciclo de selección.
 */
public record AdScoringContext(
        Long consumerId,
        Integer consumerAge,
        TargetGender consumerGender,
        Set<Long> consumerCategoryIds,
        /** adId → momento de la última visualización del consumidor. Ausente = nunca visto. */
        Map<Long, ZonedDateTime> lastViewedAtByAdId,
        ZonedDateTime now
) {}
