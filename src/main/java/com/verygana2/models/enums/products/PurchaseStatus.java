package com.verygana2.models.enums.products;

public enum PurchaseStatus {
    PENDING,           // Pago pendiente
    PAYMENT_FAILED,    // Pago falló
    CONFIRMED,         // Pago confirmado, preparando envío
    PROCESSING,        // Vendedor preparando el pedido
    SHIPPED,           // En camino
    DELIVERED,         // Entregado
    CANCELLED,         // Cancelado
    REFUNDED          // Reembolsado
}
