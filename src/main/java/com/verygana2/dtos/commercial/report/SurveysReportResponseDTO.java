package com.verygana2.dtos.commercial.report;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

/**
 * Métricas de encuestas de un comercial (pestaña "Encuestas"). Exclusiva de los
 * planes Estándar y Premium (ver {@code CAN_VIEW_PERFORMANCE_METRICS}).
 *
 * <p>Los conteos de sesiones ({@code startedSessions}, etc.) se miden dentro del
 * rango solicitado (por {@code startedAt}). {@code totalResponses} es acumulado.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurveysReportResponseDTO(
        ReportPeriodDTO period,
        Summary summary,
        List<SurveyRow> perSurvey,
        /** Respuestas completadas por día natural del rango, relleno con ceros. */
        List<DailyCountDTO> responsesByDay) {

    public record Summary(
            long totalSurveys,
            long draftSurveys,
            long pendingReviewSurveys,
            long approvedSurveys,
            long activeSurveys,
            long pausedSurveys,
            long completedSurveys,
            long rejectedSurveys,
            /** Respuestas acumuladas (sum de responseCount) de por vida. */
            long totalResponses,
            long startedSessions,
            long completedSessions,
            long abandonedSessions,
            long expiredSessions,
            /** completedSessions / startedSessions * 100 (null si startedSessions = 0). */
            Double completionRatePct,
            /** Sesiones completadas cuya fecha de finalización cae en el rango. */
            long responsesInPeriod,
            Double avgResponsesPerActiveSurvey,
            /** Recompensas pagadas a consumers (SurveyReward PROCESSED) dentro del rango. */
            long rewardPaidCents) {}

    public record SurveyRow(
            Long surveyId,
            String title,
            String status,
            int responseCount,
            Integer maxResponses,
            /** responseCount / maxResponses * 100 (null si no hay tope). */
            Double fillRatePct,
            long startedSessions,
            long completedSessions,
            long abandonedSessions,
            Double completionRatePct,
            int questionCount,
            ZonedDateTime createdAt,
            ZonedDateTime startsAt,
            ZonedDateTime endsAt) {}
}
