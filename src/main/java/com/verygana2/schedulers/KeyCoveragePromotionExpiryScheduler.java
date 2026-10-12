package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cierra las promociones especiales de llaves cuya vigencia terminó y devuelve a
 * FORTIFICATION la garantía que no se usó. Corre cada 30 minutos.
 *
 * La vigencia ya se respeta sin este job (una promoción vencida no aplica a ninguna
 * compra): lo que hace es dejar el estado cerrado y liberar el dinero apartado. La
 * garantía de una promoción cerrada espera a que no queden compras con pago en curso,
 * por eso puede devolverse en una corrida posterior a la del cierre.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyCoveragePromotionExpiryScheduler {

    private final KeyCoveragePromotionService promotionService;

    @Scheduled(cron = "0 10/30 * * * *")
    public void closeDuePromotions() {
        for (Long promotionId : promotionService.idsNeedingClosure()) {
            try {
                promotionService.close(promotionId);
            } catch (Exception e) {
                // Una promoción que falla no debe dejar a las demás con la garantía apartada.
                log.error("[KEY-PROMOTION] Error cerrando la promoción {}: {}", promotionId, e.getMessage(), e);
            }
        }
    }
}
