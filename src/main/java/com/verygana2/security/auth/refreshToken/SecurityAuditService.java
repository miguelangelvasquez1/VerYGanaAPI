package com.verygana2.security.auth.refreshToken;

import java.time.ZonedDateTime;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityAuditService {

    private final ApplicationEventPublisher eventPublisher;

    public void logSuspiciousActivity(String username, String action, String description) {
        log.warn("SECURITY WARNING - Action: {}, User: {}, Detail: {}", action, username, description);
        publish(username, action, description, AuditLevel.WARNING, null, "SECURITY", null, null);
    }

    public void logCriticalEvent(String username, String action, String description,
                                  Map<String, Object> additionalData) {
        log.error("SECURITY CRITICAL - Action: {}, User: {}, Detail: {}", action, username, description);
        publish(username, action, description, AuditLevel.CRITICAL, additionalData, "SECURITY", null, null);
    }

    public void logSystemError(String action, String message) {
        log.error("SECURITY SYSTEM ERROR - Action: {}, Message: {}", action, message);
        publish("SYSTEM", action, message, AuditLevel.CRITICAL, null, "SECURITY", null, null);
    }

    /**
     * Registra un intento de login fallido. Va bajo category="AUTH" (no "SECURITY")
     * porque un fallo individual es normal (contraseña tipeada mal) — es
     * SecurityMonitoringService quien decide, en agregado por IP, si un cluster de
     * fallos constituye un evento de seguridad real (credential stuffing).
     */
    public void logAuthFailure(String username, String ipAddress, String userAgent, String reason) {
        log.warn("LOGIN FAILED - User: {}, IP: {}, Reason: {}", username, ipAddress, reason);
        publish(username, "LOGIN_FAILED", reason, AuditLevel.WARNING, null, "AUTH", ipAddress, userAgent);
    }

    private void publish(String username, String action, String description, AuditLevel level,
                          Map<String, Object> additionalData, String category, String ipAddress, String userAgent) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .username(username)
                    .action(action)
                    .level(level)
                    .category(category)
                    .description(description)
                    .className(SecurityAuditService.class.getName())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .timestamp(ZonedDateTime.now())
                    .success(false)
                    .additionalData(additionalData)
                    .build();

            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish security audit event for action: {}", action, e);
        }
    }
}
