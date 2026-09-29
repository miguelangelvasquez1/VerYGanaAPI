package com.verygana2.models.branding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.verygana2.models.enums.CampaignStatus;

/**
 * Cobro por sesión de una campaña: la fórmula del costo, el recorte al presupuesto restante y el
 * paso a COMPLETED al agotarse. Valores como los de los seeds de juegos: completar = 5.000 ¢,
 * factor de puntaje = 1, tope por sesión = 20.000 ¢.
 */
@DisplayName("Campaign — cobro por sesión")
class CampaignChargeSessionTest {

    private static Campaign campaign(CampaignStatus status, long budgetCents, long spentCents) {
        return Campaign.builder()
                .status(status)
                .budgetCents(budgetCents)
                .spentCents(spentCents)
                .completionRewardCents(5_000L)
                .scoreRewardFactor(1.0)
                .maxRewardPerSessionCents(20_000L)
                .averageRewardPerSessionCents(15_000L)
                .build();
    }

    @Nested
    @DisplayName("calculateSessionRewardCents")
    class Reward {

        @Test
        @DisplayName("completar + puntaje × factor")
        void completionPlusScore() {
            assertThat(campaign(CampaignStatus.ACTIVE, 100_000L, 0L).calculateSessionRewardCents(8_000))
                    .isEqualTo(13_000L);
        }

        @Test
        @DisplayName("puntaje nulo, cero o negativo: solo la recompensa por completar")
        void nonPositiveScore_onlyCompletion() {
            Campaign c = campaign(CampaignStatus.ACTIVE, 100_000L, 0L);
            assertThat(c.calculateSessionRewardCents(null)).isEqualTo(5_000L);
            assertThat(c.calculateSessionRewardCents(0)).isEqualTo(5_000L);
            assertThat(c.calculateSessionRewardCents(-300)).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("nunca supera maxRewardPerSessionCents")
        void cappedAtMaxPerSession() {
            assertThat(campaign(CampaignStatus.ACTIVE, 100_000L, 0L).calculateSessionRewardCents(1_000_000))
                    .isEqualTo(20_000L);
        }

        @Test
        @DisplayName("factor fraccionario: el aporte del puntaje se redondea al centavo")
        void fractionalFactor_isRounded() {
            Campaign c = campaign(CampaignStatus.ACTIVE, 100_000L, 0L);
            c.setScoreRewardFactor(0.5);
            // 5.000 + round(3 × 0,5 = 1,5) = 5.000 + 2
            assertThat(c.calculateSessionRewardCents(3)).isEqualTo(5_002L);
        }
    }

    @Nested
    @DisplayName("chargeSession")
    class Charge {

        @ParameterizedTest
        @EnumSource(value = CampaignStatus.class, names = {"ACTIVE", "PAUSED"})
        @DisplayName("ACTIVE y PAUSED cobran (una sesión iniciada antes de la pausa sigue costando)")
        void activeAndPausedCharge(CampaignStatus status) {
            Campaign c = campaign(status, 100_000L, 10_000L);

            long charged = c.chargeSession(8_000);

            assertThat(charged).isEqualTo(13_000L);
            assertThat(c.getSpentCents()).isEqualTo(23_000L);
            assertThat(c.getStatus()).isEqualTo(status);
            assertThat(c.getRemainingBudgetCents()).isEqualTo(77_000L);
        }

        @ParameterizedTest
        @EnumSource(value = CampaignStatus.class, names = {"DRAFT", "COMPLETED", "CANCELLED"})
        @DisplayName("DRAFT, COMPLETED y CANCELLED no cobran ni cambian de estado")
        void otherStatusesDoNotCharge(CampaignStatus status) {
            Campaign c = campaign(status, 100_000L, 10_000L);

            assertThat(c.chargeSession(8_000)).isZero();
            assertThat(c.getSpentCents()).isEqualTo(10_000L);
            assertThat(c.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("la última sesión se recorta a lo que queda y la campaña pasa a COMPLETED")
        void lastSession_isClippedAndCompletes() {
            // Quedan 3.000 ¢ pero la sesión costaría 13.000 ¢.
            Campaign c = campaign(CampaignStatus.ACTIVE, 100_000L, 97_000L);

            long charged = c.chargeSession(8_000);

            assertThat(charged).isEqualTo(3_000L);
            assertThat(c.getSpentCents()).isEqualTo(100_000L);
            assertThat(c.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(c.getRemainingBudgetCents()).isZero();
        }

        @Test
        @DisplayName("si la sesión gasta exactamente lo que queda, también COMPLETED")
        void exactExhaustion_completes() {
            Campaign c = campaign(CampaignStatus.PAUSED, 100_000L, 87_000L);

            assertThat(c.chargeSession(8_000)).isEqualTo(13_000L);
            assertThat(c.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        }

        @Test
        @DisplayName("una sesión de costo 0 no cobra ni cierra la campaña")
        void zeroCostSession_doesNothing() {
            Campaign c = campaign(CampaignStatus.ACTIVE, 100_000L, 0L);
            c.setCompletionRewardCents(0L);

            assertThat(c.chargeSession(0)).isZero();
            assertThat(c.getSpentCents()).isZero();
            assertThat(c.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("sesiones sucesivas nunca sobregiran el presupuesto")
        void neverOverdraws() {
            Campaign c = campaign(CampaignStatus.ACTIVE, 50_000L, 0L);
            long totalCharged = 0;

            for (int i = 0; i < 20; i++) {
                totalCharged += c.chargeSession(30_000);   // cada sesión costaría el tope: 20.000 ¢
            }

            assertThat(totalCharged).isEqualTo(50_000L);
            assertThat(c.getSpentCents()).isEqualTo(50_000L);
            assertThat(c.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
        }
    }

    @Test
    @DisplayName("getRemainingBudgetCents nunca es negativo, aunque spentCents supere al presupuesto")
    void remainingBudgetIsNeverNegative() {
        assertThat(campaign(CampaignStatus.ACTIVE, 10_000L, 12_000L).getRemainingBudgetCents()).isZero();
    }
}
