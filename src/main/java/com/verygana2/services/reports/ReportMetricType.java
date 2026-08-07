package com.verygana2.services.reports;

/**
 * Tipos de métrica que puede incluir el reporte ejecutivo PDF (exclusivo Premium,
 * ver {@code CAN_EXPORT_REPORT}). Cada tipo corresponde a un dominio con su propio
 * {@link ReportMetricProvider} — al agregar un nuevo dominio (ads, encuestas, juegos,
 * referidos, etc.) solo hace falta sumar la constante aquí y registrar el @Component
 * correspondiente; el reporte lo recoge automáticamente.
 */
public enum ReportMetricType {
    SALES,
    ADS,
    SURVEYS,
    GAMES,
    REFERRALS,
    PAGE_VIEWS,
    PET_STORE
}
