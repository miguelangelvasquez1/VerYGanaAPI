package com.verygana2.models.enums.commercial.diagnostic;

/**
 * Capacidad de aprobar recursos institucionales: F-7 y PR-4. DEPENDE_APROBACION
 * mantiene la candidatura Premium como condicionada (§13: "Depende de aprobación =
 * candidatura condicionada"); NO la excluye.
 */
public enum BudgetApproval {
    SI,
    DEPENDE_APROBACION,
    NO
}
