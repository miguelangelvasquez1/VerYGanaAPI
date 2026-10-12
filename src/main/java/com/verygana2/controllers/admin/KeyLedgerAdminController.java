package com.verygana2.controllers.admin;

import java.time.ZonedDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.finance.responses.KeyExpiryRunResponseDTO;
import com.verygana2.services.interfaces.finance.KeyExpiryService;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.KeyLedgerService.ReconciliationResult;

import lombok.RequiredArgsConstructor;

/**
 * Corre bajo demanda los dos jobs del Libro Mayor de Llaves (MP-12): el vencimiento de
 * lotes y la conciliación. Hacen lo mismo que sus schedulers; sirven para operar y probar
 * sin esperar a la corrida programada.
 */
@RestController
@RequestMapping("/admin/key-ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KeyLedgerAdminController {

    private final KeyExpiryService keyExpiryService;
    private final KeyLedgerService keyLedgerService;

    /**
     * POST /admin/key-ledger/expiry — vence ya los lotes vencidos.
     * POST /admin/key-ledger/expiry?asOf=2026-11-01T06:00:00Z — como si fuera esa fecha.
     * {@code asOf} solo funciona con financial.allow-simulated-expiry encendido (dev); en
     * producción responde 422.
     */
    @PostMapping("/expiry")
    public ResponseEntity<KeyExpiryRunResponseDTO> runExpiry(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime asOf) {
        return ResponseEntity.ok(asOf == null
                ? keyExpiryService.processExpiredKeys()
                : keyExpiryService.processExpiredKeysAsOf(asOf));
    }

    /** POST /admin/key-ledger/reconciliation — billeteras cuyo saldo no cuadra con su libro o sus lotes. */
    @PostMapping("/reconciliation")
    public ResponseEntity<ReconciliationResult> reconcile() {
        return ResponseEntity.ok(keyLedgerService.reconcile());
    }
}
