package com.verygana2.utils.audit;

/**
 * Niveles de criticidad para auditoría
 */
public enum AuditLevel {
    /**
     * Operaciones normales, no críticas
     * Ejemplo: Consultar lista de productos
     */
    INFO,
    
    /**
     * Operaciones que requieren atención
     * Ejemplo: Múltiples intentos fallidos de login
     */
    WARNING,
    
    /**
     * Operaciones críticas que deben ser monitoreadas
     * Ejemplo: Transferencias de dinero, cambios de permisos
     */
    CRITICAL,
    
    /**
     * Solo para debugging, no persiste en producción
     */
    DEBUG;
    
    /**
     * Días de retención por nivel
     */
    public int getRetentionDays() {
        return switch (this) {
            case DEBUG -> 7;
            case INFO -> 90;
            case WARNING -> 365;
            case CRITICAL -> 2555; // 7 años para compliance
        };
    }
}