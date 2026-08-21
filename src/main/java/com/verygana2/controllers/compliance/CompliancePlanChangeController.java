package com.verygana2.controllers.compliance;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.finance.plans.responses.PlanChangeReviewListItemDTO;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.services.interfaces.finance.PlanChangeRequestService;

import lombok.RequiredArgsConstructor;

/**
 * Cola de compliance para solicitudes de cambio de plan. La aprobación/rechazo en sí
 * se hace sobre el contrato vinculado vía los endpoints ya existentes de
 * {@code ComplianceContractController} (aprobar/rechazar/firma funcionan igual
 * sin importar el propósito del contrato).
 */
@RestController
@RequestMapping("/compliance/plan-changes")
@PreAuthorize("hasRole('COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class CompliancePlanChangeController {

    private final PlanChangeRequestService planChangeRequestService;

    @GetMapping
    public ResponseEntity<List<PlanChangeReviewListItemDTO>> listPendingReview() {
        List<PlanChangeReviewListItemDTO> result = planChangeRequestService.listPendingReview().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    private PlanChangeReviewListItemDTO toResponse(PlanChangeRequest r) {
        return new PlanChangeReviewListItemDTO(
                r.getId(),
                r.getCommercial().getId(),
                r.getCommercial().getCompanyName(),
                r.getCommercial().getUser().getEmail(),
                r.getFromPlan() != null ? r.getFromPlan().getCode() : null,
                r.getToPlan().getCode(),
                r.getRequiredTopUpAmountCents(),
                r.getStatus(),
                r.getContract() != null ? r.getContract().getId() : null,
                r.getContract() != null ? r.getContract().getStatus() : null,
                r.getRequestedAt(),
                r.getAppliedAt());
    }
}
