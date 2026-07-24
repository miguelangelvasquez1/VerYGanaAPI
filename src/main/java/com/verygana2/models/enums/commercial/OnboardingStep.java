package com.verygana2.models.enums.commercial;

/** Paso actual del flujo de registro comercial extendido. */
public enum OnboardingStep {
    TERMS_PENDING,
    LEGAL_IDENTIFICATION_PENDING,
    DIAGNOSTIC_PENDING,
    CLASSIFICATION_PENDING,
    PLAN_PENDING,               // Paso 6-7: configuración de plan + resumen económico, pendiente de aceptación
    DOCUMENTS_PENDING,          // Paso 8: carga documental
    CONTRACT_PENDING,           // Paso 9: pendiente de generar el contrato
    BUSINESS_REVIEW_PENDING,    // Paso 10: contrato generado, pendiente de revisión/aprobación del empresario
    VERYGANA_REVIEW_PENDING,    // Paso 11: pendiente de revisión de VERYGANA
    SIGNATURE_PENDING,          // Paso 11b: contrato aprobado, enviado a firma electrónica
    PAYMENT_PENDING,            // Paso 12: firmado, pendiente de pago/activación
    COMPLETED
}
