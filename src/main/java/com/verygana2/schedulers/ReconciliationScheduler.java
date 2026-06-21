package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job semanal de reconciliación financiera.
 * Se ejecuta los lunes a las 6:00 AM UTC (1:00 AM Colombia).
 *
 * Verifica que ninguna cuenta de tesorería tenga saldo negativo
 * y loguea el estado de salud de KEYS_RESERVE.
 * Un saldo negativo es una anomalía crítica que nunca debería ocurrir
 * si el sistema de locks pesimistas funciona correctamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final TreasuryService treasuryService;

    @Scheduled(cron = "0 0 6 * * MON")
    public void runWeeklyReconciliation() {
        log.info("[RECONCILIATION-SCHEDULER] === Reconciliación semanal iniciada ===");
        try {
            treasuryService.runReconciliation();
        } catch (Exception e) {
            log.error("[RECONCILIATION-SCHEDULER] Error inesperado en reconciliación: {}", e.getMessage(), e);
        }
        log.info("[RECONCILIATION-SCHEDULER] === Reconciliación semanal finalizada ===");
    }
}
