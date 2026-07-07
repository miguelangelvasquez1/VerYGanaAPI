package com.verygana2.models.enums;

public enum BrandingRequestStatus {
    DRAFT,                       // anunciante completa la solicitud (sube recursos corporativos)
    PENDING_REVIEW,              // enviado al admin para revisión
    APPROVED,                    // admin aprueba y asigna diseñador
    REJECTED,                    // admin rechaza (con notas)
    DESIGN_IN_PROGRESS,          // diseñador está creando assets
    PENDING_ADVERTISER_APPROVAL, // diseñador publicó propuesta, anunciante debe revisar
    CHANGES_REQUESTED,           // anunciante rechazó propuesta, vuelve al diseñador
    LAUNCHED,                    // anunciante aprobó diseño → Campaign creada y activa
    CANCELLED                    // cancelado en cualquier punto
}
