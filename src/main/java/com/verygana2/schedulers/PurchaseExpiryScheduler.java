package com.verygana2.schedulers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.marketplace.CopaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancela compras PENDING cuya sesión de Wompi ya expiró.
 * Devuelve stock y llaves reservadas al pool disponible.
 *
 * Antes de expirar, reconcilia cada copago directamente contra la API de Wompi
 * (ver {@link com.verygana2.services.marketplace.CopaymentServiceImpl#reconcileWithWompi})
 * por si el pago sí se resolvió y el webhook nunca llegó — por eso la ventana
 * puede ser corta sin riesgo de cancelar una compra ya cobrada.
 *
 * Configurable en application.yml:
 *   purchase.expiry.max-age-minutes: 30   (default 30 min)
 *   purchase.expiry.cron: "0 0/30 * * * *" (default cada 30 min)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseExpiryScheduler {

    private final CopaymentService copaymentService;

    @Value("${purchase.expiry.max-age-minutes:30}")
    private int maxAgeMinutes;

    @Scheduled(cron = "${purchase.expiry.cron:0 0/30 * * * *}")
    public void expireStale() {
        log.info("[SCHEDULER] Buscando compras PENDING expiradas (maxAge={}min)...", maxAgeMinutes);
        copaymentService.expireStale(maxAgeMinutes);
    }
}
