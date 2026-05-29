package com.verygana2.security.auth.refreshToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.security.suspicious-activity.tokens-per-ip-threshold:10}")
    private int suspiciousTokenThreshold;

    @Value("${app.security.suspicious-activity.check-window-hours:1}")
    private int checkWindowHours;

    @Value("${app.security.sessions.max-per-user:15}")
    private int maxSessionsPerUser;

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

    @Scheduled(fixedRateString = "${app.security.monitoring.check-interval-ms:3600000}")
    public void detectSuspiciousActivity() {
        try {
            log.debug("Starting suspicious activity detection");

            Instant since = Instant.now().minus(checkWindowHours, ChronoUnit.HOURS);
            List<Object[]> suspiciousIPs = refreshTokenRepository.findSuspiciousIPs(since, suspiciousTokenThreshold);

            if (!suspiciousIPs.isEmpty()) {
                log.warn("Detected suspicious activity from {} IP(s)", suspiciousIPs.size());
                for (Object[] row : suspiciousIPs) {
                    String ip = (String) row[0];
                    long count = ((Number) row[1]).longValue();
                    securityAuditService.logSuspiciousActivity(
                            "SYSTEM",
                            "SUSPICIOUS_IP_ACTIVITY",
                            String.format("IP %s created %d tokens in %d hours", ip, count, checkWindowHours)
                    );
                }
            }

            securityMonitoringService.analyzeSecurityPatterns();

        } catch (Exception e) {
            log.error("Error during suspicious activity detection", e);
        }
    }

    // ── Monitoreo de sesiones anómalas (cada 30 minutos) ─────────────────────

    @Scheduled(fixedRateString = "${app.security.monitoring.session-check-interval-ms:1800000}")
    public void monitorAnomalousSessions() {
        try {
            log.debug("Starting anomalous session monitoring");

            Map<String, List<RefreshToken>> tokensByUser = refreshTokenRepository
                    .findAllActiveTokens(Instant.now())
                    .stream()
                    .collect(Collectors.groupingBy(RefreshToken::getUsername));

            for (Map.Entry<String, List<RefreshToken>> entry : tokensByUser.entrySet()) {
                String username = entry.getKey();
                int sessionCount = entry.getValue().size();

                if (sessionCount > maxSessionsPerUser) {
                    log.warn("User {} has {} active sessions (max: {})", username, sessionCount, maxSessionsPerUser);
                    securityAuditService.logSuspiciousActivity(
                            username,
                            "EXCESSIVE_SESSIONS",
                            String.format("User has %d active sessions (threshold: %d)", sessionCount, maxSessionsPerUser)
                    );

                    if (sessionCount > maxSessionsPerUser * 2) {
                        autoRevokeOldestSessions(username, entry.getValue(), maxSessionsPerUser);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during anomalous session monitoring", e);
        }
    }

    // ── Limpieza profunda mensual (día 1 del mes, 1:00 AM) ────────────────────

    @Scheduled(cron = "0 0 1 1 * ?")
    @Transactional
    public void monthlyDeepCleanup() {
        log.info("Starting monthly deep cleanup");
        try {
            Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
            int deleted = refreshTokenRepository.deleteOldRevokedTokens(cutoff);
            log.info("Monthly deep cleanup: {} old revoked tokens deleted", deleted);
        } catch (Exception e) {
            log.error("Error during monthly deep cleanup", e);
            securityAuditService.logSystemError("MONTHLY_CLEANUP_FAILED", e.getMessage());
        }
    }

    // ── Health check (cada 5 minutos) ─────────────────────────────────────────

    @Scheduled(fixedRateString = "${app.security.monitoring.health-check-interval-ms:300000}")
    public void healthCheck() {
        try {
            long start = System.currentTimeMillis();
            long activeTokens = refreshTokenRepository.countAllActiveTokens(Instant.now());
            long elapsed = System.currentTimeMillis() - start;

            if (elapsed > 1000) {
                log.warn("Slow DB response querying active tokens: {}ms", elapsed);
            }
            if (activeTokens > 100_000) {
                log.warn("High number of active tokens: {}", activeTokens);
            }

            log.debug("Health check OK — active tokens: {}, query time: {}ms", activeTokens, elapsed);

        } catch (Exception e) {
            log.error("Health check failed", e);
            securityAuditService.logSystemError("HEALTH_CHECK_FAILED", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void autoRevokeOldestSessions(String username, List<RefreshToken> tokens, int keepCount) {
        List<RefreshToken> toRevoke = tokens.stream()
                .sorted(Comparator.comparing(
                        t -> t.getLastUsedAt() != null ? t.getLastUsedAt() : t.getCreatedAt(),
                        Comparator.reverseOrder()
                ))
                .skip(keepCount)
                .collect(Collectors.toList());

        toRevoke.forEach(RefreshToken::revoke);
        refreshTokenRepository.saveAll(toRevoke);

        log.info("Auto-revoked {} old sessions for user: {}", toRevoke.size(), username);
    }
}
