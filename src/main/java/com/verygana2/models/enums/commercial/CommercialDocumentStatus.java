package com.verygana2.models.enums.commercial;

public enum CommercialDocumentStatus {
    PENDING,    // Creado pero no confirmado en storage
    VALIDATED,  // Subido y validado (tamaño/mime real)
    ORPHANED    // Descartado/reemplazado, pendiente de limpieza
}
