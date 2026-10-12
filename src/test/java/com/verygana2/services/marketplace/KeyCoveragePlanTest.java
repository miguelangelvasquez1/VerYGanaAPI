package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.services.marketplace.KeyCoveragePlan.Allocation;
import com.verygana2.services.marketplace.KeyCoveragePlan.Unit;

/**
 * Reparto de las llaves de una compra entre sus unidades. Lo que importa: la suma es
 * exactamente lo que el usuario aplicó, y el margen de una promoción solo se toca cuando
 * los topes ordinarios ya están llenos.
 */
@DisplayName("KeyCoveragePlan")
class KeyCoveragePlanTest {

    /** $100.000 con tope ordinario 50 % → $50.000. */
    private static Unit ordinary(long productId) {
        return new Unit(productId, 10_000_000L, 50, 5_000_000L, null, 50, 5_000_000L, null);
    }

    /** $100.000 con promoción al 70 % → $70.000, de los cuales $20.000 son margen de la promoción. */
    private static Unit promoted(long productId) {
        return new Unit(productId, 10_000_000L, 50, 5_000_000L, 9L, 70, 7_000_000L, null);
    }

    @Test
    @DisplayName("la cobertura máxima suma el tope de cada unidad")
    void maxCoverageSumsUnitLimits() {
        KeyCoveragePlan plan = new KeyCoveragePlan(List.of(ordinary(1), promoted(2)));

        assertThat(plan.totalCents()).isEqualTo(20_000_000L);
        assertThat(plan.maxCoverageCents()).isEqualTo(12_000_000L);
        assertThat(plan.maxPromotionExcessCents()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("uso parcial: mientras quepa en los topes ordinarios no gasta margen de promoción")
    void partialUseDoesNotTouchPromotionMargin() {
        KeyCoveragePlan plan = new KeyCoveragePlan(List.of(promoted(1), ordinary(2)));

        List<Allocation> allocations = plan.allocate(8_000_000L);

        assertThat(allocations).containsExactly(
                new Allocation(5_000_000L, 0L),
                new Allocation(3_000_000L, 0L));
    }

    @Test
    @DisplayName("por encima de los topes ordinarios, el resto sale del margen de la promoción")
    void aboveOrdinaryLimitsUsesPromotionMargin() {
        KeyCoveragePlan plan = new KeyCoveragePlan(List.of(promoted(1), ordinary(2)));

        List<Allocation> allocations = plan.allocate(11_000_000L);

        assertThat(allocations).containsExactly(
                new Allocation(6_000_000L, 1_000_000L),
                new Allocation(5_000_000L, 0L));
        assertThat(allocations.stream().mapToLong(Allocation::keysCents).sum()).isEqualTo(11_000_000L);
    }

    @Test
    @DisplayName("sin llaves: todas las unidades quedan en cero")
    void zeroKeys() {
        KeyCoveragePlan plan = new KeyCoveragePlan(List.of(ordinary(1), promoted(2)));

        assertThat(plan.allocate(0L)).containsExactly(new Allocation(0L, 0L), new Allocation(0L, 0L));
    }

    @Test
    @DisplayName("más llaves que la cobertura máxima: no reparte, lanza")
    void aboveMaxCoverage_throws() {
        KeyCoveragePlan plan = new KeyCoveragePlan(List.of(ordinary(1)));

        assertThatThrownBy(() -> plan.allocate(5_000_001L)).isInstanceOf(IllegalArgumentException.class);
    }
}
