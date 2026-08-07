package com.verygana2.services.surveys;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Peso del scoring de encuestas — análogo a {@code AdScoringConfig} / {@code CampaignScoringConfig}
 * pero aplicado en SQL nativo dentro de {@code SurveyRepository.findActiveSurveysRankedForUser},
 * ya que el ranking de encuestas se resuelve paginado en base de datos y no vía {@code EntityScorer}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "surveys.scoring")
public class SurveyScoringConfig {

    /**
     * Peso del boost de prioridad en visibilidad del plan del comercial (VISIBILITY_BOOST).
     * Se suma a match_score (cantidad de categorías coincidentes) como boostPct/100 * este peso.
     */
    private double visibilityBoost = 1.0;
}
