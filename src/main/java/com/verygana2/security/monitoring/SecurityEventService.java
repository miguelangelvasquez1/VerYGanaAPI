package com.verygana2.security.monitoring;

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
import com.verygana2.dtos.security.SecurityEventDTO;
import com.verygana2.dtos.security.SecurityEventSearchResponseDTO;
import com.verygana2.utils.audit.AuditLevel;
import com.verygana2.utils.audit.AuditLog;
import com.verygana2.utils.audit.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityEventService {

    private static final String CATEGORY = "SECURITY";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public SecurityEventSearchResponseDTO search(String action, AuditLevel level, ZonedDateTime from,
                                                   ZonedDateTime to, Pageable pageable) {
        ZonedDateTime start = from != null ? from : ZonedDateTime.now().minusDays(30);
        ZonedDateTime end = to != null ? to : ZonedDateTime.now();

        Page<AuditLog> logs = auditLogRepository.searchAuditLogs(
                null, action, level, CATEGORY, null, start, end, pageable);

        return SecurityEventSearchResponseDTO.builder()
                .events(PagedResponse.from(logs).map(this::toDTO))
                .summary(buildSummary(action, level, start, end))
                .build();
    }

    public SecurityEventSearchResponseDTO getCritical(ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        return search(null, AuditLevel.CRITICAL, from, to, pageable);
    }

    /** Mismos filtros que la búsqueda (action/level/rango), para que el resumen coincida con lo listado. */
    private Map<String, Long> buildSummary(String action, AuditLevel level, ZonedDateTime start, ZonedDateTime end) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.searchTopActionsByCategory(action, level, CATEGORY, start, end)) {
            summary.put((String) row[0], (Long) row[1]);
        }
        return summary;
    }

    private SecurityEventDTO toDTO(AuditLog log) {
        return SecurityEventDTO.builder()
                .id(log.getId())
                .username(log.getUsername())
                .action(log.getAction())
                .level(log.getLevel())
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
