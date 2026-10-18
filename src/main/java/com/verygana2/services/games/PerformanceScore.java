package com.verygana2.services.games;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.dtos.game.GameMetricDTO;

/**
 * Extrae el puntaje de la partida desde la métrica {@code performance} que mandan los builds
 * (spec 013, RN-2). Las métricas {@code keys_earned} y {@code keys_total} las calcula el juego y
 * no se leen para pagar.
 */
public final class PerformanceScore {

    static final String PERFORMANCE_KEY = "performance";

    private PerformanceScore() {
    }

    /**
     * @return el puntaje entero (&gt; 0), o {@code null} si no viene, no es un número JSON, no es
     *         finito o es menor que 1. Un decimal se trunca; un valor mayor que el máximo entero se
     *         recorta a {@link Integer#MAX_VALUE} (el tope por partida de la campaña limita lo cobrado).
     */
    public static Integer from(List<GameMetricDTO> metrics) {
        if (metrics == null) {
            return null;
        }
        for (GameMetricDTO metric : metrics) {
            if (metric != null && PERFORMANCE_KEY.equals(metric.getKey())) {
                return toScore(metric.getValue());
            }
        }
        return null;
    }

    private static Integer toScore(Object value) {
        if (!(value instanceof Number number)) {
            return null; // texto (aunque sea "3"), booleano o nulo: sin puntaje
        }
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            return null; // Number de una clase propia que no se puede leer como decimal
        }
        if (decimal.compareTo(BigDecimal.ONE) < 0) {
            return null;
        }
        if (decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return decimal.intValue();
    }
}
