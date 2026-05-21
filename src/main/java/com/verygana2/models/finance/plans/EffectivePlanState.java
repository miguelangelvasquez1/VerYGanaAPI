package com.verygana2.models.finance.plans;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

/**
 * Estado efectivo del anunciante en un momento dado.
 * NO es una entidad persistida; se calcula en tiempo real por EffectivePlanResolver.
 *
 * La fuente de verdad es CommercialDetails.currentPlan:
 *  - null           → noPlanMode(): sin capacidades ni comisión
 *  - BASIC          → plan con suscripción mensual, sin presupuesto publicitario
 *  - STANDARD/PREMIUM → plan activo con wallet, canAdvertise según features
 *
 * La comisión siempre aplica cuando hay plan (commissionActive = commissionPerSale > 0).
 * No existe la regla de ROI x6.
 */
@Value
@Builder
public class EffectivePlanState {   

    boolean hasActivePlan;

    Plan.PlanCode effectivePlan;

    boolean commissionActive;

    BigDecimal commissionRate;

    /** Saldo actual del wallet en COP (0 si es BASIC o sin wallet). */
    BigDecimal remainingBudget;

    boolean canAdvertise;

    boolean canUseGames;

    boolean canUseSurveys;

    int maxProducts;

    int maxAds;

    int maxBrandedGames;

    int maxSurveys;

    public static EffectivePlanState noPlanMode() {
        return EffectivePlanState.builder()
                .hasActivePlan(false)
                .commissionActive(false)
                .commissionRate(BigDecimal.ZERO)
                .remainingBudget(BigDecimal.ZERO)
                .canAdvertise(false)
                .canUseGames(false)
                .canUseSurveys(false)
                .maxProducts(0)
                .maxAds(0)
                .maxBrandedGames(0)
                .maxSurveys(0)
                .build();
    }
}
