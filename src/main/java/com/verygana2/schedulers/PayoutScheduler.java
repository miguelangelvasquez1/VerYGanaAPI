package com.verygana2.schedulers;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutBalanceResponseDTO;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.wompi.WompiPayoutClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job diario de payouts. Se ejecuta a las 11 PM hora Colombia (04:00 UTC).
 *
 * Fase 1 — scheduleDailyPayouts: agrupa copayments del día y crea Payouts SCHEDULED.
 * Fase 2 — processScheduledPayouts: llama a Wompi (Pagos a Terceros) para cada Payout SCHEDULED.
 * Fase 3 — retryFailedPayouts: reintenta los FAILED del ciclo anterior (30 min después).
 *
 * Prerequisito: el balance de la cuenta de dispersión de Wompi debe estar fondeado
 * manualmente. Este job alerta si el balance es inferior al umbral configurado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    private static final ZoneId COLOMBIA_TZ = ZoneId.of("America/Bogota");

    private final PayoutService payoutService;
    private final WompiPayoutClient wompiPayoutClient;
    private final WompiPayoutConfig wompiPayoutConfig;

    @Scheduled(cron = "${wompi.payout.cron}")
    public void runDailyPayouts() {
        ZonedDateTime now = ZonedDateTime.now(COLOMBIA_TZ);
        ZonedDateTime periodEnd = now.toLocalDate().atStartOfDay(COLOMBIA_TZ);   // medianoche actual
        ZonedDateTime periodStart = periodEnd.minusDays(1);                       // medianoche anterior

        log.info("[PAYOUT-SCHEDULER] Iniciando ciclo diario. Período: {} → {}", periodStart, periodEnd);

        // Verificar balance antes de procesar
        checkWompiBalance();

        // Fase 1 — crear Payouts SCHEDULED
        payoutService.scheduleDailyPayouts(periodStart, periodEnd);

        // Fase 2 — enviar transferencias a Wompi
        payoutService.processScheduledPayouts();

        log.info("[PAYOUT-SCHEDULER] Ciclo diario completado.");
    }

    @Scheduled(cron = "${wompi.payout.retry-cron}")
    public void retryFailedPayouts() {
        ZonedDateTime now = ZonedDateTime.now(COLOMBIA_TZ);
        ZonedDateTime periodEnd = now.toLocalDate().atStartOfDay(COLOMBIA_TZ);
        ZonedDateTime periodStart = periodEnd.minusDays(1);

        log.info("[PAYOUT-RETRY] Reintentando payouts FAILED del período: {} → {}",
                periodStart, periodEnd);

        payoutService.retryFailedPayouts(periodStart, periodEnd);
    }

    private void checkWompiBalance() {
        try {
            WompiPayoutBalanceResponseDTO balance = wompiPayoutClient.getBalance();
            long balanceCents = balance.getBalanceInCents();
            long alertThreshold = wompiPayoutConfig.getPayout().getMinBalanceAlertCents();

            if (balanceCents < alertThreshold) {
                log.warn("[PAYOUT-SCHEDULER] ⚠ Balance Wompi Payouts bajo: {} COP (umbral: {} COP). " +
                        "Recargar la cuenta de dispersión de Wompi.",
                        balanceCents / 100, alertThreshold / 100);
            } else {
                log.info("[PAYOUT-SCHEDULER] Balance Wompi Payouts OK: {} COP", balanceCents / 100);
            }
        } catch (Exception e) {
            // No bloqueamos el job por un fallo en la consulta de balance
            log.error("[PAYOUT-SCHEDULER] No se pudo consultar balance Wompi Payouts: {}", e.getMessage());
        }
    }
}
