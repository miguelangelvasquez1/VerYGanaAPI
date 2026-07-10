package com.verygana2.services.scoring;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.verygana2.models.Category;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.TargetGender;

/**
 * Fábricas de {@link ScoringFactor} reutilizables para cualquier entidad que exponga un
 * {@link TargetAudience} (Ad, Campaign, ...). La lógica de coincidencia es idéntica entre
 * entidades — solo cambia cómo se extrae el {@code TargetAudience} de cada una.
 */
public final class TargetAudienceScoringFactors {

    private TargetAudienceScoringFactors() {}

    /**
     * Similitud de Jaccard entre las categorías de la entidad y las preferencias del consumidor.
     * Score ∈ [0, weight].
     */
    public static <T> ScoringFactor<T> categoryMatch(Function<T, TargetAudience> targetAudience, double weight) {
        return (entity, ctx) -> {
            TargetAudience ta = targetAudience.apply(entity);
            if (ta == null || ta.getCategories() == null || ta.getCategories().isEmpty()) return 0.0;

            Set<Long> entityCategoryIds = ta.getCategories().stream()
                    .map(Category::getId)
                    .collect(Collectors.toSet());

            Set<Long> intersection = new HashSet<>(entityCategoryIds);
            intersection.retainAll(ctx.consumerCategoryIds());
            if (intersection.isEmpty()) return 0.0;

            Set<Long> union = new HashSet<>(entityCategoryIds);
            union.addAll(ctx.consumerCategoryIds());
            return weight * (double) intersection.size() / union.size();
        };
    }

    /**
     * Coincidencia del rango de edad de la entidad con la edad del consumidor.
     * Si la edad del consumidor es desconocida (null), se concede el score completo.
     * Score ∈ {0, weight}.
     */
    public static <T> ScoringFactor<T> ageMatch(Function<T, TargetAudience> targetAudience, double weight) {
        return (entity, ctx) -> {
            Integer age = ctx.consumerAge();
            if (age == null) return weight;

            TargetAudience ta = targetAudience.apply(entity);
            boolean minOk = ta == null || ta.getMinAge() == null || ta.getMinAge() <= age;
            boolean maxOk = ta == null || ta.getMaxAge() == null || ta.getMaxAge() >= age;
            return (minOk && maxOk) ? weight : 0.0;
        };
    }

    /**
     * Coincidencia del género objetivo de la entidad con el género del consumidor.
     * TargetGender.ALL o null → score completo (entidad universal).
     * Género del consumidor desconocido → score parcial (50%).
     * Score ∈ {0, weight/2, weight}.
     */
    public static <T> ScoringFactor<T> genderMatch(Function<T, TargetAudience> targetAudience, double weight) {
        return (entity, ctx) -> {
            TargetAudience ta = targetAudience.apply(entity);
            TargetGender entityGender = ta != null ? ta.getTargetGender() : null;
            if (entityGender == null || entityGender == TargetGender.ALL) return weight;

            TargetGender consumer = ctx.consumerGender();
            if (consumer == null) return weight / 2.0;
            return consumer == entityGender ? weight : 0.0;
        };
    }

    /**
     * Penalización por interacción reciente: reduce el score de entidades vistas/jugadas
     * recientemente por el consumidor. Decae linealmente hasta 0 después de {@code decayWindowMinutes}.
     * Score ∈ [-penaltyWeight, 0].
     */
    public static <T> ScoringFactor<T> recencyPenalty(Function<T, Long> idExtractor, double penaltyWeight, long decayWindowMinutes) {
        return (entity, ctx) -> {
            ZonedDateTime last = ctx.lastInteractionAt().get(idExtractor.apply(entity));
            if (last == null) return 0.0;

            long minutesSince = Duration.between(last, ctx.now()).toMinutes();
            if (minutesSince < 0) return 0.0;

            double decay = Math.max(0.0, 1.0 - (double) minutesSince / decayWindowMinutes);
            return -penaltyWeight * decay;
        };
    }
}
