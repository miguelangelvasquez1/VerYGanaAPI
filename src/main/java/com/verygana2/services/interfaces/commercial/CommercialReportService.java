package com.verygana2.services.interfaces.commercial;

import java.time.ZonedDateTime;

import com.verygana2.dtos.commercial.report.AdsReportResponseDTO;
import com.verygana2.dtos.commercial.report.GamesReportResponseDTO;
import com.verygana2.dtos.commercial.report.PageVisitsReportResponseDTO;
import com.verygana2.dtos.commercial.report.SurveysReportResponseDTO;

/**
 * Métricas de rendimiento del comercial, adaptadas a su plan
 * ({@code GET /commercials/report/*}).
 *
 * <ul>
 *   <li>Anuncios / encuestas / juegos → exclusivo Estándar y Premium
 *       ({@code CAN_VIEW_PERFORMANCE_METRICS}).</li>
 *   <li>Visitas a la página oficial ("Remisión") → exclusivo Premium
 *       ({@code CAN_VIEW_PAGE_VISIT_METRICS}).</li>
 * </ul>
 *
 * El gating se aplica vía {@code @RequirePlanCapability} en la implementación
 * (el parámetro debe llamarse {@code commercialId}). {@code from} inclusivo,
 * {@code to} exclusivo.
 */
public interface CommercialReportService {

    AdsReportResponseDTO getAdsReport(Long commercialId, ZonedDateTime from, ZonedDateTime to);

    SurveysReportResponseDTO getSurveysReport(Long commercialId, ZonedDateTime from, ZonedDateTime to);

    GamesReportResponseDTO getGamesReport(Long commercialId, ZonedDateTime from, ZonedDateTime to);

    PageVisitsReportResponseDTO getPageVisitsReport(Long commercialId, ZonedDateTime from, ZonedDateTime to);
}
