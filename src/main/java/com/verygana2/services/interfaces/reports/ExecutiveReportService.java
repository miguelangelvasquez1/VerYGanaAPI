package com.verygana2.services.interfaces.reports;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.services.reports.ReportMetricType;

public interface ExecutiveReportService {

    /**
     * Genera el reporte ejecutivo en PDF (exclusivo Premium, ver CAN_EXPORT_REPORT).
     *
     * @param metricTypes tipos de métrica a incluir; null o vacío = todas las disponibles.
     */
    byte[] generateExecutiveReportPdf(Long commercialId, List<ReportMetricType> metricTypes,
            ZonedDateTime startDate, ZonedDateTime endDate);
}
