package com.verygana2.models.enums.commercial;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Rango temporal del panel de inicio del comercial
 * ({@code GET /commercial/dashboard/summary}).
 *
 * <p>Cada valor sabe resolverse a una {@link Window} con el periodo actual y el
 * periodo inmediatamente anterior de la misma duración, para poder mostrar el
 * delta (variación %) en las tarjetas de KPI.
 */
public enum DashboardPeriod {

    /** Hoy (00:00 hasta ahora); se compara contra ayer. */
    TODAY,
    /** Los últimos 7 días naturales incluyendo hoy; se compara contra los 7 previos. */
    LAST_7_DAYS,
    /** Los últimos 30 días naturales incluyendo hoy; se compara contra los 30 previos. */
    LAST_30_DAYS,
    /** El mes calendario en curso; se compara contra el mes calendario anterior. */
    THIS_MONTH;

    /**
     * Ventana resuelta: {@code [start, end)} para el periodo actual y
     * {@code [previousStart, previousEnd)} para el anterior. Todos los límites son
     * instantes con zona; {@code end} y {@code previousEnd} son exclusivos.
     */
    public record Window(ZonedDateTime start, ZonedDateTime end,
                         ZonedDateTime previousStart, ZonedDateTime previousEnd) {

        /** Número de días naturales que abarca el periodo actual (para iterar la tendencia). */
        public long days() {
            return Duration.between(start, end).toDays();
        }
    }

    public Window resolve(ZoneId zone) {
        ZonedDateTime todayStart = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone);

        return switch (this) {
            case TODAY -> {
                ZonedDateTime end = todayStart.plusDays(1);
                yield new Window(todayStart, end, todayStart.minusDays(1), todayStart);
            }
            case LAST_7_DAYS -> {
                ZonedDateTime end = todayStart.plusDays(1);
                ZonedDateTime start = end.minusDays(7);
                yield new Window(start, end, start.minusDays(7), start);
            }
            case LAST_30_DAYS -> {
                ZonedDateTime end = todayStart.plusDays(1);
                ZonedDateTime start = end.minusDays(30);
                yield new Window(start, end, start.minusDays(30), start);
            }
            case THIS_MONTH -> {
                ZonedDateTime start = todayStart.withDayOfMonth(1);
                ZonedDateTime end = start.plusMonths(1);
                yield new Window(start, end, start.minusMonths(1), start);
            }
        };
    }
}
