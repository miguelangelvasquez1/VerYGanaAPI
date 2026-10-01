package com.verygana2.dtos.commercial.report;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

/**
 * Métricas de rendimiento de anuncios de un comercial (pestaña "Anuncios" del
 * panel de analítica). Exclusiva de los planes Estándar y Premium
 * (ver {@code CAN_VIEW_PERFORMANCE_METRICS}).
 *
 * <p>Todos los montos van en centavos ({@code *Cents}). Los porcentajes son 0-100
 * y pueden venir en {@code null} cuando el denominador es 0.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdsReportResponseDTO(
        ReportPeriodDTO period,
        Summary summary,
        List<AdRow> perAd,
        /** Interacciones (likes) por día natural del rango, relleno con ceros. */
        List<DailyCountDTO> interactionsByDay) {

    public record Summary(
            long totalAds,
            long activeAds,
            long pausedAds,
            long completedAds,
            long pendingAds,
            long rejectedAds,
            /** Likes recibidos dentro del rango. */
            long interactions,
            /** Likes acumulados de por vida (todos los anuncios). */
            long lifetimeInteractions,
            /** Recompensas pagadas a consumers por likes dentro del rango. */
            long rewardPaidCents,
            long totalBudgetCents,
            long spentBudgetCents,
            long remainingBudgetCents,
            /** Promedio de currentLikes/maxLikes entre los anuncios (0-100). */
            Double avgCompletionRatePct) {}

    public record AdRow(
            Long adId,
            String title,
            String status,
            /** Likes dentro del rango. */
            long interactions,
            /** Likes acumulados de por vida. */
            long lifetimeLikes,
            int maxLikes,
            Double completionRatePct,
            long rewardPerLikeCents,
            long totalBudgetCents,
            long spentBudgetCents,
            ZonedDateTime createdAt,
            ZonedDateTime startDate,
            ZonedDateTime endDate) {}
}
