package com.verygana2.controllers.admin;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.responses.CashRefundResponseDTO;
import com.verygana2.services.interfaces.finance.CashRefundService;

import lombok.RequiredArgsConstructor;

/**
 * Reembolsos en efectivo pendientes de pago manual (ver PurchaseItemCashRefund
 * y PurchaseItemRefundService) — Wompi no documenta un endpoint de reverso de
 * cargos, así que el admin transfiere por fuera de la app y lo confirma aquí.
 */
@RestController
@RequestMapping("/admin/cash-refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CashRefundAdminController {

    private final CashRefundService cashRefundService;

    /** GET /admin/cash-refunds — reembolsos pendientes de transferencia manual. */
    @GetMapping
    public ResponseEntity<PagedResponse<CashRefundResponseDTO>> getPendingRefunds(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(cashRefundService.getPendingPayments(pageable));
    }

    /** PATCH /admin/cash-refunds/{id}/mark-paid — el admin confirma que ya hizo la transferencia. */
    @PatchMapping("/{id}/mark-paid")
    public ResponseEntity<Void> markPaid(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Long adminUserId = jwt.getClaim("userId");
        cashRefundService.markPaid(id, adminUserId);
        return ResponseEntity.ok().build();
    }
}
