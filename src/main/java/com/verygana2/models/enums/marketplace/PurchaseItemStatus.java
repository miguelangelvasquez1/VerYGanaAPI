package com.verygana2.models.enums.marketplace;

public enum PurchaseItemStatus {
    PENDING,        // Esperando acción del vendedor
    CLAIMED,        // Entregado/Reclamado
    IN_REVIEW,      // Comprador reportó un problema, PQRS abierto (ver PurchaseItem.statusBeforeReview)
    EXPIRED_UNCLAIMED, // Producto fisico que el comprador no reclama (15 dias)
    REFUNDED,          // Producto reembolsado
    CANCELLED         // Cancelado por expiración de la compra
}
