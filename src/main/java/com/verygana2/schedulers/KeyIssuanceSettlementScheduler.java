package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.finance.KeyIssuanceSettlementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liquida en tesorería el diferencial acumulado de emisión de llaves.
 *
 * Corre cada 10 minutos por defecto. Si no corre, el diferencial NO se pierde
 * — queda pendiente en las filas sin marcar y lo recoge el siguiente ciclo —,
 * pero KEYS_RESERVE queda temporalmente por encima de lo que debería, así que
 * conviene alertar si el job deja de ejecutarse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyIssuanceSettlementScheduler {

    private final KeyIssuanceSettlementService settlementService;

    @Scheduled(cron = "${treasury.issuance-settlement.cron:0 0/10 * * * *}")
    public void settlePendingIssuance() {
        try {
            settlementService.settlePendingIssuance();
        } catch (Exception e) {
            // No relanzamos: el diferencial queda sin marcar y se reintenta solo
            // en el siguiente ciclo. Relanzar solo mataría el hilo del scheduler.
            log.error("[ISSUANCE-SETTLEMENT] Error liquidando emisión de llaves: {}", e.getMessage(), e);
        }
    }
}
