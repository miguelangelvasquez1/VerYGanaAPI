package com.verygana2.controllers.admin;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.prosperity.ProsperityAdjustmentRequestDTO;
import com.verygana2.dtos.prosperity.ProsperityMovementResponseDTO;
import com.verygana2.dtos.prosperity.ProsperityReconciliationResultDTO;
import com.verygana2.dtos.prosperity.ProsperityReversalRequestDTO;
import com.verygana2.dtos.prosperity.ProsperitySummaryResponseDTO;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.finance.ProsperityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Administración del Motor de Prosperidad: consulta por comercial, reversión de Umbrales
 * (10.20), ajustes compensatorios (10.27) y conciliación.
 */
@RestController
@RequestMapping("/admin/prosperity")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProsperityAdminController {

    private final ProsperityService prosperityService;
    private final UserIdResolver userIdResolver;

    /** GET /admin/prosperity/commercials/{publicId} */
    @GetMapping("/commercials/{publicId}")
    public ResponseEntity<ProsperitySummaryResponseDTO> getSummary(@PathVariable UUID publicId) {
        return ResponseEntity.ok(prosperityService.getSummary(userIdResolver.toInternalId(publicId)));
    }

    /** GET /admin/prosperity/commercials/{publicId}/movements?page=0&size=20 */
    @GetMapping("/commercials/{publicId}/movements")
    public ResponseEntity<PagedResponse<ProsperityMovementResponseDTO>> getMovements(
            @PathVariable UUID publicId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(
                prosperityService.getMovements(userIdResolver.toInternalId(publicId), pageable)));
    }

    /** POST /admin/prosperity/thresholds/{investmentId}/reversal */
    @PostMapping("/thresholds/{investmentId}/reversal")
    public ResponseEntity<ProsperityMovementResponseDTO> reverseThreshold(
            @PathVariable Long investmentId,
            @Valid @RequestBody ProsperityReversalRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(prosperityService.reverseThreshold(investmentId, request, adminId));
    }

    /** POST /admin/prosperity/commercials/{publicId}/adjustments */
    @PostMapping("/commercials/{publicId}/adjustments")
    public ResponseEntity<ProsperityMovementResponseDTO> adjust(
            @PathVariable UUID publicId,
            @Valid @RequestBody ProsperityAdjustmentRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(prosperityService.adjust(userIdResolver.toInternalId(publicId), request, adminId));
    }

    /** POST /admin/prosperity/reconciliation — conciliación bajo demanda. */
    @PostMapping("/reconciliation")
    public ResponseEntity<ProsperityReconciliationResultDTO> reconcile() {
        return ResponseEntity.ok(prosperityService.reconcile());
    }
}
