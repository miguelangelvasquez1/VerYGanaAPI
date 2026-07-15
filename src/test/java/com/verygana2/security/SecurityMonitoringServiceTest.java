package com.verygana2.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.security.auth.refreshToken.RefreshToken;
import com.verygana2.security.auth.refreshToken.RefreshTokenRepository;
import com.verygana2.security.auth.refreshToken.SecurityAuditService;
import com.verygana2.security.auth.refreshToken.SecurityMonitoringService;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.utils.audit.AuditLog;
import com.verygana2.utils.audit.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityMonitoringService")
class SecurityMonitoringServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock SecurityAuditService securityAuditService;
    @Mock AdminDetailsRepository adminDetailsRepository;
    @Mock NotificationService notificationService;
    @Mock EmailService emailService;

    @InjectMocks SecurityMonitoringService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "monitoringEnabled", true);
        ReflectionTestUtils.setField(service, "autoBlockEnabled", false);
        ReflectionTestUtils.setField(service, "tokenFarmingThreshold", 10);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RefreshToken token(String username, String ip, String userAgent) {
        RefreshToken t = new RefreshToken();
        t.setUsername(username);
        t.setJti("jti-" + Math.random());
        t.setToken("tok-" + Math.random());
        t.setIpAddress(ip);
        t.setUserAgent(userAgent);
        t.setCreatedAt(Instant.now());  // vía setter — campo tiene valor por defecto pero podemos sobreescribir con reflexión
        t.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        t.setRevoked(false);
        t.setLastUsedAt(Instant.now());
        return t;
    }

    private AuditLog failedLogin(String username, String ip, Instant createdAt) {
        return AuditLog.builder()
                .username(username)
                .ipAddress(ip)
                .action("LOGIN_FAILED")
                .category("AUTH")
                .success(false)
                .createdAt(ZonedDateTime.ofInstant(createdAt, ZoneId.systemDefault()))
                .build();
    }

    /**
     * 6 usuarios distintos con login fallido desde una IP, espaciados uniformemente
     * (60s) para disparar solo multipleUsers (no escalatingPattern) de forma determinista.
     */
    private List<AuditLog> manyUsersFailedLogins(String ip) {
        Instant base = Instant.now();
        return List.of(
                failedLogin("u1", ip, base),
                failedLogin("u2", ip, base.plusSeconds(60)),
                failedLogin("u3", ip, base.plusSeconds(120)),
                failedLogin("u4", ip, base.plusSeconds(180)),
                failedLogin("u5", ip, base.plusSeconds(240)),
                failedLogin("u6", ip, base.plusSeconds(300))
        );
    }

    // ─── analyzeSecurityPatterns ──────────────────────────────────────────────

    @Nested
    @DisplayName("analyzeSecurityPatterns")
    class AnalyzeSecurityPatterns {

        @Test
        @DisplayName("no ejecuta análisis si monitoringEnabled=false")
        void skipsWhenDisabled() {
            ReflectionTestUtils.setField(service, "monitoringEnabled", false);

            service.analyzeSecurityPatterns();

            verify(auditLogRepository, never()).findIpsWithFailedLoginsSince(any(), anyInt());
            verify(refreshTokenRepository, never()).findActiveTokensCreatedSince(any(), any());
        }

        @Test
        @DisplayName("ejecuta todas las detecciones cuando está habilitado")
        void runsAllDetectionsWhenEnabled() {
            when(auditLogRepository.findIpsWithFailedLoginsSince(any(), anyInt())).thenReturn(List.of());
            when(refreshTokenRepository.findAllTokensCreatedSince(any())).thenReturn(List.of());
            when(refreshTokenRepository.findActiveTokensCreatedSince(any(), any())).thenReturn(List.of());

            service.analyzeSecurityPatterns();

            verify(auditLogRepository).findIpsWithFailedLoginsSince(any(), anyInt());
            verify(refreshTokenRepository).findAllTokensCreatedSince(any());
            verify(refreshTokenRepository).findActiveTokensCreatedSince(any(), any());
        }
    }

    // ─── Fuerza bruta ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("detectSuspiciousIpActivity")
    class BruteForce {

        @Test
        @DisplayName("registra actividad sospechosa cuando hay más de 5 usuarios distintos con login fallido desde la IP")
        void logsSuspiciousIPWithHighAttempts() {
            when(auditLogRepository.findIpsWithFailedLoginsSince(any(), anyInt()))
                    .thenReturn(Collections.singletonList(new Object[]{"10.0.0.1", 15L}));
            when(auditLogRepository.findFailedLoginsByIpSince(eq("10.0.0.1"), any(), any()))
                    .thenReturn(manyUsersFailedLogins("10.0.0.1"));

            service.analyzeSecurityPatterns();

            verify(securityAuditService).logCriticalEvent(
                    eq("SYSTEM"), eq("BRUTE_FORCE_ATTEMPT"), anyString(), any());
        }

        @Test
        @DisplayName("no registra nada cuando no hay IPs con logins fallidos")
        void doesNothingWhenNoSuspiciousIPs() {
            when(auditLogRepository.findIpsWithFailedLoginsSince(any(), anyInt())).thenReturn(List.of());
            when(refreshTokenRepository.findActiveTokensCreatedSince(any(), any())).thenReturn(List.of());

            service.analyzeSecurityPatterns();

            verify(securityAuditService, never()).logCriticalEvent(
                    eq("SYSTEM"), eq("BRUTE_FORCE_ATTEMPT"), anyString(), any());
        }

        @Test
        @DisplayName("no bloquea IP automáticamente si autoBlockEnabled=false")
        void doesNotAutoBlockWhenDisabled() {
            when(auditLogRepository.findIpsWithFailedLoginsSince(any(), anyInt()))
                    .thenReturn(Collections.singletonList(new Object[]{"10.0.0.1", 50L}));
            when(auditLogRepository.findFailedLoginsByIpSince(eq("10.0.0.1"), any(), any()))
                    .thenReturn(manyUsersFailedLogins("10.0.0.1"));

            service.analyzeSecurityPatterns();

            verify(refreshTokenRepository, never()).revokeAllByIpAddress(anyString());
        }

        @Test
        @DisplayName("no bloquea IP automáticamente si riskScore no supera 8 (solo multipleUsers)")
        void doesNotAutoBlockWhenRiskBelowThreshold() {
            ReflectionTestUtils.setField(service, "autoBlockEnabled", true);

            when(auditLogRepository.findIpsWithFailedLoginsSince(any(), anyInt()))
                    .thenReturn(Collections.singletonList(new Object[]{"10.0.0.1", 15L}));

            // 6 usuarios distintos espaciados uniformemente → multipleUsers=true (+3),
            // escalatingPattern=false (intervalos iguales) → riskScore=4+3=7, no supera el umbral de 8
            when(auditLogRepository.findFailedLoginsByIpSince(eq("10.0.0.1"), any(), any()))
                    .thenReturn(manyUsersFailedLogins("10.0.0.1"));

            service.analyzeSecurityPatterns();

            verify(refreshTokenRepository, never()).revokeAllByIpAddress("10.0.0.1");
        }

        @Test
        @DisplayName("bloquea IP automáticamente si autoBlockEnabled=true y riskScore>8 (multipleUsers + escalatingPattern)")
        void autoBlocksIPWhenEnabledAndHighRisk() {
            ReflectionTestUtils.setField(service, "autoBlockEnabled", true);

            when(auditLogRepository.findIpsWithFailedLoginsSince(any(), anyInt()))
                    .thenReturn(Collections.singletonList(new Object[]{"10.0.0.1", 15L}));

            // 6 usuarios distintos → multipleUsers=true (+3); intervalos que se duplican
            // (160s, 80s, 40s, 20s, 10s) → escalatingPattern=true (+3) → riskScore=4+3+3=10 > 8
            Instant base = Instant.now();
            List<AuditLog> failedLogins = List.of(
                    failedLogin("u1", "10.0.0.1", base),
                    failedLogin("u2", "10.0.0.1", base.plusSeconds(160)),
                    failedLogin("u3", "10.0.0.1", base.plusSeconds(240)),
                    failedLogin("u4", "10.0.0.1", base.plusSeconds(280)),
                    failedLogin("u5", "10.0.0.1", base.plusSeconds(300)),
                    failedLogin("u6", "10.0.0.1", base.plusSeconds(310))
            );
            when(auditLogRepository.findFailedLoginsByIpSince(eq("10.0.0.1"), any(), any())).thenReturn(failedLogins);

            service.analyzeSecurityPatterns();

            verify(refreshTokenRepository).revokeAllByIpAddress("10.0.0.1");
        }
    }

    // ─── Token Farming ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("detectTokenFarmingPatterns")
    class TokenFarming {

        @Test
        @DisplayName("registra TOKEN_FARMING cuando un usuario tiene más de 10 tokens recientes")
        void detectsFarming() {
            String username = "farmer@test.com";
            List<RefreshToken> manyTokens = List.of(
                    token(username, "1.1.1.1", "UA"), token(username, "1.1.1.2", "UA"),
                    token(username, "1.1.1.3", "UA"), token(username, "1.1.1.4", "UA"),
                    token(username, "1.1.1.5", "UA"), token(username, "1.1.1.6", "UA"),
                    token(username, "1.1.1.7", "UA"), token(username, "1.1.1.8", "UA"),
                    token(username, "1.1.1.9", "UA"), token(username, "1.1.1.10", "UA"),
                    token(username, "1.1.1.11", "UA")
            );

            when(refreshTokenRepository.findAllTokensCreatedSince(any())).thenReturn(manyTokens);

            service.analyzeSecurityPatterns();

            verify(securityAuditService).logCriticalEvent(
                    eq(username), eq("TOKEN_FARMING"), anyString(), any());
        }

        @Test
        @DisplayName("no detecta farming con 10 tokens o menos")
        void doesNotDetectWithFewTokens() {
            String username = "normal@test.com";
            List<RefreshToken> fewTokens = List.of(
                    token(username, "1.1.1.1", "UA"),
                    token(username, "1.1.1.2", "UA")
            );

            when(refreshTokenRepository.findAllTokensCreatedSince(any())).thenReturn(fewTokens);

            service.analyzeSecurityPatterns();

            verify(securityAuditService, never()).logCriticalEvent(
                    eq(username), eq("TOKEN_FARMING"), anyString(), any());
        }
    }

    // ─── Session Hijacking ────────────────────────────────────────────────────

    @Nested
    @DisplayName("detectSessionHijackingAttempts")
    class SessionHijacking {

        @Test
        @DisplayName("detecta session hijacking cuando cambia user-agent e IP en menos de 5 min")
        void detectsHijacking() {
            RefreshToken current = token("victim@test.com", "5.5.5.5", "Chrome/100");
            current.setLastUsedAt(Instant.now());

            RefreshToken other = token("victim@test.com", "9.9.9.9", "Firefox/100");
            other.setLastUsedAt(Instant.now().minusSeconds(60));

            when(refreshTokenRepository.findActiveTokensCreatedSince(any(), any())).thenReturn(List.of(current));
            when(refreshTokenRepository.findActiveTokensByUsernameIn(eq(Set.of("victim@test.com")), any()))
                    .thenReturn(List.of(current, other));

            service.analyzeSecurityPatterns();

            verify(securityAuditService).logCriticalEvent(
                    eq("victim@test.com"), eq("SESSION_HIJACKING_SUSPECTED"), anyString(), any());
        }

        @Test
        @DisplayName("revoca las dos sesiones en conflicto si autoBlockEnabled=true")
        void revokesBothSessionsWhenAutoBlockEnabled() {
            ReflectionTestUtils.setField(service, "autoBlockEnabled", true);

            RefreshToken current = token("victim@test.com", "5.5.5.5", "Chrome/100");
            current.setLastUsedAt(Instant.now());

            RefreshToken other = token("victim@test.com", "9.9.9.9", "Firefox/100");
            other.setLastUsedAt(Instant.now().minusSeconds(60));

            when(refreshTokenRepository.findActiveTokensCreatedSince(any(), any())).thenReturn(List.of(current));
            when(refreshTokenRepository.findActiveTokensByUsernameIn(eq(Set.of("victim@test.com")), any()))
                    .thenReturn(List.of(current, other));
            when(refreshTokenRepository.findByJti(current.getJti())).thenReturn(Optional.of(current));
            when(refreshTokenRepository.findByJti(other.getJti())).thenReturn(Optional.of(other));

            service.analyzeSecurityPatterns();

            org.assertj.core.api.Assertions.assertThat(current.getRevoked()).isTrue();
            org.assertj.core.api.Assertions.assertThat(other.getRevoked()).isTrue();
            verify(refreshTokenRepository).save(current);
            verify(refreshTokenRepository).save(other);
        }
    }

    // ─── RefreshToken helpers ─────────────────────────────────────────────────

    @Nested
    @DisplayName("RefreshToken helper methods")
    class RefreshTokenHelpers {

        @Test
        @DisplayName("isActive() devuelve true cuando no está revocado y no ha expirado")
        void isActiveReturnsTrueForValidToken() {
            RefreshToken t = token("u", "1.1.1.1", "UA");
            org.assertj.core.api.Assertions.assertThat(t.isActive()).isTrue();
        }

        @Test
        @DisplayName("isActive() devuelve false cuando está revocado")
        void isActiveReturnsFalseWhenRevoked() {
            RefreshToken t = token("u", "1.1.1.1", "UA");
            t.revoke();
            org.assertj.core.api.Assertions.assertThat(t.isActive()).isFalse();
        }

        @Test
        @DisplayName("isActive() devuelve false cuando ha expirado")
        void isActiveReturnsFalseWhenExpired() {
            RefreshToken t = token("u", "1.1.1.1", "UA");
            t.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            org.assertj.core.api.Assertions.assertThat(t.isActive()).isFalse();
        }

        @Test
        @DisplayName("revoke() establece revoked=true")
        void revokeSetsFlag() {
            RefreshToken t = token("u", "1.1.1.1", "UA");
            t.revoke();
            org.assertj.core.api.Assertions.assertThat(t.getRevoked()).isTrue();
        }
    }
}
