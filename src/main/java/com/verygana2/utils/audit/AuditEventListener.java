package com.verygana2.utils.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Listener asíncrono que procesa eventos de auditoría
 * y los persiste en la base de datos
 */
@Component
@Slf4j
public class AuditEventListener {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Procesa el evento de auditoría de forma asíncrona
     * Usa nueva transacción para no afectar la transacción principal
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuditEvent(AuditEvent event) {
        try {
            AuditLog auditLog = mapEventToLog(event);
            auditLogRepository.save(Objects.requireNonNull(auditLog));
            
            // Log adicional para eventos críticos
            if (event.getLevel().name().equals("CRITICAL")) {
                log.warn("AUDIT CRITICAL: Action={}, User={}, Success={}", 
                    event.getAction(), 
                    event.getUsername(), 
                    event.getSuccess());
            }

        } catch (Exception e) {
            // No fallar silenciosamente, loggear el error
            log.error("Error persistiendo audit log para acción: {} - Error: {}", 
                event.getAction(), e.getMessage(), e);
            
            // Opcionalmente, publicar a sistema de alertas
            // alertService.sendAlert("Audit log failed", event, e);
        }
    }

    /**
     * Mapea el evento a la entidad de persistencia
     */
    private AuditLog mapEventToLog(AuditEvent event) {
        AuditLog.AuditLogBuilder logBuilder = AuditLog.builder()
            // Usuario
            .userId(event.getUserId())
            .username(event.getUsername())
            .userEmail(event.getUserEmail())
            
            // Acción
            .action(event.getAction())
            .level(event.getLevel())
            .category(event.getCategory())
            .description(event.getDescription())
            
            // Método
            .methodName(event.getMethodName())
            .className(event.getClassName())
            
            // Contexto
            .ipAddress(event.getIpAddress())
            .userAgent(truncate(event.getUserAgent(), 500))
            .sessionId(event.getSessionId())
            
            // Timing
            .createdAt(event.getTimestamp())
            .executionTimeMs(event.getExecutionTimeMs())
            
            // Entidad
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            
            // Estado
            .success(event.getSuccess());

        // Serializar parámetros a JSON
        if (event.getParams() != null && !event.getParams().isEmpty()) {
            try {
                logBuilder.params(objectMapper.writeValueAsString(event.getParams()));
            } catch (JsonProcessingException e) {
                log.warn("Error serializando params: {}", e.getMessage());
                logBuilder.params(event.getParams().toString());
            }
        }

        // Serializar resultado a JSON
        if (event.getResult() != null) {
            try {
                String resultJson = objectMapper.writeValueAsString(event.getResult());
                logBuilder.result(truncate(resultJson, 5000)); // Limitar tamaño
            } catch (JsonProcessingException e) {
                log.warn("Error serializando result: {}", e.getMessage());
                logBuilder.result(truncate(event.getResult().toString(), 5000));
            }
        }

        // Serializar excepción
        if (event.getException() != null) {
            Throwable ex = event.getException();
            logBuilder.exceptionMessage(truncate(ex.getMessage(), 1000));
            logBuilder.stackTrace(truncate(getStackTrace(ex), 5000));
        }

        // Serializar datos adicionales
        if (event.getAdditionalData() != null && !event.getAdditionalData().isEmpty()) {
            try {
                logBuilder.additionalData(
                    objectMapper.writeValueAsString(event.getAdditionalData())
                );
            } catch (JsonProcessingException e) {
                log.warn("Error serializando additionalData: {}", e.getMessage());
            }
        }

        return logBuilder.build();
    }

    /**
     * Convierte el stack trace de una excepción a String
     */
    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Trunca un string a un tamaño máximo
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}