package com.verygana2.controllers.admin;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.services.interfaces.finance.PayoutService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class PayoutAdminController {

    private final PayoutService payoutService;

    /**
     * Lista todos los payouts de una fecha específica.
     * Si no se pasa fecha, usa el día de hoy (UTC).
     *
     * GET /api/admin/payouts
     * GET /api/admin/payouts?date=2025-01-15
     */
    @GetMapping
    public ResponseEntity<List<PayoutResponseDTO>> getPayoutsForDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        LocalDate target = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(payoutService.getPayoutsForDate(target));
    }
}
