// package com.VerYGana.security.auth.refreshToken;

// import java.time.Duration;
// import java.time.Instant;
// import java.time.temporal.ChronoUnit;
// import java.util.Comparator;
// import java.util.List;
// import java.util.Map;
// import java.util.Objects;
// import java.util.Set;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.atomic.AtomicInteger;
// import java.util.stream.Collectors;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.scheduling.annotation.Async;
// import org.springframework.stereotype.Service;

// import com.VerYGana.services.interfaces.NotificationService;

// import lombok.extern.slf4j.Slf4j;

// @Service
// @Slf4j
// public class SecurityMonitoringService {
    
//     private final RefreshTokenRepository refreshTokenRepository;
//     private final SecurityAuditService securityAuditService;
//     private final NotificationService notificationService;
    
//     @Value("${app.security.monitoring.enabled:true}")
//     private boolean monitoringEnabled;
    
//     @Value("${app.security.monitoring.auto-block-enabled:false}")
//     private boolean autoBlockEnabled;
    
//     private final Map<String, AtomicInteger> ipAttemptCounter = new ConcurrentHashMap<>();
//     private final Map<String, Instant> ipLastAttempt = new ConcurrentHashMap<>();
    
//     public SecurityMonitoringService(RefreshTokenRepository refreshTokenRepository,
//                                    SecurityAuditService securityAuditService,
//                                    NotificationService notificationService) {
//         this.refreshTokenRepository = refreshTokenRepository;
//         this.securityAuditService = securityAuditService;
//         this.notificationService = notificationService;
//     }
    
//     /**
//      * Detecta y analiza patrones de actividad sospechosa
//      */
//     @Async
//     public void analyzeSecurityPatterns() {
//         if (!monitoringEnabled) {
//             return;
//         }
        
//         try {
//             log.debug("Starting comprehensive security pattern analysis");
            
//             // Análisis de múltiples vectores de ataque
//             detectBruteForceAttempts();
//             detectTokenFarmingPatterns();
//             detectGeographicAnomalies();
//             detectDeviceFingerprinting();
//             detectSessionHijackingAttempts();
            
//             log.debug("Security pattern analysis completed");
            
//         } catch (Exception e) {
//             log.error("Error during security pattern analysis", e);
//         }
//     }
    
//     /**
//      * Detecta intentos de fuerza bruta basados en IPs
//      */
//     private void detectBruteForceAttempts() {
//         Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
//         List<Object[]> suspiciousIPs = refreshTokenRepository.findSuspiciousIPs(since, 5);
        
//         for (Object[] result : suspiciousIPs) {
//             String ipAddress = (String) result[0];
//             Long attemptCount = (Long) result[1];
            
//             BruteForcePattern pattern = analyzeBruteForcePattern(ipAddress, attemptCount, since);
            
//             if (pattern.isSuspicious()) {
//                 handleBruteForceDetection(pattern);
//             }
//         }
//     }
    
//     /**
//      * Detecta patrones de "token farming" (creación masiva de tokens)
//      */
//     private void detectTokenFarmingPatterns() {
//         Instant since = Instant.now().minus(15, ChronoUnit.MINUTES);
        
//         // Buscar usuarios que crean tokens muy rápidamente
//         Map<String, List<RefreshToken>> recentTokensByUser = getRecentTokensByUser(since);
        
//         for (Map.Entry<String, List<RefreshToken>> entry : recentTokensByUser.entrySet()) {
//             String username = entry.getKey();
//             List<RefreshToken> tokens = entry.getValue();
            
//             if (tokens.size() > 10) { // Más de 10 tokens en 15 minutos
//                 TokenFarmingPattern pattern = new TokenFarmingPattern(
//                     username, 
//                     tokens.size(), 
//                     extractUniqueIPs(tokens),
//                     since
//                 );
                
//                 handleTokenFarmingDetection(pattern);
//             }
//         }
//     }
    
//     /**
//      * Detecta anomalías geográficas (cambios de ubicación imposibles)
//      */
//     private void detectGeographicAnomalies() {
//         // Implementación básica - puedes integrar con servicios de geolocalización
//         List<RefreshToken> recentTokens = getTokensFromLastHour();
        
//         Map<String, List<RefreshToken>> tokensByUser = recentTokens.stream()
//             .collect(Collectors.groupingBy(RefreshToken::getUsername));
        
//         for (Map.Entry<String, List<RefreshToken>> entry : tokensByUser.entrySet()) {
//             String username = entry.getKey();
//             List<RefreshToken> userTokens = entry.getValue();
            
//             if (userTokens.size() > 1) {
//                 GeographicAnomalyPattern anomaly = analyzeGeographicPattern(username, userTokens);
//                 if (anomaly.isAnomalous()) {
//                     handleGeographicAnomaly(anomaly);
//                 }
//             }
//         }
//     }
    
//     /**
//      * Detecta fingerprinting de dispositivos sospechoso
//      */
//     private void detectDeviceFingerprinting() {
//         Instant since = Instant.now().minus(2, ChronoUnit.HOURS);
        
//         // Buscar dispositivos que cambian user agent muy frecuentemente
//         Map<String, List<RefreshToken>> tokensByDevice = getTokensByDevice(since);
        
//         for (Map.Entry<String, List<RefreshToken>> entry : tokensByDevice.entrySet()) {
//             String deviceId = entry.getKey();
//             List<RefreshToken> tokens = entry.getValue();
            
//             Set<String> uniqueUserAgents = tokens.stream()
//                 .map(RefreshToken::getUserAgent)
//                 .filter(Objects::nonNull)
//                 .collect(Collectors.toSet());
            
//             if (uniqueUserAgents.size() > 5) { // Más de 5 user agents diferentes
//                 DeviceFingerprintingPattern pattern = new DeviceFingerprintingPattern(
//                     deviceId,
//                     uniqueUserAgents.size(),
//                     tokens.size(),
//                     since
//                 );
                
//                 handleDeviceFingerprintingDetection(pattern);
//             }
//         }
//     }
    
//     /**
//      * Detecta intentos de secuestro de sesión
//      */
//     private void detectSessionHijackingAttempts() {
//         Instant since = Instant.now().minus(30, ChronoUnit.MINUTES);
        
//         List<RefreshToken> recentTokens = getTokensFromLastHour();
        
//         for (RefreshToken token : recentTokens) {
//             SessionHijackingPattern pattern = analyzeSessionHijacking(token);
//             if (pattern.isSuspicious()) {
//                 handleSessionHijackingDetection(pattern);
//             }
//         }
//     }
    
//     /**
//      * Analiza patrón de fuerza bruta para una IP específica
//      */
//     private BruteForcePattern analyzeBruteForcePattern(String ipAddress, Long attemptCount, Instant since) {
//         // Obtener historial de la IP
//         List<RefreshToken> ipHistory = getTokenHistoryForIP(ipAddress, since.minus(24, ChronoUnit.HOURS));
        
//         // Calcular métricas
//         boolean rapidFire = attemptCount > 10; // Más de 10 intentos por hora
//         boolean escalatingPattern = isEscalatingPattern(ipHistory);
//         boolean multipleUsers = getUniqueUsersFromTokens(ipHistory).size() > 5;
        
//         return BruteForcePattern.builder()
//             .ipAddress(ipAddress)
//             .attemptCount(attemptCount.intValue())
//             .timeWindow(Duration.between(since, Instant.now()))
//             .rapidFire(rapidFire)
//             .escalatingPattern(escalatingPattern)
//             .multipleUsers(multipleUsers)
//             .suspicious(rapidFire || escalatingPattern || multipleUsers)
//             .riskScore(calculateBruteForceRiskScore(rapidFire, escalatingPattern, multipleUsers))
//             .build();
//     }
    
//     /**
//      * Maneja detección de fuerza bruta
//      */
//     private void handleBruteForceDetection(BruteForcePattern pattern) {
//         String ipAddress = pattern.getIpAddress();
        
//         log.warn("BRUTE FORCE DETECTED - IP: {}, Attempts: {}, Risk Score: {}", 
//                 ipAddress, pattern.getAttemptCount(), pattern.getRiskScore());
        
//         // Registrar en auditoría
//         securityAuditService.logSuspiciousActivity(
//             "SYSTEM",
//             "BRUTE_FORCE_ATTEMPT",
//             String.format("IP %s attempted %d tokens in %s", 
//                 ipAddress, pattern.getAttemptCount(), pattern.getTimeWindow())
//         );
        
//         // Acciones automáticas basadas en el riesgo
//         if (pattern.getRiskScore() > 8 && autoBlockEnabled) {
//             autoBlockIP(ipAddress, "Brute force attack detected");
//         }
        
//         // Notificar administradores
//         SecurityAlert alert = SecurityAlert.builder()
//             .type(SecurityAlertType.BRUTE_FORCE)
//             .severity(pattern.getRiskScore() > 7 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM)
//             .source(ipAddress)
//             .description(String.format("Brute force pattern detected from IP %s", ipAddress))
//             .additionalData(Map.of(
//                 "attemptCount", pattern.getAttemptCount(),
//                 "riskScore", pattern.getRiskScore(),
//                 "multipleUsers", pattern.isMultipleUsers()
//             ))
//             .build();
        
//         notificationService.sendSecurityAlert(alert);
//     }
    
//     /**
//      * Maneja detección de token farming
//      */
//     private void handleTokenFarmingDetection(TokenFarmingPattern pattern) {
//         log.warn("TOKEN FARMING DETECTED - User: {}, Tokens: {}, IPs: {}", 
//                 pattern.getUsername(), pattern.getTokenCount(), pattern.getUniqueIPs().size());
        
//         securityAuditService.logSuspiciousActivity(
//             pattern.getUsername(),
//             "TOKEN_FARMING",
//             String.format("User created %d tokens from %d different IPs in 15 minutes", 
//                 pattern.getTokenCount(), pattern.getUniqueIPs().size())
//         );
        
//         // Auto-revocar tokens excesivos si está habilitado
//         if (autoBlockEnabled && pattern.getTokenCount() > 20) {
//             revokeExcessiveTokensForUser(pattern.getUsername(), 5); // Mantener solo 5
//         }
        
//         SecurityAlert alert = SecurityAlert.builder()
//             .type(SecurityAlertType.TOKEN_FARMING)
//             .severity(AlertSeverity.HIGH)
//             .source(pattern.getUsername())
//             .description(String.format("Token farming detected for user %s", pattern.getUsername()))
//             .additionalData(Map.of(
//                 "tokenCount", pattern.getTokenCount(),
//                 "uniqueIPs", pattern.getUniqueIPs().size()
//             ))
//             .build();
        
//         notificationService.sendSecurityAlert(alert);
//     }
    
//     /**
//      * Maneja detección de anomalía geográfica
//      */
//     private void handleGeographicAnomaly(GeographicAnomalyPattern anomaly) {
//         log.warn("GEOGRAPHIC ANOMALY DETECTED - User: {}, Distance: {}km in {}min", 
//                 anomaly.getUsername(), anomaly.getEstimatedDistance(), anomaly.getTimeDifference().toMinutes());
        
//         securityAuditService.logSuspiciousActivity(
//             anomaly.getUsername(),
//             "GEOGRAPHIC_ANOMALY",
//             String.format("User appeared to travel %d km in %d minutes", 
//                 anomaly.getEstimatedDistance(), anomaly.getTimeDifference().toMinutes())
//         );
        
//         // No auto-bloquear por anomalías geográficas (pueden ser VPNs legítimos)
//         // Solo alertar
        
//         SecurityAlert alert = SecurityAlert.builder()
//             .type(SecurityAlertType.GEOGRAPHIC_ANOMALY)
//             .severity(AlertSeverity.MEDIUM)
//             .source(anomaly.getUsername())
//             .description(String.format("Impossible travel detected for user %s", anomaly.getUsername()))
//             .additionalData(Map.of(
//                 "estimatedDistance", anomaly.getEstimatedDistance(),
//                 "timeDifference", anomaly.getTimeDifference().toMinutes(),
//                 "locations", anomaly.getLocations()
//             ))
//             .build();
        
//         notificationService.sendSecurityAlert(alert);
//     }
    
//     // Métodos helper y clases de datos
    
//     private Map<String, List<RefreshToken>> getRecentTokensByUser(Instant since) {
//         // Implementar query para obtener tokens recientes agrupados por usuario
//         return refreshTokenRepository.findActiveTokensByUsername("", Instant.now())
//             .stream()
//             .filter(token -> token.getCreatedAt().isAfter(since))
//             .collect(Collectors.groupingBy(RefreshToken::getUsername));
//     }
    
//     private Set<String> extractUniqueIPs(List<RefreshToken> tokens) {
//         return tokens.stream()
//             .map(RefreshToken::getIpAddress)
//             .filter(Objects::nonNull)
//             .collect(Collectors.toSet());
//     }
    
//     private List<RefreshToken> getTokensFromLastHour() {
//         Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
//         return refreshTokenRepository.findActiveTokensByUsername("", Instant.now())
//             .stream()
//             .filter(token -> token.getCreatedAt().isAfter(since))
//             .collect(Collectors.toList());
//     }
    
//     private void autoBlockIP(String ipAddress, String reason) {
//         // Implementar bloqueo automático de IP
//         // Esto puede ser a nivel de firewall, nginx, o base de datos
//         log.warn("AUTO-BLOCKING IP: {} - Reason: {}", ipAddress, reason);
        
//         // Opción 1: Blacklist en BD
//         // ipBlacklistRepository.save(new IPBlacklist(ipAddress, reason, Instant.now()));
        
//         // Opción 2: Integración con firewall
//         // firewallService.blockIP(ipAddress, Duration.ofHours(24));
        
//         // Opción 3: Rate limiting agresivo
//         // rateLimitingService.banIP(ipAddress);
        
//         // Revocar todos los tokens existentes de esta IP
//         revokeAllTokensFromIP(ipAddress);
//     }
    
//     private void revokeAllTokensFromIP(String ipAddress) {
//         List<RefreshToken> tokensToRevoke = refreshTokenRepository.findAll()
//             .stream()
//             .filter(token -> ipAddress.equals(token.getIpAddress()) && token.isActive())
//             .collect(Collectors.toList());
        
//         tokensToRevoke.forEach(RefreshToken::revoke);
//         refreshTokenRepository.saveAll(tokensToRevoke);
        
//         log.info("Revoked {} tokens from blocked IP: {}", tokensToRevoke.size(), ipAddress);
//     }
    
//     private void revokeExcessiveTokensForUser(String username, int keepCount) {
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
        
//         log.info("Auto-revoked {} excessive tokens for user: {}", tokensToRevoke.size(), username);
//     }
    
//     // Métodos helper adicionales
    
//     private List<RefreshToken> getTokenHistoryForIP(String ipAddress, Instant since) {
//         return refreshTokenRepository.findAll()
//             .stream()
//             .filter(token -> ipAddress.equals(token.getIpAddress()))
//             .filter(token -> token.getCreatedAt().isAfter(since))
//             .collect(Collectors.toList());
//     }
    
//     private Set<String> getUniqueUsersFromTokens(List<RefreshToken> tokens) {
//         return tokens.stream()
//             .map(RefreshToken::getUsername)
//             .collect(Collectors.toSet());
//     }
    
//     private boolean isEscalatingPattern(List<RefreshToken> tokens) {
//         if (tokens.size() < 3) return false;
        
//         // Ordenar por tiempo
//         List<RefreshToken> sorted = tokens.stream()
//             .sorted(Comparator.comparing(RefreshToken::getCreatedAt))
//             .collect(Collectors.toList());
        
//         // Verificar si los intervalos se acortan (escalation)
//         for (int i = 2; i < sorted.size(); i++) {
//             Duration interval1 = Duration.between(
//                 sorted.get(i-2).getCreatedAt(), 
//                 sorted.get(i-1).getCreatedAt()
//             );
//             Duration interval2 = Duration.between(
//                 sorted.get(i-1).getCreatedAt(), 
//                 sorted.get(i).getCreatedAt()
//             );
            
//             if (interval2.toMinutes() < interval1.toMinutes() / 2) {
//                 return true; // Intervalo se redujo a la mitad o menos
//             }
//         }
        
//         return false;
//     }
    
//     private int calculateBruteForceRiskScore(boolean rapidFire, boolean escalatingPattern, boolean multipleUsers) {
//         int score = 0;
//         if (rapidFire) score += 4;
//         if (escalatingPattern) score += 3;
//         if (multipleUsers) score += 3;
//         return Math.min(score, 10); // Máximo 10
//     }
    
//     private Map<String, List<RefreshToken>> getTokensByDevice(Instant since) {
//         return refreshTokenRepository.findAll()
//             .stream()
//             .filter(token -> token.getCreatedAt().isAfter(since))
//             .filter(token -> token.getDeviceId() != null)
//             .collect(Collectors.groupingBy(RefreshToken::getDeviceId));
//     }
    
//     private GeographicAnomalyPattern analyzeGeographicPattern(String username, List<RefreshToken> userTokens) {
//         // Ordenar por tiempo
//         List<RefreshToken> sorted = userTokens.stream()
//             .sorted(Comparator.comparing(RefreshToken::getCreatedAt))
//             .collect(Collectors.toList());
        
//         for (int i = 1; i < sorted.size(); i++) {
//             RefreshToken previous = sorted.get(i-1);
//             RefreshToken current = sorted.get(i);
            
//             // Simular detección geográfica (en producción usar servicio real)
//             int estimatedDistance = calculateDistance(previous.getIpAddress(), current.getIpAddress());
//             Duration timeDifference = Duration.between(previous.getCreatedAt(), current.getCreatedAt());
            
//             // Velocidad imposible (más de 1000 km/h)
//             if (estimatedDistance > 100 && timeDifference.toHours() < 1) {
//                 double speed = estimatedDistance / Math.max(timeDifference.toMinutes(), 1) * 60; // km/h
                
//                 if (speed > 1000) { // Velocidad imposible
//                     return GeographicAnomalyPattern.builder()
//                         .username(username)
//                         .estimatedDistance(estimatedDistance)
//                         .timeDifference(timeDifference)
//                         .anomalous(true)
//                         .locations(List.of(previous.getIpAddress(), current.getIpAddress()))
//                         .build();
//                 }
//             }
//         }
        
//         return GeographicAnomalyPattern.builder()
//             .username(username)
//             .anomalous(false)
//             .build();
//     }
    
//     private int calculateDistance(String ip1, String ip2) {
//         // Simulación - en producción usar servicio de geolocalización real
//         // como MaxMind GeoIP2, ipstack, etc.
//         if (ip1 == null || ip2 == null) return 0;
        
//         // Simulación básica basada en hash de IPs
//         int hash1 = Math.abs(ip1.hashCode() % 1000);
//         int hash2 = Math.abs(ip2.hashCode() % 1000);
        
//         return Math.abs(hash1 - hash2) * 20; // Simulación de distancia en km
//     }
    
//     private SessionHijackingPattern analyzeSessionHijacking(RefreshToken token) {
//         // Detectar cambios súbitos en user agent o patrones sospechosos
//         List<RefreshToken> userHistory = refreshTokenRepository
//             .findActiveTokensByUsername(token.getUsername(), Instant.now());
        
//         boolean userAgentChanged = userHistory.stream()
//             .anyMatch(t -> !Objects.equals(t.getUserAgent(), token.getUserAgent()));
        
//         boolean rapidIPChange = userHistory.stream()
//             .filter(t -> !Objects.equals(t.getIpAddress(), token.getIpAddress()))
//             .anyMatch(t -> Duration.between(t.getLastUsedAt(), token.getLastUsedAt()).toMinutes() < 5);
        
//         return SessionHijackingPattern.builder()
//             .tokenId(token.getJti())
//             .username(token.getUsername())
//             .userAgentChanged(userAgentChanged)
//             .rapidIPChange(rapidIPChange)
//             .suspicious(userAgentChanged && rapidIPChange)
//             .build();
//     }
    
//     private void handleDeviceFingerprintingDetection(DeviceFingerprintingPattern pattern) {
//         log.warn("DEVICE FINGERPRINTING DETECTED - Device: {}, UserAgents: {}, Tokens: {}", 
//                 pattern.getDeviceId(), pattern.getUniqueUserAgents(), pattern.getTokenCount());
        
//         securityAuditService.logSuspiciousActivity(
//             "SYSTEM",
//             "DEVICE_FINGERPRINTING",
//             String.format("Device %s used %d different user agents for %d tokens", 
//                 pattern.getDeviceId(), pattern.getUniqueUserAgents(), pattern.getTokenCount())
//         );
//     }
    
//     private void handleSessionHijackingDetection(SessionHijackingPattern pattern) {
//         log.warn("SESSION HIJACKING SUSPECTED - User: {}, Token: {}", 
//                 pattern.getUsername(), pattern.getTokenId());
        
//         securityAuditService.logSuspiciousActivity(
//             pattern.getUsername(),
//             "SESSION_HIJACKING_SUSPECTED",
//             String.format("Suspicious session activity detected for token %s", pattern.getTokenId())
//         );
        
//         // Revocar token sospechoso inmediatamente
//         if (autoBlockEnabled) {
//             refreshTokenRepository.findByJti(pattern.getTokenId())
//                 .ifPresent(token -> {
//                     token.revoke();
//                     refreshTokenRepository.save(token);
//                     log.info("Auto-revoked suspicious token: {}", pattern.getTokenId());
//                 });
//         }
//     }
// }