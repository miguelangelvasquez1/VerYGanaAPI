package com.verygana2.config.maintenance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.marketplace.ProductImageRepairService;
import com.verygana2.services.interfaces.marketplace.ProductImageRepairService.ProductImageRepairReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dispara la reparación de imágenes de producto (spec 014, RN-7) cuando la aplicación queda lista.
 * Sin {@code matchIfMissing}: si la bandera no está en {@code true}, el bean no existe y nada
 * corre al arrancar. Ver el runbook en {@code specs/014-cdn-public-urls-by-env}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "maintenance.product-image-repair", name = "enabled", havingValue = "true")
public class ProductImageRepairRunner {

    private final ProductImageRepairService repairService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Hilo propio: con muchos productos son varias llamadas a R2 y no debe frenar el arranque ni el health check.
        Thread.ofVirtual().name("product-image-repair").start(() -> {
            try {
                ProductImageRepairReport report = repairService.repairActiveProductImages();
                log.info("Reparación de imágenes de producto: {}", report);
            } catch (RuntimeException e) {
                log.error("Reparación de imágenes de producto interrumpida", e);
            }
        });
    }
}
