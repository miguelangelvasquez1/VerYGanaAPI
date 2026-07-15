package com.verygana2.services.games;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.verygana2.models.branding.Campaign;
import com.verygana2.services.scoring.EntityScorer;
import com.verygana2.services.scoring.ScoringContext;
import com.verygana2.services.scoring.ScoringFactor;
import com.verygana2.services.scoring.TargetAudienceScoringFactors;

/**
 * Selecciona la mejor campaña elegible para un consumidor, usando el mismo enfoque de
 * scoring ponderado que {@code AdScorer} usa para anuncios.
 */
@Component
public class CampaignScorer {

    private final EntityScorer<Campaign> scorer;

    public CampaignScorer(CampaignScoringConfig config) {
        List<ScoringFactor<Campaign>> factors = List.of(
            TargetAudienceScoringFactors.categoryMatch(Campaign::getTargetAudience, config.getCategoryMatch()),
            TargetAudienceScoringFactors.ageMatch(Campaign::getTargetAudience, config.getAgeMatch()),
            TargetAudienceScoringFactors.genderMatch(Campaign::getTargetAudience, config.getGenderMatch()),
            budgetOpportunity(config.getBudgetOpportunity()),
            TargetAudienceScoringFactors.recencyPenalty(
                Campaign::getId, config.getRecencyPenalty(), config.getRecencyDecayWindowMinutes())
        );
        this.scorer = new EntityScorer<>(factors);
    }

    /**
     * Ratio de oportunidad: cuánto presupuesto queda por gastar en la campaña.
     * Favorece campañas que aún necesitan mayor distribución (análogo a remainingLikes/maxLikes en Ad).
     * Score ∈ [0, weight].
     */
    private static ScoringFactor<Campaign> budgetOpportunity(double weight) {
        return (campaign, ctx) -> {
            Long budgetCents = campaign.getBudgetCents();
            if (budgetCents == null || budgetCents <= 0) return 0.0;

            long spentCents = campaign.getSpentCents() != null ? campaign.getSpentCents() : 0L;
            double remainingRatio = Math.max(0.0, (double) (budgetCents - spentCents) / budgetCents);
            return weight * remainingRatio;
        };
    }

    public Optional<Campaign> selectBest(List<Campaign> candidates, ScoringContext ctx) {
        return scorer.selectBest(candidates, ctx, Campaign::getId, Campaign::getCreatedAt);
    }

    /** Visibilidad de paquete para permitir pruebas unitarias directas. */
    double computeScore(Campaign campaign, ScoringContext ctx) {
        return scorer.computeScore(campaign, ctx);
    }
}
