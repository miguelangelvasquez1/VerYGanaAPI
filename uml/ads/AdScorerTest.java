package ads;

import com.verygana2.models.Category;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.services.ads.AdScorer;
import com.verygana2.services.ads.AdScoringConfig;
import com.verygana2.services.ads.AdScoringContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("AdScorer")
class AdScorerTest {

    private AdScoringConfig config;
    private AdScorer scorer;

    @BeforeEach
    void setUp() {
        config = new AdScoringConfig();
        // Valores por defecto: categoryMatch=40, ageMatch=15, genderMatch=15,
        //                       opportunityRatio=20, recencyPenalty=30, decayWindow=10080
        scorer = new AdScorer(config);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Ad adWithCategories(Long id, Long... catIds) {
        Ad ad = new Ad();
        ad.setId(id);
        ad.setMaxLikes(100);
        ad.setCurrentLikes(0);
        ad.setCategories(buildCategories(catIds));
        return ad;
    }

    private List<Category> buildCategories(Long... ids) {
        return java.util.Arrays.stream(ids).map(id -> {
            Category c = new Category();
            c.setId(id);
            return c;
        }).toList();
    }

    private AdScoringContext ctx(Set<Long> catIds) {
        return new AdScoringContext(1L, null, null, catIds, Map.of(), ZonedDateTime.now());
    }

    // ─── CATEGORY_MATCH ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("CATEGORY_MATCH factor")
    class CategoryMatch {

        @Test
        @DisplayName("sin intersección → 0")
        void noMatch_returnsZero() {
            Ad ad = adWithCategories(1L, 10L, 20L);
            AdScoringContext ctx = ctx(Set.of(30L, 40L));
            double score = AdScorer.CATEGORY_MATCH.apply(ad, ctx, config);
            assertThat(score).isEqualTo(0.0);
        }

        @Test
        @DisplayName("intersección parcial → score proporcional (Jaccard)")
        void partialMatch_proportionalScore() {
            Ad ad = adWithCategories(1L, 10L, 20L, 30L);   // 3 cats
            AdScoringContext ctx = ctx(Set.of(10L, 40L));   // 1 coincidencia de 4 en unión
            double score = AdScorer.CATEGORY_MATCH.apply(ad, ctx, config);
            // Jaccard = 1/4 = 0.25  →  40 * 0.25 = 10
            assertThat(score).isCloseTo(10.0, within(0.001));
        }

        @Test
        @DisplayName("coincidencia total → score máximo")
        void fullMatch_returnsMaxWeight() {
            Ad ad = adWithCategories(1L, 10L, 20L);
            AdScoringContext ctx = ctx(Set.of(10L, 20L));
            double score = AdScorer.CATEGORY_MATCH.apply(ad, ctx, config);
            assertThat(score).isCloseTo(config.getCategoryMatch(), within(0.001));
        }

        @Test
        @DisplayName("anuncio sin categorías → 0")
        void adNoCats_returnsZero() {
            Ad ad = adWithCategories(1L);
            AdScoringContext ctx = ctx(Set.of(10L));
            double score = AdScorer.CATEGORY_MATCH.apply(ad, ctx, config);
            assertThat(score).isEqualTo(0.0);
        }
    }

    // ─── AGE_MATCH ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AGE_MATCH factor")
    class AgeMatch {

        @Test
        @DisplayName("edad dentro del rango → score completo")
        void inRange_fullScore() {
            Ad ad = new Ad();
            ad.setMinAge(18);
            ad.setMaxAge(35);
            AdScoringContext ctx = new AdScoringContext(1L, 25, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.AGE_MATCH.apply(ad, ctx, config)).isCloseTo(config.getAgeMatch(), within(0.001));
        }

        @Test
        @DisplayName("edad fuera del rango → 0")
        void outOfRange_zero() {
            Ad ad = new Ad();
            ad.setMinAge(18);
            ad.setMaxAge(25);
            AdScoringContext ctx = new AdScoringContext(1L, 40, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.AGE_MATCH.apply(ad, ctx, config)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("edad del consumidor null → score completo (beneficio de la duda)")
        void nullAge_fullScore() {
            Ad ad = new Ad();
            ad.setMinAge(18);
            ad.setMaxAge(35);
            AdScoringContext ctx = new AdScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.AGE_MATCH.apply(ad, ctx, config)).isCloseTo(config.getAgeMatch(), within(0.001));
        }

        @Test
        @DisplayName("anuncio sin restricción de edad → score completo siempre")
        void noAdAgeConstraint_fullScore() {
            Ad ad = new Ad();
            AdScoringContext ctx = new AdScoringContext(1L, 80, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.AGE_MATCH.apply(ad, ctx, config)).isCloseTo(config.getAgeMatch(), within(0.001));
        }
    }

    // ─── GENDER_MATCH ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GENDER_MATCH factor")
    class GenderMatch {

        @Test
        @DisplayName("targetGender ALL → score completo")
        void targetAll_fullScore() {
            Ad ad = new Ad();
            ad.setTargetGender(TargetGender.ALL);
            AdScoringContext ctx = new AdScoringContext(1L, null, TargetGender.MALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.GENDER_MATCH.apply(ad, ctx, config)).isCloseTo(config.getGenderMatch(), within(0.001));
        }

        @Test
        @DisplayName("targetGender null → score completo (sin restricción)")
        void targetNull_fullScore() {
            Ad ad = new Ad();
            AdScoringContext ctx = new AdScoringContext(1L, null, TargetGender.FEMALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.GENDER_MATCH.apply(ad, ctx, config)).isCloseTo(config.getGenderMatch(), within(0.001));
        }

        @Test
        @DisplayName("género del consumidor coincide → score completo")
        void exactMatch_fullScore() {
            Ad ad = new Ad();
            ad.setTargetGender(TargetGender.FEMALE);
            AdScoringContext ctx = new AdScoringContext(1L, null, TargetGender.FEMALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.GENDER_MATCH.apply(ad, ctx, config)).isCloseTo(config.getGenderMatch(), within(0.001));
        }

        @Test
        @DisplayName("género no coincide → 0")
        void mismatch_zero() {
            Ad ad = new Ad();
            ad.setTargetGender(TargetGender.FEMALE);
            AdScoringContext ctx = new AdScoringContext(1L, null, TargetGender.MALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.GENDER_MATCH.apply(ad, ctx, config)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("género del consumidor null → score parcial (50%)")
        void consumerGenderNull_halfScore() {
            Ad ad = new Ad();
            ad.setTargetGender(TargetGender.MALE);
            AdScoringContext ctx = new AdScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.GENDER_MATCH.apply(ad, ctx, config)).isCloseTo(config.getGenderMatch() / 2.0, within(0.001));
        }
    }

    // ─── OPPORTUNITY_RATIO ────────────────────────────────────────────────────

    @Nested
    @DisplayName("OPPORTUNITY_RATIO factor")
    class OpportunityRatio {

        @Test
        @DisplayName("100% likes restantes → score máximo")
        void fullRemaining_maxScore() {
            Ad ad = new Ad();
            ad.setMaxLikes(100);
            ad.setCurrentLikes(0);
            double score = AdScorer.OPPORTUNITY_RATIO.apply(ad, ctx(Set.of()), config);
            assertThat(score).isCloseTo(config.getOpportunityRatio(), within(0.001));
        }

        @Test
        @DisplayName("50% likes restantes → score proporcional (no división entera)")
        void halfRemaining_halfScore() {
            Ad ad = new Ad();
            ad.setMaxLikes(100);
            ad.setCurrentLikes(50);
            double score = AdScorer.OPPORTUNITY_RATIO.apply(ad, ctx(Set.of()), config);
            assertThat(score).isCloseTo(config.getOpportunityRatio() * 0.5, within(0.001));
        }

        @Test
        @DisplayName("0 likes restantes → 0")
        void noRemaining_zero() {
            Ad ad = new Ad();
            ad.setMaxLikes(100);
            ad.setCurrentLikes(100);
            double score = AdScorer.OPPORTUNITY_RATIO.apply(ad, ctx(Set.of()), config);
            assertThat(score).isEqualTo(0.0);
        }
    }

    // ─── RECENCY_PENALTY ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("RECENCY_PENALTY factor")
    class RecencyPenalty {

        @Test
        @DisplayName("nunca visto → penalización 0")
        void neverViewed_zeroPenalty() {
            Ad ad = new Ad();
            ad.setId(99L);
            AdScoringContext ctx = new AdScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(AdScorer.RECENCY_PENALTY.apply(ad, ctx, config)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("visto muy recientemente → penalización máxima (negativa)")
        void viewedJustNow_maxPenalty() {
            Ad ad = new Ad();
            ad.setId(1L);
            ZonedDateTime now = ZonedDateTime.now();
            // Visto hace 1 minuto (dentro del decay window de 10080 min)
            ZonedDateTime lastViewed = now.minusMinutes(1);
            AdScoringContext ctx = new AdScoringContext(1L, null, null, Set.of(), Map.of(1L, lastViewed), now);
            double score = AdScorer.RECENCY_PENALTY.apply(ad, ctx, config);
            // decay = 1 - 1/10080 ≈ 0.9999  → penalización ≈ -30 * 0.9999 ≈ -29.997
            assertThat(score).isLessThan(0.0);
            assertThat(score).isGreaterThanOrEqualTo(-config.getRecencyPenalty());
        }

        @Test
        @DisplayName("visto hace más tiempo que la ventana de decay → penalización 0")
        void viewedBeyondDecayWindow_zeroPenalty() {
            Ad ad = new Ad();
            ad.setId(1L);
            ZonedDateTime now = ZonedDateTime.now();
            // Visto hace 10081 minutos (más que la ventana de 10080)
            ZonedDateTime lastViewed = now.minusMinutes(config.getRecencyDecayWindowMinutes() + 1);
            AdScoringContext ctx = new AdScoringContext(1L, null, null, Set.of(), Map.of(1L, lastViewed), now);
            double score = AdScorer.RECENCY_PENALTY.apply(ad, ctx, config);
            assertThat(score).isEqualTo(0.0);
        }
    }

    // ─── selectBest ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("selectBest")
    class SelectBest {

        @Test
        @DisplayName("lista vacía → Optional.empty()")
        void emptyList_returnsEmpty() {
            AdScoringContext ctx = new AdScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(scorer.selectBest(List.of(), ctx)).isEmpty();
        }

        @Test
        @DisplayName("gana el anuncio con mayor score")
        void highestScoreWins() {
            // ad1: coincide con todas las categorías del usuario → score alto
            Ad ad1 = adWithCategories(1L, 10L, 20L);
            ad1.setMaxLikes(100);
            ad1.setCurrentLikes(0);

            // ad2: no coincide con ninguna categoría → score bajo
            Ad ad2 = adWithCategories(2L, 30L, 40L);
            ad2.setMaxLikes(100);
            ad2.setCurrentLikes(0);

            AdScoringContext ctx = new AdScoringContext(
                    1L, null, null, Set.of(10L, 20L), Map.of(), ZonedDateTime.now());

            Optional<Ad> best = scorer.selectBest(List.of(ad2, ad1), ctx);
            assertThat(best).isPresent().hasValueSatisfying(ad -> assertThat(ad.getId()).isEqualTo(1L));
        }

        @Test
        @DisplayName("desempate: nunca visto gana sobre visto recientemente")
        void tiebreak_neverViewedWinsOverRecentlyViewed() {
            ZonedDateTime now = ZonedDateTime.now();

            Ad ad1 = adWithCategories(1L, 10L);  // mismo score que ad2
            ad1.setMaxLikes(100);
            ad1.setCurrentLikes(50);

            Ad ad2 = adWithCategories(2L, 10L);  // mismo score que ad1
            ad2.setMaxLikes(100);
            ad2.setCurrentLikes(50);

            // ad2 fue visto hace 2 horas, ad1 nunca
            Map<Long, ZonedDateTime> lastViewed = Map.of(2L, now.minusHours(2));

            AdScoringContext ctx = new AdScoringContext(
                    1L, null, null, Set.of(10L), lastViewed, now);

            // ad1 nunca visto → gana por tiebreak (recency)
            Optional<Ad> best = scorer.selectBest(List.of(ad2, ad1), ctx);
            assertThat(best).isPresent().hasValueSatisfying(ad -> assertThat(ad.getId()).isEqualTo(1L));
        }

        @Test
        @DisplayName("desempate: anuncio más antiguo gana si ambos tienen el mismo score y lastViewedAt (evita starvation)")
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

            AdScoringContext ctx = new AdScoringContext(
                    1L, null, null, Set.of(10L), Map.of(), now);

            Optional<Ad> best = scorer.selectBest(List.of(newer, older), ctx);
            assertThat(best).isPresent().hasValueSatisfying(ad -> assertThat(ad.getId()).isEqualTo(1L));
        }
    }
}
