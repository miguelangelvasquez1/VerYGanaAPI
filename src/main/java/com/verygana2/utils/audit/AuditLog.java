package com.verygana2.utils.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad para almacenar logs de auditoría
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_user_action", columnList = "user_id, action, created_at"),
        @Index(name = "idx_audit_level_date", columnList = "level, created_at"),
        @Index(name = "idx_audit_category", columnList = "category, created_at"),
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== Quién ====================
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "username", length = 100)
    private String username;
    
    @Column(name = "user_email", length = 255)
    private String userEmail;

    // ==================== Qué ====================
    
    @Column(name = "action", nullable = false, length = 100)
    private String action;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private AuditLevel level;
    
    @Column(name = "category", length = 50)
    private String category;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "method_name", length = 255)
    private String methodName;
    
    @Column(name = "class_name", length = 255)
    private String className;

    // ==================== Dónde ====================
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "session_id", length = 255)
    private String sessionId;

    // ==================== Cuándo ====================
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    // ==================== Entidad Relacionada ====================
    
    @Column(name = "entity_type", length = 100)
    private String entityType;
    
    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "old_values", columnDefinition = "json") // Opcional
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "json") // Opcional
    private String newValues;

    // ==================== Datos ====================
    
    @Column(name = "params", columnDefinition = "TEXT") // Quitar?
    private String params; // JSON
    
    @Column(name = "result", columnDefinition = "TEXT") // Quitar?
    private String result; // JSON
    
    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;
    
    @Column(name = "stack_trace", columnDefinition = "TEXT") //Quitar
    private String stackTrace;

    // ==================== Metadata ====================
    
    @Column(name = "success", nullable = false)
    private Boolean success;
    
    @Column(name = "additional_data", columnDefinition = "TEXT") //Quitar
    private String additionalData; // JSON para datos extra

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}