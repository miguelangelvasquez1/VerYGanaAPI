package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

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

    /**
     * Motivo del rechazo cuando contractStatus == REJECTED — así el front tiene
     * el "por qué" disponible en la misma llamada que usa para decidir a qué
     * paso/tab redirigir, sin depender de un segundo fetch a /contract.
     */
    private String rejectionReason;

    /**
     * Fecha del rechazo (misma condición que rejectionReason). Sirve como llave
     * estable para que el front recuerde "ya le mostré el modal de este rechazo
     * al usuario" sin volver a mostrarlo en cada navegación, pero sí mostrarlo
     * de nuevo ante un rechazo nuevo.
     */
    private ZonedDateTime rejectedAt;
}
