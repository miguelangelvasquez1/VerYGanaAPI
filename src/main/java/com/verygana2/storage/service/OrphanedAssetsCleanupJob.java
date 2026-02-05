package com.verygana2.storage.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.games.Asset;
import com.verygana2.repositories.games.AssetRepository;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OrphanedAssetsCleanupJob {

    private final AssetRepository assetRepository;
    private final R2Service r2Service;

    @Value("${cleanup.orphaned-assets.max-age-hours:24}")
    private int maxAgeHours;

    @Transactional //    @Scheduled(cron = "${cleanup.orphaned-assets.cron:0 0 2 * * ?}")
    @Scheduled(cron = "0 0 * * * *") // cada hora
    public void cleanupAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        List<Asset> assets = assetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold); //falta poner pending assets si llevan mucho como pendign y tambien el trabajo de remove para assets de nauncios

        log.info("Cleanup job: {} assets candidatos", assets.size());

        for (Asset asset : assets) {
            try {
                r2Service.deleteObject(asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                assetRepository.save(asset);

                log.info("Asset {} eliminado de R2", asset.getId());
                // Opcional: enviar métricas a sistema de monitoreo
                // metricsService.recordCleanup(deletedCount, durationSeconds);

            } catch (Exception e) {
                log.warn(
                    "No se pudo eliminar asset {} ({}): {}",
                    asset.getId(),
                    asset.getObjectKey(),
                    e.getMessage()
                );
            }
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