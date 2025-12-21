package com.verygana2.storage.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Job programado para limpiar assets huérfanos en R2
 * Se ejecuta automáticamente para liberar espacio de storage
 */
@Component
@ConditionalOnProperty(
    prefix = "cleanup.orphaned-assets",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@Slf4j
public class OrphanedAssetsCleanupJob {

    @Autowired
    private R2Service r2Service;

    @Value("${cleanup.orphaned-assets.max-age-hours:24}")
    private int maxAgeHours;

    /**
     * Se ejecuta según el cron configurado en application.yml
     * Por defecto: cada día a las 2 AM
     */
    @Scheduled(cron = "${cleanup.orphaned-assets.cron:0 0 2 * * ?}")
    public void cleanupOrphanedAssets() {
        log.info("Iniciando limpieza de assets huérfanos (edad máxima: {} horas)", maxAgeHours);
        
        Instant startTime = Instant.now();
        
        try {
            int deletedCount = r2Service.cleanOrphanedObjects(maxAgeHours);
            
            long durationSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();
            
            log.info("Limpieza completada en {} segundos. Assets eliminados: {}", 
                durationSeconds, deletedCount);
            
            // Opcional: enviar métricas a sistema de monitoreo
            // metricsService.recordCleanup(deletedCount, durationSeconds);
            
        } catch (Exception e) {
            log.error("Error durante limpieza de assets huérfanos: {}", e.getMessage(), e);
            // Opcional: enviar alerta
            // alertService.sendAlert("Cleanup job falló", e);
        }
    }

    /**
     * Health check del servicio R2 cada 5 minutos
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void healthCheck() {
        if (!r2Service.healthCheck()) {
            log.error("R2 health check FALLÓ - Servicio no disponible");
            // Opcional: enviar alerta crítica
        }
    }
}