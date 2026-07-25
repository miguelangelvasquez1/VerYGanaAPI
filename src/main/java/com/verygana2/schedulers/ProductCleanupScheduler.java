package com.verygana2.schedulers;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.marketplace.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Purga semanalmente productos REJECTED/INACTIVE que llevan al menos
 * {gracePeriodDays} días en ese estado, para liberar espacio en BD y en R2.
 * Se purgan incluso si tuvieron compras reales: ProductServiceImpl.purgeProduct
 * desvincula (nunca borra) los PurchaseItem asociados —son historial
 * financiero/auditable (comisiones, payouts, soporte de venta)— antes de
 * borrar el producto, así que ese historial nunca se pierde.
 * Ver ProductRepository.findPurgeableProducts / ProductServiceImpl.purgeProduct.
 *
 * Corre los domingos 3:30 AM para no coincidir con el resto de jobs nocturnos
 * (orphaned-assets 2AM, audit cleanup 3AM diario, payouts 4AM) ni con tráfico.
 *
 * Configurable en application.yml:
 *   marketplace.product.cleanup.cron: "0 30 3 * * SUN" (default)
 *   marketplace.product.cleanup.grace-period-days: 30  (default)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCleanupScheduler {

    private final ProductService productService;

    @Value("${marketplace.product.cleanup.grace-period-days:30}")
    private int gracePeriodDays;

    @Scheduled(cron = "${marketplace.product.cleanup.cron:0 30 3 * * SUN}")
    public void purgeInactiveAndRejectedProducts() {
        List<Long> candidates = productService.findPurgeableProductIds(gracePeriodDays);

        if (candidates.isEmpty()) {
            log.debug("[PRODUCT-CLEANUP] Sin productos elegibles para purgar.");
            return;
        }

        log.info("[PRODUCT-CLEANUP] {} producto(s) candidatos a purga (REJECTED/INACTIVE, >{} días sin cambios)",
                candidates.size(), gracePeriodDays);

        int deleted = 0;
        for (Long productId : candidates) {
            try {
                productService.purgeProduct(productId);
                deleted++;
            } catch (Exception e) {
                log.error("[PRODUCT-CLEANUP] Error purgando producto {}: {}", productId, e.getMessage(), e);
            }
        }

        log.info("[PRODUCT-CLEANUP] Purga completada: {}/{} productos eliminados", deleted, candidates.size());
    }
}
