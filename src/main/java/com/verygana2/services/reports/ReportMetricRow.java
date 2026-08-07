package com.verygana2.services.reports;

/** Un par etiqueta/valor dentro de una sección del reporte (ej. "Ventas totales" → "$1.200.000"). */
public record ReportMetricRow(String label, String value) {
}
