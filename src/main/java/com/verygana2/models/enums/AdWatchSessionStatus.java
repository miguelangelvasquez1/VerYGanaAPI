package com.verygana2.models.enums;

public enum AdWatchSessionStatus {

    ACTIVE,        // anuncio entregado, aún viendo
    WATCHED,       // video visto completo (>= umbral), XP otorgado, aún sin like
    LIKED,         // ya se dio like / llaves entregadas
    EXPIRED,       // nunca completó / sesión inválida
    INVALIDATED
}
