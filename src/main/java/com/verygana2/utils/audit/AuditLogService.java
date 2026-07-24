package com.verygana2.utils.audit;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.audit.AuditLogDTO;
import com.verygana2.dtos.audit.AuditLogSearchResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Eventos WARNING/CRITICAL de cualquier categoría EXCEPTO SECURITY, para
 * /admin/audit-logs. Los eventos de categoría SECURITY (fuerza bruta, token
 * farming, session hijacking, IP auto-bloqueada) ya se manejan en el front
 * vía /admin/security-events (SecurityEventService) — este servicio cubre
 * el resto (ej. PQRS, y lo que se agregue a futuro).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogSearchResponseDTO search(String action, String category, AuditLevel level,
                                             ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        ZonedDateTime start = from != null ? from : ZonedDateTime.now().minusDays(30);
        ZonedDateTime end = to != null ? to : ZonedDateTime.now();

        Page<AuditLog> logs = auditLogRepository.searchNonSecurityAuditLogs(
                action, category, level, start, end, pageable);

        return AuditLogSearchResponseDTO.builder()
                .events(PagedResponse.from(logs).map(this::toDTO))
                .summary(buildSummary(action, category, level, start, end))
                .build();
    }

    public AuditLogSearchResponseDTO getCritical(String category, ZonedDateTime from, ZonedDateTime to,
                                                  Pageable pageable) {
        return search(null, category, AuditLevel.CRITICAL, from, to, pageable);
    }

    /** Mismos filtros que la búsqueda (action/category/level/rango), para que el resumen coincida con lo listado. */
    private Map<String, Long> buildSummary(String action, String category, AuditLevel level,
                                            ZonedDateTime start, ZonedDateTime end) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.searchTopNonSecurityActions(action, category, level, start, end)) {
            summary.put((String) row[0], (Long) row[1]);
        }
        return summary;
    }

    private AuditLogDTO toDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .userEmail(log.getUserEmail())
                .action(log.getAction())
                .level(log.getLevel())
                .category(log.getCategory())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .success(log.getSuccess())
                .additionalData(parseAdditionalData(log.getAdditionalData()))
                .build();
    }

    private Map<String, Object> parseAdditionalData(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Error deserializing additionalData: {}", e.getMessage());
            return Map.of("raw", json);
        }
    }
}
