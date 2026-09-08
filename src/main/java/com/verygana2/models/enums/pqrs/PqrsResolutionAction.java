package com.verygana2.models.enums.pqrs;

/**
 * Acción estructurada que el admin elige al resolver un PQRS vinculado a un
 * PurchaseItem (ver Pqrs.purchaseItem). Para PQRS genéricos (sin ítem
 * vinculado) esta acción no aplica.
 */
public enum PqrsResolutionAction {
    /** El ítem sigue su curso normal (código válido, entrega confirmada, etc.). */
    DISMISS,
    /** Reembolso: ver PurchaseItemRefundService. */
    REFUND
}
