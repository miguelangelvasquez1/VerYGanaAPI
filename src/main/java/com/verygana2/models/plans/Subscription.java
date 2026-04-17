package com.verygana2.models.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Suscripción mensual al plan BASIC.
 *
 * IMPORTANTE: Este modelo SOLO aplica para el plan BASIC (suscripción recurrente).
 * Los planes STANDARD y PREMIUM NO son suscripciones; se activan mediante
 * AdvertiserInvestment + Budget. Ver EffectivePlanResolver para la lógica
 * de qué plan está activo en cada momento.
 *
 * Cuando un anunciante tiene un Budget activo (STANDARD/PREMIUM), su Subscription
 * BASIC permanece registrada pero el sistema prioriza el plan por presupuesto.
 * Al agotarse el presupuesto, el comportamiento de BASIC se reactiva
 * automáticamente (incluida la comisión por ventas).
 */
@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private CommercialDetails commercialDetails;

    /** Siempre debe apuntar al plan BASIC. */
    @ManyToOne(optional = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** Monto mensual cobrado. */
    @Column(precision = 20, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(nullable = false)
    private ZonedDateTime startDate;

    /** Fecha de renovación o vencimiento del ciclo mensual actual. */
    private ZonedDateTime nextBillingDate;

    private ZonedDateTime cancelledAt;

    public enum SubscriptionStatus {
        ACTIVE,
        CANCELLED,
        PAST_DUE
    }

    public boolean isActive() {
        return SubscriptionStatus.ACTIVE.equals(status);
    }
}