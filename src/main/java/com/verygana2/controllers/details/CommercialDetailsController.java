package com.verygana2.controllers.details;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
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
    public ResponseEntity<CommercialInitialDataResponseDTO> getCommercialInitialData (@AuthenticationPrincipal Jwt jwt){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getCommercialInitialData(commercialId));
    }

    @GetMapping("/report")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PayoutReportResponseDTO> getPayoutReport (@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year, @RequestParam Integer month){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(commercialDetailsService.getPayoutReport(commercialId, year, month));
        
    }

    /**
     * Reporte de ventas por rango de fechas arbitrario (no limitado a mes calendario).
     * endDate es inclusivo: internamente se filtra hasta el final de ese día.
     */
    @GetMapping("/report/range")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<SalesReportResponseDTO> getSalesReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long commercialId = jwt.getClaim("userId");
        ZoneId zone = ZoneId.of("America/Bogota");
        ZonedDateTime start = startDate.atStartOfDay(zone);
        ZonedDateTime end = endDate.plusDays(1).atStartOfDay(zone);
        return ResponseEntity.ok(commercialDetailsService.getSalesReport(commercialId, start, end));
    }

    @GetMapping("/{commercialId}/profile")
    public ResponseEntity<CommercialProfileResponseDTO> getCommercialProfile (@PathVariable Long commercialId){
        return ResponseEntity.ok(commercialDetailsService.getCommercialProfile(commercialId));
    }
}