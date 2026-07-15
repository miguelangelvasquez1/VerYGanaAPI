package com.verygana2.services.games;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.models.Category;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.branding.Campaign;
import com.verygana2.services.scoring.ScoringContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests de {@link CampaignScorer}. La lógica de coincidencia de TargetAudience (categoría/edad/género)
 * y el desempate genérico ya se prueban en {@code TargetAudienceScoringFactorsTest} y
 * {@code EntityScorer} — aquí se cubre lo específico de CampaignScorer: el factor
 * {@code budgetOpportunity} y la integración end-to-end vía {@link CampaignScorer#selectBest}.
 */
@DisplayName("CampaignScorer")
class CampaignScorerTest {

    private CampaignScoringConfig config;
    private CampaignScorer scorer;

    @BeforeEach
    void setUp() {
        config = new CampaignScoringConfig();
        // Defaults: categoryMatch=40, ageMatch=15, genderMatch=15, budgetOpportunity=20, recencyPenalty=20, decayWindow=1440
        scorer = new CampaignScorer(config);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Campaign campaignWithCategories(Long id, Long... catIds) {
        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setBudgetCents(100_000L);
        campaign.setSpentCents(0L);
        TargetAudience ta = new TargetAudience();
        ta.setCategories(buildCategories(catIds));
        campaign.setTargetAudience(ta);
        return campaign;
    }

    private List<Category> buildCategories(Long... ids) {
        return Arrays.stream(ids).map(id -> {
            Category c = new Category();
            c.setId(id);
            return c;
        }).toList();
    }

    private ScoringContext ctx(Set<Long> catIds) {
        return new ScoringContext(1L, null, null, catIds, Map.of(), ZonedDateTime.now());
    }

    // ─── budgetOpportunity (única lógica de scoring propia de CampaignScorer) ─

    @Nested
    @DisplayName("budgetOpportunity")
    class BudgetOpportunity {

        @Test
        @DisplayName("100% presupuesto restante → aporta el peso completo al score")
        void fullRemaining_addsFullWeight() {
            Campaign full = campaignWithCategories(1L);
            full.setBudgetCents(100_000L);
            full.setSpentCents(0L);

            Campaign none = campaignWithCategories(2L);
            none.setBudgetCents(100_000L);
            none.setSpentCents(100_000L);

            double fullScore = scorer.computeScore(full, ctx(Set.of()));
            double noneScore = scorer.computeScore(none, ctx(Set.of()));

            // La única diferencia entre ambas campañas es spentCents/budgetCents.
            assertThat(fullScore - noneScore).isCloseTo(config.getBudgetOpportunity(), within(0.001));
        }

        @Test
        @DisplayName("50% presupuesto restante → aporta la mitad del peso")
        void halfRemaining_addsHalfWeight() {
            Campaign half = campaignWithCategories(1L);
            half.setBudgetCents(100_000L);
            half.setSpentCents(50_000L);

            Campaign none = campaignWithCategories(2L);
            none.setBudgetCents(100_000L);
            none.setSpentCents(100_000L);

            double halfScore = scorer.computeScore(half, ctx(Set.of()));
            double noneScore = scorer.computeScore(none, ctx(Set.of()));

            assertThat(halfScore - noneScore).isCloseTo(config.getBudgetOpportunity() * 0.5, within(0.001));
        }

        @Test
        @DisplayName("budgetCents null o 0 → no aporta score de oportunidad")
        void noBudget_zeroOpportunity() {
            Campaign campaign = campaignWithCategories(1L);
            campaign.setBudgetCents(0L);
            campaign.setSpentCents(0L);

            Campaign withBudget = campaignWithCategories(2L);
            withBudget.setBudgetCents(100_000L);
            withBudget.setSpentCents(100_000L); // 0% restante, para aislar el caso budget=0

            double zeroBudgetScore = scorer.computeScore(campaign, ctx(Set.of()));
            double exhaustedBudgetScore = scorer.computeScore(withBudget, ctx(Set.of()));

            // Ambos casos deben aportar 0 de oportunidad, por lo que sus scores deben coincidir.
            assertThat(zeroBudgetScore).isCloseTo(exhaustedBudgetScore, within(0.001));
        }
    }

    // ─── selectBest (integración end-to-end) ──────────────────────────────────

    @Nested
    @DisplayName("selectBest")
    class SelectBest {

        @Test
        @DisplayName("lista vacía → Optional.empty()")
        void emptyList_returnsEmpty() {
            assertThat(scorer.selectBest(List.of(), ctx(Set.of()))).isEmpty();
        }

        @Test
        @DisplayName("gana la campaña con mayor score")
        void highestScoreWins() {
            Campaign c1 = campaignWithCategories(1L, 10L, 20L); // coincide con todas las categorías del usuario
            Campaign c2 = campaignWithCategories(2L, 30L, 40L); // no coincide con ninguna

            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(10L, 20L), Map.of(), ZonedDateTime.now());

            Optional<Campaign> best = scorer.selectBest(List.of(c2, c1), ctx);
            assertThat(best).isPresent().hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(1L));
        }

        @Test
        @DisplayName("desempate: nunca jugada gana sobre jugada recientemente")
        void tiebreak_neverPlayedWinsOverRecentlyPlayed() {
            ZonedDateTime now = ZonedDateTime.now();

            Campaign c1 = campaignWithCategories(1L, 10L); // mismo score que c2
            Campaign c2 = campaignWithCategories(2L, 10L); // mismo score que c1

            // c2 fue jugada hace 2 horas, c1 nunca
            Map<Long, ZonedDateTime> lastPlayed = Map.of(2L, now.minusHours(2));
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(10L), lastPlayed, now);

            Optional<Campaign> best = scorer.selectBest(List.of(c2, c1), ctx);
            assertThat(best).isPresent().hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(1L));
        }

        @Test
        @DisplayName("desempate: campaña más antigua gana si score y recencia son iguales (evita starvation)")
        void tiebreak_olderCampaignWinsOnCreatedAt() {
            ZonedDateTime now = ZonedDateTime.now();

            Campaign older = campaignWithCategories(1L, 10L);
            older.setCreatedAt(now.minusDays(10));

            Campaign newer = campaignWithCategories(2L, 10L);
            newer.setCreatedAt(now.minusDays(1));

            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(10L), Map.of(), now);

            Optional<Campaign> best = scorer.selectBest(List.of(newer, older), ctx);
            assertThat(best).isPresent().hasValueSatisfying(c -> assertThat(c.getId()).isEqualTo(1L));
        }
    }
}
