package com.verygana2.models.enums.marketplace;

public enum KeyCoveragePromotionStatus {
    /** Editable, sin efecto sobre ninguna compra. */
    DRAFT,
    /** Autorizada por el admin, con su garantía apartada. Aplica dentro de su vigencia. */
    AUTHORIZED,
    /** Suspendida por el admin: deja de aplicar hasta que se reanude. */
    PAUSED,
    /** Cerrada, por fecha o por el admin. No vuelve a aplicar. */
    ENDED
}
