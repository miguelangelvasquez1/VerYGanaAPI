package com.verygana2.services.ads;

import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.TargetGender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Selecciona el mejor anuncio elegible para un consumidor usando scoring ponderado.
 *
 * <p>Separación de responsabilidades:
 * <ul>
 *   <li><b>Elegibilidad</b> (hard filters) → resuelta en {@code AdRepository}</li>
 *   <li><b>Preferencia</b> (scoring) → resuelta aquí</li>
 * </ul>
 *
 * <p>Para extender el sistema con nuevas señales de comportamiento, implementar
 * {@link ScoringFactor} y agregar la instancia a la lista {@code factors}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdScorer {

    private final AdScoringConfig config;

    // ─── ScoringFactor ────────────────────────────────────────────────────────

    /**
     * Función de puntuación para un factor individual.
     * Puede retornar valores negativos (penalizaciones).
     */
    @FunctionalInterface
    public interface ScoringFactor {
        double apply(Ad ad, AdScoringContext ctx, AdScoringConfig cfg);
    }

    // ─── Implementaciones de cada factor ─────────────────────────────────────

    /**
     * Similitud de Jaccard entre las categorías del anuncio y las preferencias del consumidor.
     * Score ∈ [0, cfg.categoryMatch].
     */
    static final ScoringFactor CATEGORY_MATCH = (ad, ctx, cfg) -> {
        if (ad.getTargetAudience() == null || ad.getTargetAudience().getCategories() == null || ad.getTargetAudience().getCategories().isEmpty()) return 0.0;
        Set<Long> adCatIds = ad.getTargetAudience().getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toSet());
        Set<Long> intersection = new HashSet<>(adCatIds);
        intersection.retainAll(ctx.consumerCategoryIds());
        if (intersection.isEmpty()) return 0.0;
        Set<Long> union = new HashSet<>(adCatIds);
        union.addAll(ctx.consumerCategoryIds());
        return cfg.getCategoryMatch() * (double) intersection.size() / union.size();
    };

    /**
     * Coincidencia del rango de edad del anuncio con la edad del consumidor.
     * Si la edad del consumidor es desconocida (null), se concede el score completo.
     * Score ∈ {0, cfg.ageMatch}.
     */
    static final ScoringFactor AGE_MATCH = (ad, ctx, cfg) -> {
        Integer age = ctx.consumerAge();
        if (age == null) return cfg.getAgeMatch();
        boolean minOk = ad.getTargetAudience() == null || ad.getTargetAudience().getMinAge() == null || ad.getTargetAudience().getMinAge() <= age;
        boolean maxOk = ad.getTargetAudience() == null || ad.getTargetAudience().getMaxAge() == null || ad.getTargetAudience().getMaxAge() >= age;
        return (minOk && maxOk) ? cfg.getAgeMatch() : 0.0;
    };

    /**
     * Coincidencia del género objetivo del anuncio con el género del consumidor.
     * TargetGender.ALL o null → score completo (anuncio universal).
     * Género del consumidor desconocido → score parcial (50%).
     * Score ∈ {0, cfg.genderMatch/2, cfg.genderMatch}.
     */
    static final ScoringFactor GENDER_MATCH = (ad, ctx, cfg) -> {
        TargetGender adGender = ad.getTargetAudience() != null ? ad.getTargetAudience().getTargetGender() : null;
        if (adGender == null || adGender == TargetGender.ALL) return cfg.getGenderMatch();
        TargetGender consumer = ctx.consumerGender();
        if (consumer == null) return cfg.getGenderMatch() / 2.0;
        return consumer == adGender ? cfg.getGenderMatch() : 0.0;
    };

    /**
     * Ratio de oportunidad comercial: cuánto presupuesto / likes quedan por consumir.
     * Favorece anuncios que aún necesitan mayor distribución.
     * Score ∈ [0, cfg.opportunityRatio].
     */
    static final ScoringFactor OPPORTUNITY_RATIO = (ad, ctx, cfg) -> {
        if (ad.getMaxLikes() == null || ad.getMaxLikes() == 0) return 0.0;
        double ratio = (double) ad.getRemainingLikes() / ad.getMaxLikes();
        return cfg.getOpportunityRatio() * ratio;
    };

    /**
     * Penalización por repetición reciente: reduce el score de anuncios vistos recientemente.
     * Decae linealmente hasta 0 después de {@code cfg.recencyDecayWindowMinutes}.
     * Score ∈ [-cfg.recencyPenalty, 0].
     */
    static final ScoringFactor RECENCY_PENALTY = (ad, ctx, cfg) -> {
        ZonedDateTime lastViewed = ctx.lastViewedAtByAdId().get(ad.getId());
        if (lastViewed == null) return 0.0;
        long minutesSince = Duration.between(lastViewed, ctx.now()).toMinutes();
        if (minutesSince < 0) return 0.0;
        double decay = Math.max(0.0, 1.0 - (double) minutesSince / cfg.getRecencyDecayWindowMinutes());
        return -cfg.getRecencyPenalty() * decay;
    };

    // ─── Constantes auxiliares ────────────────────────────────────────────────

    /** Sustituto de "nunca visto": la fecha más antigua representable. */
    static final ZonedDateTime NEVER_VIEWED = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
            .minusYears(1_000_000);

    // ─── Pipeline de factores ─────────────────────────────────────────────────

    private final List<ScoringFactor> factors = List.of(
            CATEGORY_MATCH,
            AGE_MATCH,
            GENDER_MATCH,
            OPPORTUNITY_RATIO,
            RECENCY_PENALTY
    );

    /**
     * Selecciona el mejor anuncio de la lista de candidatos elegibles.
     *
     * <p>Criterios de desempate (en orden):
     * <ol>
     *   <li>Mayor score</li>
     *   <li>Menor recencia de visualización (nunca visto = prioridad máxima)</li>
     *   <li>Anuncio más reciente (mayor createdAt)</li>
     * </ol>
     */
    public Optional<Ad> selectBest(List<Ad> candidates, AdScoringContext ctx) {
        if (candidates.isEmpty()) return Optional.empty();

        List<ScoredAd> scored = candidates.stream()
                .map(ad -> {
                    double score = computeScore(ad, ctx);
                    log.debug("Ad {} score={} for consumer {}", ad.getId(), String.format("%.2f", score), ctx.consumerId());
                    return new ScoredAd(ad, score);
                })
                .collect(Collectors.toList());

        Comparator<ScoredAd> comparator = Comparator
                .<ScoredAd>comparingDouble(ScoredAd::score)
                // Tiebreak 1: menos reciente gana (nunca visto = NEVER_VIEWED = prioritario con reverseOrder)
                .thenComparing(
                        (ScoredAd sa) -> ctx.lastViewedAtByAdId().getOrDefault(sa.ad().getId(), NEVER_VIEWED),
                        Comparator.<ZonedDateTime>reverseOrder()
                )
                // Tiebreak 2: anuncio más antiguo gana — evita starvation de inventario viejo
                .thenComparing((ScoredAd sa) -> sa.ad().getCreatedAt(), Comparator.<ZonedDateTime>reverseOrder());

        return scored.stream()
                .max(comparator)
                .map(ScoredAd::ad);
    }

    /**
     * Calcula el score total de un anuncio para un contexto de consumidor dado.
     * Visibilidad de paquete para permitir pruebas unitarias directas.
     */
    double computeScore(Ad ad, AdScoringContext ctx) {
        return factors.stream()
                .mapToDouble(f -> f.apply(ad, ctx, config))
                .sum();
    }

    record ScoredAd(Ad ad, double score) {}
}
