package com.verygana2.models.enums.commercial;

public enum CommercialDocumentStatus {
    PENDING,    // Creado pero no confirmado en storage
    VALIDATED,  // Subido y validado (tamaño/mime real). No significa que alguien revisó el contenido.
    ORPHANED,   // Descartado durante el onboarding, antes de enviarlo a revisión. Se borra de storage.
    /**
     * Reemplazado por una versión más nueva desde el expediente (después del onboarding).
     * Se conserva en storage y en el historial: nunca se borra.
     */
    SUPERSEDED
}
