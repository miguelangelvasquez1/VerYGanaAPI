package com.verygana2.models.plans;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

/**
 * Estado efectivo del anunciante en un momento dado.
 * NO es una entidad persistida; se calcula en tiempo real por EffectivePlanResolver.
 *
 * Este objeto responde a la pregunta central del sistema:
 * "¿Con qué capacidades opera este anunciante AHORA MISMO?"
 *
 * La lógica de resolución es:
 *
 *  1. Si budget == null || budget.remainingAmount == 0:
 *       → effectivePlan = BASIC
 *       → commissionActive = true (siempre aplica comisión en BASIC)
 *
 *  2. Si budget.remainingAmount > 0:
 *       → effectivePlan = STANDARD o PREMIUM (según investmentAmount)
 *       → commissionActive = investment.roiReached
 *         (comisión solo si ya recuperó 6x la inversión)
 *
 * Las features (canAdvertise, maxProducts, etc.) se resuelven siempre
 * desde el PlanFeature del effectivePlan.
 */
@Value
@Builder
public class EffectivePlanState {

    boolean hasActivePlan; // true si el anunciante tiene un plan activo (BASIC, STANDARD o PREMIUM)

    /** Plan que aplica en este momento para el anunciante. */
    Plan.PlanCode effectivePlan;

    /** true si se debe cobrar comisión sobre las próximas ventas. */
    boolean commissionActive;

    /** Porcentaje de comisión a aplicar (0 si commissionActive = false). */
    BigDecimal commissionRate;

    /** Saldo actual del presupuesto activo (0 si no hay presupuesto). */
    BigDecimal remainingBudget;

    /** true si el anunciante puede publicar anuncios ahora mismo. */
    boolean canAdvertise;

    /** true si el anunciante puede usar juegos branded ahora mismo. */
    boolean canUseGames;

    boolean canUseSurveys;

    /** Número máximo de productos que puede tener activos. */
    int maxProducts;

    /** Número máximo de anuncios activos permitidos. */
    int maxAds;

    /** Número máximo de juegos branded activos. */
    int maxBrandedGames;

    int maxSurveys;

    /** true si el ROI x6 ya fue alcanzado en la inversión activa. */
    boolean roiReached;

    // ── Factories de conveniencia ─────────────────────────────────────────────

    /**
     * Estado efectivo para un anunciante en modo BASIC (sin presupuesto).
     */
    public static EffectivePlanState basicMode(BigDecimal commissionRate, int maxProducts) {
        return EffectivePlanState.builder()
                .hasActivePlan(true)
                .effectivePlan(Plan.PlanCode.BASIC)
                .commissionActive(true)
                .commissionRate(commissionRate)
                .remainingBudget(BigDecimal.ZERO)
                .canAdvertise(false)
                .canUseGames(false)
                .maxProducts(maxProducts)
                .maxAds(0)
                .maxBrandedGames(0)
                .roiReached(false)
                .build();
    }

    /**
     * Estado efectivo para un anunciante sin plan.
     */
    public static EffectivePlanState noPlanMode() {
        return EffectivePlanState.builder()
                .hasActivePlan(false)
                .commissionActive(false)
                .commissionRate(BigDecimal.ZERO)
                .remainingBudget(BigDecimal.ZERO)
                .canAdvertise(false)
                .canUseGames(false)
                .maxProducts(0)
                .maxAds(0)
                .maxBrandedGames(0)
                .roiReached(false)
                .build();
    }
}