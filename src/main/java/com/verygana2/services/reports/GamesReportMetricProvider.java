package com.verygana2.services.reports;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.commercial.report.GamesReportResponseDTO;
import com.verygana2.services.interfaces.commercial.CommercialReportService;

import lombok.RequiredArgsConstructor;

/** Aporta la sección de juegos/campañas al reporte ejecutivo PDF, reutilizando {@link CommercialReportService}. */
@Component
@RequiredArgsConstructor
public class GamesReportMetricProvider implements ReportMetricProvider {

    private final CommercialReportService reportService;

    @Override
    public ReportMetricType getType() {
        return ReportMetricType.GAMES;
    }

    @Override
    public ReportMetricSection buildSection(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate) {
        GamesReportResponseDTO report = reportService.getGamesReport(commercialId, startDate, endDate);
        GamesReportResponseDTO.Summary s = report.summary();

        List<ReportMetricRow> rows = List.of(
                new ReportMetricRow("Campañas totales", String.valueOf(s.totalCampaigns())),
                new ReportMetricRow("Campañas publicadas", String.valueOf(s.publishedCampaigns())),
                new ReportMetricRow("Partidas jugadas", String.valueOf(s.sessionsPlayed())),
                new ReportMetricRow("Partidas completadas", String.valueOf(s.completedSessions())),
                new ReportMetricRow("Jugadores únicos", String.valueOf(s.uniquePlayers())),
                new ReportMetricRow("Tasa de finalización", ReportFormat.pct(s.completionRatePct())),
                new ReportMetricRow("Tiempo de juego total", ReportFormat.duration(s.totalPlayTimeSeconds())),
                new ReportMetricRow("Presupuesto gastado", ReportFormat.cop(s.spentBudgetCents())));

        List<ReportMetricTable> tables = new ArrayList<>();
        if (report.perCampaign() != null && !report.perCampaign().isEmpty()) {
            tables.add(new ReportMetricTable(
                    "Rendimiento por campaña",
                    List.of("Juego", "Estado", "Partidas", "Completadas", "Jugadores", "Finalización"),
                    report.perCampaign().stream().limit(15).map(c -> List.of(
                            c.gameTitle(),
                            c.status(),
                            String.valueOf(c.sessionsPlayed()),
                            String.valueOf(c.completedSessions()),
                            String.valueOf(c.uniquePlayers()),
                            ReportFormat.pct(c.completionRatePct()))).toList()));
        }
        return new ReportMetricSection("Juegos", rows, tables);
    }
}
