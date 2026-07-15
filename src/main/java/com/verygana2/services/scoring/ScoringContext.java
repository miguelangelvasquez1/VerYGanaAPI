package com.verygana2.services.scoring;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import com.verygana2.models.enums.TargetGender;

/**
 * Perfil del consumidor necesario para puntuar candidatos (anuncios, campañas, etc.)
 * frente a un {@code TargetAudience}. Inmutable: se construye una vez por ciclo de selección.
 */
public record ScoringContext(
        Long consumerId,
        Integer consumerAge,
        TargetGender consumerGender,
        Set<Long> consumerCategoryIds,
        /** entityId → momento de la última interacción del consumidor con esa entidad. Ausente = nunca. */
        Map<Long, ZonedDateTime> lastInteractionAt,
        ZonedDateTime now
) {}
