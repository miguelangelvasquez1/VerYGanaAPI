package com.verygana2.security.auth.refreshToken;

import java.time.Duration;
import java.time.Instant;
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

import com.verygana2.security.monitoring.AlertSeverity;
import com.verygana2.security.monitoring.BruteForcePattern;
import com.verygana2.security.monitoring.DeviceFingerprintingPattern;
import com.verygana2.security.monitoring.GeographicAnomalyPattern;
import com.verygana2.security.monitoring.SecurityAlert;
import com.verygana2.security.monitoring.SecurityAlertType;
import com.verygana2.security.monitoring.SessionHijackingPattern;
import com.verygana2.security.monitoring.TokenFarmingPattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityMonitoringService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService securityAuditService;

    @Value("${app.security.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${app.security.monitoring.auto-block-enabled:false}")
    private boolean autoBlockEnabled;

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
            detectBruteForceAttempts();
            detectTokenFarmingPatterns();
            detectGeographicAnomalies();
            detectDeviceFingerprinting();
            detectSessionHijackingAttempts();
            log.debug("Security pattern analysis completed");
        } catch (Exception e) {
            log.error("Error during security pattern analysis", e);
        }
    }

    // ── Detección de ataques ───────────────────────────────────────────────────

    private void detectBruteForceAttempts() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Object[]> suspiciousIPs = refreshTokenRepository.findSuspiciousIPs(since, 5);

        for (Object[] result : suspiciousIPs) {
            String ipAddress = (String) result[0];
            long attemptCount = ((Number) result[1]).longValue();

            BruteForcePattern pattern = analyzeBruteForcePattern(ipAddress, attemptCount, since);
            if (pattern.isSuspicious()) {
                handleBruteForceDetection(pattern);
            }
        }
    }

    private void detectTokenFarmingPatterns() {
        Instant since = Instant.now().minus(15, ChronoUnit.MINUTES);

        Map<String, List<RefreshToken>> recentTokensByUser = refreshTokenRepository
                .findAllActiveTokens(Instant.now())
                .stream()
                .filter(t -> t.getCreatedAt().isAfter(since))
                .collect(Collectors.groupingBy(RefreshToken::getUsername));

        for (Map.Entry<String, List<RefreshToken>> entry : recentTokensByUser.entrySet()) {
            String username = entry.getKey();
            List<RefreshToken> tokens = entry.getValue();

            if (tokens.size() > 10) {
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

    private void detectGeographicAnomalies() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);

        Map<String, List<RefreshToken>> tokensByUser = refreshTokenRepository
                .findAllActiveTokens(Instant.now())
                .stream()
                .filter(t -> t.getCreatedAt().isAfter(since))
                .collect(Collectors.groupingBy(RefreshToken::getUsername));

        for (Map.Entry<String, List<RefreshToken>> entry : tokensByUser.entrySet()) {
            String username = entry.getKey();
            List<RefreshToken> userTokens = entry.getValue();

            if (userTokens.size() > 1) {
                GeographicAnomalyPattern anomaly = analyzeGeographicPattern(username, userTokens);
                if (anomaly.isAnomalous()) {
                    handleGeographicAnomaly(anomaly);
                }
            }
        }
    }

    private void detectDeviceFingerprinting() {
        Instant since = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant now = Instant.now();

        Map<String, List<RefreshToken>> tokensByDevice = refreshTokenRepository
                .findActiveTokensByDeviceSince(since, now)
                .stream()
                .collect(Collectors.groupingBy(RefreshToken::getDeviceId));

        for (Map.Entry<String, List<RefreshToken>> entry : tokensByDevice.entrySet()) {
            String deviceId = entry.getKey();
            List<RefreshToken> tokens = entry.getValue();

            Set<String> uniqueUserAgents = tokens.stream()
                    .map(RefreshToken::getUserAgent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (uniqueUserAgents.size() > 5) {
                DeviceFingerprintingPattern pattern = new DeviceFingerprintingPattern(
                        deviceId,
                        uniqueUserAgents.size(),
                        tokens.size(),
                        since
                );
                handleDeviceFingerprintingDetection(pattern);
            }
        }
    }

    private void detectSessionHijackingAttempts() {
        Instant since = Instant.now().minus(30, ChronoUnit.MINUTES);

        List<RefreshToken> recentTokens = refreshTokenRepository.findAllActiveTokens(Instant.now())
                .stream()
                .filter(t -> t.getCreatedAt().isAfter(since))
                .toList();

        for (RefreshToken token : recentTokens) {
            SessionHijackingPattern pattern = analyzeSessionHijacking(token);
            if (pattern.isSuspicious()) {
                handleSessionHijackingDetection(pattern);
            }
        }
    }

    // ── Análisis de patrones ───────────────────────────────────────────────────

    private BruteForcePattern analyzeBruteForcePattern(String ipAddress, long attemptCount, Instant since) {
        List<RefreshToken> ipHistory = refreshTokenRepository.findAllActiveTokens(Instant.now())
                .stream()
                .filter(t -> ipAddress.equals(t.getIpAddress()))
                .toList();

        boolean rapidFire = attemptCount > 10;
        boolean escalatingPattern = isEscalatingPattern(ipHistory);
        boolean multipleUsers = getUniqueUsersFromTokens(ipHistory).size() > 5;

        return BruteForcePattern.builder()
                .ipAddress(ipAddress)
                .attemptCount((int) attemptCount)
                .timeWindow(Duration.between(since, Instant.now()))
                .rapidFire(rapidFire)
                .escalatingPattern(escalatingPattern)
                .multipleUsers(multipleUsers)
                .suspicious(rapidFire || escalatingPattern || multipleUsers)
                .riskScore(calculateRiskScore(rapidFire, escalatingPattern, multipleUsers))
                .build();
    }

    private GeographicAnomalyPattern analyzeGeographicPattern(String username, List<RefreshToken> userTokens) {
        List<RefreshToken> sorted = userTokens.stream()
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt))
                .toList();

        for (int i = 1; i < sorted.size(); i++) {
            RefreshToken previous = sorted.get(i - 1);
            RefreshToken current = sorted.get(i);

            int estimatedDistance = simulateDistance(previous.getIpAddress(), current.getIpAddress());
            Duration timeDifference = Duration.between(previous.getCreatedAt(), current.getCreatedAt());

            if (estimatedDistance > 100 && timeDifference.toHours() < 1) {
                double speed = estimatedDistance / (double) Math.max(timeDifference.toMinutes(), 1) * 60;
                if (speed > 1000) {
                    return GeographicAnomalyPattern.builder()
                            .username(username)
                            .estimatedDistance(estimatedDistance)
                            .timeDifference(timeDifference)
                            .anomalous(true)
                            .locations(List.of(
                                    Objects.requireNonNullElse(previous.getIpAddress(), "unknown"),
                                    Objects.requireNonNullElse(current.getIpAddress(), "unknown")
                            ))
                            .build();
                }
            }
        }

        return GeographicAnomalyPattern.builder()
                .username(username)
                .anomalous(false)
                .build();
    }

    private SessionHijackingPattern analyzeSessionHijacking(RefreshToken token) {
        List<RefreshToken> userHistory = refreshTokenRepository
                .findActiveTokensByUsername(token.getUsername(), Instant.now());

        boolean userAgentChanged = userHistory.stream()
                .anyMatch(t -> !Objects.equals(t.getUserAgent(), token.getUserAgent()));

        boolean rapidIPChange = token.getLastUsedAt() != null && userHistory.stream()
                .filter(t -> !Objects.equals(t.getIpAddress(), token.getIpAddress()))
                .filter(t -> t.getLastUsedAt() != null)
                .anyMatch(t -> Duration.between(t.getLastUsedAt(), token.getLastUsedAt()).toMinutes() < 5);

        return SessionHijackingPattern.builder()
                .tokenId(token.getJti())
                .username(token.getUsername())
                .userAgentChanged(userAgentChanged)
                .rapidIPChange(rapidIPChange)
                .suspicious(userAgentChanged && rapidIPChange)
                .build();
    }

    // ── Manejadores de alertas ─────────────────────────────────────────────────

    private void handleBruteForceDetection(BruteForcePattern pattern) {
        log.warn("BRUTE FORCE DETECTED - IP: {}, Attempts: {}, Risk: {}",
                pattern.getIpAddress(), pattern.getAttemptCount(), pattern.getRiskScore());

        securityAuditService.logCriticalEvent(
                "SYSTEM",
                "BRUTE_FORCE_ATTEMPT",
                String.format("IP %s: %d tokens in %s (risk=%d)",
                        pattern.getIpAddress(),
                        pattern.getAttemptCount(),
                        pattern.getTimeWindow(),
                        pattern.getRiskScore()),
                Map.of(
                        "ip", pattern.getIpAddress(),
                        "attempts", pattern.getAttemptCount(),
                        "riskScore", pattern.getRiskScore(),
                        "multipleUsers", pattern.isMultipleUsers()
                )
        );

        if (pattern.getRiskScore() > 8 && autoBlockEnabled) {
            autoBlockIP(pattern.getIpAddress(), "Brute force attack detected");
        }

        logAlert(SecurityAlert.builder()
                .type(SecurityAlertType.BRUTE_FORCE)
                .severity(pattern.getRiskScore() > 7 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM)
                .source(pattern.getIpAddress())
                .description("Brute force pattern detected from IP " + pattern.getIpAddress())
                .additionalData(Map.of("riskScore", pattern.getRiskScore()))
                .build());
    }

    private void handleTokenFarmingDetection(TokenFarmingPattern pattern) {
        log.warn("TOKEN FARMING DETECTED - User: {}, Tokens: {}, IPs: {}",
                pattern.getUsername(), pattern.getTokenCount(), pattern.getUniqueIPs().size());

        securityAuditService.logCriticalEvent(
                pattern.getUsername(),
                "TOKEN_FARMING",
                String.format("User created %d tokens from %d different IPs in 15 minutes",
                        pattern.getTokenCount(), pattern.getUniqueIPs().size()),
                Map.of(
                        "tokenCount", pattern.getTokenCount(),
                        "uniqueIPs", pattern.getUniqueIPs().size()
                )
        );

        if (autoBlockEnabled && pattern.getTokenCount() > 20) {
            revokeExcessiveTokensForUser(pattern.getUsername(), 5);
        }

        logAlert(SecurityAlert.builder()
                .type(SecurityAlertType.TOKEN_FARMING)
                .severity(AlertSeverity.HIGH)
                .source(pattern.getUsername())
                .description("Token farming detected for user " + pattern.getUsername())
                .additionalData(Map.of("tokenCount", pattern.getTokenCount()))
                .build());
    }

    private void handleGeographicAnomaly(GeographicAnomalyPattern anomaly) {
        log.warn("GEOGRAPHIC ANOMALY DETECTED - User: {}, ~{}km in ~{}min",
                anomaly.getUsername(),
                anomaly.getEstimatedDistance(),
                anomaly.getTimeDifference() != null ? anomaly.getTimeDifference().toMinutes() : 0);

        securityAuditService.logSuspiciousActivity(
                anomaly.getUsername(),
                "GEOGRAPHIC_ANOMALY",
                String.format("Impossible travel: ~%d km in ~%d minutes",
                        anomaly.getEstimatedDistance(),
                        anomaly.getTimeDifference() != null ? anomaly.getTimeDifference().toMinutes() : 0)
        );

        logAlert(SecurityAlert.builder()
                .type(SecurityAlertType.GEOGRAPHIC_ANOMALY)
                .severity(AlertSeverity.MEDIUM)
                .source(anomaly.getUsername())
                .description("Impossible travel detected for user " + anomaly.getUsername())
                .additionalData(Map.of(
                        "estimatedDistanceKm", anomaly.getEstimatedDistance(),
                        "locations", anomaly.getLocations() != null ? anomaly.getLocations() : List.of()
                ))
                .build());
    }

    private void handleDeviceFingerprintingDetection(DeviceFingerprintingPattern pattern) {
        log.warn("DEVICE FINGERPRINTING DETECTED - Device: {}, UserAgents: {}, Tokens: {}",
                pattern.getDeviceId(), pattern.getUniqueUserAgents(), pattern.getTokenCount());

        securityAuditService.logSuspiciousActivity(
                "SYSTEM",
                "DEVICE_FINGERPRINTING",
                String.format("Device %s used %d different user agents for %d tokens",
                        pattern.getDeviceId(), pattern.getUniqueUserAgents(), pattern.getTokenCount())
        );

        logAlert(SecurityAlert.builder()
                .type(SecurityAlertType.DEVICE_FINGERPRINTING)
                .severity(AlertSeverity.MEDIUM)
                .source(pattern.getDeviceId())
                .description("Suspicious device fingerprinting for device " + pattern.getDeviceId())
                .additionalData(Map.of(
                        "uniqueUserAgents", pattern.getUniqueUserAgents(),
                        "tokenCount", pattern.getTokenCount()
                ))
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
                        "userAgentChanged", pattern.isUserAgentChanged(),
                        "rapidIPChange", pattern.isRapidIPChange()
                )
        );

        if (autoBlockEnabled) {
            refreshTokenRepository.findByJti(pattern.getTokenId()).ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.save(token);
                log.info("Auto-revoked suspicious token: {}", pattern.getTokenId());
            });
        }
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

    private Set<String> getUniqueUsersFromTokens(List<RefreshToken> tokens) {
        return tokens.stream()
                .map(RefreshToken::getUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean isEscalatingPattern(List<RefreshToken> tokens) {
        if (tokens.size() < 3) return false;

        List<RefreshToken> sorted = tokens.stream()
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt))
                .toList();

        for (int i = 2; i < sorted.size(); i++) {
            Duration interval1 = Duration.between(
                    sorted.get(i - 2).getCreatedAt(),
                    sorted.get(i - 1).getCreatedAt()
            );
            Duration interval2 = Duration.between(
                    sorted.get(i - 1).getCreatedAt(),
                    sorted.get(i).getCreatedAt()
            );
            if (interval2.toSeconds() > 0 && interval1.toSeconds() / interval2.toSeconds() >= 2) {
                return true;
            }
        }
        return false;
    }

    private int calculateRiskScore(boolean rapidFire, boolean escalatingPattern, boolean multipleUsers) {
        int score = 0;
        if (rapidFire) score += 4;
        if (escalatingPattern) score += 3;
        if (multipleUsers) score += 3;
        return Math.min(score, 10);
    }

    /** Simulación de distancia geográfica basada en IPs (reemplazar con GeoIP real en producción). */
    private int simulateDistance(String ip1, String ip2) {
        if (ip1 == null || ip2 == null || ip1.equals(ip2)) return 0;
        return Math.abs(Math.abs(ip1.hashCode() % 1000) - Math.abs(ip2.hashCode() % 1000)) * 20;
    }

    private void logAlert(SecurityAlert alert) {
        log.warn("SECURITY ALERT [{}] severity={} source={}: {}",
                alert.getType(), alert.getSeverity(), alert.getSource(), alert.getDescription());
    }
}
