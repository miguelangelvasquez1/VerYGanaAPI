package com.verygana2.services.scoring;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

/**
 * Selecciona la mejor entidad candidata para un {@link ScoringContext} usando scoring
 * ponderado, calculado como la suma de una lista de {@link ScoringFactor}.
 *
 * <p>No es un bean de Spring: cada caso de uso (anuncios, campañas, ...) construye su propia
 * instancia con la lista de factores y pesos que le correspondan, típicamente desde un
 * {@code @Component} específico de esa entidad (ver {@code CampaignScorer}).
 *
 * <p>Separación de responsabilidades esperada en el llamador:
 * <ul>
 *   <li><b>Elegibilidad</b> (hard filters) → resuelta en el repositorio</li>
 *   <li><b>Preferencia</b> (scoring) → resuelta aquí</li>
 * </ul>
 */
@Slf4j
public class EntityScorer<T> {

    /** Sustituto de "nunca interactuado": la fecha más antigua representable. */
    public static final ZonedDateTime NEVER_INTERACTED =
            ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC).minusYears(1_000_000);

    private final List<ScoringFactor<T>> factors;

    public EntityScorer(List<ScoringFactor<T>> factors) {
        this.factors = List.copyOf(factors);
    }

    public double computeScore(T entity, ScoringContext ctx) {
        return factors.stream().mapToDouble(f -> f.apply(entity, ctx)).sum();
    }

    /**
     * Selecciona la mejor entidad de la lista de candidatos elegibles.
     *
     * <p>Criterios de desempate (en orden):
     * <ol>
     *   <li>Mayor score</li>
     *   <li>Menor recencia de interacción (nunca interactuado = prioridad máxima)</li>
     *   <li>Entidad más antigua (mayor createdAt) — evita starvation de inventario viejo</li>
     * </ol>
     */
    public Optional<T> selectBest(
            List<T> candidates,
            ScoringContext ctx,
            Function<T, Long> idExtractor,
            Function<T, ZonedDateTime> createdAtExtractor) {

        if (candidates.isEmpty()) return Optional.empty();

        List<Scored<T>> scored = candidates.stream()
                .map(entity -> {
                    double score = computeScore(entity, ctx);
                    log.debug("Entity {} score={} for consumer {}",
                            idExtractor.apply(entity), String.format("%.2f", score), ctx.consumerId());
                    return new Scored<>(entity, score);
                })
                .toList();

        Comparator<Scored<T>> comparator = Comparator
                .<Scored<T>>comparingDouble(Scored::score)
                .thenComparing(
                        (Scored<T> s) -> ctx.lastInteractionAt().getOrDefault(idExtractor.apply(s.entity()), NEVER_INTERACTED),
                        Comparator.<ZonedDateTime>reverseOrder())
                .thenComparing((Scored<T> s) -> createdAtExtractor.apply(s.entity()), Comparator.<ZonedDateTime>reverseOrder());

        return scored.stream().max(comparator).map(Scored::entity);
    }

    private record Scored<E>(E entity, double score) {}
}
