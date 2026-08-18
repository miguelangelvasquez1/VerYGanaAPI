package com.verygana2.models.enums;

public enum CatalogRequestStatus {
    PENDING,           // el comercial la envió, nadie la ha tomado
    IN_REVIEW,         // el diseñador la está revisando
    APPROVED,          // aceptada: el diseñador ya puede armar el ítem del catálogo
    ITEM_IN_PROGRESS,  // el diseñador guardó al menos un borrador del ítem
    COMPLETED,         // el ítem se publicó en el catálogo (ver resultCatalogItemId)
    REJECTED
}
