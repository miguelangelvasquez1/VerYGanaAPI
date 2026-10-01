package com.verygana2.services.reports;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.commercial.report.PageVisitsReportResponseDTO;
import com.verygana2.services.interfaces.commercial.CommercialReportService;

import lombok.RequiredArgsConstructor;

/**
 * Aporta la sección de visitas a la página oficial ("Remisión") al reporte
 * ejecutivo PDF. Exclusiva Premium — igual que el resto del reporte ejecutivo.
 */
@Component
@RequiredArgsConstructor
public class PageViewsReportMetricProvider implements ReportMetricProvider {

    private final CommercialReportService reportService;

    @Override
    public ReportMetricType getType() {
        return ReportMetricType.PAGE_VIEWS;
    }

    @Override
    public ReportMetricSection buildSection(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        PageVisitsReportResponseDTO report = reportService.getPageVisitsReport(commercialId, startDate, endDate);
        PageVisitsReportResponseDTO.Summary s = report.summary();

        List<ReportMetricRow> rows = List.of(
                new ReportMetricRow("Visitas del período", String.valueOf(s.totalVisits())),
                new ReportMetricRow("Visitantes únicos", String.valueOf(s.uniqueVisitors())),
                new ReportMetricRow("Visitas acumuladas", String.valueOf(s.lifetimeVisits())),
                new ReportMetricRow("Período anterior", String.valueOf(s.previousPeriodVisits())),
                new ReportMetricRow("Variación vs. período anterior", ReportFormat.pct(s.deltaPct())),
                new ReportMetricRow("Conversión desde anuncios", ReportFormat.pct(s.conversionRatePct())));

        List<ReportMetricTable> tables = new ArrayList<>();
        if (report.visitsByAd() != null && !report.visitsByAd().isEmpty()) {
            tables.add(new ReportMetricTable(
                    "Visitas por anuncio de origen",
                    List.of("Anuncio", "Visitas", "Visitantes únicos"),
                    report.visitsByAd().stream().limit(15).map(a -> List.of(
                            a.adTitle() != null ? a.adTitle() : "—",
                            String.valueOf(a.visits()),
                            String.valueOf(a.uniqueVisitors()))).toList()));
        }
        return new ReportMetricSection("Remisión (visitas a la página oficial)", rows, tables);
    }
}
