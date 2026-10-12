package com.verygana2.models.enums.marketplace;

public enum KeyCoveragePromotionOfferStatus {
    PENDING_ACCEPTANCE,
    /** El empresario aceptó expresamente: solo en este estado la promoción aplica a su producto. */
    ACCEPTED,
    REJECTED,
    /** El empresario se retiró después de aceptar. Solo afecta compras futuras. */
    WITHDRAWN
}
