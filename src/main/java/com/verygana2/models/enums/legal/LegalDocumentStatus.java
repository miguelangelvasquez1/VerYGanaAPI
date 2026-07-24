package com.verygana2.models.enums.legal;

public enum LegalDocumentStatus {
    PENDING,    // Creado, esperando que el archivo se suba a R2
    VALIDATED,  // Subido y validado (tamaño/mime real) — versión publicada
    ORPHANED    // Subida descartada/cancelada
}
