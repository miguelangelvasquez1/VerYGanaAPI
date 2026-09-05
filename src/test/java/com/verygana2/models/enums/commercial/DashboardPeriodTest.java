package com.verygana2.models.enums.commercial;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DashboardPeriod.resolve")
class DashboardPeriodTest {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    @Test
    @DisplayName("TODAY: [hoy 00:00, mañana 00:00) y el periodo anterior es ayer")
    void today() {
        DashboardPeriod.Window w = DashboardPeriod.TODAY.resolve(ZONE);
        ZonedDateTime todayStart = ZonedDateTime.now(ZONE).toLocalDate().atStartOfDay(ZONE);

        assertThat(w.start()).isEqualTo(todayStart);
        assertThat(w.end()).isEqualTo(todayStart.plusDays(1));
        assertThat(w.previousStart()).isEqualTo(todayStart.minusDays(1));
        assertThat(w.previousEnd()).isEqualTo(todayStart);
        assertThat(w.days()).isEqualTo(1);
    }

    @Test
    @DisplayName("LAST_7_DAYS: 7 días naturales incluyendo hoy, comparados contra los 7 previos")
    void last7Days() {
        DashboardPeriod.Window w = DashboardPeriod.LAST_7_DAYS.resolve(ZONE);

        assertThat(w.days()).isEqualTo(7);
        assertThat(w.start()).isEqualTo(w.end().minusDays(7));
        assertThat(w.previousEnd()).isEqualTo(w.start());
        assertThat(w.previousStart()).isEqualTo(w.start().minusDays(7));
    }

    @Test
    @DisplayName("LAST_30_DAYS: 30 días naturales y periodo anterior contiguo")
    void last30Days() {
        DashboardPeriod.Window w = DashboardPeriod.LAST_30_DAYS.resolve(ZONE);

        assertThat(w.days()).isEqualTo(30);
        assertThat(w.previousEnd()).isEqualTo(w.start());
        assertThat(w.previousStart()).isEqualTo(w.start().minusDays(30));
    }

    @Test
    @DisplayName("THIS_MONTH: mes calendario en curso vs. mes calendario anterior")
    void thisMonth() {
        DashboardPeriod.Window w = DashboardPeriod.THIS_MONTH.resolve(ZONE);
        LocalDate today = LocalDate.now(ZONE);

        assertThat(w.start().toLocalDate()).isEqualTo(today.withDayOfMonth(1));
        assertThat(w.end()).isEqualTo(w.start().plusMonths(1));
        assertThat(w.previousStart()).isEqualTo(w.start().minusMonths(1));
        assertThat(w.previousEnd()).isEqualTo(w.start());
    }
}
