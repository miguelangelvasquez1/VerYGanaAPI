package com.verygana2.models.enums;

public enum AssetStatus {
    
    PENDING, // Creado pero no subido a CDN
    VALIDATED, // Validado y subido al CDN, sin campaign asociado
    ATTACHED, // Asociado a una campaign activa
    ORPHANED, // Para borrar en CDN
    DELETED, // Borrados en CDN
    CANCELLED; // Campaign cancelada
}