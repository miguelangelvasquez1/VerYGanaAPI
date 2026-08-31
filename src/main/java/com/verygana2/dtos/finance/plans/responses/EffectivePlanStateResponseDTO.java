package com.verygana2.dtos.finance.plans.responses;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EffectivePlanStateResponseDTO {
        /**
     * Código del plan activo. Null si no tiene plan.
     * "BASIC", "STANDARD" o "PREMIUM"
     */
    private String effectivePlan;
 
    /**
     * true si el comercial tiene un plan contratado y vigente.
     * NO implica que tenga saldo publicitario — un STANDARD/PREMIUM con la
     * billetera en EXHAUSTED sigue teniendo {@code hasActivePlan = true}.
     * Para saber si debe bloquearse la creación de activos nuevos usar
     * {@link #budgetSuspended} / {@link #walletStatus}.
     */
    private boolean hasActivePlan;

    /**
     * true cuando el plan no es BASIC y el saldo de la billetera es 0.
     * El frontend lo usa para bloquear SOLO la creación de activos nuevos
     * (anuncios, encuestas, juegos branded, productos, export PDF); ver/editar/
     * pausar lo ya creado sigue disponible.
     */
    private boolean budgetSuspended;
 
    // ─── Financiero ───────────────────────────────────────────────────────────
 
    /**
     * Saldo disponible en centavos.
     * Solo aplica para STANDARD/PREMIUM — para BASIC es 0.
     */
    private long remainingBudgetCents;
 
    /** Porcentaje de comisión por venta que cobra la app */
    private int commissionRate;
 
    // ─── Capacidades (de PlanFeature) ─────────────────────────────────────────
 
    private boolean canAdvertise;
    private boolean canUseGames;
    private boolean canUseSurveys;
    private int maxProducts;
    private int maxAds;
    private int maxBrandedGames;
    private int maxSurveys;
    private int maxKeysPct;
 
    // ─── Suscripción (solo BASIC) ─────────────────────────────────────────────
 
    /** Días restantes de la suscripción. Solo aplica para BASIC. */
    private Long subscriptionDaysRemaining;
 
    /** Estado de la wallet: INACTIVE, ACTIVE, LOW_BALANCE, EXHAUSTED */
    private String walletStatus;
}

