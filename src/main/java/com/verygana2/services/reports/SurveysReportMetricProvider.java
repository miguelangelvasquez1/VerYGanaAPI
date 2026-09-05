package com.verygana2.services.reports;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.commercial.report.SurveysReportResponseDTO;
import com.verygana2.services.interfaces.commercial.CommercialReportService;

import lombok.RequiredArgsConstructor;

/** Aporta la sección de encuestas al reporte ejecutivo PDF, reutilizando {@link CommercialReportService}. */
@Component
@RequiredArgsConstructor
public class SurveysReportMetricProvider implements ReportMetricProvider {

    private final CommercialReportService reportService;

    @Override
    public ReportMetricType getType() {
        return ReportMetricType.SURVEYS;
    }

    @Override
    public ReportMetricSection buildSection(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        SurveysReportResponseDTO report = reportService.getSurveysReport(commercialId, startDate, endDate);
        SurveysReportResponseDTO.Summary s = report.summary();

        List<ReportMetricRow> rows = List.of(
                new ReportMetricRow("Encuestas totales", String.valueOf(s.totalSurveys())),
                new ReportMetricRow("Encuestas activas", String.valueOf(s.activeSurveys())),
                new ReportMetricRow("Respuestas acumuladas", String.valueOf(s.totalResponses())),
                new ReportMetricRow("Respuestas del período", String.valueOf(s.responsesInPeriod())),
                new ReportMetricRow("Sesiones iniciadas", String.valueOf(s.startedSessions())),
                new ReportMetricRow("Tasa de finalización", ReportFormat.pct(s.completionRatePct())),
                new ReportMetricRow("Recompensas pagadas", ReportFormat.cop(s.rewardPaidCents())));

        List<ReportMetricTable> tables = new ArrayList<>();
        if (report.perSurvey() != null && !report.perSurvey().isEmpty()) {
            tables.add(new ReportMetricTable(
                    "Rendimiento por encuesta",
                    List.of("Encuesta", "Estado", "Respuestas", "Cupo", "% llenado", "Finalización"),
                    report.perSurvey().stream().limit(15).map(q -> List.of(
                            q.title(),
                            q.status(),
                            String.valueOf(q.responseCount()),
                            q.maxResponses() != null ? String.valueOf(q.maxResponses()) : "—",
                            ReportFormat.pct(q.fillRatePct()),
                            ReportFormat.pct(q.completionRatePct()))).toList()));
        }
        return new ReportMetricSection("Encuestas", rows, tables);
    }
}
