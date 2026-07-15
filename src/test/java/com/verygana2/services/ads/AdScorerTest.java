package com.verygana2.services.ads;

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
import com.verygana2.models.ads.Ad;
import com.verygana2.services.scoring.ScoringContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests de {@link AdScorer}. La lógica de coincidencia de TargetAudience (categoría/edad/género)
 * y el desempate genérico ya se prueban en {@code TargetAudienceScoringFactorsTest} y
 * {@code EntityScorer} — aquí se cubre lo específico de AdScorer: el factor {@code opportunityRatio}
 * y la integración end-to-end vía {@link AdScorer#selectBest}.
 */
@DisplayName("AdScorer")
class AdScorerTest {

    private AdScoringConfig config;
    private AdScorer scorer;

    @BeforeEach
    void setUp() {
        config = new AdScoringConfig();
        // Defaults: categoryMatch=40, ageMatch=15, genderMatch=15, opportunityRatio=20, recencyPenalty=20, decayWindow=10080
        scorer = new AdScorer(config);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Ad adWithCategories(Long id, Long... catIds) {
        Ad ad = new Ad();
        ad.setId(id);
        ad.setMaxLikes(100);
        ad.setCurrentLikes(0);
        TargetAudience ta = new TargetAudience();
        ta.setCategories(buildCategories(catIds));
        ad.setTargetAudience(ta);
        return ad;
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

    // ─── opportunityRatio (única lógica de scoring propia de AdScorer) ────────

    @Nested
    @DisplayName("opportunityRatio")
    class OpportunityRatio {

        @Test
        @DisplayName("100% likes restantes → aporta el peso completo al score")
        void fullRemaining_addsFullWeight() {
            Ad full = adWithCategories(1L);
            full.setMaxLikes(100);
            full.setCurrentLikes(0);

            Ad none = adWithCategories(2L);
            none.setMaxLikes(100);
            none.setCurrentLikes(100);

            double fullScore = scorer.computeScore(full, ctx(Set.of()));
            double noneScore = scorer.computeScore(none, ctx(Set.of()));

            // La única diferencia entre ambos anuncios es remainingLikes/maxLikes.
            assertThat(fullScore - noneScore).isCloseTo(config.getOpportunityRatio(), within(0.001));
        }

        @Test
        @DisplayName("50% likes restantes → aporta la mitad del peso")
        void halfRemaining_addsHalfWeight() {
            Ad half = adWithCategories(1L);
            half.setMaxLikes(100);
            half.setCurrentLikes(50);

            Ad none = adWithCategories(2L);
            none.setMaxLikes(100);
            none.setCurrentLikes(100);

            double halfScore = scorer.computeScore(half, ctx(Set.of()));
            double noneScore = scorer.computeScore(none, ctx(Set.of()));

            assertThat(halfScore - noneScore).isCloseTo(config.getOpportunityRatio() * 0.5, within(0.001));
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
        @DisplayName("gana el anuncio con mayor score")
        void highestScoreWins() {
            Ad ad1 = adWithCategories(1L, 10L, 20L); // coincide con todas las categorías del usuario
            ad1.setMaxLikes(100);
            ad1.setCurrentLikes(0);

            Ad ad2 = adWithCategories(2L, 30L, 40L); // no coincide con ninguna
            ad2.setMaxLikes(100);
            ad2.setCurrentLikes(0);

            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(10L, 20L), Map.of(), ZonedDateTime.now());

            Optional<Ad> best = scorer.selectBest(List.of(ad2, ad1), ctx);
            assertThat(best).isPresent().hasValueSatisfying(ad -> assertThat(ad.getId()).isEqualTo(1L));
        }

        @Test
        @DisplayName("desempate: nunca visto gana sobre visto recientemente")
        void tiebreak_neverViewedWinsOverRecentlyViewed() {
            ZonedDateTime now = ZonedDateTime.now();

            Ad ad1 = adWithCategories(1L, 10L); // mismo score que ad2
            ad1.setMaxLikes(100);
            ad1.setCurrentLikes(50);

            Ad ad2 = adWithCategories(2L, 10L); // mismo score que ad1
            ad2.setMaxLikes(100);
            ad2.setCurrentLikes(50);

            // ad2 fue visto hace 2 horas, ad1 nunca
            Map<Long, ZonedDateTime> lastViewed = Map.of(2L, now.minusHours(2));
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(10L), lastViewed, now);

            Optional<Ad> best = scorer.selectBest(List.of(ad2, ad1), ctx);
            assertThat(best).isPresent().hasValueSatisfying(ad -> assertThat(ad.getId()).isEqualTo(1L));
        }

        @Test
        @DisplayName("desempate: anuncio más antiguo gana si score y recencia son iguales (evita starvation)")
        void tiebreak_olderAdWinsOnCreatedAt() {
            ZonedDateTime now = ZonedDateTime.now();

            Ad older = adWithCategories(1L, 10L);
            older.setMaxLikes(100);
            older.setCurrentLikes(50);
            older.setCreatedAt(now.minusDays(10));

            Ad newer = adWithCategories(2L, 10L);
            newer.setMaxLikes(100);
            newer.setCurrentLikes(50);
            newer.setCreatedAt(now.minusDays(1));

            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(10L), Map.of(), now);

            Optional<Ad> best = scorer.selectBest(List.of(newer, older), ctx);
            assertThat(best).isPresent().hasValueSatisfying(ad -> assertThat(ad.getId()).isEqualTo(1L));
        }
    }
}
