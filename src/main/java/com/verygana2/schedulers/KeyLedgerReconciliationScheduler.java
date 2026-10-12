package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.finance.KeyLedgerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Conciliación diaria del Libro Mayor de Llaves (Contrato B 2.25: "conciliar"). Corre a
 * las 7:30 AM UTC (2:30 AM Colombia), después del vencimiento de las 5:05.
 * Los descuadres se registran con log.error en KeyLedgerServiceImpl.reconcile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyLedgerReconciliationScheduler {

    private final KeyLedgerService keyLedgerService;

    @Scheduled(cron = "0 30 7 * * *")
    public void runDailyReconciliation() {
        try {
            keyLedgerService.reconcile();
        } catch (Exception e) {
            log.error("[KEY-LEDGER-RECONCILIATION] Error inesperado: {}", e.getMessage(), e);
        }
    }
}
