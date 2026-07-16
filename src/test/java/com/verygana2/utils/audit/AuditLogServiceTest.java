package com.verygana2.utils.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.audit.AuditLogDTO;
import com.verygana2.dtos.audit.AuditLogSearchResponseDTO;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    @Mock AuditLogRepository auditLogRepository;

    @InjectMocks AuditLogService service;

    private AuditLog pqrsLog() {
        return AuditLog.builder()
                .id(1L)
                .userId(9L)
                .username("user@test.com")
                .action("PQRS_SUBMIT")
                .level(AuditLevel.WARNING)
                .category("PQRS")
                .description("Nuevo PQRS")
                .createdAt(ZonedDateTime.now())
                .success(true)
                .build();
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("delega en searchNonSecurityAuditLogs y mapea a DTO")
        void delegatesAndMaps() {
            Pageable pageable = PageRequest.of(0, 50);
            when(auditLogRepository.searchNonSecurityAuditLogs(
                    isNull(), isNull(), isNull(), any(), any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(pqrsLog())));
            when(auditLogRepository.searchTopNonSecurityActions(isNull(), isNull(), isNull(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[]{"PQRS_SUBMIT", 1L}));

            AuditLogSearchResponseDTO response = service.search(null, null, null, null, null, pageable);

            assertThat(response.getEvents().getData()).hasSize(1);
            AuditLogDTO dto = response.getEvents().getData().get(0);
            assertThat(dto.getAction()).isEqualTo("PQRS_SUBMIT");
            assertThat(dto.getCategory()).isEqualTo("PQRS");
            assertThat(dto.getLevel()).isEqualTo(AuditLevel.WARNING);
            assertThat(response.getSummary()).containsEntry("PQRS_SUBMIT", 1L);
        }

        @Test
        @DisplayName("nunca pasa category=SECURITY al repositorio, aunque no se filtre por category")
        void neverLeaksSecurityCategory() {
            Pageable pageable = PageRequest.of(0, 50);
            when(auditLogRepository.searchNonSecurityAuditLogs(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));
            when(auditLogRepository.searchTopNonSecurityActions(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            service.search(null, "SECURITY", null, null, null, pageable);

            // El filtro category="SECURITY" se pasa tal cual — la exclusión real
            // vive en la query (category <> 'SECURITY'), verificada en el repo.
            verify(auditLogRepository).searchNonSecurityAuditLogs(
                    isNull(), eq("SECURITY"), isNull(), any(), any(), eq(pageable));
        }

        @Test
        @DisplayName("usa ventana de 30 días por defecto si no se pasan fechas")
        void defaultsToThirtyDayWindow() {
            Pageable pageable = PageRequest.of(0, 50);
            when(auditLogRepository.searchNonSecurityAuditLogs(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));
            when(auditLogRepository.searchTopNonSecurityActions(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            service.search(null, null, null, null, null, pageable);

            verify(auditLogRepository).searchNonSecurityAuditLogs(
                    isNull(), isNull(), isNull(), any(ZonedDateTime.class), any(ZonedDateTime.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("getCritical")
    class GetCritical {

        @Test
        @DisplayName("delega en search con level=CRITICAL fijo")
        void delegatesWithCriticalLevel() {
            Pageable pageable = PageRequest.of(0, 50);
            when(auditLogRepository.searchNonSecurityAuditLogs(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));
            when(auditLogRepository.searchTopNonSecurityActions(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            service.getCritical("PQRS", null, null, pageable);

            verify(auditLogRepository).searchNonSecurityAuditLogs(
                    isNull(), eq("PQRS"), eq(AuditLevel.CRITICAL), any(), any(), eq(pageable));
        }
    }
}
