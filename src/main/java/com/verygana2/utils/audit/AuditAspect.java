package com.verygana2.utils.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Aspecto que intercepta métodos anotados con @Auditable
 * y publica eventos de auditoría de forma asíncrona
 */
@Aspect
@Component
@Order(1) // Ejecutar antes que transacciones
@Slf4j
public class AuditAspect {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AuditContextService contextService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Intercepta todos los métodos anotados con @Auditable
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;
        boolean success = true;

        try {
            // Ejecutar el método original
            result = joinPoint.proceed();
            return result;

        } catch (Throwable e) {
            success = false;
            exception = e;
            throw e;

        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            try {
                // Construir y publicar evento de auditoría
                AuditEvent event = buildAuditEvent(
                    joinPoint,
                    auditable,
                    result,
                    exception,
                    executionTime,
                    success
                );

                // Publicar evento de forma asíncrona
                eventPublisher.publishEvent(Objects.requireNonNull(event));

            } catch (Exception e) {
                // No fallar la operación principal si falla la auditoría
                log.error("Error publicando evento de auditoría para acción: {}", 
                    auditable.action(), e);
            }
        }
    }

    /**
     * Construye el evento de auditoría con toda la información
     */
    private AuditEvent buildAuditEvent(
            ProceedingJoinPoint joinPoint,
            Auditable auditable,
            Object result,
            Throwable exception,
            long executionTime,
            boolean success) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
            // Usuario actual
            .userId(contextService.getCurrentUserId())
            .username(contextService.getCurrentUsername())
            .userEmail(contextService.getCurrentUserEmail())
            
            // Acción
            .action(auditable.action())
            .level(auditable.level())
            .category(auditable.category())
            .description(auditable.description())
            
            // Método
            .methodName(method.getName())
            .className(joinPoint.getTarget().getClass().getName())
            
            // Contexto HTTP
            .ipAddress(contextService.getClientIpAddress())
            .userAgent(contextService.getUserAgent())
            .sessionId(contextService.getSessionId())
            
            // Timing
            .timestamp(LocalDateTime.now())
            .executionTimeMs(executionTime)
            
            // Estado
            .success(success);

        // Incluir parámetros si está habilitado
        if (auditable.includeParams()) {
            Map<String, Object> params = extractParameters(
                joinPoint, 
                signature, 
                auditable.maskParams()
            );
            eventBuilder.params(params);
            
            // Intentar extraer entidad relacionada de los parámetros
            extractEntityInfo(params, eventBuilder);
        }

        // Incluir resultado si está habilitado
        if (auditable.includeResult() && result != null) {
            eventBuilder.result(result);
        }

        // Incluir excepción si está habilitado
        if (auditable.captureException() && exception != null) {
            eventBuilder.exception(exception);
        }

        return eventBuilder.build();
    }

    /**
     * Extrae parámetros del método con sus nombres
     */
    private Map<String, Object> extractParameters(
            ProceedingJoinPoint joinPoint,
            MethodSignature signature,
            String[] maskParams) {

        Map<String, Object> params = new LinkedHashMap<>();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = signature.getMethod().getParameters();
        Set<String> maskSet = new HashSet<>(Arrays.asList(maskParams));

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];

            // Enmascarar parámetros sensibles
            if (maskSet.contains(paramName)) {
                params.put(paramName, "***MASKED***");
            } else if (paramValue != null) {
                // Convertir a string seguro (evitar lazy loading, etc)
                params.put(paramName, sanitizeValue(paramValue));
            } else {
                params.put(paramName, null);
            }
        }

        return params;
    }

    /**
     * Sanitiza valores para evitar problemas de serialización
     */
    private Object sanitizeValue(Object value) {
        try {
            // Si es primitivo o String, devolver tal cual
            if (value instanceof String || 
                value instanceof Number || 
                value instanceof Boolean ||
                value instanceof Enum) {
                return value;
            }

            // Si es colección, limitar tamaño
            if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                if (collection.size() > 10) {
                    return String.format("Collection[size=%d]", collection.size());
                }
            }

            // Para objetos complejos, usar toString o serializar
            if (value.getClass().getPackage() != null && 
                value.getClass().getPackage().getName().startsWith("com.verygana2")) {
                // Es una entidad propia, intentar serializar
                try {
                    return objectMapper.writeValueAsString(value);
                } catch (Exception e) {
                    return value.toString();
                }
            }

            return value.toString();

        } catch (Exception e) {
            return "Error sanitizing: " + e.getMessage();
        }
    }

    /**
     * Intenta extraer información de entidad relacionada
     */
    private void extractEntityInfo(
            Map<String, Object> params, 
            AuditEvent.AuditEventBuilder eventBuilder) {

        // Buscar parámetros comunes que indiquen entidad
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey().toLowerCase();
            
            if (key.endsWith("id") && entry.getValue() instanceof Number) {
                String entityType = key.substring(0, key.length() - 2).toUpperCase();
                eventBuilder
                    .entityType(entityType)
                    .entityId(((Number) entry.getValue()).longValue());
                break;
            }
        }
    }
}