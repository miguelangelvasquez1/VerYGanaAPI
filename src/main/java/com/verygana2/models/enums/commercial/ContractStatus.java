package com.verygana2.models.enums.commercial;

/** Pasos 9-11: generación y revisión del Contrato Marco. */
public enum ContractStatus {
    PENDING_BUSINESS_REVIEW,   // Generado, esperando que el empresario lo revise/apruebe (paso 10)
    PENDING_VERYGANA_REVIEW,   // Aprobado por el empresario, esperando revisión de VERYGANA (paso 11)
    APPROVED,                  // Contenido aprobado por VERYGANA; dispara el envío a firma electrónica
    PENDING_SIGNATURE,         // Enviado al proveedor de firma electrónica, esperando firma del empresario
    SIGNED,                    // Firmado; el onboarding pasa a PAYMENT_PENDING
    REJECTED
}
