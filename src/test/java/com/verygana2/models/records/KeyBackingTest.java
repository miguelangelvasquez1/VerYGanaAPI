package com.verygana2.models.records;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La identidad contable del fondo de llaves. Los casos que importan son los
 * bordes: sin llaves en circulación, y el presupuesto comprometido que NO debe
 * contarse como descuadre.
 */
@DisplayName("KeyBacking — identidad contable")
class KeyBackingTest {

    private KeyBacking backing(long reserve, long liability, long balance,
                               long ads, long surveys, long branding, long campaigns) {
        return new KeyBacking(reserve, liability, balance, ads, surveys, branding, campaigns);
    }

    @Test
    @DisplayName("identidad cuadrada: todos los sumandos dan KEYS_RESERVE, desviación cero")
    void balancedIdentityHasNoDrift() {
        KeyBacking b = backing(10_000L, 3_000L, 2_000L, 1_000L, 1_000L, 1_500L, 1_500L);

        assertThat(b.accountedCents()).isEqualTo(10_000L);
        assertThat(b.driftCents()).isZero();
    }

    @Test
    @DisplayName("el presupuesto de brandeo y campañas cuenta: olvidarlo reportaría déficit falso")
    void brandingAndCampaignBudgetsCount() {
        // Fue el bug que la métrica destapó en dev: sin estos dos sumandos, plata
        // legítimamente respaldada se veía como un agujero.
        KeyBacking conTodo = backing(10_000L, 0L, 0L, 0L, 0L, 6_000L, 4_000L);
        assertThat(conTodo.driftCents()).isZero();

        KeyBacking sinEllos = backing(10_000L, 0L, 0L, 0L, 0L, 0L, 0L);
        assertThat(sinEllos.driftCents()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("sin llaves en circulación el respaldo es 100%, no 0")
    void zeroLiabilityIsFullyBacked() {
        // Con 0 la alerta LlavesSinRespaldo dispararía en toda plataforma nueva.
        KeyBacking b = backing(0L, 0L, 0L, 0L, 0L, 0L, 0L);

        assertThat(b.backingPct()).isEqualTo(100.0);
        assertThat(b.isUnderBacked()).isFalse();
    }

    @Test
    @DisplayName("reserva por debajo del pasivo: respaldo bajo 100 y marcado como insolvente")
    void underBackedIsDetected() {
        KeyBacking b = backing(5_000L, 8_000L, 0L, 0L, 0L, 0L, 0L);

        assertThat(b.backingPct()).isEqualTo(62.5);
        assertThat(b.isUnderBacked()).isTrue();
        assertThat(b.driftCents()).isNegative();
    }

    @Test
    @DisplayName("sobra respaldo: desviación positiva (llaves que salieron sin debitar el fondo)")
    void surplusIsPositiveDrift() {
        KeyBacking b = backing(10_000L, 1_000L, 0L, 0L, 0L, 0L, 0L);

        assertThat(b.driftCents()).isEqualTo(9_000L);
        assertThat(b.isUnderBacked()).isFalse();
    }
}
