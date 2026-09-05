package com.verygana2.dtos.commercial.report;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

/**
 * Métricas de campañas de juegos brandeados de un comercial (pestaña "Juegos").
 * Exclusiva de los planes Estándar y Premium (ver {@code CAN_VIEW_PERFORMANCE_METRICS}).
 *
 * <p>Las métricas de partidas ({@code sessionsPlayed}, {@code uniquePlayers}, ...)
 * se miden dentro del rango (por {@code GameSession.startTime}). Las
 * {@code lifetime*} vienen de los contadores persistidos en la campaña.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GamesReportResponseDTO(
        ReportPeriodDTO period,
        Summary summary,
        List<CampaignRow> perCampaign,
        /** Partidas iniciadas por día natural del rango, relleno con ceros. */
        List<DailyCountDTO> playsByDay) {

    public record Summary(
            long totalCampaigns,
            long draftCampaigns,
            /** Campañas publicadas (estado ACTIVE). */
            long publishedCampaigns,
            long pausedCampaigns,
            long completedCampaigns,
            long cancelledCampaigns,
            long sessionsPlayed,
            long completedSessions,
            long uniquePlayers,
            /** completedSessions / sessionsPlayed * 100 (null si sessionsPlayed = 0). */
            Double completionRatePct,
            long totalPlayTimeSeconds,
            Double avgSessionDurationSeconds,
            long totalBudgetCents,
            long spentBudgetCents,
            /** Monedas otorgadas a jugadores dentro del rango. */
            long rewardsPaidCents,
            long lifetimeSessionsPlayed,
            long lifetimeCompletedSessions) {}

    public record CampaignRow(
            Long campaignId,
            String gameTitle,
            String status,
            long sessionsPlayed,
            long completedSessions,
            long uniquePlayers,
            Double completionRatePct,
            long totalPlayTimeSeconds,
            long budgetCents,
            long spentCents,
            ZonedDateTime startDate,
            ZonedDateTime endDate) {}
}
