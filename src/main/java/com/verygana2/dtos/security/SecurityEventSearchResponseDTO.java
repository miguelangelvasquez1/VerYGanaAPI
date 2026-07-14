package com.verygana2.dtos.security;

import java.util.Map;

import com.verygana2.dtos.PagedResponse;

import lombok.Builder;
import lombok.Data;

/**
 * Respuesta combinada de /admin/security-events: la página de eventos y el
 * resumen (conteo por action) calculado con los MISMOS filtros de la búsqueda
 * — evita que el front tenga que pegarle a /summary por separado.
 */
@Data
@Builder
public class SecurityEventSearchResponseDTO {
    private PagedResponse<SecurityEventDTO> events;
    private Map<String, Long> summary;
}
