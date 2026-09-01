package com.verygana2.controllers.commercial;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO;
import com.verygana2.models.enums.commercial.DashboardPeriod;
import com.verygana2.services.interfaces.commercial.CommercialDashboardService;

import lombok.RequiredArgsConstructor;

/**
 * Panel de inicio del comercial: un único endpoint agregador que arma toda la
 * pantalla de arranque (KPIs con delta, tendencia, top productos, engagement,
 * uso vs. límites del plan, activos activos, accesos directos y pendientes).
 * Adaptable al plan — ver {@link CommercialDashboardSummaryResponseDTO}.
 */
@RestController
@RequestMapping("/commercial/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class CommercialDashboardController {

    private final CommercialDashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<CommercialDashboardSummaryResponseDTO> getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "LAST_30_DAYS") DashboardPeriod period) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(dashboardService.getSummary(commercialId, period));
    }
}
