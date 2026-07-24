package com.verygana2.controllers.compliance;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.user.commercial.onboarding.ContractRejectRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractReviewListItemDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.services.interfaces.commercial.CommercialContractService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Paso 11: revisión de VERYGANA sobre los Contratos Marco generados por comerciantes. */
@RestController
@RequestMapping("/compliance/contracts")
@PreAuthorize("hasRole('ROLE_COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceContractController {

    private final CommercialContractService contractService;

    /**
     * Listado de contratos para el panel de compliance. Sin `status`, trae todos los
     * relevantes para VERYGANA (PENDING_VERYGANA_REVIEW, APPROVED, REJECTED). Con
     * `status`, filtra a ese estado únicamente.
     */
    @GetMapping
    public ResponseEntity<List<ContractReviewListItemDTO>> listContracts(
            @RequestParam(required = false) ContractStatus status) {
        return ResponseEntity.ok(contractService.listContracts(status));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ContractSummaryResponseDTO> getForReview(@PathVariable Long contractId) {
        return ResponseEntity.ok(contractService.getForReview(contractId));
    }

    @PostMapping("/{contractId}/approve")
    public ResponseEntity<ContractSummaryResponseDTO> approve(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long contractId) {
        Long reviewerUserId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.approve(contractId, reviewerUserId));
    }

    @PostMapping("/{contractId}/reject")
    public ResponseEntity<ContractSummaryResponseDTO> reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long contractId,
            @Valid @RequestBody ContractRejectRequestDTO dto) {
        Long reviewerUserId = jwt.getClaim("userId");
        return ResponseEntity.ok(contractService.reject(contractId, reviewerUserId, dto.getReason(), dto.getDocumentsIssue()));
    }

    /**
     * Registra la firma del contrato. Manual mientras no exista un proveedor real de
     * firma electrónica; en producción este paso lo dispara el webhook del proveedor.
     */
    @PostMapping("/{contractId}/esignature/mark-signed")
    public ResponseEntity<ContractSummaryResponseDTO> markSigned(@PathVariable Long contractId) {
        return ResponseEntity.ok(contractService.markSigned(contractId));
    }
}
