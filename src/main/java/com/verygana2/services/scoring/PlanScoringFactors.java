package com.verygana2.services.scoring;

import java.math.BigDecimal;
import java.util.function.Function;

import com.verygana2.models.userDetails.CommercialDetails;

/**
 * Fábricas de {@link ScoringFactor} basadas en el plan del comercial dueño de la entidad
 * (Ad, Campaign, ...). Hoy solo implementa el boost de prioridad en visibilidad
 * (feature {@code VISIBILITY_BOOST}, ver {@code PlanDataInitializer}).
 */
public final class PlanScoringFactors {

    private PlanScoringFactors() {}

    /**
     * Boost de visibilidad configurado en el plan del comercial dueño de la entidad.
     * Score ∈ [0, weight], proporcional al % de boost del plan (0-100).
     */
    public static <T> ScoringFactor<T> visibilityBoost(Function<T, CommercialDetails> commercialExtractor, double weight) {
        return (entity, ctx) -> {
            CommercialDetails commercial = commercialExtractor.apply(entity);
            if (commercial == null || commercial.getCurrentPlan() == null) return 0.0;

            BigDecimal boostPct = commercial.getCurrentPlan()
                    .getDecimalFeature("VISIBILITY_BOOST", BigDecimal.ZERO);
            return weight * (boostPct.doubleValue() / 100.0);
        };
    }
}
