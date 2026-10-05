package com.verygana2.schedulers;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.finance.PlanService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancela las recargas de saldo (otrosí generado) que quedaron a medias — sin
 * firmar o firmadas sin pagar — pasado el plazo configurado. Sin esto una recarga
 * interrumpida queda "en curso" para siempre y bloquea al comercial para pedir
 * otra recarga o un cambio de plan.
 *
 * Antes de vencer una recarga que ya había abierto el checkout, se concilia contra
 * Wompi (ver {@link com.verygana2.services.finance.PlanServiceImpl#expireRecharge})
 * por si el pago sí se resolvió y el webhook nunca llegó.
 *
 * Configurable en application.yml:
 *   commercial.contract.recharge-expiry.max-age-hours: 24
 *   commercial.contract.recharge-expiry.cron: "0 15 * * * *" (cada hora)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeExpiryScheduler {

    private final PlanService planService;

    @Scheduled(cron = "${commercial.contract.recharge-expiry.cron:0 15 * * * *}")
    public void expireStale() {
        List<Long> stale = planService.findStaleRechargeContractIds();
        if (stale.isEmpty()) {
            return;
        }
        log.info("[SCHEDULER] Venciendo {} recargas sin pagar...", stale.size());

        // Cada recarga en su propia transacción: que Wompi no responda para una no
        // debe impedir vencer las demás; esa se reintenta en la próxima corrida.
        for (Long contractId : stale) {
            try {
                planService.expireRecharge(contractId);
            } catch (Exception e) {
                log.error("[SCHEDULER] No se pudo vencer la recarga contractId={}: {}", contractId, e.getMessage());
            }
        }
    }
}
