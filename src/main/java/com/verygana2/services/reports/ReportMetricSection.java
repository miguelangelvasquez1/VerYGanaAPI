package com.verygana2.services.reports;

import java.util.List;

/**
 * Una sección independiente del reporte ejecutivo, producida por un {@link ReportMetricProvider}.
 * El renderer del PDF no conoce nada específico de cada dominio — solo itera título, filas y tablas.
 */
public record ReportMetricSection(String title, List<ReportMetricRow> rows, List<ReportMetricTable> tables) {

    public ReportMetricSection {
        rows = rows != null ? rows : List.of();
        tables = tables != null ? tables : List.of();
    }
}
