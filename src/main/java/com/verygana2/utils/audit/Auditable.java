package com.verygana2.utils.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para auditar operaciones de negocio.
 * Captura automáticamente: usuario, timestamp, IP, parámetros, resultado, excepciones
 * 
 * Ejemplo:
 * @Auditable(action = "WITHDRAW_FUNDS", level = AuditLevel.CRITICAL)
 * public void withdrawMoney(Long userId, BigDecimal amount) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME) // saber para que sirve cada clase de audit y poner anotaciones y niveles, logging adecuado.
public @interface Auditable {
    
    /**
     * Acción que se está auditando (ej: "CREATE_CAMPAIGN", "PLACE_BID")
     */
    String action();
    
    /**
     * Nivel de criticidad de la auditoría
     */
    AuditLevel level() default AuditLevel.INFO;
    
    /**
     * Descripción adicional de la operación
     */
    String description() default "";
    
    /**
     * Si se deben incluir los parámetros del método en el log
     */
    boolean includeParams() default true;
    
    /**
     * Si se debe incluir el resultado del método en el log
     */
    boolean includeResult() default false;
    
    /**
     * Si se debe capturar la excepción en caso de error
     */
    boolean captureException() default true;
    
    /**
     * Nombres de parámetros que deben ser enmascarados (ej: password, token)
     */
    String[] maskParams() default {};
    
    /**
     * Si la auditoría es obligatoria (no se puede desactivar en runtime)
     */
    boolean mandatory() default false;
    
    /**
     * Categoría de la auditoría (para filtrado y reportes)
     */
    String category() default "GENERAL";
}