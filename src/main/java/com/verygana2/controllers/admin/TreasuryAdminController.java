package com.verygana2.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.treasury.TreasuryBalanceResponseDTO;
import com.verygana2.dtos.treasury.TreasuryMovementResponseDTO;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/treasury")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class TreasuryAdminController {

    private final TreasuryService treasuryService;

    /**
     * Saldos actuales de las 4 cuentas virtuales con estado de salud.
     *
     * GET /api/admin/treasury/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<TreasuryBalanceResponseDTO> getBalance() {
        return ResponseEntity.ok(treasuryService.getBalanceReport());
    }

    /**
     * Historial paginado de movimientos de una cuenta específica.
     *
     * GET /api/admin/treasury/KEYS_RESERVE/movements?page=0&size=20
     */
    @GetMapping("/{code}/movements")
    public ResponseEntity<Page<TreasuryMovementResponseDTO>> getMovements(
            @PathVariable TreasuryAccountCode code,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(treasuryService.getMovements(code, pageable));
    }
}
