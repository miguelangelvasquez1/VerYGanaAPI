package com.verygana2.services.reports;

import java.time.ZonedDateTime;

/**
 * Contrato que implementa cada dominio (ventas, ads, encuestas, juegos, referidos, ...)
 * para aportar su sección al reporte ejecutivo PDF.
 *
 * <p>Basta con anotar la implementación con {@code @Component} — {@link ExecutiveReportService}
 * recolecta automáticamente todos los beans de este tipo (vía inyección de {@code List<ReportMetricProvider>})
 * sin necesidad de tocar el servicio de reporte ni el de otros dominios.
 *
 * <p>Ver {@code SalesReportMetricProvider} para un ejemplo de implementación.
 */
public interface ReportMetricProvider {

    ReportMetricType getType();

    /**
     * Construye la sección de este dominio para el comercial y rango de fechas dados.
     * Si el dominio no tiene datos para el período, debe devolver una sección con listas
     * vacías (no null) — el renderer la muestra igual, con un mensaje o simplemente vacía.
     */
    ReportMetricSection buildSection(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
}
