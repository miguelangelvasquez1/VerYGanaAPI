package com.verygana2.schedulers;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.finance.PayoutService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job diario de payouts. Se ejecuta a las 4:00 AM UTC (11:00 PM Colombia).
 *
 * Secuencia:
 * 1. Reintentar payouts FAILED del día anterior.
 * 2. Agrupar copayments COMPLETED del día y crear Payouts SCHEDULED.
 * 3. Ejecutar transferencias para todos los payouts SCHEDULED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    private final PayoutService payoutService;

    @Scheduled(cron = "0 0 4 * * *")
    public void runDailyPayouts() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        // Período del día actual (00:00 – 04:00 UTC, es decir ventas hasta las 11 PM COL)
        ZonedDateTime periodEnd = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusHours(4);
        ZonedDateTime periodStart = periodEnd.minusDays(1);

        // Período anterior (para reintentar FAILED)
        ZonedDateTime previousPeriodStart = periodStart.minusDays(1);
        ZonedDateTime previousPeriodEnd = periodStart;

        log.info("[PAYOUT-SCHEDULER] === Inicio job diario de payouts. Período: {} → {} ===",
                periodStart, periodEnd);

        try {
            log.info("[PAYOUT-SCHEDULER] Fase 0: Reintentando payouts FAILED del día anterior...");
            payoutService.retryFailedPayouts(previousPeriodStart, previousPeriodEnd);
        } catch (Exception e) {
            log.error("[PAYOUT-SCHEDULER] Error en fase de reintento: {}", e.getMessage(), e);
        }

        try {
            log.info("[PAYOUT-SCHEDULER] Fase 1: Creando payouts SCHEDULED para el período actual...");
            payoutService.scheduleDailyPayouts(periodStart, periodEnd);
        } catch (Exception e) {
            log.error("[PAYOUT-SCHEDULER] Error en fase de scheduling: {}", e.getMessage(), e);
        }

        try {
            log.info("[PAYOUT-SCHEDULER] Fase 2: Ejecutando transferencias para payouts SCHEDULED...");
            payoutService.processScheduledPayouts();
        } catch (Exception e) {
            log.error("[PAYOUT-SCHEDULER] Error en fase de procesamiento: {}", e.getMessage(), e);
        }

        log.info("[PAYOUT-SCHEDULER] === Job diario de payouts completado. ===");
    }
}
