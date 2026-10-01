package com.verygana2.schedulers;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemRefundService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vence diariamente los ítems físicos que nadie reclamó dentro del plazo
 * (PurchaseItem.claimExpiresAt) y dispara su reembolso interno automático —
 * ver PurchaseItemRefundService.expireUnclaimed. Cada ítem se procesa en su
 * propia transacción (dentro de expireUnclaimed): que uno falle no detiene
 * a los demás.
 *
 * Configurable en application.yml:
 *   marketplace.claim.expiration-scheduler.cron: "0 0 5 * * *" (default 5 AM UTC)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseItemExpirationScheduler {

    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseItemRefundService purchaseItemRefundService;
    private final EmailService emailService;

    @Scheduled(cron = "${marketplace.claim.expiration-scheduler.cron:0 0 5 * * *}")
    public void expireUnclaimedPhysicalItems() {
        List<PurchaseItem> expired = purchaseItemRepository
                .findExpiredUnclaimedPhysicalItems(ZonedDateTime.now(ZoneOffset.UTC));

        if (expired.isEmpty()) {
            log.debug("[CLAIM-EXPIRY] Sin ítems físicos vencidos sin reclamar.");
            return;
        }

        log.info("[CLAIM-EXPIRY] {} ítem(s) físico(s) vencido(s) sin reclamar.", expired.size());

        for (PurchaseItem item : expired) {
            try {
                purchaseItemRefundService.expireUnclaimed(item);
            } catch (Exception e) {
                log.error("[CLAIM-EXPIRY] Error venciendo purchaseItemId={}: {}", item.getId(), e.getMessage(), e);
                continue;
            }

            try {
                emailService.sendPhysicalItemExpiredToConsumer(item, item.getPurchase().getDeliveryEmail());
            } catch (Exception e) {
                log.error("[CLAIM-EXPIRY] Error notificando al comprador para purchaseItemId={}: {}",
                        item.getId(), e.getMessage(), e);
            }

            try {
                emailService.sendPhysicalItemExpiredToCommercial(item);
            } catch (Exception e) {
                log.error("[CLAIM-EXPIRY] Error notificando al comerciante para purchaseItemId={}: {}",
                        item.getId(), e.getMessage(), e);
            }
        }

        log.info("[CLAIM-EXPIRY] Ciclo completado.");
    }
}
