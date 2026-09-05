package com.verygana2.services.reports;

/** Formateo compartido por los {@link ReportMetricProvider} de métricas del comercial. */
final class ReportFormat {

    private ReportFormat() {}

    /** Centavos de COP → "$1.234.567". */
    static String cop(long cents) {
        return "$" + String.format("%,d", cents / 100).replace(",", ".");
    }

    /** Porcentaje 0-100 → "12,5 %"; null → "—". */
    static String pct(Double value) {
        if (value == null) {
            return "—";
        }
        return String.format("%.2f", value).replace(".", ",") + " %";
    }

    /** Segundos → "3 min 12 s". */
    static String duration(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return min > 0 ? min + " min " + sec + " s" : sec + " s";
    }
}
