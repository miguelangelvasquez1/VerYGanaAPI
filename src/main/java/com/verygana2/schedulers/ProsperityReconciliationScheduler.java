package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.finance.ProsperityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Conciliación diaria del Libro Mayor de Prosperidad (Contrato B 10.5: "saldo mostrado ≠
 * suma de movimientos" es una alerta). Corre a las 7:00 AM UTC (2:00 AM Colombia).
 * Los descuadres se registran con log.error en ProsperityServiceImpl.reconcile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProsperityReconciliationScheduler {

    private final ProsperityService prosperityService;

    @Scheduled(cron = "0 0 7 * * *")
    public void runDailyReconciliation() {
        try {
            prosperityService.reconcile();
        } catch (Exception e) {
            log.error("[PROSPERITY-RECONCILIATION] Error inesperado: {}", e.getMessage(), e);
        }
    }
}
