package com.verygana2.dtos.user.commercial.onboarding;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Funcionalidades y límites de un plan (sin repetir identidad/precio, que ya
 * viven en el DTO contenedor — ver PlanSummaryResponseDTO.benefits).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanBenefitsDTO {

    // ==================== FUNCIONALIDADES (booleanas) ====================
    private boolean canAdvertise;
    private boolean canUseGames;
    private boolean canUseSurveys;
    private boolean canUsePets;

    // ==================== LÍMITES (-1 = ilimitado) ====================
    private int maxProducts;
    private int maxAds;
    private int maxBrandedGames;
    private int maxSurveys;

    // ==================== VISIBILIDAD ====================
    private BigDecimal visibilityBoostPct;
}
