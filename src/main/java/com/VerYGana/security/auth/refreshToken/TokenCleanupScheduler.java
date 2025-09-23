// package com.VerYGana.security.auth.refreshToken;

// import java.time.Instant;
// import java.time.temporal.ChronoUnit;
// import java.util.List;

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

//     public TokenCleanupScheduler(TokenService tokenService, 
//                                 RefreshTokenRepository refreshTokenRepository) {
//         this.tokenService = tokenService;
//         this.refreshTokenRepository = refreshTokenRepository;
//     }

//     /**
//      * Limpieza diaria de tokens expirados (ejecuta a las 2:00 AM)
//      */
//     @Scheduled(cron = "0 0 2 * * ?")
//     @Transactional
//     public void cleanupExpiredTokens() {
//         log.info("Starting scheduled cleanup of expired tokens");
        
//         try {
//             tokenService.cleanupExpiredTokens();
//             log.info("Successfully completed scheduled token cleanup");
//         } catch (Exception e) {
//             log.error("Error during scheduled token cleanup", e);
//         }
//     }

//     /**
//      * Verificación de actividad sospechosa cada hora
//      */
//     @Scheduled(fixedRate = 3600000) // 1 hora
//     public void detectSuspiciousActivity() {
//         try {
//             List<Object[]> suspiciousIPs = tokenService.detectSuspiciousActivity();
            
//             if (!suspiciousIPs.isEmpty()) {
//                 log.warn("Detected suspicious activity from {} IP addresses", suspiciousIPs.size());
                
//                 for (Object[] result : suspiciousIPs) {
//                     String ipAddress = (String) result[0];
//                     Long tokenCount = (Long) result[1];
//                     log.warn("Suspicious IP: {} with {} tokens in last hour", ipAddress, tokenCount);
                    
//                     // Aquí puedes implementar acciones adicionales:
//                     // - Notificar administradores
//                     // - Bloquear IP temporalmente
//                     // - Revocar tokens sospechosos
//                 }
//             }
//         } catch (Exception e) {
//             log.error("Error during suspicious activity detection", e);
//         }
//     }

//     /**
//      * Estadísticas semanales (ejecuta los domingos a las 3:00 AM)
//      */
//     @Scheduled(cron = "0 0 3 * * SUN")
//     public void generateWeeklyStats() {
//         try {
//             log.info("Generating weekly token statistics");
            
//             // Contar tokens activos
//             Instant now = Instant.now();
//             long totalActiveTokens = refreshTokenRepository.count();
            
//             // Puedes expandir esto para generar reportes más detallados
//             log.info("Weekly stats - Total active tokens: {}", totalActiveTokens);
            
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
//             int deleted = refreshTokenRepository.deleteOldRevokedTokens(cutoff);
            
//             log.info("Monthly deep cleanup completed: {} old revoked tokens deleted", deleted);
            
//         } catch (Exception e) {
//             log.error("Error during monthly deep cleanup", e);
//         }
//     }
// }