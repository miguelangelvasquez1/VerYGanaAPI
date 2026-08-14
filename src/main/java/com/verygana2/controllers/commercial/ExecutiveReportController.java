package com.verygana2.controllers.commercial;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.services.interfaces.reports.ExecutiveReportService;
import com.verygana2.services.reports.ReportMetricType;

import lombok.RequiredArgsConstructor;

/**
 * Reporte ejecutivo exportable en PDF, exclusivo Premium (ver CAN_EXPORT_REPORT).
 * Flexible en las métricas que incluye: 0 (todas), 1 o varias, según lo que el
 * comercial pida en {@code metrics}.
 */
@RestController
@RequestMapping("/commercial/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class ExecutiveReportController {

    private final ExecutiveReportService executiveReportService;

    @GetMapping("/executive")
    public ResponseEntity<byte[]> getExecutiveReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<ReportMetricType> metrics) {

        Long commercialId = jwt.getClaim("userId");
        ZoneId zone = ZoneId.of("America/Bogota");
        ZonedDateTime start = startDate.atStartOfDay(zone);
        ZonedDateTime end = endDate.plusDays(1).atStartOfDay(zone);

        byte[] pdfBytes = executiveReportService.generateExecutiveReportPdf(commercialId, metrics, start, end);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("reporte-ejecutivo.pdf").build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
