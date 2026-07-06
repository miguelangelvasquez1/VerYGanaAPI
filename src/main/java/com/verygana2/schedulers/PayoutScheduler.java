package com.verygana2.schedulers;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.config.kushki.KushkiConfig;
import com.verygana2.dtos.kushki.KushkiBalanceResponseDTO;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.kushki.KushkiPayoutClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job diario de payouts. Se ejecuta a las 11 PM hora Colombia (04:00 UTC).
 *
 * Fase 1 — scheduleDailyPayouts: agrupa copayments del día y crea Payouts SCHEDULED.
 * Fase 2 — processScheduledPayouts: llama a Kushki para cada Payout SCHEDULED.
 * Fase 3 — retryFailedPayouts: reintenta los FAILED del ciclo anterior (30 min después).
 *
 * Prerequisito: el balance de Kushki debe estar fondeado manualmente.
 * Este job alerta si el balance es inferior al umbral configurado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    private static final ZoneId COLOMBIA_TZ = ZoneId.of("America/Bogota");

    private final PayoutService payoutService;
    private final KushkiPayoutClient kushkiPayoutClient;
    private final KushkiConfig kushkiConfig;

    @Scheduled(cron = "${kushki.payout.cron}")
    public void runDailyPayouts() {
        ZonedDateTime now = ZonedDateTime.now(COLOMBIA_TZ);
        ZonedDateTime periodEnd = now.toLocalDate().atStartOfDay(COLOMBIA_TZ);   // medianoche actual
        ZonedDateTime periodStart = periodEnd.minusDays(1);                       // medianoche anterior

        log.info("[PAYOUT-SCHEDULER] Iniciando ciclo diario. Período: {} → {}", periodStart, periodEnd);

        // Verificar balance antes de procesar
        checkKushkiBalance();

        // Fase 1 — crear Payouts SCHEDULED
        payoutService.scheduleDailyPayouts(periodStart, periodEnd);

        // Fase 2 — enviar transferencias a Kushki
        payoutService.processScheduledPayouts();

        log.info("[PAYOUT-SCHEDULER] Ciclo diario completado.");
    }

    @Scheduled(cron = "${kushki.payout.retry-cron}")
    public void retryFailedPayouts() {
        ZonedDateTime now = ZonedDateTime.now(COLOMBIA_TZ);
        ZonedDateTime periodEnd = now.toLocalDate().atStartOfDay(COLOMBIA_TZ);
        ZonedDateTime periodStart = periodEnd.minusDays(1);

        log.info("[PAYOUT-RETRY] Reintentando payouts FAILED del período: {} → {}",
                periodStart, periodEnd);

        payoutService.retryFailedPayouts(periodStart, periodEnd);
    }

    private void checkKushkiBalance() {
        try {
            KushkiBalanceResponseDTO balance = kushkiPayoutClient.getBalance();
            long balanceCents = balance.getAvailableBalanceCents();
            long alertThreshold = kushkiConfig.getPayout().getMinBalanceAlertCents();

            if (balanceCents < alertThreshold) {
                log.warn("[PAYOUT-SCHEDULER] ⚠ Balance Kushki bajo: {} COP (umbral: {} COP). " +
                        "Recargar cuenta Davivienda de Kushki Colombia.",
                        balanceCents / 100, alertThreshold / 100);
            } else {
                log.info("[PAYOUT-SCHEDULER] Balance Kushki OK: {} COP", balanceCents / 100);
            }
        } catch (Exception e) {
            // No bloqueamos el job por un fallo en la consulta de balance
            log.error("[PAYOUT-SCHEDULER] No se pudo consultar balance Kushki: {}", e.getMessage());
        }
    }
}
