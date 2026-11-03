package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.inforequests.AlertRuleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Regla de alerta versionada. Sus umbrales los fija compliance con fundamento: el código no
 * trae ninguno. Cambiar una regla es crear una versión nueva; la anterior se desactiva.
 */
@Entity
@Table(name = "compliance_alert_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_rule_code_version", columnNames = { "code", "version" }))
@Getter
@Setter
public class ComplianceAlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30, updatable = false)
    private AlertRuleType ruleType;

    @Column(nullable = false, length = 500, updatable = false)
    private String description;

    /** Fundamento del criterio (norma, política interna aprobada...). */
    @Column(name = "legal_basis", nullable = false, length = 500, updatable = false)
    private String legalBasis;

    /** Parámetros del evaluador en JSON (p. ej. ventana y porcentaje). */
    @Column(name = "params_json", nullable = false, length = 2000, updatable = false)
    private String paramsJson;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
