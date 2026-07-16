package com.verygana2.dtos.audit;

import java.util.Map;

import com.verygana2.dtos.PagedResponse;

import lombok.Builder;
import lombok.Data;

/**
 * Respuesta combinada de /admin/audit-logs: la página de eventos y el
 * resumen (conteo por action) calculado con los MISMOS filtros de la
 * búsqueda — mismo patrón que SecurityEventSearchResponseDTO.
 */
@Data
@Builder
public class AuditLogSearchResponseDTO {
    private PagedResponse<AuditLogDTO> events;
    private Map<String, Long> summary;
}
