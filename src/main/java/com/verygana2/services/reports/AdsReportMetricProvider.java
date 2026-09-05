package com.verygana2.services.reports;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.commercial.report.AdsReportResponseDTO;
import com.verygana2.services.interfaces.commercial.CommercialReportService;

import lombok.RequiredArgsConstructor;

/** Aporta la sección de anuncios al reporte ejecutivo PDF, reutilizando {@link CommercialReportService}. */
@Component
@RequiredArgsConstructor
public class AdsReportMetricProvider implements ReportMetricProvider {

    private final CommercialReportService reportService;

    @Override
    public ReportMetricType getType() {
        return ReportMetricType.ADS;
    }

    @Override
    public ReportMetricSection buildSection(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        AdsReportResponseDTO report = reportService.getAdsReport(commercialId, startDate, endDate);
        AdsReportResponseDTO.Summary s = report.summary();

        List<ReportMetricRow> rows = List.of(
                new ReportMetricRow("Anuncios totales", String.valueOf(s.totalAds())),
                new ReportMetricRow("Anuncios activos", String.valueOf(s.activeAds())),
                new ReportMetricRow("Interacciones (likes) del período", String.valueOf(s.interactions())),
                new ReportMetricRow("Recompensas pagadas", ReportFormat.cop(s.rewardPaidCents())),
                new ReportMetricRow("Presupuesto gastado", ReportFormat.cop(s.spentBudgetCents())),
                new ReportMetricRow("% de avance promedio", ReportFormat.pct(s.avgCompletionRatePct())));

        // Las visitas a la página oficial ("Remisión") tienen su propia sección — ver
        // PageViewsReportMetricProvider — porque son exclusivas Premium; este reporte de
        // anuncios también lo ve un comercial Estándar (CAN_VIEW_PERFORMANCE_METRICS).
        List<ReportMetricTable> tables = new ArrayList<>();
        if (report.perAd() != null && !report.perAd().isEmpty()) {
            tables.add(new ReportMetricTable(
                    "Rendimiento por anuncio",
                    List.of("Anuncio", "Estado", "Interacciones", "% avance", "Gastado"),
                    report.perAd().stream().limit(15).map(a -> List.of(
                            a.title(),
                            a.status(),
                            String.valueOf(a.interactions()),
                            ReportFormat.pct(a.completionRatePct()),
                            ReportFormat.cop(a.spentBudgetCents()))).toList()));
        }
        return new ReportMetricSection("Anuncios", rows, tables);
    }
}
