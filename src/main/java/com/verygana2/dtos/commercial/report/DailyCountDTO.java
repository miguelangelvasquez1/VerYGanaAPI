package com.verygana2.dtos.commercial.report;

import java.time.LocalDate;

/** Un punto de una serie temporal diaria (rango relleno con ceros). */
public record DailyCountDTO(LocalDate date, long count) {
}
