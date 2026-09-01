package com.verygana2.dtos.finance.plans.responses;

import java.util.List;

import com.verygana2.dtos.user.commercial.onboarding.PlanOptionDTO;
import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catálogo completo de planes activos (BASIC/STANDARD/PREMIUM) para que el comercial
 * ya onboardeado los compare (vista tarjetas o tabla) y decida si cambiar o recargar.
 * A diferencia de PlanComparisonResponseDTO (onboarding, marca el recomendado por
 * ruta), este marca cuál es el plan actualmente activo del comercial.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanCatalogResponseDTO {
    /** Código del plan activo del comercial. Null si no tiene plan vigente. */
    private PlanCode currentPlanCode;

    private List<PlanOptionDTO> plans;
}
