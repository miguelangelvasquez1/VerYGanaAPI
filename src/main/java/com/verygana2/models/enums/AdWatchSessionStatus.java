package com.verygana2.models.enums;

public enum AdWatchSessionStatus {
    
    ACTIVE,        // anuncio entregado, aún viendo
    LIKED,         // ya se dio like / rewarded
    EXPIRED,    // nunca completó / sesión inválida
    INVALIDATED
}
