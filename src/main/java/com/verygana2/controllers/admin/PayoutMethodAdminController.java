package com.verygana2.controllers.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.RejectPayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/payout-methods")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class PayoutMethodAdminController {

    private final PayoutMethodService payoutMethodService;

    /**
     * Lista métodos de pago filtrados por estado.
     * Uso principal: ver los BANK_TRANSFER pendientes de revisión.
     *
     * GET /api/admin/payout-methods?status=UNDER_REVIEW&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<PagedResponse<PayoutMethodResponseDTO>> getByStatus(
            @RequestParam(defaultValue = "UNDER_REVIEW") VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(payoutMethodService.getByStatus(status, pageable));
    }

    /**
     * Aprueba un método BANK_TRANSFER pendiente de revisión.
     * El commercial podrá recibir pagos a esta cuenta a partir de este momento.
     *
     * POST /api/admin/payout-methods/{id}/verify
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<Void> verify(@PathVariable Long id) {
        payoutMethodService.adminVerifyMethod(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Rechaza un método de pago, indicando el motivo al commercial.
     *
     * POST /api/admin/payout-methods/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectPayoutMethodRequestDTO request) {

        payoutMethodService.adminRejectMethod(id, request.getReason());
        return ResponseEntity.ok().build();
    }
}
