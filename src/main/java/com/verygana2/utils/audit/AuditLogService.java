package com.verygana2.utils.audit;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.audit.AuditLogDTO;
import com.verygana2.dtos.audit.AuditLogSearchResponseDTO;
import com.verygana2.services.UserIdResolver;

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

    private static final Pattern USER_ENTITY_TYPE = Pattern.compile(
            "USER|CONSUMER|COMMERCIAL|ADMIN|DESIGNER|OFFICER|SELLER|BUYER|WINNER|CREATOR|OWNER|REQUESTER|ACTOR");

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserIdResolver userIdResolver;

    public AuditLogSearchResponseDTO search(String action, String category, AuditLevel level,
                                             ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        ZonedDateTime start = from != null ? from : ZonedDateTime.now().minusDays(30);
        ZonedDateTime end = to != null ? to : ZonedDateTime.now();

        Page<AuditLog> logs = auditLogRepository.searchNonSecurityAuditLogs(
                action, category, level, start, end, pageable);

        return AuditLogSearchResponseDTO.builder()
                .events(PagedResponse.from(toDTOs(logs)))
                .summary(buildSummary(action, category, level, start, end))
                .build();
    }

    /**
     * Convierte una página de logs a DTO traduciendo los ids internos de usuario
     * (userId y entityId cuando la entidad es un usuario) a publicId en una sola query.
     */
    public Page<AuditLogDTO> toDTOs(Page<AuditLog> logs) {
        List<Long> userIds = logs.getContent().stream()
                .flatMap(l -> Stream.of(l.getUserId(), isUserEntity(l.getEntityType()) ? l.getEntityId() : null))
                .toList();
        Map<Long, UUID> publicIds = userIdResolver.toPublicIds(userIds);
        return logs.map(l -> toDTO(l, publicIds));
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

    private AuditLogDTO toDTO(AuditLog log, Map<Long, UUID> publicIds) {
        boolean userEntity = isUserEntity(log.getEntityType());
        return AuditLogDTO.builder()
                .id(log.getId())
                .userPublicId(log.getUserId() != null ? publicIds.get(log.getUserId()) : null)
                .entityType(log.getEntityType())
                .entityId(userEntity ? null : log.getEntityId())
                .entityPublicId(userEntity && log.getEntityId() != null ? publicIds.get(log.getEntityId()) : null)
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

    /**
     * AuditAspect deriva entityType del nombre del parámetro ("commercialId" -> "COMMERCIAL"),
     * así que un entityId puede ser el id interno de un usuario. Esos se exponen como publicId.
     */
    private static boolean isUserEntity(String entityType) {
        return entityType != null && USER_ENTITY_TYPE.matcher(entityType).find();
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
