package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommercialOnboardingStatusResponseDTO {
    private OnboardingStep currentStep;
    private boolean termsAccepted;
    private boolean legalIdentificationCompleted;
    private boolean diagnosticCompleted;
    private boolean routeClassified;
    private boolean routeConfirmed;
    private RouteClassificationResponseDTO classification; // null si aún no hay clasificación
    private boolean planAccepted;
    private boolean documentsCompleted;
    private boolean contractGenerated;
    private ContractStatus contractStatus; // null si aún no se ha generado un contrato
    private boolean businessApproved;
    private boolean veryganaReviewed;
    private boolean completed;
}
