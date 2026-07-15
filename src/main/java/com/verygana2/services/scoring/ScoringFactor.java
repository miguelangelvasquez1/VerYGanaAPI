package com.verygana2.services.scoring;

/**
 * Un componente de puntuación evaluable sobre una entidad candidata para un
 * {@link ScoringContext} dado. La suma de todos los factores de un {@link EntityScorer}
 * produce el score total de la entidad.
 */
@FunctionalInterface
public interface ScoringFactor<T> {
    double apply(T entity, ScoringContext ctx);
}
