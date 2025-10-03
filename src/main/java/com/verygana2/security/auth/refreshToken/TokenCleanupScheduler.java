package com.verygana2.security.auth.refreshToken;
// package com.VerYGana.security.auth.refreshToken;

// import java.time.Instant;
// import java.time.temporal.ChronoUnit;
// import java.util.List;
// import java.util.stream.Collectors;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.scheduling.annotation.EnableScheduling;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.stereotype.Component;

// import com.VerYGana.security.auth.TokenService;

// import jakarta.transaction.Transactional;
// import lombok.extern.slf4j.Slf4j;

// @Component
// @EnableScheduling
// @Slf4j
// public class TokenCleanupScheduler {

//     private final TokenService tokenService;
//     private final RefreshTokenRepository refreshTokenRepository;
//     private final SecurityAuditService securityAuditService;

//     @Value("${app.security.suspicious-activity.tokens-per-ip-threshold:10}")
//     private int suspiciousTokenThreshold;

//     @Value("${app.security.suspicious-activity.check-window-hours:1}")
//     private int checkWindowHours;

//     public TokenCleanupScheduler(TokenService tokenService, 
//                                 RefreshTokenRepository refreshTokenRepository,
//                                 SecurityAuditService securityAuditService) {
//         this.tokenService = tokenService;
//         this.refreshTokenRepository = refreshTokenRepository;
//         this.securityAuditService = securityAuditService;
//     }

//     /**
//      * Limpieza diaria de tokens expirados (ejecuta a las 2:00 AM)
//      */
//     @Scheduled(cron = "${app.security.cleanup.cron:0 0 2 * * ?}")
//     @Transactional
//     public void cleanupExpiredTokens() {
//         log.info("Starting scheduled cleanup of expired tokens");
        
//         try {
//             CleanupResult result = performCleanup();
            
//             log.info("Successfully completed scheduled token cleanup - " +
//                     "Expired: {}, Old revoked: {}, Total freed space: {} MB", 
//                     result.getExpiredTokens(), 
//                     result.getRevokedTokens(),
//                     result.getEstimatedSpaceFreed());
            
//             // Generar métrica para monitoreo
//             publishCleanupMetrics(result);
            
//         } catch (Exception e) {
//             log.error("Error during scheduled token cleanup", e);
//             securityAuditService.logSystemError("TOKEN_CLEANUP_FAILED", e.getMessage());
//         }
//     }

//     /**
//      * Verificación de actividad sospechosa cada hora
//      */
//     @Scheduled(fixedRate = 3600000) // 1 hora
//     public void detectSuspiciousActivity() {
//         try {
//             log.debug("Starting suspicious activity detection");
            
//             Instant since = Instant.now().minus(checkWindowHours, ChronoUnit.HOURS);
//             List<SuspiciousActivityResult> suspiciousIPs = findSuspiciousIPs(since);
            
//             if (!suspiciousIPs.isEmpty()) {
//                 log.warn("Detected suspicious activity from {} IP addresses", suspiciousIPs.size());
                
//                 for (SuspiciousActivityResult suspicious : suspiciousIPs) {
//                     handleSuspiciousIP(suspicious);
//                 }
                
//                 // Generar alerta para administradores
//                 generateSecurityAlert(suspiciousIPs);
//             }
            
//         } catch (Exception e) {
//             log.error("Error during suspicious activity detection", e);
//         }
//     }

//     /**
//      * Monitoreo de sesiones anómalas cada 30 minutos
//      */
//     @Scheduled(fixedRate = 1800000) // 30 minutos
//     public void monitorAnomalousSessions() {
//         try {
//             log.debug("Starting anomalous session monitoring");
            
//             // Detectar usuarios con demasiadas sesiones activas
//             List<UserSessionStats> anomalousUsers = findUsersWithExcessiveSessions();
            
//             for (UserSessionStats userStats : anomalousUsers) {
//                 log.warn("User {} has {} active sessions (threshold: {})", 
//                         userStats.getUsername(), 
//                         userStats.getActiveSessionCount(),
//                         userStats.getThreshold());
                
//                 securityAuditService.logSuspiciousActivity(
//                     userStats.getUsername(),
//                     "EXCESSIVE_SESSIONS",
//                     String.format("User has %d active sessions", userStats.getActiveSessionCount())
//                 );
                
//                 // Opcional: Revocar sesiones más antiguas automáticamente
//                 if (userStats.getActiveSessionCount() > 20) { // Threshold crítico
//                     autoRevokeOldestSessions(userStats.getUsername(), 15); // Mantener solo 15
//                 }
//             }
            
//         } catch (Exception e) {
//             log.error("Error during anomalous session monitoring", e);
//         }
//     }

//     /**
//      * Estadísticas semanales (ejecuta los domingos a las 3:00 AM)
//      */
//     @Scheduled(cron = "0 0 3 * * SUN")
//     public void generateWeeklyStats() {
//         try {
//             log.info("Generating weekly token statistics");
            
//             WeeklyTokenStats stats = calculateWeeklyStats();
            
//             log.info("Weekly Statistics Summary:\n" +
//                     "- Total active tokens: {}\n" +
//                     "- New tokens this week: {}\n" +
//                     "- Tokens revoked this week: {}\n" +
//                     "- Average sessions per user: {}\n" +
//                     "- Most active IPs: {}\n" +
//                     "- Peak concurrent sessions: {}",
//                     stats.getTotalActiveTokens(),
//                     stats.getNewTokensThisWeek(),
//                     stats.getRevokedTokensThisWeek(),
//                     stats.getAverageSessionsPerUser(),
//                     stats.getMostActiveIPs().size(),
//                     stats.getPeakConcurrentSessions());
            
//             // Guardar stats en BD o enviar por email si es necesario
//             persistWeeklyStats(stats);
            
//         } catch (Exception e) {
//             log.error("Error generating weekly statistics", e);
//         }
//     }

//     /**
//      * Limpieza agresiva mensual (primer día del mes a las 1:00 AM)
//      */
//     @Scheduled(cron = "0 0 1 1 * ?")
//     @Transactional
//     public void monthlyDeepCleanup() {
//         log.info("Starting monthly deep cleanup");
        
//         try {
//             // Limpiar tokens revocados muy antiguos (más de 90 días)
//             Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
//             int deletedRevoked = refreshTokenRepository.deleteOldRevokedTokens(cutoff);
            
//             // Limpiar logs de auditoría antiguos si tienes tabla separada
//             // int deletedLogs = auditLogRepository.deleteOldLogs(cutoff);
            
//             // Optimizar tabla (esto depende de tu BD)
//             optimizeRefreshTokenTable();
            
//             log.info("Monthly deep cleanup completed: {} old revoked tokens deleted", deletedRevoked);
            
//         } catch (Exception e) {
//             log.error("Error during monthly deep cleanup", e);
//         }
//     }

//     /**
//      * Verificación de health cada 5 minutos
//      */
//     @Scheduled(fixedRate = 300000) // 5 minutos
//     public void healthCheck() {
//         try {
//             // Verificar que la BD esté respondiendo bien
//             long startTime = System.currentTimeMillis();
//             long tokenCount = refreshTokenRepository.count();
//             long responseTime = System.currentTimeMillis() - startTime;
            
//             if (responseTime > 1000) { // Más de 1 segundo
//                 log.warn("Slow database response for token count: {}ms", responseTime);
//             }
            
//             // Verificar que no haya demasiados tokens activos
//             Instant now = Instant.now();
//             long activeTokens = refreshTokenRepository.countActiveTokensByUsername("", now);
            
//             if (activeTokens > 100000) { // Ajustar según tu escala
//                 log.warn("High number of active tokens: {}", activeTokens);
//             }
            
//         } catch (Exception e) {
//             log.error("Health check failed", e);
//             securityAuditService.logSystemError("HEALTH_CHECK_FAILED", e.getMessage());
//         }
//     }

//     // Métodos helper privados

//     private CleanupResult performCleanup() {
//         Instant expiredCutoff = Instant.now().minus(1, ChronoUnit.DAYS);
//         Instant revokedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        
//         int expiredDeleted = refreshTokenRepository.deleteExpiredTokens(expiredCutoff);
//         int revokedDeleted = refreshTokenRepository.deleteOldRevokedTokens(revokedCutoff);
        
//         // Estimación aproximada del espacio liberado (asumiendo 1KB promedio por token)
//         long estimatedSpaceFreed = (expiredDeleted + revokedDeleted) / 1024; // MB
        
//         return new CleanupResult(expiredDeleted, revokedDeleted, estimatedSpaceFreed);
//     }

//     private List<SuspiciousActivityResult> findSuspiciousIPs(Instant since) {
//         List<Object[]> rawResults = refreshTokenRepository.findSuspiciousIPs(since, suspiciousTokenThreshold);
        
//         return rawResults.stream()
//             .map(result -> new SuspiciousActivityResult(
//                 (String) result[0],    // IP address
//                 ((Number) result[1]).longValue(), // token count
//                 since
//             ))
//             .collect(Collectors.toList());
//     }

//     private void handleSuspiciousIP(SuspiciousActivityResult suspicious) {
//         String ipAddress = suspicious.getIpAddress();
//         long tokenCount = suspicious.getTokenCount();
        
//         log.warn("Suspicious IP detected: {} with {} tokens in last {} hours", 
//                 ipAddress, tokenCount, checkWindowHours);
        
//         // Log para auditoría
//         securityAuditService.logSuspiciousActivity(
//             "SYSTEM",
//             "SUSPICIOUS_IP_ACTIVITY", 
//             String.format("IP %s created %d tokens in %d hours", ipAddress, tokenCount, checkWindowHours)
//         );
        
//         // Acciones automáticas según severidad
//         if (tokenCount > 50) { // Muy sospechoso
//             // Revocar todos los tokens de esta IP
//             revokeTokensByIP(ipAddress);
//             log.warn("Auto-revoked all tokens from highly suspicious IP: {}", ipAddress);
//         } else if (tokenCount > 20) { // Moderadamente sospechoso
//             // Solo alertar pero no revocar automáticamente
//             log.warn("Monitoring suspicious IP for potential action: {}", ipAddress);
//         }
//     }

//     private void revokeTokensByIP(String ipAddress) {
//         try {
//             List<RefreshToken> suspiciousTokens = refreshTokenRepository
//                 .findActiveTokensByDevice("", Instant.now()); // Adaptar query para IP
            
//             for (RefreshToken token : suspiciousTokens) {
//                 if (ipAddress.equals(token.getIpAddress())) {
//                     token.revoke();
//                 }
//             }
            
//             refreshTokenRepository.saveAll(suspiciousTokens);
            
//         } catch (Exception e) {
//             log.error("Error revoking tokens for IP: {}", ipAddress, e);
//         }
//     }

//     private void generateSecurityAlert(List<SuspiciousActivityResult> suspiciousActivity) {
//         // Implementar según tus necesidades:
//         // - Enviar email a administradores
//         // - Integrar con sistema de alertas (Slack, Discord, etc.)
//         // - Crear ticket en sistema de soporte
//         // - Notificar por webhook
        
//         log.warn("SECURITY ALERT: {} suspicious IPs detected", suspiciousActivity.size());
        
//         // Ejemplo básico de notificación
//         StringBuilder alertMessage = new StringBuilder();
//         alertMessage.append("🚨 SECURITY ALERT - Suspicious Activity Detected\n\n");
        
//         for (SuspiciousActivityResult activity : suspiciousActivity) {
//             alertMessage.append(String.format("IP: %s - %d tokens in %d hours\n", 
//                 activity.getIpAddress(), activity.getTokenCount(), checkWindowHours));
//         }
        
//         // notificationService.sendSecurityAlert(alertMessage.toString());
//     }

//     private List<UserSessionStats> findUsersWithExcessiveSessions() {
//         // Query personalizada para encontrar usuarios con demasiadas sesiones
//         Instant now = Instant.now();
        
//         // Implementar query específica o usar repository method
//         return refreshTokenRepository.findActiveTokensByUsername("", now)
//             .stream()
//             .collect(Collectors.groupingBy(RefreshToken::getUsername))
//             .entrySet()
//             .stream()
//             .filter(entry -> entry.getValue().size() > 10) // Threshold configurable
//             .map(entry -> new UserSessionStats(
//                 entry.getKey(), 
//                 entry.getValue().size(), 
//                 10
//             ))
//             .collect(Collectors.toList());
//     }

//     private void autoRevokeOldestSessions(String username, int keepCount) {
//         List<RefreshToken> userTokens = refreshTokenRepository
//             .findActiveTokensByUsername(username, Instant.now());
        
//         if (userTokens.size() <= keepCount) {
//             return;
//         }
        
//         // Mantener solo los más recientes
//         List<RefreshToken> tokensToRevoke = userTokens.stream()
//             .sorted((a, b) -> b.getLastUsedAt().compareTo(a.getLastUsedAt()))
//             .skip(keepCount)
//             .collect(Collectors.toList());
        
//         tokensToRevoke.forEach(RefreshToken::revoke);
//         refreshTokenRepository.saveAll(tokensToRevoke);
        
//         log.info("Auto-revoked {} old sessions for user: {}", tokensToRevoke.size(), username);
//     }

//     private WeeklyTokenStats calculateWeeklyStats() {
//         Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
//         Instant now = Instant.now();
        
//         // Implementar queries específicas para estadísticas
//         return WeeklyTokenStats.builder()
//             .totalActiveTokens(refreshTokenRepository.count())
//             .newTokensThisWeek(countNewTokensSince(weekAgo))
//             .revokedTokensThisWeek(countRevokedTokensSince(weekAgo))
//             .averageSessionsPerUser(calculateAverageSessionsPerUser())
//             .mostActiveIPs(findMostActiveIPs(weekAgo))
//             .peakConcurrentSessions(findPeakConcurrentSessions(weekAgo))
//             .build();
//     }

//     private void persistWeeklyStats(WeeklyTokenStats stats) {
//         // Implementar persistencia de estadísticas si es necesario
//         // weeklyStatsRepository.save(stats);
//     }

//     private void optimizeRefreshTokenTable() {
//         // Implementación específica según tu BD
//         // Para PostgreSQL: VACUUM ANALYZE refresh_tokens;
//         // Para MySQL: OPTIMIZE TABLE refresh_tokens;
//     }

//     private void publishCleanupMetrics(CleanupResult result) {
//         // Publicar métricas para monitoreo (Micrometer, etc.)
//         // meterRegistry.counter("tokens.cleanup.expired").increment(result.getExpiredTokens());
//         // meterRegistry.counter("tokens.cleanup.revoked").increment(result.getRevokedTokens());
//     }

//     // Métodos helper para estadísticas
//     private long countNewTokensSince(Instant since) {
//         return refreshTokenRepository.count(); // Implementar query específica
//     }

//     private long countRevokedTokensSince(Instant since) {
//         return 0; // Implementar query específica
//     }

//     private double calculateAverageSessionsPerUser() {
//         return 0.0; // Implementar cálculo
//     }

//     private List<String> findMostActiveIPs(Instant since) {
//         return List.of(); // Implementar query
//     }

//     private long findPeakConcurrentSessions(Instant since) {
//         return 0; // Implementar cálculo
//     }
// }