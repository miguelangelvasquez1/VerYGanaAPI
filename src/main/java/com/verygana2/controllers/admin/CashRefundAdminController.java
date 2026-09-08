package com.verygana2.controllers.admin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.responses.CashRefundResponseDTO;
import com.verygana2.models.enums.finance.CashRefundStatus;
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

    /**
     * GET /admin/cash-refunds — reembolsos en efectivo, opcionalmente filtrados
     * por status y/o rango de fechas de creación (ambos extremos opcionales e
     * independientes entre sí — ver PurchaseItemCashRefundRepository.findByStatusAndRangeDates).
     */
    @GetMapping
    public ResponseEntity<PagedResponse<CashRefundResponseDTO>> getPendingRefunds(
            @RequestParam(required = false) CashRefundStatus status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        ZoneId bogota = ZoneId.of("America/Bogota");
        ZonedDateTime start = startDate != null ? startDate.atStartOfDay(bogota) : null;
        // Exclusivo: hasta el inicio del día siguiente, para incluir todo endDate completo.
        ZonedDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay(bogota) : null;
        return ResponseEntity.ok(cashRefundService.getRefunds(status, start, end, pageable));
    }

    /**
     * GET /admin/cash-refunds/by-purchase-item/{purchaseItemId} — reembolso
     * asociado a ese ítem, si existe. Pensado para el detalle de un PQRS
     * (action=REFUND): permite mostrar los datos bancarios y el botón de
     * marcar como pagado sin salir de la solicitud. 404 si el ítem no tiene
     * un reembolso en efectivo asociado (ej. PQRS resuelto con DISMISS).
     */
    @GetMapping("/by-purchase-item/{purchaseItemId}")
    public ResponseEntity<CashRefundResponseDTO> getByPurchaseItemId(@PathVariable Long purchaseItemId) {
        return cashRefundService.findByPurchaseItemId(purchaseItemId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** PATCH /admin/cash-refunds/{id}/mark-paid — el admin confirma que ya hizo la transferencia. */
    @PatchMapping("/{id}/mark-paid")
    public ResponseEntity<Void> markPaid(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Long adminUserId = jwt.getClaim("userId");
        cashRefundService.markPaid(id, adminUserId);
        return ResponseEntity.ok().build();
    }
}
