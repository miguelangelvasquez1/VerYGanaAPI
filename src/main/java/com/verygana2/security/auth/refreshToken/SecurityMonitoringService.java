package com.verygana2.security.auth.refreshToken;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.security.monitoring.AlertSeverity;
import com.verygana2.security.monitoring.BruteForcePattern;
import com.verygana2.security.monitoring.SecurityAlert;
import com.verygana2.security.monitoring.SecurityAlertType;
import com.verygana2.security.monitoring.SessionHijackingPattern;
import com.verygana2.security.monitoring.TokenFarmingPattern;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.utils.audit.AuditLog;
import com.verygana2.utils.audit.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityMonitoringService {

    private static final ZoneId NOTIFICATION_ZONE = ZoneId.of("America/Bogota");

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityAuditService securityAuditService;
    private final AdminDetailsRepository adminDetailsRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Value("${app.security.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${app.security.monitoring.auto-block-enabled:false}")
    private boolean autoBlockEnabled;

    @Value("${app.security.suspicious-activity.failed-logins-per-ip-threshold:10}")
    private int failedLoginThreshold;

    @Value("${app.security.suspicious-activity.check-window-hours:1}")
    private int checkWindowHours;

    @Value("${app.security.token-farming.threshold:10}")
    private int tokenFarmingThreshold;

    @Value("${app.admin-notification-email:admin@verygana.com}")
    private String adminNotificationEmail;

    // ── Punto de entrada principal ─────────────────────────────────────────────

    /**
     * Analiza múltiples vectores de ataque de forma asíncrona.
     * Llamado desde TokenCleanupScheduler cada hora.
     */
    @Async
    public void analyzeSecurityPatterns() {
        if (!monitoringEnabled) return;

        try {
            log.debug("Starting comprehensive security pattern analysis");
            detectSuspiciousFailedLoginsByIpActivity();
            detectTokenFarmingPatterns();
            detectSessionHijackingAttempts();
            log.debug("Security pattern analysis completed");
        } catch (Exception e) {
            log.error("Error during security pattern analysis", e);
        }
    }

    // ── Detección de ataques ───────────────────────────────────────────────────

    /**
     * Detecta IPs con muchos logins FALLIDOS en la ventana configurada — la señal
     * real de credential stuffing.
     */
    private void detectSuspiciousFailedLoginsByIpActivity() {
        Instant since = Instant.now().minus(checkWindowHours, ChronoUnit.HOURS);
        List<Object[]> suspiciousIPs = auditLogRepository.findIpsWithFailedLoginsSince(toZonedDateTime(since), failedLoginThreshold);

        for (Object[] result : suspiciousIPs) {
            String ipAddress = (String) result[0];
            long failedCount = ((Number) result[1]).longValue();

            BruteForcePattern pattern = analyzeBruteForcePattern(ipAddress, failedCount, since);
            handleBruteForceDetection(pattern);
        }
    }

    private void detectTokenFarmingPatterns() {
        Instant since = Instant.now().minus(checkWindowHours, ChronoUnit.HOURS);

        Map<String, List<RefreshToken>> recentTokensByUser = refreshTokenRepository
                .findAllTokensCreatedSince(since)
                .stream()
                .collect(Collectors.groupingBy(RefreshToken::getUsername));

        for (Map.Entry<String, List<RefreshToken>> entry : recentTokensByUser.entrySet()) {
            String username = entry.getKey();
            List<RefreshToken> tokens = entry.getValue();

            if (tokens.size() > tokenFarmingThreshold) {
                TokenFarmingPattern pattern = new TokenFarmingPattern(
                        username,
                        tokens.size(),
                        extractUniqueIPs(tokens),
                        since
                );
                handleTokenFarmingDetection(pattern);
            }
        }
    }

    private void detectSessionHijackingAttempts() {
        Instant since = Instant.now().minus(checkWindowHours, ChronoUnit.HOURS);
        Instant now = Instant.now();

        List<RefreshToken> recentTokens = refreshTokenRepository.findActiveTokensCreatedSince(since, now);
        if (recentTokens.isEmpty()) return;

        Set<String> usernames = recentTokens.stream()
                .map(RefreshToken::getUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, List<RefreshToken>> historyByUser = refreshTokenRepository
                .findActiveTokensByUsernameIn(usernames, now)
                .stream()
                .collect(Collectors.groupingBy(RefreshToken::getUsername));

        for (RefreshToken token : recentTokens) {
            List<RefreshToken> userHistory = historyByUser.getOrDefault(token.getUsername(), List.of());
            SessionHijackingPattern pattern = analyzeSessionHijacking(token, userHistory);
            if (pattern.isSuspicious()) {
                handleSessionHijackingDetection(pattern);
            }
        }
    }

    // ── Análisis de patrones ───────────────────────────────────────────────────

    private BruteForcePattern analyzeBruteForcePattern(String ipAddress, long failedCount, Instant since) {
        Instant now = Instant.now();
        List<AuditLog> failedLogins = auditLogRepository.findFailedLoginsByIpSince(
                ipAddress, toZonedDateTime(since), toZonedDateTime(now));

        Set<String> affectedUsernames = failedLogins.stream()
                .map(AuditLog::getUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        boolean escalatingPattern = isEscalatingPattern(failedLogins.stream().map(al -> toInstant(al.getCreatedAt())).toList());
        boolean multipleUsers = affectedUsernames.size() > 3;

        return BruteForcePattern.builder()
                .ipAddress(ipAddress)
                .attemptCount((int) failedCount)
                .timeWindow(Duration.between(since, now))
                .escalatingPattern(escalatingPattern)
                .multipleUsers(multipleUsers)
                .affectedUsernames(affectedUsernames)
                .riskScore(calculateRiskScore(escalatingPattern, multipleUsers))
                .build();
    }

    private SessionHijackingPattern analyzeSessionHijacking(RefreshToken token, List<RefreshToken> userHistory) {
        boolean userAgentChanged = userHistory.stream()
                .anyMatch(t -> !Objects.equals(t.getUserAgent(), token.getUserAgent()));

        RefreshToken conflictingSession = token.getLastUsedAt() == null ? null : userHistory.stream()
                .filter(t -> !Objects.equals(t.getIpAddress(), token.getIpAddress()))
                .filter(t -> t.getLastUsedAt() != null)
                .filter(t -> Duration.between(t.getLastUsedAt(), token.getLastUsedAt()).abs().toMinutes() < 5)
                .findFirst()
                .orElse(null);

        boolean rapidIPChange = conflictingSession != null;

        return SessionHijackingPattern.builder()
                .tokenId(token.getJti())
                .conflictingTokenId(conflictingSession != null ? conflictingSession.getJti() : null)
                .username(token.getUsername())
                .userAgentChanged(userAgentChanged)
                .rapidIPChange(rapidIPChange)
                .suspicious(userAgentChanged && rapidIPChange)
                .build();
    }

    // ── Manejadores de alertas ─────────────────────────────────────────────────

    private void handleBruteForceDetection(BruteForcePattern pattern) {
        log.warn("BRUTE FORCE DETECTED - IP: {}, Failed logins: {}, Risk: {}",
                pattern.getIpAddress(), pattern.getAttemptCount(), pattern.getRiskScore());

        securityAuditService.logCriticalEvent(
                "SYSTEM",
                "BRUTE_FORCE_ATTEMPT",
                String.format("IP %s: %d failed logins in %s (risk=%d)",
                        pattern.getIpAddress(),
                        pattern.getAttemptCount(),
                        pattern.getTimeWindow(),
                        pattern.getRiskScore()),
                Map.of(
                        "ip", pattern.getIpAddress(),
                        "attempts", pattern.getAttemptCount(),
                        "riskScore", pattern.getRiskScore(),
                        "multipleUsers", pattern.isMultipleUsers(),
                        "affectedUsernames", pattern.getAffectedUsernames()
                )
        );

        if (pattern.getRiskScore() > 8 && autoBlockEnabled) {
            autoBlockIP(pattern.getIpAddress(), "Brute force attack detected");
        }

        dispatchAlert(SecurityAlert.builder()
                .type(SecurityAlertType.BRUTE_FORCE)
                .severity(pattern.getRiskScore() > 7 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM)
                .source(pattern.getIpAddress())
                .description("Brute force pattern detected from IP " + pattern.getIpAddress())
                .additionalData(Map.of(
                        "riskScore", pattern.getRiskScore(),
                        "affectedUsernames", pattern.getAffectedUsernames()
                ))
                .build());
    }

    private void handleTokenFarmingDetection(TokenFarmingPattern pattern) {
        log.warn("TOKEN FARMING DETECTED - User: {}, Tokens: {}, IPs: {}",
                pattern.getUsername(), pattern.getTokenCount(), pattern.getUniqueIPs().size());

        securityAuditService.logCriticalEvent(
                pattern.getUsername(),
                "TOKEN_FARMING",
                String.format("User created %d tokens from %d different IPs in %d hour(s)",
                        pattern.getTokenCount(), pattern.getUniqueIPs().size(), checkWindowHours),
                Map.of(
                        "tokenCount", pattern.getTokenCount(),
                        "uniqueIPs", pattern.getUniqueIPs().size()
                )
        );

        if (autoBlockEnabled && pattern.getTokenCount() > tokenFarmingThreshold * 2) {
            revokeExcessiveTokensForUser(pattern.getUsername(), 5);
        }

        dispatchAlert(SecurityAlert.builder()
                .type(SecurityAlertType.TOKEN_FARMING)
                .severity(AlertSeverity.HIGH)
                .source(pattern.getUsername())
                .description("Token farming detected for user " + pattern.getUsername())
                .additionalData(Map.of("tokenCount", pattern.getTokenCount()))
                .build());
    }

    private void handleSessionHijackingDetection(SessionHijackingPattern pattern) {
        log.warn("SESSION HIJACKING SUSPECTED - User: {}, Token: {}",
                pattern.getUsername(), pattern.getTokenId());

        securityAuditService.logCriticalEvent(
                pattern.getUsername(),
                "SESSION_HIJACKING_SUSPECTED",
                "Suspicious session activity detected for token " + pattern.getTokenId(),
                Map.of(
                        "tokenId", pattern.getTokenId(),
                        "conflictingTokenId", String.valueOf(pattern.getConflictingTokenId()),
                        "userAgentChanged", pattern.isUserAgentChanged(),
                        "rapidIPChange", pattern.isRapidIPChange()
                )
        );

        // Se revocan las DOS sesiones en conflicto, no solo la analizada: no hay forma
        // confiable de saber cuál de las dos es la del atacante, y revocar una sola
        // corre el riesgo de tumbar al usuario legítimo mientras deja viva la otra.
        // Cortando ambas, el atacante pierde acceso seguro y el usuario legítimo
        // simplemente vuelve a loguearse con su contraseña.
        if (autoBlockEnabled) {
            revokeByJti(pattern.getTokenId());
            revokeByJti(pattern.getConflictingTokenId());
        }

        dispatchAlert(SecurityAlert.builder()
                .type(SecurityAlertType.SESSION_HIJACKING)
                .severity(AlertSeverity.HIGH)
                .source(pattern.getUsername())
                .description("Possible session hijacking for user " + pattern.getUsername())
                .additionalData(Map.of(
                        "tokenId", pattern.getTokenId(),
                        "conflictingTokenId", String.valueOf(pattern.getConflictingTokenId()),
                        "userAgentChanged", pattern.isUserAgentChanged(),
                        "rapidIPChange", pattern.isRapidIPChange()
                ))
                .build());
    }

    private void revokeByJti(String jti) {
        if (jti == null) return;
        refreshTokenRepository.findByJti(jti).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
            log.info("Auto-revoked suspicious token: {}", jti);
        });
    }

    // ── Acciones de remediación ────────────────────────────────────────────────

    private void autoBlockIP(String ipAddress, String reason) {
        log.warn("AUTO-BLOCKING IP: {} - Reason: {}", ipAddress, reason);
        int revoked = refreshTokenRepository.revokeAllByIpAddress(ipAddress);
        log.info("Revoked {} tokens from blocked IP: {}", revoked, ipAddress);

        securityAuditService.logCriticalEvent(
                "SYSTEM",
                "IP_AUTO_BLOCKED",
                String.format("IP %s blocked: %s (%d tokens revoked)", ipAddress, reason, revoked),
                Map.of("ip", ipAddress, "tokensRevoked", revoked, "reason", reason)
        );

        dispatchAlert(SecurityAlert.builder()
                .type(SecurityAlertType.IP_BLOCKED)
                .severity(AlertSeverity.CRITICAL)
                .source(ipAddress)
                .description("IP " + ipAddress + " auto-blocked: " + reason)
                .additionalData(Map.of("reason", reason, "tokensRevoked", revoked))
                .build());
    }

    private void revokeExcessiveTokensForUser(String username, int keepCount) {
        List<RefreshToken> userTokens = refreshTokenRepository
                .findActiveTokensByUsername(username, Instant.now());

        if (userTokens.size() <= keepCount) return;

        List<RefreshToken> tokensToRevoke = userTokens.stream()
                .sorted(Comparator.comparing(
                        t -> t.getLastUsedAt() != null ? t.getLastUsedAt() : t.getCreatedAt(),
                        Comparator.reverseOrder()
                ))
                .skip(keepCount)
                .collect(Collectors.toList());

        tokensToRevoke.forEach(RefreshToken::revoke);
        refreshTokenRepository.saveAll(tokensToRevoke);

        log.info("Auto-revoked {} excessive tokens for user: {}", tokensToRevoke.size(), username);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Set<String> extractUniqueIPs(List<RefreshToken> tokens) {
        return tokens.stream()
                .map(RefreshToken::getIpAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean isEscalatingPattern(List<Instant> timestamps) {
        if (timestamps.size() < 3) return false;

        List<Instant> sorted = timestamps.stream().sorted().toList();

        for (int i = 2; i < sorted.size(); i++) {
            Duration interval1 = Duration.between(sorted.get(i - 2), sorted.get(i - 1));
            Duration interval2 = Duration.between(sorted.get(i - 1), sorted.get(i));
            // interval2 en cero (ráfaga instantánea) es el caso más extremo de aceleración;
            // si no, comparamos las duraciones directamente para no truncar por división entera
            // (ej. 19s/10s truncaba a 1, perdiendo que en realidad casi se duplicó el ritmo)
            if (interval2.isZero() || interval1.compareTo(interval2.multipliedBy(2)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private ZonedDateTime toZonedDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Instant toInstant(ZonedDateTime zonedDateTime) {
        return zonedDateTime.toInstant();
    }

    private int calculateRiskScore(boolean escalatingPattern, boolean multipleUsers) {
        // Base 4: ya cruzó el umbral de logins fallidos reales por sí solo es una
        // señal genuina (a diferencia del extinto "rapidFire", que era tautológico).
        int score = 4;
        if (escalatingPattern) score += 3;
        if (multipleUsers) score += 3;
        return score;
    }

    /**
     * Registra la alerta y, según severidad, avisa a los admins activos: in-app para
     * HIGH/CRITICAL (vía SSE), email para CRITICAL (evita saturar de correos con
     * falsos positivos de severidad menor).
     */
    private void dispatchAlert(SecurityAlert alert) {
        log.warn("SECURITY ALERT [{}] severity={} source={}: {}",
                alert.getType(), alert.getSeverity(), alert.getSource(), alert.getDescription());

        if (alert.getSeverity() == AlertSeverity.HIGH || alert.getSeverity() == AlertSeverity.CRITICAL) {
            notifyAdminsInApp(alert);
        }
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            notifyAdminsByEmail(alert);
        }
    }

    private void notifyAdminsInApp(SecurityAlert alert) {
        String title = "Alerta de seguridad: " + alert.getType();
        for (AdminDetails admin : adminDetailsRepository.findActiveAdmins()) {
            try {
                notificationService.createInternalNotification(
                        admin.getId(), title, alert.getDescription(), alert.getDetectedAt());
            } catch (Exception e) {
                log.error("Failed to notify admin {} about security alert", admin.getId(), e);
            }
        }
    }

    private void notifyAdminsByEmail(SecurityAlert alert) {
        emailService.sendSecurityAlertEmail(
                adminNotificationEmail,
                alert.getType().name(),
                alert.getSeverity().name(),
                alert.getSource(),
                alert.getDescription(),
                ZonedDateTime.ofInstant(alert.getDetectedAt(), NOTIFICATION_ZONE));
    }
}
