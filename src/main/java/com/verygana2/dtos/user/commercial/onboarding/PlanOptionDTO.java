package com.verygana2.dtos.user.commercial.onboarding;

import java.math.BigDecimal;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una fila del catálogo de planes, con todos los datos necesarios para armar
 * una tabla comparativa en el front (precio/depósito, comisión, límites y
 * funcionalidades). Se usa tanto para el catálogo completo (PlanComparisonResponseDTO)
 * como, indirectamente, para el detalle de un plan ya aceptado (PlanSummaryResponseDTO).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanOptionDTO {
    private PlanCode planCode;
    private String planName;
    private String description;

    /** true si este es el plan que el motor de reglas recomienda para la ruta del comercial. */
    private boolean recommended;

    /** Solo aplica a planes de suscripción mensual (BASIC). */
    private Long monthlyFeeCents;

    /** Solo aplica a planes de inversión (STANDARD/PREMIUM). */
    private Long minInvestmentCents;
    private Long maxInvestmentCents;

    private int saleCommissionPct;
    private int maxKeysPct;

    // ==================== FUNCIONALIDADES (booleanas) ====================
    private boolean canAdvertise;
    private boolean canUseGames;
    private boolean canUseSurveys;
    private boolean canHavePets;

    // ==================== LÍMITES (-1 = ilimitado) ====================
    private int maxProducts;
    private int maxAds;
    private int maxBrandedGames;
    private int maxSurveys;

    // ==================== VISIBILIDAD ====================
    private BigDecimal visibilityBoostPct;
}
