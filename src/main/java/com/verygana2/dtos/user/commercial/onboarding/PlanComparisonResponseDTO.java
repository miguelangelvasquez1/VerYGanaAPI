package com.verygana2.dtos.user.commercial.onboarding;

import java.util.List;

import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paso 6-7: catálogo completo de planes activos (BASIC/STANDARD/PREMIUM) para que
 * el front arme una tabla comparativa, marcando cuál es el recomendado según la
 * ruta (A-E) del comercial. El empresario puede aceptar cualquiera de los planes
 * listados aquí, no solo el recomendado (ver POST /commercials/onboarding/plan/accept).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanComparisonResponseDTO {
    private PlanCode recommendedPlanCode;

    /** true para rutas D/E: el catálogo estándar es orientativo, un asesor debe confirmar condiciones. */
    private boolean requiresAdvisorContact;

    /** Notas económicas comunes a todos los planes (no varían por plan). */
    private String taxNote;
    private String liquidationConditions;

    private List<PlanOptionDTO> plans;
}
