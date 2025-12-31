package com.verygana2.models.enums;

public enum AssetStatus {
    
    PENDING,
    VALIDATED,
    ORPHANED, // Para borrar en CDN
    DELETED, // Borrados en CDN
    CANCELLED; // Campaign cancelada
}