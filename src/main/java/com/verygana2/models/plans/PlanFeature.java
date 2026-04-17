package com.verygana2.models.plans;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Valor específico de una Feature para un Plan dado.
 * Permite configurar dinámicamente los límites y permisos por plan.
 *
 * Reglas de uso de valor:
 *  - Si feature.type == BOOLEAN   → usar boolValue
 *  - Si feature.type == LIMIT     → usar intValue
 *  - Si feature.type == PERCENTAGE → usar decimalValue (0.00 – 100.00)
 */
@Entity
@Table(
    name = "plan_features",
    uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "feature_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Plan plan;

    @ManyToOne(optional = false)
    private Feature feature;

    private Integer intValue;
    private Boolean boolValue;

    @Column(precision = 10, scale = 4)
    private BigDecimal decimalValue;

    // ── Helpers de lectura ────────────────────────────────────────────────────

    public boolean getBoolOrDefault(boolean defaultValue) {
        return boolValue != null ? boolValue : defaultValue;
    }

    public int getIntOrDefault(int defaultValue) {
        return intValue != null ? intValue : defaultValue;
    }

    public BigDecimal getDecimalOrDefault(BigDecimal defaultValue) {
        return decimalValue != null ? decimalValue : defaultValue;
    }
}