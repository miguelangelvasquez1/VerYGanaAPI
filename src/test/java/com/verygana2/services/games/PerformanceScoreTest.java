package com.verygana2.services.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.game.GameMetricDTO;
import com.verygana2.models.enums.MetricType;

class PerformanceScoreTest {

    private static GameMetricDTO metric(String key, Object value) {
        return new GameMetricDTO(key, MetricType.DOUBLE, value);
    }

    private static Integer score(Object value) {
        return PerformanceScore.from(List.of(metric("performance", value)));
    }

    @Test
    @DisplayName("lee un puntaje entero")
    void readsIntegerPerformance() {
        assertThat(score(3)).isEqualTo(3);
        assertThat(score(3L)).isEqualTo(3);
        assertThat(score(new BigDecimal("14"))).isEqualTo(14);
    }

    @Test
    @DisplayName("trunca el decimal; menos de 1 queda sin puntaje")
    void truncatesDecimalPerformance() {
        assertThat(score(3.9)).isEqualTo(3);
        assertThat(score(0.5)).isNull();
    }

    @Test
    @DisplayName("sin la métrica no hay puntaje")
    void missingPerformanceIsNull() {
        assertThat(PerformanceScore.from(null)).isNull();
        assertThat(PerformanceScore.from(List.of())).isNull();
        assertThat(PerformanceScore.from(Arrays.asList(null, metric("other", 5)))).isNull();
        assertThat(PerformanceScore.from(Arrays.asList((GameMetricDTO) null))).isNull();
    }

    @Test
    @DisplayName("un valor no numérico no da puntaje")
    void nonNumericPerformanceIsNull() {
        assertThat(score("3")).isNull();
        assertThat(score(true)).isNull();
        assertThat(score(null)).isNull();
    }

    @Test
    @DisplayName("cero o negativo no da puntaje")
    void zeroOrNegativePerformanceIsNull() {
        assertThat(score(0)).isNull();
        assertThat(score(-4)).isNull();
        assertThat(score(-0.5)).isNull();
    }

    @Test
    @DisplayName("NaN e infinito no dan puntaje")
    void nanOrInfiniteIsNull() {
        assertThat(score(Double.NaN)).isNull();
        assertThat(score(Double.POSITIVE_INFINITY)).isNull();
        assertThat(score(Double.NEGATIVE_INFINITY)).isNull();
    }

    @Test
    @DisplayName("un valor enorme se recorta al máximo entero")
    void hugeValueIsClampedToIntMax() {
        assertThat(score(Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        assertThat(score(1e20)).isEqualTo(Integer.MAX_VALUE);
        assertThat(score(new BigDecimal("1e30"))).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("keys_earned y keys_total no cuentan")
    void ignoresKeysEarnedAndKeysTotal() {
        List<GameMetricDTO> metrics = new ArrayList<>();
        metrics.add(metric("keys_earned", 10));
        metrics.add(metric("keys_total", 120));
        assertThat(PerformanceScore.from(metrics)).isNull();
    }

    @Test
    @DisplayName("con performance repetida gana la primera")
    void usesFirstPerformanceWhenDuplicated() {
        assertThat(PerformanceScore.from(
                List.of(metric("performance", 7), metric("performance", 9)))).isEqualTo(7);
    }
}
