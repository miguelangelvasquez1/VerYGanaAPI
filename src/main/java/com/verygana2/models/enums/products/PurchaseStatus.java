package com.verygana2.models.enums.products;

public enum PurchaseStatus {
    PENDING_PAYMENT,      // Esperando confirmación de pago
    PAID,                 // Pagado, vendedor notificado
    IN_PROGRESS,          // Vendedor preparando/coordinando entrega
    COMPLETED,            // Comprador confirmó que recibió todo
    CANCELLED,            // Cancelado por alguna parte
    DISPUTE              // En disputa (mediación de admin)
}
