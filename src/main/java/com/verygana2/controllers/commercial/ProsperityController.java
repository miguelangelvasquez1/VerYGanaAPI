package com.verygana2.controllers.commercial;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.prosperity.ProsperityMovementResponseDTO;
import com.verygana2.dtos.prosperity.ProsperitySummaryResponseDTO;
import com.verygana2.services.interfaces.finance.ProsperityService;

import lombok.RequiredArgsConstructor;

/**
 * Consulta del Saldo de Prosperidad del comercial (Contrato B 10.23: el panel consulta
 * el libro, nunca recalcula). Sin restricción de plan: un comercial que dejó de ser
 * STANDARD sigue viendo su saldo congelado y su historial.
 */
@RestController
@RequestMapping("/commercial/prosperity")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class ProsperityController {

    private final ProsperityService prosperityService;

    /** GET /commercial/prosperity */
    @GetMapping
    public ResponseEntity<ProsperitySummaryResponseDTO> getSummary(@AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(prosperityService.getSummary(commercialId));
    }

    /** GET /commercial/prosperity/movements?page=0&size=20 — más reciente primero. */
    @GetMapping("/movements")
    public ResponseEntity<PagedResponse<ProsperityMovementResponseDTO>> getMovements(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(PagedResponse.from(prosperityService.getMovements(commercialId, pageable)));
    }
}
