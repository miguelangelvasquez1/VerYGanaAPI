package com.verygana2.models.enums.commercial;

/** Pasos 9-11: generación y revisión del Contrato Marco. */
public enum ContractStatus {
    PENDING_BUSINESS_REVIEW,   // Generado, esperando que el empresario lo revise/apruebe (paso 10)
    PENDING_VERYGANA_REVIEW,   // Aprobado por el empresario, esperando revisión de VERYGANA (paso 11)
    APPROVED,
    REJECTED
}
