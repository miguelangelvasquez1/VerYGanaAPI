package com.verygana2.controllers.commercial;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.commercial.report.AdsReportResponseDTO;
import com.verygana2.dtos.commercial.report.GamesReportResponseDTO;
import com.verygana2.dtos.commercial.report.PageVisitsReportResponseDTO;
import com.verygana2.dtos.commercial.report.SurveysReportResponseDTO;
import com.verygana2.services.interfaces.commercial.CommercialReportService;

import lombok.RequiredArgsConstructor;

/**
 * Métricas de rendimiento del comercial para el panel de analítica
 * (frontend {@code /commercial/analytics}). Cada pestaña tiene su endpoint:
 *
 * <ul>
 *   <li>{@code GET /commercials/report/ads}         → Anuncios   (Estándar y Premium)</li>
 *   <li>{@code GET /commercials/report/surveys}     → Encuestas  (Estándar y Premium)</li>
 *   <li>{@code GET /commercials/report/games}       → Juegos     (Estándar y Premium)</li>
 *   <li>{@code GET /commercials/report/page-visits} → Remisión   (solo Premium)</li>
 * </ul>
 *
 * El gating por plan lo aplica {@link CommercialReportService} vía
 * {@code @RequirePlanCapability} (respuesta 400 si el plan no lo permite).
 *
 * <p>{@code from} / {@code to} son fechas ISO (yyyy-MM-dd) opcionales; por defecto
 * los últimos 30 días. Zona horaria {@code America/Bogota}.
 */
@RestController
@RequestMapping("/commercials/report")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class CommercialReportController {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final CommercialReportService reportService;

    @GetMapping("/ads")
    public ResponseEntity<AdsReportResponseDTO> getAdsReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(reportService.getAdsReport(jwt.getClaim("userId"), r.start(), r.end()));
    }

    @GetMapping("/surveys")
    public ResponseEntity<SurveysReportResponseDTO> getSurveysReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(reportService.getSurveysReport(jwt.getClaim("userId"), r.start(), r.end()));
    }

    @GetMapping("/games")
    public ResponseEntity<GamesReportResponseDTO> getGamesReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(reportService.getGamesReport(jwt.getClaim("userId"), r.start(), r.end()));
    }

    @GetMapping("/page-visits")
    public ResponseEntity<PageVisitsReportResponseDTO> getPageVisitsReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(reportService.getPageVisitsReport(jwt.getClaim("userId"), r.start(), r.end()));
    }

    private record Range(ZonedDateTime start, ZonedDateTime end) {}

    private Range resolveRange(LocalDate from, LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now(ZONE);
        LocalDate fromDate = from != null ? from : toDate.minusDays(DEFAULT_RANGE_DAYS);
        if (fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }
        return new Range(fromDate.atStartOfDay(ZONE), toDate.plusDays(1).atStartOfDay(ZONE));
    }
}
