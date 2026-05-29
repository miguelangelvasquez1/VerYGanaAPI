package com.verygana2.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.security.auth.refreshToken.SecurityAuditService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAuditService")
class SecurityAuditServiceTest {

    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks SecurityAuditService service;

    // ─── logSuspiciousActivity ────────────────────────────────────────────────

    @Nested
    @DisplayName("logSuspiciousActivity")
    class LogSuspiciousActivity {

        @Test
        @DisplayName("publica un AuditEvent en nivel WARNING")
        void publishesWarningEvent() {
            service.logSuspiciousActivity("user@test.com", "BRUTE_FORCE", "10 intentos en 1 hora");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getLevel()).isEqualTo(AuditLevel.WARNING);
            assertThat(event.getUsername()).isEqualTo("user@test.com");
            assertThat(event.getAction()).isEqualTo("BRUTE_FORCE");
            assertThat(event.getCategory()).isEqualTo("SECURITY");
            assertThat(event.getSuccess()).isFalse();
        }

        @Test
        @DisplayName("no lanza excepción si el publisher falla")
        void doesNotThrowWhenPublisherFails() {
            org.mockito.Mockito.doThrow(RuntimeException.class).when(eventPublisher).publishEvent(any());

            assertThatCode(() -> service.logSuspiciousActivity("u", "A", "D"))
                    .doesNotThrowAnyException();
        }
    }

    // ─── logCriticalEvent ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("logCriticalEvent")
    class LogCriticalEvent {

        @Test
        @DisplayName("publica un AuditEvent en nivel CRITICAL con additionalData")
        void publishesCriticalEventWithData() {
            Map<String, Object> extra = Map.of("ip", "1.2.3.4", "riskScore", 9);

            service.logCriticalEvent("sistema", "IP_BLOCKED", "IP bloqueada por ataque", extra);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getLevel()).isEqualTo(AuditLevel.CRITICAL);
            assertThat(event.getAdditionalData()).containsEntry("ip", "1.2.3.4");
        }
    }

    // ─── logSystemError ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("logSystemError")
    class LogSystemError {

        @Test
        @DisplayName("publica un AuditEvent con username SYSTEM y nivel CRITICAL")
        void publishesSystemCriticalEvent() {
            service.logSystemError("TOKEN_CLEANUP_FAILED", "Connection refused");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getUsername()).isEqualTo("SYSTEM");
            assertThat(event.getLevel()).isEqualTo(AuditLevel.CRITICAL);
            assertThat(event.getAction()).isEqualTo("TOKEN_CLEANUP_FAILED");
        }

        @Test
        @DisplayName("publica exactamente un evento por llamada")
        void publishesExactlyOneEvent() {
            service.logSystemError("ERROR", "msg");
            verify(eventPublisher, times(1)).publishEvent(any(AuditEvent.class));
        }
    }
}
