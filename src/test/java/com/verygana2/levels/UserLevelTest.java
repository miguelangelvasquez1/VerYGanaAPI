package com.verygana2.levels;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.verygana2.models.enums.UserLevel;

@DisplayName("UserLevel (enum)")
class UserLevelTest {

    // ─── fromXp ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromXp")
    class FromXp {

        @ParameterizedTest(name = "{0} XP → {1}")
        @CsvSource({
                "0,        BRONCE",
                "999,      BRONCE",
                "1000,     PLATA",
                "3999,     PLATA",
                "4000,     ORO",
                "8999,     ORO",
                "9000,     RUBI",
                "17999,    RUBI",
                "18000,    ESMERALDA",
                "34999,    ESMERALDA",
                "35000,    DIAMANTE",
                "999999,   DIAMANTE"
        })
        void mapsXpToCorrectLevel(long xp, UserLevel expected) {
            assertThat(UserLevel.fromXp(xp)).isEqualTo(expected);
        }

        @Test
        @DisplayName("XP negativo cae en BRONCE")
        void negativeXpFallsToBronce() {
            assertThat(UserLevel.fromXp(-100)).isEqualTo(UserLevel.BRONCE);
        }
    }

    // ─── applyMultiplier ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("applyMultiplier")
    class ApplyMultiplier {

        @Test
        @DisplayName("BRONCE aplica ×0.5")
        void bronceHalvesBase() {
            assertThat(UserLevel.BRONCE.applyMultiplier(100)).isEqualTo(50);
        }

        @Test
        @DisplayName("DIAMANTE aplica ×1.0 (valor completo)")
        void diamanteKeepsFullValue() {
            assertThat(UserLevel.DIAMANTE.applyMultiplier(100)).isEqualTo(100);
        }

        @Test
        @DisplayName("redondea en vez de truncar (ORO: 5 × 0.7 = 3.5 → 4)")
        void roundsInsteadOfTruncating() {
            assertThat(UserLevel.ORO.applyMultiplier(5)).isEqualTo(4);
        }

        @Test
        @DisplayName("cero permanece cero")
        void zeroStaysZero() {
            assertThat(UserLevel.PLATA.applyMultiplier(0)).isZero();
        }
    }

    // ─── xpToNextLevel ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("xpToNextLevel")
    class XpToNextLevel {

        @Test
        @DisplayName("BRONCE con 400 XP necesita 600 para PLATA")
        void bronceNeedsRemainingXp() {
            assertThat(UserLevel.BRONCE.xpToNextLevel(400)).isEqualTo(600);
        }

        @Test
        @DisplayName("DIAMANTE retorna 0 (nivel máximo)")
        void diamanteReturnsZero() {
            assertThat(UserLevel.DIAMANTE.xpToNextLevel(50000)).isZero();
        }
    }

    // ─── canAccessRaffle ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("canAccessRaffle")
    class CanAccessRaffle {

        @Test
        @DisplayName("nivel superior accede a rifas de nivel inferior")
        void higherLevelAccessesLowerRaffle() {
            assertThat(UserLevel.RUBI.canAccessRaffle(UserLevel.BRONCE)).isTrue();
        }

        @Test
        @DisplayName("nivel inferior NO accede a rifas de nivel superior")
        void lowerLevelCannotAccessHigherRaffle() {
            assertThat(UserLevel.PLATA.canAccessRaffle(UserLevel.ORO)).isFalse();
        }

        @Test
        @DisplayName("mismo nivel accede")
        void sameLevelAccesses() {
            assertThat(UserLevel.ORO.canAccessRaffle(UserLevel.ORO)).isTrue();
        }
    }
}
