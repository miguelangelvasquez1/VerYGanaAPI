package com.verygana2.security.auth.refreshToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService securityAuditService;
    private final SecurityMonitoringService securityMonitoringService;

    // ── Limpieza diaria de tokens expirados (2:00 AM) ─────────────────────────

    @Scheduled(cron = "${app.security.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired tokens");
        try {
            Instant expiredCutoff = Instant.now();
            Instant revokedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);

            int expiredDeleted = refreshTokenRepository.deleteExpiredTokens(expiredCutoff);
            int revokedDeleted = refreshTokenRepository.deleteOldRevokedTokens(revokedCutoff);

            log.info("Token cleanup completed — expired: {}, old-revoked: {}", expiredDeleted, revokedDeleted);

        } catch (Exception e) {
            log.error("Error during scheduled token cleanup", e);
            securityAuditService.logSystemError("TOKEN_CLEANUP_FAILED", e.getMessage());
        }
    }

    // ── Detección de actividad sospechosa (cada hora) ─────────────────────────

    /**
     * Delega el análisis completo en SecurityMonitoringService, que ya cubre la
     * detección de IPs sospechosas (fuerza bruta) junto con los demás vectores
     * (token farming, session hijacking).
     */
    @Scheduled(fixedRateString = "${app.security.monitoring.check-interval-ms:3600000}")
    public void detectSuspiciousActivity() {
        try {
            log.debug("Starting suspicious activity detection");
            securityMonitoringService.analyzeSecurityPatterns();
        } catch (Exception e) {
            log.error("Error during suspicious activity detection", e);
        }
    }
}
