package com.verygana2.dtos.user.commercial.onboarding;

import java.util.Set;

import com.verygana2.models.enums.commercial.PrimaryGoal;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resumen de las respuestas del paso 4 (diagnóstico comercial, Q3-Q11), de solo lectura. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticSummaryDTO {
    private PrimaryGoal primaryGoal;
    private Boolean wantsFixedFee;
    private Boolean requiresCustomGames;
    private Set<TechIntegrationNeed> techIntegrationNeeds;
    private String integrationDetails;
    private Boolean regulatedSector;
    private Boolean requiresSpecialNegotiation;
}
