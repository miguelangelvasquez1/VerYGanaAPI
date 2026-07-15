package com.verygana2.utils.pqrs;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

/**
 * Suma días hábiles (lunes a viernes) a una fecha. No contempla el calendario
 * de festivos colombianos todavía — mejora futura, no bloquea el MVP de PQRS.
 */
@Component
public class BusinessDayCalculator {

    public ZonedDateTime addBusinessDays(ZonedDateTime start, int businessDays) {
        ZonedDateTime result = start;
        int added = 0;
        while (added < businessDays) {
            result = result.plusDays(1);
            if (result.getDayOfWeek() != DayOfWeek.SATURDAY && result.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return result;
    }
}
