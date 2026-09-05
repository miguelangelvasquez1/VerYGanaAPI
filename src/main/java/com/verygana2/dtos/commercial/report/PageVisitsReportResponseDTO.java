package com.verygana2.dtos.commercial.report;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

/**
 * Métrica de visitas a la página oficial del empresario / "Remisión"
 * (pestaña "Remisión"). Exclusiva del plan Premium
 * (ver {@code CAN_VIEW_PAGE_VISIT_METRICS}).
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageVisitsReportResponseDTO(
        ReportPeriodDTO period,
        Summary summary,
        List<DailyCountDTO> visitsByDay,
        List<AdVisits> visitsByAd,
        List<RecentVisit> recentVisits) {

    public record Summary(
            long totalVisits,
            long uniqueVisitors,
            long lifetimeVisits,
            long previousPeriodVisits,
            /** Variación % vs. el periodo inmediatamente anterior (null si el previo fue 0). */
            Double deltaPct,
            /**
             * % de consumers que dieron like a un anuncio del comercial y ADEMÁS visitaron su
             * página oficial, sobre el total de consumers que dieron like (ambos en el rango).
             * null si nadie dio like en el rango. Acotado 0-100 por construcción: es una
             * intersección de personas, no una razón entre conteos de eventos independientes
             * (un mismo consumer puede generar varias visitas sin volver a dar like).
             */
            Double conversionRatePct) {}

    public record AdVisits(Long adId, String adTitle, long visits, long uniqueVisitors) {}

    public record RecentVisit(Long adId, String adTitle, ZonedDateTime visitedAt) {}
}
