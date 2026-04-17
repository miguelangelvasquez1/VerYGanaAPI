package com.verygana2.models.plans;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Define la estructura y capacidades de cada nivel de plan.
 * No tiene estado del anunciante (eso vive en AdvertiserInvestment y Budget).
 *
 * BASIC   → suscripción mensual, sin presupuesto activo
 * STANDARD → presupuesto entre 1.000.000 y 9.999.999
 * PREMIUM  → presupuesto >= 10.000.000
 */
@Entity
@Table(
    name = "plans",
    uniqueConstraints = @UniqueConstraint(columnNames = {"version", "code"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true; // si esta versión sigue vigente

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PlanCode code;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * Solo aplica para BASIC
     */
    private boolean monthlySubscription;

    /**
     * Precio mensual. Solo relevante cuando monthlySubscription = true (BASIC).
     */
    private BigDecimal monthlyPrice;

    /**
     * Rango de inversión que activa este plan.
     * BASIC: null / null (no requiere inversión mínima)
     * STANDARD: 1_000_000 – 9_999_999
     * PREMIUM: 10_000_000 – null (sin máximo)
     */
    private BigDecimal minInvestment;
    private BigDecimal maxInvestment;

    public enum PlanCode {
        BASIC,
        STANDARD,
        PREMIUM
    }
}