package com.verygana2.utils.pqrs;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link BusinessDayCalculator}: suma de días hábiles (lunes-viernes)
 * usada para calcular el vencimiento legal (SLA) de un PQRS.
 */
@DisplayName("BusinessDayCalculator")
class BusinessDayCalculatorTest {

    private final BusinessDayCalculator calculator = new BusinessDayCalculator();

    @Test
    @DisplayName("0 días hábiles: retorna la misma fecha de inicio sin modificarla")
    void zeroBusinessDays_returnsSameDate() {
        ZonedDateTime start = mondayAt(10);

        ZonedDateTime result = calculator.addBusinessDays(start, 0);

        assertThat(result).isEqualTo(start);
    }

    @Test
    @DisplayName("suma dentro de la misma semana: no salta ningún día")
    void withinSameWeek_addsConsecutiveDays() {
        ZonedDateTime monday = mondayAt(10);

        ZonedDateTime result = calculator.addBusinessDays(monday, 3);

        // Lunes + 3 días hábiles = Jueves.
        assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
    }

    @Test
    @DisplayName("cruzando el fin de semana: salta sábado y domingo")
    void crossingWeekend_skipsSaturdayAndSunday() {
        ZonedDateTime friday = mondayAt(10).plusDays(4); // viernes

        ZonedDateTime result = calculator.addBusinessDays(friday, 1);

        // Viernes + 1 día hábil = Lunes siguiente, no sábado.
        assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.toLocalDate()).isEqualTo(friday.toLocalDate().plusDays(3));
    }

    @Test
    @DisplayName("varias semanas de plazo (15 días hábiles, SLA típico de una PETICION)")
    void multipleWeeks_skipsAllWeekendsInBetween() {
        ZonedDateTime monday = mondayAt(10);

        ZonedDateTime result = calculator.addBusinessDays(monday, 15);

        // 15 días hábiles = 3 bloques de 5 (lunes a viernes); como se cuenta desde el
        // día SIGUIENTE al inicio, cada bloque de 5 aterriza en el lunes de la semana
        // siguiente → 3 bloques = 3 semanas completas después, mismo día de la semana.
        assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.toLocalDate()).isEqualTo(monday.toLocalDate().plusDays(21));
    }

    /** 2024-01-01 es lunes; se usa como ancla estable para todos los tests. */
    private ZonedDateTime mondayAt(int hour) {
        return ZonedDateTime.now().withYear(2024).withMonth(1).withDayOfMonth(1)
                .withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }
}
