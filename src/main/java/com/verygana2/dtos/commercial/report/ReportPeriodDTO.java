package com.verygana2.dtos.commercial.report;

import java.time.LocalDate;

/** Rango de fechas (inclusivo en ambos extremos) al que corresponde un reporte. */
public record ReportPeriodDTO(LocalDate from, LocalDate to) {
}
