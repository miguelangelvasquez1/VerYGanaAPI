package com.verygana2.utils.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Job programado para limpiar logs de auditoría antiguos
 * según políticas de retención por nivel
 */
@Component
@Slf4j
public class AuditCleanupJob {

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Se ejecuta todos los días a las 3 AM
     * Limpia logs según su nivel de criticidad
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldAuditLogs() {
        log.info("Iniciando limpieza de audit logs antiguos");
        
        try {
            int totalDeleted = 0;
            
            // Limpiar por cada nivel según su política de retención
            for (AuditLevel level : AuditLevel.values()) {
                if (level == AuditLevel.DEBUG) {
                    // DEBUG no se persiste en producción, skip
                    continue;
                }
                
                LocalDateTime cutoffDate = LocalDateTime.now()
                    .minusDays(level.getRetentionDays());
                
                int deleted = auditLogRepository.deleteOldLogsByLevel(cutoffDate, level);
                totalDeleted += deleted;
                
                log.info("Limpieza nivel {}: {} registros eliminados (retención: {} días)", 
                    level, deleted, level.getRetentionDays());
            }
            
            log.info("Limpieza completada: {} registros eliminados en total", totalDeleted);
            
        } catch (Exception e) {
            log.error("Error durante limpieza de audit logs: {}", e.getMessage(), e);
        }
    }

    /**
     * Limpieza de emergencia si el volumen es muy alto
     * Se ejecuta cada hora y elimina logs DEBUG antiguos
     */
    @Scheduled(cron = "0 0 * * * ?") // Cada hora
    @Transactional
    public void emergencyCleanupDebugLogs() {
        try {
            // Eliminar logs DEBUG más viejos de 24 horas
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(1);
            int deleted = auditLogRepository.deleteOldLogsByLevel(
                cutoffDate, 
                AuditLevel.DEBUG
            );
            
            if (deleted > 0) {
                log.info("Limpieza emergency DEBUG: {} registros eliminados", deleted);
            }
            
        } catch (Exception e) {
            log.error("Error en limpieza emergency: {}", e.getMessage(), e);
        }
    }

    /**
     * Genera reporte semanal de auditoría
     * Se ejecuta todos los lunes a las 8 AM
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void generateWeeklyReport() {
        try {
            LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
            
            long criticalCount = auditLogRepository.countByLevelSince(
                AuditLevel.CRITICAL, oneWeekAgo
            );
            
            long warningCount = auditLogRepository.countByLevelSince(
                AuditLevel.WARNING, oneWeekAgo
            );
            
            log.info("=== Reporte Semanal de Auditoría ===");
            log.info("Eventos CRÍTICOS: {}", criticalCount);
            log.info("Eventos WARNING: {}", warningCount);
            
            // Top acciones de la semana
            var topActions = auditLogRepository.getTopActionsSince(oneWeekAgo);
            log.info("Top 5 acciones:");
            topActions.stream()
                .limit(5)
                .forEach(action -> 
                    log.info("  - {}: {} veces", action[0], action[1])
                );
            
            // Aquí podrías enviar email con el reporte
            // emailService.sendWeeklyAuditReport(...);
            
        } catch (Exception e) {
            log.error("Error generando reporte semanal: {}", e.getMessage(), e);
        }
    }
}