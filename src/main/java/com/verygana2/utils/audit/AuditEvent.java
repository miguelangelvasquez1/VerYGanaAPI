package com.verygana2.utils.audit;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Evento de auditoría que se publica de forma asíncrona
 */
@Data
@Builder
public class AuditEvent {
    
    // Usuario
    private Long userId;
    private String username;
    private String userEmail;
    
    // Acción
    private String action;
    private AuditLevel level;
    private String category;
    private String description;
    
    // Método
    private String methodName;
    private String className;
    
    // Contexto
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    
    // Timing
    private LocalDateTime timestamp;
    private Long executionTimeMs;
    
    // Entidad
    private String entityType;
    private Long entityId;
    
    // Datos
    private Map<String, Object> params;
    private Object result;
    private Throwable exception;
    
    // Estado
    private Boolean success;
    private Map<String, Object> additionalData;
}