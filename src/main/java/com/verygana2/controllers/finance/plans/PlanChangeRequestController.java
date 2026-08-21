package com.verygana2.controllers.finance.plans;

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

import com.verygana2.dtos.finance.plans.requests.PlanChangeRequestDTO;
import com.verygana2.dtos.finance.plans.responses.PlanChangeRequestResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PlanChangeRequestService;
import com.verygana2.services.interfaces.finance.PlanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Cambio explícito de plan (BASIC/STANDARD/PREMIUM) — un comercial nunca cambia de
 * plan implícitamente, solo renueva su plan actual salvo que use estos endpoints.
 */
@RestController
@RequestMapping("/plans/change-request")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMMERCIAL')")
public class PlanChangeRequestController {

    private final PlanChangeRequestService planChangeRequestService;
    private final CommercialContractService contractService;
    private final PlanService planService;
    private final CommercialDetailsService commercialDetailsService;

    @PostMapping
    public ResponseEntity<PlanChangeRequestResponseDTO> requestPlanChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PlanChangeRequestDTO request) {

        Long commercialId = jwt.getClaim("userId");
        PlanChangeRequest created = planChangeRequestService.requestPlanChange(
                commercialId, request.getTargetPlanCode(), request.getIntendedInvestmentAmountCents());
        return ResponseEntity.ok(toResponse(created));
    }

    @GetMapping("/current")
    public ResponseEntity<PlanChangeRequestResponseDTO> getCurrent(@AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        PlanChangeRequest current = planChangeRequestService.getCurrent(commercialId);
        return ResponseEntity.ok(current != null ? toResponse(current) : null);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PlanChangeRequestResponseDTO> cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {

        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(toResponse(planChangeRequestService.cancelPlanChangeRequest(commercialId, id)));
    }

    /** El comercial aprueba el otrosí de cambio de plan generado y lo envía a revisión de VerYGana. */
    @PostMapping("/contract/{contractId}/approve")
    public ResponseEntity<ContractSummaryResponseDTO> approveContract(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long contractId) {

        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.businessApproveContract(contractId, commercialId));
    }

    /**
     * Genera el checkout del abono requerido para que el cambio de plan aplique
     * (solo si requiredTopUpAmountCents &gt; 0 — el frontend lo llama cuando ve la
     * solicitud en PAYMENT_PENDING). El cambio se aplica automáticamente al
     * confirmarse el pago.
     */
    @PostMapping("/{id}/top-up-checkout")
    public ResponseEntity<WompiCheckoutResponseDTO> generateTopUpCheckout(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {

        Long commercialId = jwt.getClaim("userId");
        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);
        return ResponseEntity.ok(planService.generatePlanChangeTopUpCheckout(id, commercial));
    }

    private PlanChangeRequestResponseDTO toResponse(PlanChangeRequest r) {
        return new PlanChangeRequestResponseDTO(
                r.getId(),
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
