package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paso 6-7: resumen del plan más adecuado según la ruta asignada, con todos
 * los valores económicos (tarifa, comisión, impuestos, condiciones de
 * liquidación) visibles ANTES de aceptar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanSummaryResponseDTO {
    private PlanCode planCode;
    private String planName;
    private String description;

    /** Solo aplica a planes de suscripción mensual (BASIC). */
    private Long monthlyFeeCents;

    /** Solo aplica a planes de inversión (STANDARD/PREMIUM). */
    private Long minInvestmentCents;
    private Long maxInvestmentCents;

    /** Monto elegido por el empresario dentro del rango. Null hasta que se acepte el plan (o si es BASIC). */
    private Long investmentAmountCents;

    /** Solo aplica a BASIC. Null para STANDARD/PREMIUM o hasta que se acepte el plan. */
    private Integer contractDurationMonths;

    private int saleCommissionPct;
    private int maxKeysPct;

    private String taxNote;
    private String liquidationConditions;

    /**
     * true mientras la negociación esté PENDIENTE (rutas D/E, sin resolver todavía por
     * compliance) — bloquea la generación del contrato. Una vez resuelta (ver
     * specialNegotiationResolvedAt) pasa a false, aunque la cuenta haya requerido
     * negociación especial: en ese punto ya no bloquea nada, solo queda prohibido
     * volver a cambiar de plan (ver acceptPlan()).
     */
    private boolean requiresSpecialNegotiation;

    /**
     * Cuándo compliance resolvió la negociación. null = nunca tuvo negociación especial
     * O sigue pendiente (distinguir con requiresSpecialNegotiation). No-null = ya se
     * resolvió: solo queda generar el contrato, no se puede volver a elegir plan.
     */
    private ZonedDateTime specialNegotiationResolvedAt;

    /** Qué necesita, cuando requiresSpecialNegotiation es true (solo aplica a Ruta E). */
    private String specialNegotiationDetails;

    private boolean accepted;
    private ZonedDateTime acceptedAt;
}
