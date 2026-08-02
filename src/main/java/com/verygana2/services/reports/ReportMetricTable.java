package com.verygana2.services.reports;

import java.util.List;

/** Una tabla dentro de una sección del reporte (ej. "Top 5 productos vendidos"). */
public record ReportMetricTable(String title, List<String> headers, List<List<String>> rows) {
}
