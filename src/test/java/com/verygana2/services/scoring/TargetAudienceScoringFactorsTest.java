package com.verygana2.services.scoring;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.models.Category;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.TargetGender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Prueba la lógica de coincidencia de {@link TargetAudience} de forma independiente de
 * cualquier entidad concreta (Ad, Campaign, ...) — se usa {@code Function.identity()} como
 * extractor para probar los factores directamente sobre un TargetAudience.
 */
@DisplayName("TargetAudienceScoringFactors")
class TargetAudienceScoringFactorsTest {

    private static final double WEIGHT = 40.0;
    private static final Function<TargetAudience, TargetAudience> SELF = Function.identity();

    private TargetAudience audienceWithCategories(Long... ids) {
        TargetAudience ta = new TargetAudience();
        ta.setCategories(categories(ids));
        return ta;
    }

    private List<Category> categories(Long... ids) {
        return Arrays.stream(ids).map(id -> {
            Category c = new Category();
            c.setId(id);
            return c;
        }).toList();
    }

    private ScoringContext ctx(Set<Long> catIds) {
        return new ScoringContext(1L, null, null, catIds, Map.of(), ZonedDateTime.now());
    }

    @Nested
    @DisplayName("categoryMatch")
    class CategoryMatch {

        private final ScoringFactor<TargetAudience> factor = TargetAudienceScoringFactors.categoryMatch(SELF, WEIGHT);

        @Test
        @DisplayName("sin intersección → 0")
        void noMatch_returnsZero() {
            TargetAudience ta = audienceWithCategories(10L, 20L);
            assertThat(factor.apply(ta, ctx(Set.of(30L, 40L)))).isEqualTo(0.0);
        }

        @Test
        @DisplayName("intersección parcial → score proporcional (Jaccard)")
        void partialMatch_proportionalScore() {
            TargetAudience ta = audienceWithCategories(10L, 20L, 30L); // 3 categorías
            double score = factor.apply(ta, ctx(Set.of(10L, 40L)));    // 1 coincidencia de 4 en unión
            // Jaccard = 1/4 = 0.25 → 40 * 0.25 = 10
            assertThat(score).isCloseTo(10.0, within(0.001));
        }

        @Test
        @DisplayName("coincidencia total → score máximo")
        void fullMatch_returnsMaxWeight() {
            TargetAudience ta = audienceWithCategories(10L, 20L);
            assertThat(factor.apply(ta, ctx(Set.of(10L, 20L)))).isCloseTo(WEIGHT, within(0.001));
        }

        @Test
        @DisplayName("sin categorías → 0")
        void noCategories_returnsZero() {
            TargetAudience ta = audienceWithCategories();
            assertThat(factor.apply(ta, ctx(Set.of(10L)))).isEqualTo(0.0);
        }

        @Test
        @DisplayName("TargetAudience null → 0")
        void nullAudience_returnsZero() {
            ScoringFactor<TargetAudience> f = TargetAudienceScoringFactors.categoryMatch(a -> null, WEIGHT);
            assertThat(f.apply(new TargetAudience(), ctx(Set.of(10L)))).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("ageMatch")
    class AgeMatch {

        private final ScoringFactor<TargetAudience> factor = TargetAudienceScoringFactors.ageMatch(SELF, WEIGHT);

        @Test
        @DisplayName("edad dentro del rango → score completo")
        void inRange_fullScore() {
            TargetAudience ta = new TargetAudience();
            ta.setMinAge(18);
            ta.setMaxAge(35);
            ScoringContext ctx = new ScoringContext(1L, 25, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT, within(0.001));
        }

        @Test
        @DisplayName("edad fuera del rango → 0")
        void outOfRange_zero() {
            TargetAudience ta = new TargetAudience();
            ta.setMinAge(18);
            ta.setMaxAge(25);
            ScoringContext ctx = new ScoringContext(1L, 40, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("edad del consumidor null → score completo (beneficio de la duda)")
        void nullAge_fullScore() {
            TargetAudience ta = new TargetAudience();
            ta.setMinAge(18);
            ta.setMaxAge(35);
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT, within(0.001));
        }

        @Test
        @DisplayName("sin restricción de edad → score completo siempre")
        void noAgeConstraint_fullScore() {
            TargetAudience ta = new TargetAudience();
            ScoringContext ctx = new ScoringContext(1L, 80, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT, within(0.001));
        }
    }

    @Nested
    @DisplayName("genderMatch")
    class GenderMatch {

        private final ScoringFactor<TargetAudience> factor = TargetAudienceScoringFactors.genderMatch(SELF, WEIGHT);

        @Test
        @DisplayName("targetGender ALL → score completo")
        void targetAll_fullScore() {
            TargetAudience ta = new TargetAudience();
            ta.setTargetGender(TargetGender.ALL);
            ScoringContext ctx = new ScoringContext(1L, null, TargetGender.MALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT, within(0.001));
        }

        @Test
        @DisplayName("targetGender null → score completo (sin restricción)")
        void targetNull_fullScore() {
            TargetAudience ta = new TargetAudience();
            ScoringContext ctx = new ScoringContext(1L, null, TargetGender.FEMALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT, within(0.001));
        }

        @Test
        @DisplayName("género del consumidor coincide → score completo")
        void exactMatch_fullScore() {
            TargetAudience ta = new TargetAudience();
            ta.setTargetGender(TargetGender.FEMALE);
            ScoringContext ctx = new ScoringContext(1L, null, TargetGender.FEMALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT, within(0.001));
        }

        @Test
        @DisplayName("género no coincide → 0")
        void mismatch_zero() {
            TargetAudience ta = new TargetAudience();
            ta.setTargetGender(TargetGender.FEMALE);
            ScoringContext ctx = new ScoringContext(1L, null, TargetGender.MALE, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("género del consumidor null → score parcial (50%)")
        void consumerGenderNull_halfScore() {
            TargetAudience ta = new TargetAudience();
            ta.setTargetGender(TargetGender.MALE);
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isCloseTo(WEIGHT / 2.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("recencyPenalty")
    class RecencyPenalty {

        private static final long DECAY_WINDOW_MINUTES = 10080; // 7 días
        private static final double PENALTY = 30.0;

        private final ScoringFactor<TargetAudience> factor =
                TargetAudienceScoringFactors.recencyPenalty(TargetAudience::getId, PENALTY, DECAY_WINDOW_MINUTES);

        @Test
        @DisplayName("nunca interactuado → penalización 0")
        void neverInteracted_zeroPenalty() {
            TargetAudience ta = new TargetAudience();
            ta.setId(99L);
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(), Map.of(), ZonedDateTime.now());
            assertThat(factor.apply(ta, ctx)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("interacción muy reciente → penalización negativa cercana al máximo")
        void interactedJustNow_maxPenalty() {
            TargetAudience ta = new TargetAudience();
            ta.setId(1L);
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime last = now.minusMinutes(1);
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(), Map.of(1L, last), now);

            double score = factor.apply(ta, ctx);
            assertThat(score).isLessThan(0.0);
            assertThat(score).isGreaterThanOrEqualTo(-PENALTY);
        }

        @Test
        @DisplayName("interacción más antigua que la ventana de decay → penalización 0")
        void interactedBeyondDecayWindow_zeroPenalty() {
            TargetAudience ta = new TargetAudience();
            ta.setId(1L);
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime last = now.minusMinutes(DECAY_WINDOW_MINUTES + 1);
            ScoringContext ctx = new ScoringContext(1L, null, null, Set.of(), Map.of(1L, last), now);

            assertThat(factor.apply(ta, ctx)).isEqualTo(0.0);
        }
    }
}
