package com.verygana2.services.ads;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.verygana2.models.ads.Ad;
import com.verygana2.services.scoring.EntityScorer;
import com.verygana2.services.scoring.ScoringContext;
import com.verygana2.services.scoring.ScoringFactor;
import com.verygana2.services.scoring.TargetAudienceScoringFactors;

/**
 * Selecciona el mejor anuncio elegible para un consumidor usando scoring ponderado.
 *
 * <p>Separación de responsabilidades:
 * <ul>
 *   <li><b>Elegibilidad</b> (hard filters) → resuelta en {@code AdRepository}</li>
 *   <li><b>Preferencia</b> (scoring) → resuelta aquí, delegando en {@link EntityScorer}
 *       (ver {@code CampaignScorer} para el análogo de campañas — comparten toda la lógica
 *       de coincidencia de {@code TargetAudience} vía {@link TargetAudienceScoringFactors})</li>
 * </ul>
 */
@Component
public class AdScorer {

    private final EntityScorer<Ad> scorer;

    public AdScorer(AdScoringConfig config) {
        List<ScoringFactor<Ad>> factors = List.of(
            TargetAudienceScoringFactors.categoryMatch(Ad::getTargetAudience, config.getCategoryMatch()),
            TargetAudienceScoringFactors.ageMatch(Ad::getTargetAudience, config.getAgeMatch()),
            TargetAudienceScoringFactors.genderMatch(Ad::getTargetAudience, config.getGenderMatch()),
            opportunityRatio(config.getOpportunityRatio()),
            TargetAudienceScoringFactors.recencyPenalty(
                Ad::getId, config.getRecencyPenalty(), config.getRecencyDecayWindowMinutes())
        );
        this.scorer = new EntityScorer<>(factors);
    }

    /**
     * Ratio de oportunidad comercial: cuánto presupuesto / likes quedan por consumir.
     * Favorece anuncios que aún necesitan mayor distribución.
     * Score ∈ [0, weight].
     */
    private static ScoringFactor<Ad> opportunityRatio(double weight) {
        return (ad, ctx) -> {
            if (ad.getMaxLikes() == null || ad.getMaxLikes() == 0) return 0.0;
            double ratio = (double) ad.getRemainingLikes() / ad.getMaxLikes();
            return weight * ratio;
        };
    }

    /**
     * Selecciona el mejor anuncio de la lista de candidatos elegibles.
     *
     * <p>Criterios de desempate (en orden): mayor score, menor recencia de visualización
     * (nunca visto = prioridad máxima), anuncio más reciente (mayor createdAt).
     */
    public Optional<Ad> selectBest(List<Ad> candidates, ScoringContext ctx) {
        return scorer.selectBest(candidates, ctx, Ad::getId, Ad::getCreatedAt);
    }

    /** Visibilidad de paquete para permitir pruebas unitarias directas. */
    double computeScore(Ad ad, ScoringContext ctx) {
        return scorer.computeScore(ad, ctx);
    }
}
