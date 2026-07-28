package com.verygana2.models.enums.commercial;

/** Paso actual del flujo de registro comercial extendido. */
public enum OnboardingStep {
    TERMS_PENDING,
    LEGAL_IDENTIFICATION_PENDING,
    DIAGNOSTIC_PENDING,
    CLASSIFICATION_PENDING,

    /**
     * Terminal para Ruta D (integración técnica): el diagnóstico ya clasificó al
     * comercial como proveedor/aliado que requiere integración técnica. No pasa por
     * plan, documentos, contrato ni pago dentro de la plataforma — un asesor de
     * VERYGANA se contacta y todo eso se coordina manualmente por fuera. El frontend
     * debe mostrar únicamente un mensaje de "nos pondremos en contacto" y no continuar
     * el wizard de onboarding.
     */
    ADVISOR_CONTACT_PENDING,

    PLAN_PENDING,               // Paso 6-7: configuración de plan + resumen económico, pendiente de aceptación
    DOCUMENTS_PENDING,          // Paso 8: carga documental
    CONTRACT_PENDING,           // Paso 9: pendiente de generar el contrato
    BUSINESS_REVIEW_PENDING,    // Paso 10: contrato generado, pendiente de revisión/aprobación del empresario
    VERYGANA_REVIEW_PENDING,    // Paso 11: pendiente de revisión de VERYGANA
    SIGNATURE_PENDING,          // Paso 11b: contrato aprobado, enviado a firma electrónica
    PAYMENT_PENDING,            // Paso 12: firmado, pendiente de pago/activación
    COMPLETED
}
