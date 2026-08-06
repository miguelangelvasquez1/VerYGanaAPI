package com.verygana2.controllers.details;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.dtos.user.commercial.responses.PayoutReportResponseDTO;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/commercials")
@RequiredArgsConstructor
public class CommercialDetailsController {

    private final CommercialDetailsService commercialDetailsService;

    @GetMapping("/initialData")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<CommercialInitialDataResponseDTO> getCommercialInitialData(@AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getCommercialInitialData(commercialId));
    }

    @GetMapping("/report/payout")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PayoutReportResponseDTO> getPayoutReport(@AuthenticationPrincipal Jwt jwt,
            @RequestParam Integer year, @RequestParam Integer month) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getPayoutReport(commercialId, year, month));

    }

    @GetMapping("/report/payouts")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PayoutReportResponseDTO[]> getPayoutReports(@AuthenticationPrincipal Jwt jwt,
            @RequestParam Integer year) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getPayoutReports(commercialId, year));

    }

    /**
     * Los 12 meses del año dado, para graficar las ventas mensuales
     * (ej. bar chart de ventas mes a mes).
     */
    @GetMapping("/report/sales/anually")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<SalesReportResponseDTO[]> getAnuallySalesReports(@AuthenticationPrincipal Jwt jwt,
            @RequestParam Integer year) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getSalesReports(commercialId, year));
    }

    @GetMapping("/report/sales/count")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Integer> getSalesCountByDateRange(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long commercialId = jwt.getClaim("userId");
        ZoneId zone = ZoneId.of("America/Bogota");
        ZonedDateTime start = startDate.atStartOfDay(zone);
        ZonedDateTime end = endDate.plusDays(1).atStartOfDay(zone);
        return ResponseEntity.ok(commercialDetailsService.getSalesCount(commercialId, start, end));
    }

    /**
     * Listado transaccional día a día de ventas individuales dentro del rango
     * (a diferencia de /report/range, que solo trae agregados + top productos).
     */
    @GetMapping("/report/sales/daily")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PagedResponse<DailySaleResponseDTO>> getDailySales(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        Long commercialId = jwt.getClaim("userId");
        ZoneId zone = ZoneId.of("America/Bogota");
        ZonedDateTime start = startDate.atStartOfDay(zone);
        ZonedDateTime end = endDate.plusDays(1).atStartOfDay(zone);
        return ResponseEntity.ok(commercialDetailsService.getDailySales(commercialId, start, end, pageable));
    }

    @GetMapping("/{commercialId}/profile")
    public ResponseEntity<CommercialProfileResponseDTO> getCommercialProfile(@PathVariable Long commercialId) {
        return ResponseEntity.ok(commercialDetailsService.getCommercialProfile(commercialId));
    }
}